package com.signet.analytics;

import com.signet.shared.event.Events;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Событийный пересчёт статистики: после приёма и после отправки письма обновляем
 * агрегаты за сегодня — дашборд остаётся актуальным без ожидания планировщика.
 */
@Component
public class StatsEventListener {

    private final StatsRollupService rollup;

    public StatsEventListener(StatsRollupService rollup) {
        this.rollup = rollup;
    }

    @ApplicationModuleListener
    public void onReceived(Events.EmailReceived event) {
        rollup.rollupToday();
    }

    @ApplicationModuleListener
    public void onSent(Events.EmailSent event) {
        rollup.rollupToday();
    }
}
