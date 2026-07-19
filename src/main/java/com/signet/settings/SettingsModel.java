package com.signet.settings;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/** Типизированные срезы настроек, отдаваемые {@link SettingsService}. */
public final class SettingsModel {

    private SettingsModel() {
    }

    /** Настройки Telegram-бота. */
    public record TelegramSettings(String botToken, long managerChatId, boolean enabled) {

        public boolean isConfigured() {
            return enabled && botToken != null && !botToken.isBlank() && managerChatId != 0;
        }
    }

    /**
     * Настройки AI.
     *
     * @param provider     "ollama" или "openai"
     * @param systemPrompt системный промпт генерации черновика ({@code {profile}} — профиль автора)
     */
    public record AiSettings(String provider,
                             String openAiApiKey,
                             String ollamaBaseUrl,
                             String model,
                             double temperature,
                             String systemPrompt) {

        public boolean isOllama() {
            return "ollama".equalsIgnoreCase(provider);
        }
    }

    /**
     * Настройки опроса почты: интервал + окно рабочих часов. Всё живёт в БД и
     * правится на {@code /settings}. Вне окна поллинг вообще не запускается —
     * планировщик спит до открытия ближайшего рабочего дня (меньше обращений к IMAP).
     */
    public record PollingSettings(int intervalSeconds,
                                  boolean windowEnabled,
                                  ZoneId zone,
                                  Set<DayOfWeek> days,
                                  LocalTime start,
                                  LocalTime end) {

        /**
         * Ближайший момент не раньше {@code candidate}, попадающий в рабочее окно.
         * Окно выключено, дни не заданы или {@code candidate} уже внутри — вернуть как есть;
         * иначе — открытие ({@code start}) ближайшего рабочего дня.
         */
        public Instant nextAllowed(Instant candidate) {
            if (!windowEnabled || days.isEmpty() || !start.isBefore(end)) {
                return candidate;
            }
            ZonedDateTime c = candidate.atZone(zone);
            for (int i = 0; i < 8; i++) {                       // максимум перепрыгнуть выходные
                LocalDate day = c.toLocalDate();
                if (days.contains(day.getDayOfWeek())) {
                    ZonedDateTime open = ZonedDateTime.of(day, start, zone);
                    ZonedDateTime close = ZonedDateTime.of(day, end, zone);
                    if (c.isBefore(open)) {
                        return open.toInstant();                // до открытия сегодня → ждём открытия
                    }
                    if (c.isBefore(close)) {
                        return c.toInstant();                   // внутри окна → как есть
                    }
                }
                c = ZonedDateTime.of(day.plusDays(1), start, zone);  // после закрытия/выходной → утро следующего дня
            }
            return c.toInstant();
        }

        /** Дни как "MON,TUE,..." — для отображения в форме настроек. */
        public String daysCsv() {
            return days.stream().map(d -> d.name().substring(0, 3)).collect(Collectors.joining(","));
        }
    }

    /** Плейсхолдер профиля автора внутри системного промпта. */
    public static final String PROFILE_PLACEHOLDER = "{profile}";

    /** Промпт по умолчанию — подставляется при первом старте и по кнопке «Сбросить». */
    public static final String DEFAULT_DRAFT_PROMPT = """
            Ты помогаешь человеку отвечать в его личной переписке: готовишь черновик
            ОТ ЕГО ИМЕНИ, от первого лица. Автор сам прочитает и одобрит текст перед отправкой.

            Профиль автора (от чьего лица пишем):
            {profile}

            Правила:
            - Пиши от первого лица, живым естественным языком, как человек пишет в личной переписке.
              Без канцелярита и шаблонных фраз.
            - Отвечай на том же языке, на котором написано письмо собеседника.
            - Опирайся только на профиль автора и историю переписки. НЕ выдумывай факты о его жизни
              (работа, поездки, семья, планы, события). Если чего-то не знаешь — задай вопрос
              собеседнику или напиши общими словами.
            - Не давай от имени автора обещаний и обязательств.
            - Никогда не проси денег, переводов, оплаты и любой финансовой помощи.
            - Уважай собеседника: никакого давления, манипуляций и навязчивости.
            - Дополнительно переведи свой ответ на русский — это нужно автору для проверки,
              собеседнику перевод не отправляется.
            """;

    /** Ключи настроек в таблице settings. */
    public static final class Keys {

        public static final String TG_BOT_TOKEN = "telegram.bot-token";
        public static final String TG_MANAGER_CHAT_ID = "telegram.manager-chat-id";
        public static final String TG_ENABLED = "telegram.enabled";

        public static final String AI_PROVIDER = "ai.provider";
        public static final String AI_OPENAI_KEY = "ai.openai-api-key";
        public static final String AI_OLLAMA_URL = "ai.ollama-base-url";
        public static final String AI_MODEL = "ai.model";
        public static final String AI_TEMPERATURE = "ai.temperature";
        public static final String AI_SYSTEM_PROMPT = "ai.system-prompt";

        /** Интервал опроса почты, секунды. */
        public static final String POLL_INTERVAL_SECONDS = "mail.poll-interval-seconds";

        // Окно рабочих часов опроса почты (группа mail.*).
        public static final String POLL_WINDOW_ENABLED = "mail.window-enabled";
        public static final String POLL_WINDOW_ZONE = "mail.window-zone";
        public static final String POLL_WINDOW_DAYS = "mail.window-days";
        public static final String POLL_WINDOW_START = "mail.window-start";
        public static final String POLL_WINDOW_END = "mail.window-end";

        private Keys() {
        }
    }
}
