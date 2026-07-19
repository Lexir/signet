package com.signet.review;

import java.util.UUID;

/** Снимок письма и черновика для отправки на валидацию (читается в короткой транзакции). */
public record ReviewPayload(
        UUID emailId,
        UUID draftId,
        String mailboxLabel,
        String from,
        String subject,
        String language,
        String body,
        String draftText,
        String draftTextRu) {
}
