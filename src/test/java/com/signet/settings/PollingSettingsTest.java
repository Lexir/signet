package com.signet.settings;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.signet.settings.SettingsModel.PollingSettings;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Проверяет сдвиг следующего запуска опроса в рабочее окно.
 * Окно: пн–пт, 08:00–20:00, Europe/Moscow. Опорные даты (2026-07):
 * 15 — среда, 17 — пятница, 18 — суббота, 20 — понедельник.
 */
class PollingSettingsTest {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private PollingSettings window() {
        return new PollingSettings(60, true, MSK,
                EnumSet.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(20, 0));
    }

    private Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(MSK).toInstant();
    }

    @Test
    void внутри_окна_момент_не_меняется() {
        assertThat(window().nextAllowed(at("2026-07-15T10:00")))
                .isEqualTo(at("2026-07-15T10:00"));
    }

    @Test
    void до_открытия_ждём_08_00() {
        assertThat(window().nextAllowed(at("2026-07-15T06:30")))
                .isEqualTo(at("2026-07-15T08:00"));
    }

    @Test
    void после_закрытия_переносим_на_утро_следующего_дня() {
        assertThat(window().nextAllowed(at("2026-07-15T21:00")))
                .isEqualTo(at("2026-07-16T08:00"));
    }

    @Test
    void ровно_в_закрытие_окно_уже_закрыто() {
        assertThat(window().nextAllowed(at("2026-07-15T20:00")))
                .isEqualTo(at("2026-07-16T08:00"));
    }

    @Test
    void вечер_пятницы_перепрыгивает_выходные() {
        assertThat(window().nextAllowed(at("2026-07-17T21:00")))
                .isEqualTo(at("2026-07-20T08:00"));
    }

    @Test
    void суббота_переносится_на_понедельник() {
        assertThat(window().nextAllowed(at("2026-07-18T12:00")))
                .isEqualTo(at("2026-07-20T08:00"));
    }

    @Test
    void выключенное_окно_возвращает_момент_как_есть() {
        PollingSettings off = new PollingSettings(60, false, MSK,
                EnumSet.of(MONDAY), LocalTime.of(8, 0), LocalTime.of(20, 0));
        assertThat(off.nextAllowed(at("2026-07-18T03:00")))
                .isEqualTo(at("2026-07-18T03:00"));
    }
}
