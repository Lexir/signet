package com.signet.context;

import com.signet.shared.domain.MessageRole;
import java.util.List;

/**
 * Контекст переписки для генерации ответа: краткое резюме старой части
 * плюс последние реплики диалога в исходных ролях.
 */
public record ThreadContext(
        String threadId,
        String summary,
        List<Turn> turns,
        String lastClientMessage) {

    public record Turn(MessageRole role, String content) {
    }

    public boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }
}
