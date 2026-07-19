package com.signet.analytics;

/** Метрики за сегодня по одному ящику. */
public record MailboxStats(
        String id,
        String label,
        long receivedToday,
        long sentToday,
        long pendingReview,
        long tokensInToday,
        long tokensOutToday) {
}
