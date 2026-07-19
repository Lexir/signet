package com.signet.ai;

import com.signet.settings.SettingsChangedEvent;
import com.signet.settings.SettingsModel.AiSettings;
import com.signet.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Отдаёт {@link ChatClient}, собранный по актуальным настройкам из UI:
 * провайдер, ключ/URL, модель, temperature. Пересобирается при изменении настроек,
 * поэтому смена провайдера или ключа применяется без перезапуска.
 *
 * <p>Если ручная сборка не удалась — используется клиент из автоконфигурации
 * (значения из application.yml), чтобы сервис остался работоспособным.
 */
@Component
public class ChatClientProvider {

    private static final Logger log = LoggerFactory.getLogger(ChatClientProvider.class);

    private final SettingsService settings;
    private final ChatClient.Builder autoConfigured;

    private volatile ChatClient cached;

    public ChatClientProvider(SettingsService settings, ChatClient.Builder autoConfigured) {
        this.settings = settings;
        this.autoConfigured = autoConfigured;
    }

    /** Актуальный клиент (ленивая сборка, кэш до изменения настроек). */
    public ChatClient client() {
        ChatClient local = cached;
        if (local == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = build();
                }
                local = cached;
            }
        }
        return local;
    }

    @EventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        if (SettingsChangedEvent.AI.equals(event.area())) {
            synchronized (this) {
                cached = null;
            }
            log.info("Настройки AI изменены — ChatClient будет пересобран");
        }
    }

    private ChatClient build() {
        AiSettings ai = settings.ai();
        // Портируемые опции: модель и temperature применяются к любому провайдеру.
        ChatOptions.Builder options = ChatOptions.builder()
                .model(ai.model())
                .temperature(ai.temperature());

        try {
            return ai.isOllama() ? buildOllama(ai, options) : buildOpenAi(ai, options);
        } catch (Exception ex) {
            log.error("Не удалось собрать ChatClient из настроек ({}), "
                    + "использую конфигурацию из application.yml", ex.getMessage());
            return autoConfigured.defaultOptions(options).build();
        }
    }

    private ChatClient buildOllama(AiSettings ai, ChatOptions.Builder options) {
        OllamaApi api = OllamaApi.builder().baseUrl(ai.ollamaBaseUrl()).build();
        OllamaChatModel model = OllamaChatModel.builder().ollamaApi(api).build();
        log.info("ChatClient: Ollama {} @ {}", ai.model(), ai.ollamaBaseUrl());
        return ChatClient.builder(model).defaultOptions(options).build();
    }

    /**
     * В Spring AI 2.0 ключ и baseUrl задаются через {@link OpenAiChatOptions}
     * (отдельного класса API-клиента больше нет), а билдер модели принимает их
     * методом {@code options(...)}.
     */
    private ChatClient buildOpenAi(AiSettings ai, ChatOptions.Builder options) {
        String apiKey = ai.openAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Выбран OpenAI, но ключ не задан — укажите его на странице /settings");
        }
        OpenAiChatOptions.Builder credentials = new OpenAiChatOptions.Builder();
        credentials.apiKey(apiKey);

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(credentials.build())
                .build();
        log.info("ChatClient: OpenAI {}", ai.model());
        return ChatClient.builder(model).defaultOptions(options).build();
    }
}
