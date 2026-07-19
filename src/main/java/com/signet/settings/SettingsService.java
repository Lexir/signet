package com.signet.settings;

import com.signet.settings.SettingsModel.AiSettings;
import com.signet.settings.SettingsModel.Keys;
import com.signet.settings.SettingsModel.PollingSettings;
import com.signet.settings.SettingsModel.TelegramSettings;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Настройки интеграций, редактируемые через UI и хранимые в БД.
 * При первом старте таблица заполняется значениями из окружения/yml,
 * чтобы уже работающая конфигурация не потерялась.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    // Дефолты окна опроса — используются как сид и как фолбэк при битых значениях.
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Moscow");
    private static final Set<DayOfWeek> DEFAULT_DAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private static final LocalTime DEFAULT_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(20, 0);

    private final SettingRepository repo;
    private final SecretCipher cipher;
    private final ApplicationEventPublisher events;

    // Значения из окружения — используются как сид при первом запуске.
    private final String seedBotToken;
    private final long seedChatId;
    private final String seedProvider;
    private final String seedOpenAiKey;
    private final String seedOllamaUrl;
    private final String seedOllamaModel;
    private final String seedOpenAiModel;
    private final java.time.Duration seedPollInterval;

    public SettingsService(SettingRepository repo,
                           SecretCipher cipher,
                           ApplicationEventPublisher events,
                           @Value("${app.telegram.bot-token:}") String seedBotToken,
                           @Value("${app.telegram.manager-chat-id:0}") long seedChatId,
                           @Value("${spring.ai.model.chat:ollama}") String seedProvider,
                           @Value("${spring.ai.openai.api-key:}") String seedOpenAiKey,
                           @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String seedOllamaUrl,
                           @Value("${spring.ai.ollama.chat.options.model:qwen2.5:14b-instruct}") String seedOllamaModel,
                           @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String seedOpenAiModel,
                           @Value("${app.poll-interval:45s}") java.time.Duration seedPollInterval) {
        this.repo = repo;
        this.cipher = cipher;
        this.events = events;
        this.seedBotToken = seedBotToken;
        this.seedChatId = seedChatId;
        this.seedProvider = seedProvider;
        this.seedOpenAiKey = seedOpenAiKey;
        this.seedOllamaUrl = seedOllamaUrl;
        this.seedOllamaModel = seedOllamaModel;
        this.seedOpenAiModel = seedOpenAiModel;
        this.seedPollInterval = seedPollInterval;
    }

    /** Первичное заполнение настроек из окружения. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        if (repo.count() > 0) {
            return;
        }
        log.info("Настройки пусты — заполняю значениями из окружения");
        boolean ollama = "ollama".equalsIgnoreCase(seedProvider);
        put(Keys.TG_BOT_TOKEN, seedBotToken, true);
        put(Keys.TG_MANAGER_CHAT_ID, String.valueOf(seedChatId), false);
        put(Keys.TG_ENABLED, "true", false);
        put(Keys.AI_PROVIDER, seedProvider, false);
        put(Keys.AI_OPENAI_KEY, seedOpenAiKey, true);
        put(Keys.AI_OLLAMA_URL, seedOllamaUrl, false);
        put(Keys.AI_MODEL, ollama ? seedOllamaModel : seedOpenAiModel, false);
        put(Keys.AI_TEMPERATURE, "0.3", false);
        put(Keys.AI_SYSTEM_PROMPT, SettingsModel.DEFAULT_DRAFT_PROMPT, false);
        put(Keys.POLL_INTERVAL_SECONDS, String.valueOf(seedPollInterval.toSeconds()), false);
        put(Keys.POLL_WINDOW_ENABLED, "true", false);
        put(Keys.POLL_WINDOW_ZONE, DEFAULT_ZONE.getId(), false);
        put(Keys.POLL_WINDOW_DAYS, daysToCsv(DEFAULT_DAYS), false);
        put(Keys.POLL_WINDOW_START, DEFAULT_START.toString(), false);
        put(Keys.POLL_WINDOW_END, DEFAULT_END.toString(), false);
    }

    /** Настройки опроса почты: интервал + окно рабочих часов (вся группа {@code mail.*} одним запросом). */
    @Transactional(readOnly = true)
    public PollingSettings polling() {
        Map<String, String> s = load(
                Keys.POLL_INTERVAL_SECONDS, Keys.POLL_WINDOW_ENABLED, Keys.POLL_WINDOW_ZONE,
                Keys.POLL_WINDOW_DAYS, Keys.POLL_WINDOW_START, Keys.POLL_WINDOW_END);
        return new PollingSettings(
                clampInterval(parseLong(s.get(Keys.POLL_INTERVAL_SECONDS))),
                !"false".equalsIgnoreCase(s.get(Keys.POLL_WINDOW_ENABLED)),
                parseZone(s.get(Keys.POLL_WINDOW_ZONE)),
                parseDays(s.get(Keys.POLL_WINDOW_DAYS)),
                parseTime(s.get(Keys.POLL_WINDOW_START), DEFAULT_START),
                parseTime(s.get(Keys.POLL_WINDOW_END), DEFAULT_END));
    }

    @Transactional
    public void savePolling(int intervalSeconds, boolean windowEnabled,
                            String zone, String days, String start, String end) {
        put(Keys.POLL_INTERVAL_SECONDS, String.valueOf(Math.max(5, intervalSeconds)), false);
        put(Keys.POLL_WINDOW_ENABLED, String.valueOf(windowEnabled), false);
        put(Keys.POLL_WINDOW_ZONE, parseZone(zone).getId(), false);          // валидируем/нормализуем
        put(Keys.POLL_WINDOW_DAYS, daysToCsv(parseDays(days)), false);
        put(Keys.POLL_WINDOW_START, parseTime(start, DEFAULT_START).toString(), false);
        put(Keys.POLL_WINDOW_END, parseTime(end, DEFAULT_END).toString(), false);
    }

    // --- Чтение ---

    @Transactional(readOnly = true)
    public TelegramSettings telegram() {
        return new TelegramSettings(
                get(Keys.TG_BOT_TOKEN),
                parseLong(get(Keys.TG_MANAGER_CHAT_ID)),
                !"false".equalsIgnoreCase(get(Keys.TG_ENABLED)));
    }

    @Transactional(readOnly = true)
    public AiSettings ai() {
        return new AiSettings(
                orDefault(get(Keys.AI_PROVIDER), "ollama"),
                get(Keys.AI_OPENAI_KEY),
                orDefault(get(Keys.AI_OLLAMA_URL), "http://localhost:11434"),
                orDefault(get(Keys.AI_MODEL), "qwen2.5:14b-instruct"),
                parseDouble(get(Keys.AI_TEMPERATURE), 0.3),
                orDefault(get(Keys.AI_SYSTEM_PROMPT), SettingsModel.DEFAULT_DRAFT_PROMPT));
    }

    // --- Запись ---

    @Transactional
    public void saveTelegram(String botToken, long managerChatId, boolean enabled) {
        // Пустой токен в форме = «не менять».
        if (botToken != null && !botToken.isBlank()) {
            put(Keys.TG_BOT_TOKEN, botToken, true);
        }
        put(Keys.TG_MANAGER_CHAT_ID, String.valueOf(managerChatId), false);
        put(Keys.TG_ENABLED, String.valueOf(enabled), false);
        events.publishEvent(new SettingsChangedEvent(SettingsChangedEvent.TELEGRAM));
    }

    @Transactional
    public void saveAi(String provider, String openAiKey, String ollamaUrl, String model, double temperature) {
        put(Keys.AI_PROVIDER, provider, false);
        if (openAiKey != null && !openAiKey.isBlank()) {
            put(Keys.AI_OPENAI_KEY, openAiKey, true);
        }
        put(Keys.AI_OLLAMA_URL, ollamaUrl, false);
        put(Keys.AI_MODEL, model, false);
        put(Keys.AI_TEMPERATURE, String.valueOf(temperature), false);
        events.publishEvent(new SettingsChangedEvent(SettingsChangedEvent.AI));
    }

    /** Сохраняет системный промпт генерации черновика (пусто — вернуть значение по умолчанию). */
    @Transactional
    public void savePrompt(String systemPrompt) {
        String value = (systemPrompt == null || systemPrompt.isBlank())
                ? SettingsModel.DEFAULT_DRAFT_PROMPT
                : systemPrompt;
        put(Keys.AI_SYSTEM_PROMPT, value, false);
        events.publishEvent(new SettingsChangedEvent(SettingsChangedEvent.AI));
    }

    // --- Внутреннее ---

    private String get(String key) {
        return repo.findById(key)
                .map(s -> s.isEncrypted() ? cipher.decrypt(s.getValue()) : s.getValue())
                .orElse(null);
    }

    /** Читает группу ключей одним запросом (атомарный снимок, один коннекшн). */
    private Map<String, String> load(String... keys) {
        Map<String, String> out = new HashMap<>();
        for (Setting s : repo.findAllById(List.of(keys))) {
            out.put(s.getKey(), s.isEncrypted() ? cipher.decrypt(s.getValue()) : s.getValue());
        }
        return out;
    }

    private void put(String key, String value, boolean secret) {
        String stored = secret ? cipher.encrypt(value) : value;
        Setting setting = repo.findById(key).orElseGet(() -> new Setting(key, null, secret));
        setting.setValue(stored);
        setting.setEncrypted(secret);
        repo.save(setting);
    }

    private static long parseLong(String s) {
        try {
            return s == null || s.isBlank() ? 0L : Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return s == null || s.isBlank() ? def : Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static String orDefault(String value, String def) {
        return value == null || value.isBlank() ? def : value;
    }

    private static int clampInterval(long value) {
        long v = value <= 0 ? 45 : value;      // не задано/битое → дефолт 45
        return (int) Math.max(5, v);           // минимум 5 c, чтобы не долбить IMAP
    }

    private static ZoneId parseZone(String s) {
        try {
            return s == null || s.isBlank() ? DEFAULT_ZONE : ZoneId.of(s.trim());
        } catch (RuntimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    private static LocalTime parseTime(String s, LocalTime def) {
        try {
            return s == null || s.isBlank() ? def : LocalTime.parse(s.trim());
        } catch (RuntimeException ex) {
            return def;
        }
    }

    /** Разбирает "MON,TUE,..." (принимает и полные имена); мусор игнорируется. */
    private static Set<DayOfWeek> parseDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_DAYS;
        }
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (String token : csv.split(",")) {
            String t = token.trim().toUpperCase(Locale.ROOT);
            if (t.length() < 3) {
                continue;
            }
            for (DayOfWeek d : DayOfWeek.values()) {
                if (d.name().startsWith(t.substring(0, 3))) {   // MON,TUE,... уникальны по 3 буквам
                    out.add(d);
                    break;
                }
            }
        }
        return out.isEmpty() ? DEFAULT_DAYS : out;
    }

    private static String daysToCsv(Set<DayOfWeek> days) {
        return days.stream().map(d -> d.name().substring(0, 3)).collect(Collectors.joining(","));
    }
}
