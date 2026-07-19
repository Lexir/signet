package com.signet.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Перевод произвольного текста. Используется, когда менеджер правит ответ
 * на русском, а клиенту нужно отправить на его языке (и обратно — для превью).
 */
@Service
public class TranslationService {

    private final ChatClientProvider chatProvider;

    public TranslationService(ChatClientProvider chatProvider) {
        this.chatProvider = chatProvider;
    }

    /**
     * @param text           исходный текст
     * @param targetLanguage ISO-код целевого языка (например "en", "de", "ru")
     * @return перевод без каких-либо пояснений
     */
    public String translate(String text, String targetLanguage) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return chatProvider.client().prompt()
                .system("Ты — профессиональный переводчик. Переводи точно, сохраняя смысл и деловой тон. Верни только перевод, без пояснений и кавычек.")
                .user("Переведи следующий текст на язык '%s':\n\n%s".formatted(targetLanguage, text))
                .call()
                .content();
    }
}
