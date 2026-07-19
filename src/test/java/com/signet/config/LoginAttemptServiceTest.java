package com.signet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.7";

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        AppSecurityProperties props = new AppSecurityProperties();
        props.setMaxLoginAttempts(3);
        props.setBlockDuration(Duration.ofMillis(150));   // короткая, чтобы тест был быстрым
        service = new LoginAttemptService(props);
    }

    @Test
    void блокирует_после_превышения_лимита_попыток() {
        service.loginFailed(IP);
        service.loginFailed(IP);
        assertThat(service.isBlocked(IP)).isFalse();       // 2 из 3 — ещё пускаем

        service.loginFailed(IP);

        assertThat(service.isBlocked(IP)).isTrue();
        assertThat(service.retryAfterSeconds(IP)).isPositive();
    }

    @Test
    void успешный_вход_сбрасывает_счётчик() {
        service.loginFailed(IP);
        service.loginFailed(IP);

        service.loginSucceeded(IP);
        service.loginFailed(IP);

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void блокировка_снимается_по_истечении_времени() throws Exception {
        service.loginFailed(IP);
        service.loginFailed(IP);
        service.loginFailed(IP);
        assertThat(service.isBlocked(IP)).isTrue();

        Thread.sleep(200);                                 // дольше blockDuration

        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void блокировка_не_затрагивает_другие_адреса() {
        service.loginFailed(IP);
        service.loginFailed(IP);
        service.loginFailed(IP);

        assertThat(service.isBlocked("198.51.100.1")).isFalse();
    }

    @Test
    void неизвестный_адрес_не_заблокирован() {
        assertThat(service.isBlocked("198.51.100.2")).isFalse();
        assertThat(service.retryAfterSeconds("198.51.100.2")).isZero();
    }
}
