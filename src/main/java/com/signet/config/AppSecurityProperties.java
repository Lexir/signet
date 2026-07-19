package com.signet.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки защиты веб-интерфейса от переборов и сканеров. */
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /** Максимум запросов с одного IP в минуту. */
    private int requestsPerMinute = 120;

    /** Сколько неудачных попыток входа допускается до блокировки IP. */
    private int maxLoginAttempts = 5;

    /** На сколько блокируется IP после превышения лимита попыток. */
    private Duration blockDuration = Duration.ofMinutes(15);

    /**
     * Доверять заголовку X-Forwarded-For (включать ТОЛЬКО за обратным прокси,
     * иначе IP можно подделать и обойти лимиты).
     */
    private boolean trustForwardedHeaders = false;

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }

    public void setMaxLoginAttempts(int maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
    }

    public Duration getBlockDuration() {
        return blockDuration;
    }

    public void setBlockDuration(Duration blockDuration) {
        this.blockDuration = blockDuration;
    }

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }
}
