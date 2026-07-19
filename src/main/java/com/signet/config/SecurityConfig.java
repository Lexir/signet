package com.signet.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Защита веб-интерфейса: аутентификация, CSRF, security-заголовки.
 * Ограничение частоты запросов и блокировка переборов — в {@link RateLimitFilter}.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final String dashboardPassword;

    public SecurityConfig(@Value("${spring.security.user.password:}") String dashboardPassword) {
        this.dashboardPassword = dashboardPassword;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Только health для проб (без деталей), остальное — под паролем.
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {
                })
                // CSRF включён: формы Thymeleaf (th:action) получают токен автоматически.
                // Исключение — health, он без сессии и только GET.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/health"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())                       // защита от clickjacking
                        .contentTypeOptions(opts -> {
                        })                                                          // nosniff
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' https://cdn.jsdelivr.net; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; form-action 'self'")))
                // Сессия создаётся только при необходимости; фиксация сессии защищена по умолчанию.
                .sessionManagement(session -> session.maximumSessions(5));
        return http.build();
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
}
