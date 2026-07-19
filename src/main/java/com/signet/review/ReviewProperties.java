package com.signet.review;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.review")
public class ReviewProperties {

    /** Через сколько напоминать менеджеру о неотвеченной задаче ревью. */
    private Duration timeout = Duration.ofHours(4);

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
