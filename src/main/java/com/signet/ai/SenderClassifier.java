package com.signet.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Классифицирует письмо: личное сообщение человека vs автоматическое/маркетинг/
 * компания/спам. Второй уровень фильтра поверх эвристик в ingest.
 */
@Service
public class SenderClassifier {

    private static final Logger log = LoggerFactory.getLogger(SenderClassifier.class);
    private static final int MAX_BODY = 1500;

    private static final String SYSTEM = """
            Ты классифицируешь входящие письма в личной почте человека.
            Определи, написано ли письмо живым человеком лично и ждёт ли он личного ответа.
            AUTOMATED (отвечать НЕ нужно): маркетинговые и новостные рассылки, дайджесты,
            уведомления сервисов и приложений, автоматические письма, счета/чеки/квитанции,
            подтверждения регистраций, письма-роботы от компаний, спам, фишинг.
            PERSONAL (нужен личный ответ): реальный человек пишет лично и ждёт ответа.
            Ответь СТРОГО одним словом: PERSONAL или AUTOMATED.
            """;

    private final ChatClientProvider chatProvider;

    public SenderClassifier(ChatClientProvider chatProvider) {
        this.chatProvider = chatProvider;
    }

    /** @return true, если письмо — личное сообщение человека. */
    public boolean isPersonalHuman(String from, String subject, String body) {
        try {
            String verdict = chatProvider.client().prompt()
                    .system(SYSTEM)
                    .user("От: %s%nТема: %s%n%n%s".formatted(
                            nz(from), nz(subject), truncate(body)))
                    .call()
                    .content();
            boolean personal = verdict != null && verdict.toUpperCase().contains("PERSONAL");
            log.debug("Классификация письма от {}: {}", from, personal ? "PERSONAL" : "AUTOMATED");
            return personal;
        } catch (Exception ex) {
            // При сбое классификатора не теряем письмо — пропускаем к генерации (менеджер увидит).
            log.warn("Классификатор недоступен ({}), считаем письмо личным", ex.getMessage());
            return true;
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_BODY ? s.substring(0, MAX_BODY) : s;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
