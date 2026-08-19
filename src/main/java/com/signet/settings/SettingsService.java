package com.signet.settings;

import com.signet.settings.SettingsModel.AiSettings;
import com.signet.settings.SettingsModel.Keys;
import com.signet.settings.SettingsModel.TelegramSettings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final SettingRepository repo;
    private final SecretCipher cipher;
    private final ApplicationEventPublisher events;

    // Значения из окружения — используются как сид при первом запуске.
    private final String seedBotToken;
    private final String seedChatId;
    private final String seedProvider;
    private final String seedOpenAiKey;
    private final String seedOllamaUrl;
    private final String seedOllamaModel;
    private final String seedOpenAiModel;

    public SettingsService(SettingRepository repo,
                           SecretCipher cipher,
                           ApplicationEventPublisher events,
                           @Value("${app.telegram.bot-token:}") String seedBotToken,
                           @Value("${app.telegram.manager-chat-id:0}") String seedChatId,
                           @Value("${spring.ai.model.chat:ollama}") String seedProvider,
                           @Value("${spring.ai.openai.api-key:}") String seedOpenAiKey,
                           @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String seedOllamaUrl,
                           @Value("${spring.ai.ollama.chat.options.model:qwen2.5:14b-instruct}") String seedOllamaModel,
                           @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String seedOpenAiModel) {
        this.repo = repo;
        this.cipher = cipher;
        this.events = events;
        this.seedBotToken = seedBotToken;
        this.seedChatId = seedChatId.isBlank() ? "0" : seedChatId.trim();
        this.seedProvider = seedProvider;
        this.seedOpenAiKey = seedOpenAiKey;
        this.seedOllamaUrl = seedOllamaUrl;
        this.seedOllamaModel = seedOllamaModel;
        this.seedOpenAiModel = seedOpenAiModel;
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
        put(Keys.TG_MANAGER_CHAT_ID, seedChatId, false);
        put(Keys.TG_ENABLED, "true", false);
        put(Keys.AI_PROVIDER, seedProvider, false);
        put(Keys.AI_OPENAI_KEY, seedOpenAiKey, true);
        put(Keys.AI_OLLAMA_URL, seedOllamaUrl, false);
        put(Keys.AI_MODEL, ollama ? seedOllamaModel : seedOpenAiModel, false);
        put(Keys.AI_TEMPERATURE, "0.3", false);
        put(Keys.AI_SYSTEM_PROMPT, SettingsModel.DEFAULT_DRAFT_PROMPT, false);
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
}
