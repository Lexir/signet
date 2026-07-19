package com.signet.ai;

import com.signet.context.ThreadContext;
import com.signet.settings.SettingsModel;
import com.signet.settings.SettingsService;
import com.signet.shared.domain.MessageRole;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * Генерация черновика ответа в личной переписке — от имени владельца ящика.
 * Системный промпт редактируется через UI (/settings), профиль подставляется
 * вместо плейсхолдера {profile}. Автор сам вычитывает и одобряет текст.
 */
@Service
public class DraftService {

    private final ChatClientProvider chatProvider;
    private final SettingsService settings;
    private final BeanOutputConverter<DraftResponse> converter =
            new BeanOutputConverter<>(DraftResponse.class);

    public DraftService(ChatClientProvider chatProvider, SettingsService settings) {
        this.chatProvider = chatProvider;
        this.settings = settings;
    }

    /**
     * @param profile описание автора (имя, о себе, тон общения)
     */
    public GenerationResult draft(ThreadContext ctx, String subject, String profile) {
        String userPrompt = """
                Тема: %s

                %s
                История переписки (последние сообщения):
                %s

                Последнее сообщение собеседника:
                %s

                %s
                """.formatted(
                nullToEmpty(subject),
                ctx.hasSummary() ? "Краткое резюме предыдущей части переписки:\n" + ctx.summary() + "\n" : "",
                renderTurns(ctx),
                ctx.lastClientMessage(),
                converter.getFormat());

        // Промпт берётся из настроек (UI); {profile} заменяется на профиль автора.
        String systemPrompt = settings.ai().systemPrompt()
                .replace(SettingsModel.PROFILE_PLACEHOLDER, nullToEmpty(profile));

        ChatResponse response = chatProvider.client().prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();

        String raw = response.getResult().getOutput().getText();
        DraftResponse parsed = converter.convert(raw);
        return GenerationResult.from(parsed, response);
    }

    private String renderTurns(ThreadContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (ThreadContext.Turn t : ctx.turns()) {
            String who = t.role() == MessageRole.USER ? "Собеседник" : "Я";
            sb.append(who).append(": ").append(t.content()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Результат генерации: разобранный ответ + метрики токенов + модель. */
    public record GenerationResult(DraftResponse response, Integer tokensIn, Integer tokensOut, String model) {

        /** Собирает результат, аккуратно доставая модель и токены из метаданных ответа. */
        static GenerationResult from(DraftResponse parsed, ChatResponse response) {
            var metadata = response.getMetadata();
            var usage = metadata != null ? metadata.getUsage() : null;
            if (usage == null) {
                return new GenerationResult(parsed, null, null, metadata != null ? metadata.getModel() : null);
            }
            Integer in = usage.getPromptTokens();
            Integer out = usage.getCompletionTokens();
            if (out == null) {
                // Провайдер не отдал completionTokens — выводим из total − prompt.
                Integer total = usage.getTotalTokens();
                out = total != null && in != null ? total - in : null;
            }
            return new GenerationResult(parsed, in, out, metadata.getModel());
        }
    }
}
