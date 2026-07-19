package com.signet.analytics;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Плановый пересчёт агрегатов за текущий день (страховка на случай пропущенных
 * событий и для перехода через полночь). Основной пересчёт — событийный
 * (см. {@link StatsEventListener}).
 */
@Component
public class RollupJob {

    private final StatsRollupService rollup;

    public RollupJob(StatsRollupService rollup) {
        this.rollup = rollup;
    }

    @Scheduled(cron = "0 5 * * * *")   // каждый час в :05
    public void rollupToday() {
        rollup.rollupToday();
    }
}
