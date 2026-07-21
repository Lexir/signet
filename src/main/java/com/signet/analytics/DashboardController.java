package com.signet.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON-метрики дашборда. Саму страницу рисует SPA. */
@RestController
public class DashboardController {

    private final AnalyticsService analytics;

    public DashboardController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/stats")
    public AnalyticsSnapshot stats() {
        return analytics.snapshot();
    }
}
