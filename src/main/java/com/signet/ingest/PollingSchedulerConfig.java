package com.signet.ingest;

import com.signet.settings.SettingsModel.PollingSettings;
import com.signet.settings.SettingsService;
import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Опрос почты по динамическому интервалу с окном рабочих часов. Интервал и окно
 * перечитываются из настроек перед каждым циклом, поэтому изменения в UI
 * применяются без перезапуска. Вне рабочего окна планировщик спит до открытия
 * ближайшего рабочего дня — никаких обращений к IMAP ночью и в выходные.
 */
@Configuration
public class PollingSchedulerConfig implements SchedulingConfigurer {

    private final MailPoller poller;
    private final SettingsService settings;

    public PollingSchedulerConfig(MailPoller poller, SettingsService settings) {
        this.poller = poller;
        this.settings = settings;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(poller::poll, context -> {
            Instant last = context.lastCompletion() != null ? context.lastCompletion() : Instant.now();
            PollingSettings p = settings.polling();
            return p.nextAllowed(last.plusSeconds(p.intervalSeconds()));
        });
    }
}
