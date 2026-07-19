package com.signet.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Учёт неудачных попыток входа по IP: после N провалов адрес блокируется
 * на заданное время. Защищает basic-аутентификацию от перебора паролей.
 */
@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int MAX_TRACKED_IPS = 10_000;

    private final AppSecurityProperties props;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(AppSecurityProperties props) {
        this.props = props;
    }

    public void loginFailed(String ip) {
        purgeIfTooLarge();
        Attempt attempt = attempts.computeIfAbsent(ip, k -> new Attempt());
        int count = attempt.count.incrementAndGet();
        attempt.lastFailure = Instant.now();
        if (count == props.getMaxLoginAttempts()) {
            log.warn("IP {} заблокирован на {} после {} неудачных попыток входа",
                    ip, props.getBlockDuration(), count);
        }
    }

    public void loginSucceeded(String ip) {
        attempts.remove(ip);
    }

    public boolean isBlocked(String ip) {
        Attempt attempt = attempts.get(ip);
        if (attempt == null) {
            return false;
        }
        Duration since = Duration.between(attempt.lastFailure, Instant.now());
        if (since.compareTo(props.getBlockDuration()) > 0) {
            attempts.remove(ip);   // срок блокировки истёк
            return false;
        }
        return attempt.count.get() >= props.getMaxLoginAttempts();
    }

    /** Сколько секунд осталось до разблокировки (для заголовка Retry-After). */
    public long retryAfterSeconds(String ip) {
        Attempt attempt = attempts.get(ip);
        if (attempt == null) {
            return 0;
        }
        long left = props.getBlockDuration().toSeconds()
                - Duration.between(attempt.lastFailure, Instant.now()).toSeconds();
        return Math.max(1, left);
    }

    private void purgeIfTooLarge() {
        if (attempts.size() < MAX_TRACKED_IPS) {
            return;
        }
        Instant cutoff = Instant.now().minus(props.getBlockDuration());
        attempts.entrySet().removeIf(e -> e.getValue().lastFailure.isBefore(cutoff));
    }

    private static final class Attempt {
        private final AtomicInteger count = new AtomicInteger();
        private volatile Instant lastFailure = Instant.now();
    }
}
