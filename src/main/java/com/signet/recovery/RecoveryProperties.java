package com.signet.recovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.recovery")
public class RecoveryProperties {

    /**
     * Сколько письмо может пробыть в промежуточном статусе, прежде чем считать
     * его зависшим. Должно быть заведомо больше времени обычной обработки
     * (генерация на локальной модели — минуты).
     */
    private Duration stuckAfter = Duration.ofMinutes(15);

    /** Как часто искать зависшие письма. */
    private Duration scanInterval = Duration.ofMinutes(5);

    /** Включён ли автоматический разбор зависших писем. */
    private boolean enabled = true;

    public Duration getStuckAfter() {
        return stuckAfter;
    }

    public void setStuckAfter(Duration stuckAfter) {
        this.stuckAfter = stuckAfter;
    }

    public Duration getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = scanInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
