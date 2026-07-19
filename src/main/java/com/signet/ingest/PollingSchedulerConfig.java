package com.signet.ingest;

import com.signet.settings.SettingsService;
import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Опрос почты по динамическому интервалу: значение перечитывается из настроек
 * перед каждым циклом, поэтому изменение в UI применяется без перезапуска
 * (не позднее, чем через один текущий интервал).
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
            return last.plusSeconds(settings.pollIntervalSeconds());
        });
    }
}
