package com.signet.settings;

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

        private Keys() {
        }
    }
}
