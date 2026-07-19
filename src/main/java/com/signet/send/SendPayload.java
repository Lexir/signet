package com.signet.send;

import com.signet.shared.config.Mailbox;
import java.util.UUID;

/**
 * Снимок данных для отправки — читается в короткой транзакции, дальше используется
 * уже без подключения к БД (сама отправка идёт вне транзакции).
 */
public record SendPayload(
        UUID emailId,
        UUID conversationId,
        String messageId,
        String toAddr,
        String subject,
        String finalText,
        Mailbox mailbox) {
}
