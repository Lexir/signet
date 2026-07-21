package com.signet.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Защита веб-интерфейса: вход формой + сессия, CSRF через cookie (для SPA), security-заголовки.
 * Ограничение частоты и блокировка переборов — в {@link RateLimitFilter}, а учёт удачных/провальных
 * входов ведётся здесь, в обработчиках формы логина (у формы нет заголовка Authorization).
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final String dashboardPassword;
    private final LoginAttemptService loginAttempts;
    private final AppSecurityProperties securityProps;

    public SecurityConfig(@Value("${spring.security.user.password:}") String dashboardPassword,
                          LoginAttemptService loginAttempts,
                          AppSecurityProperties securityProps) {
        this.dashboardPassword = dashboardPassword;
        this.loginAttempts = loginAttempts;
        this.securityProps = securityProps;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Публично: health-проба, вход и статус входа.
                        .requestMatchers("/actuator/health", "/api/login", "/api/me").permitAll()
                        // Публично: оболочка SPA и статика — данные за ней всё равно под 401.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico",
                                "/favicon.svg", "/assets/**",
                                "/dashboard", "/settings", "/mailbox", "/mailbox/**").permitAll()
                        .anyRequest().authenticated())
                // Вход формой: JSON-ответы вместо редиректов (дружелюбно к SPA).
                .formLogin(form -> form
                        .loginProcessingUrl("/api/login")
                        .successHandler(this::onLoginSuccess)
                        .failureHandler(this::onLoginFailure))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value())))
                // Неавторизованный запрос → 401 (а не редирект на HTML-логин).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(this::unauthorized))
                // CSRF-токен в cookie XSRF-TOKEN; SPA читает его и шлёт в заголовке X-XSRF-TOKEN.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/actuator/health"))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())                       // защита от clickjacking
                        .contentTypeOptions(opts -> {
                        })                                                          // nosniff
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        // Всё бандлится локально (SPA), внешних CDN больше нет — политику затянули.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "connect-src 'self'; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; form-action 'self'")))
                // Сессия создаётся только при необходимости; фиксация сессии защищена по умолчанию.
                .sessionManagement(session -> session.maximumSessions(5));
        return http.build();
    }

    // --- Обработчики формы логина: JSON + учёт попыток по IP ---

    private void onLoginSuccess(HttpServletRequest req, HttpServletResponse res,
                                org.springframework.security.core.Authentication auth) throws IOException {
        loginAttempts.loginSucceeded(ClientIp.of(req, securityProps));
        writeJson(res, HttpStatus.OK, "{\"authenticated\":true}");
    }

    private void onLoginFailure(HttpServletRequest req, HttpServletResponse res,
                                org.springframework.security.core.AuthenticationException ex) throws IOException {
        loginAttempts.loginFailed(ClientIp.of(req, securityProps));
        writeJson(res, HttpStatus.UNAUTHORIZED, "{\"error\":\"bad_credentials\"}");
    }

    private void unauthorized(HttpServletRequest req, HttpServletResponse res,
                              org.springframework.security.core.AuthenticationException ex) throws IOException {
        writeJson(res, HttpStatus.UNAUTHORIZED, "{\"error\":\"unauthenticated\"}");
    }

    private static void writeJson(HttpServletResponse res, HttpStatus status, String body) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(body);
    }

    /** Громко предупреждаем, если пароль дашборда остался дефолтным. */
    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutWeakPassword() {
        List<String> weak = List.of("changeit", "admin", "password", "");
        if (weak.contains(dashboardPassword)) {
            log.warn("!!! Пароль веб-интерфейса дефолтный или пустой. "
                    + "Задайте DASHBOARD_PASS перед публикацией сервиса в сеть !!!");
        }
    }

    /**
     * Форсирует материализацию CSRF-токена, чтобы репозиторий выставил cookie XSRF-TOKEN
     * ещё до логина (SPA должен прочитать его перед первым POST). Стандартный приём Spring Security.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        jakarta.servlet.FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();   // рендерит токен → cookie уходит клиенту
            }
            chain.doFilter(request, response);
        }
    }

    /**
     * SPA-вариант обработчика CSRF: cookie хранит «сырой» токен, из заголовка принимаем его как есть
     * (plain); XOR-маскировка остаётся для рендера в теле ответа (защита от BREACH).
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue) ? this.plain : this.xor)
                    .resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
