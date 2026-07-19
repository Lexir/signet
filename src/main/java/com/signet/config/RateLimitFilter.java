package com.signet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ограничение частоты запросов по IP + блокировка адресов, подбирающих пароль.
 * Стоит перед цепочкой Spring Security, чтобы отсекать сканеры до аутентификации.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int MAX_TRACKED_IPS = 10_000;

    private final AppSecurityProperties props;
    private final LoginAttemptService loginAttempts;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppSecurityProperties props, LoginAttemptService loginAttempts) {
        this.props = props;
        this.loginAttempts = loginAttempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = clientIp(request);

        // 1. IP в блокировке за перебор пароля — не пускаем дальше вообще.
        if (loginAttempts.isBlocked(ip)) {
            reject(response, loginAttempts.retryAfterSeconds(ip));
            return;
        }

        // 2. Общий лимит запросов в минуту.
        if (!allow(ip)) {
            log.warn("Превышен лимит запросов с IP {}", ip);
            reject(response, 60);
            return;
        }

        chain.doFilter(request, response);

        // 3. По итогу запроса учитываем результат аутентификации.
        //    Провалом входа считаем только 401 на запрос, где клиент РЕАЛЬНО прислал
        //    учётные данные. Голый 401 без Authorization — это штатный basic-auth
        //    challenge (первый заход/новая вкладка), а не перебор пароля; засчитывать
        //    его нельзя, иначе несколько анонимных запросов (документ + favicon и т.п.)
        //    заблокировали бы честного пользователя ещё до ввода пароля.
        boolean credentialsPresented = request.getHeader("Authorization") != null;
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED && credentialsPresented) {
            loginAttempts.loginFailed(ip);
        } else if (response.getStatus() < 400 && request.getUserPrincipal() != null) {
            loginAttempts.loginSucceeded(ip);
        }
    }

    private boolean allow(String ip) {
        long minute = System.currentTimeMillis() / 60_000;
        if (buckets.size() >= MAX_TRACKED_IPS) {
            buckets.entrySet().removeIf(e -> e.getValue().minute < minute);
        }
        Bucket bucket = buckets.compute(ip, (k, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Bucket(minute);
            }
            return existing;
        });
        return bucket.count.incrementAndGet() <= props.getRequestsPerMinute();
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);                       // Too Many Requests
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Too many requests");
    }

    /** IP клиента; X-Forwarded-For учитывается только если это явно разрешено. */
    private String clientIp(HttpServletRequest request) {
        if (props.isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static final class Bucket {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Bucket(long minute) {
            this.minute = minute;
        }
    }
}
