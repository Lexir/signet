package com.signet.ai;

import com.signet.context.ThreadContext;
import java.util.UUID;

/** Снимок данных для генерации — собирается в короткой транзакции. */
public record DraftPayload(
        UUID emailId,
        String from,
        String subject,
        String body,
        String profile,
        ThreadContext context) {
}
