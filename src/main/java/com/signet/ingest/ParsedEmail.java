package com.signet.ingest;

import java.time.Instant;
import java.util.List;

/**
 * Разобранное входящее письмо (плоский снимок, не привязанный к Jakarta Mail).
 *
 * @param messageId   IMAP Message-ID
 * @param references  идентификаторы цепочки (References / In-Reply-To)
 * @param from        адрес отправителя
 * @param to          адрес получателя (наш ящик)
 * @param subject     тема
 * @param body        очищенное текстовое тело (без цитат/подписей)
 * @param receivedAt  дата получения
 * @param automated   признак авто-письма/рассылки/no-reply (не отвечаем)
 * @param attachments вложения письма
 */
public record ParsedEmail(
        String messageId,
        List<String> references,
        String from,
        String to,
        String subject,
        String body,
        Instant receivedAt,
        boolean automated,
        List<ParsedAttachment> attachments) {

    /** Корень треда: первый из References, иначе сам Message-ID. */
    public String threadRoot() {
        if (references != null && !references.isEmpty()) {
            return references.get(0);
        }
        return messageId;
    }
}
