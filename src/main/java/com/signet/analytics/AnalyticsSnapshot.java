package com.signet.analytics;

import com.signet.shared.domain.DailyStats;
import java.util.List;

/** Снимок ключевых метрик для дашборда. */
public record AnalyticsSnapshot(
        long receivedToday,
        long sentToday,
        long pendingReview,
        long approvedTotal,
        long editedTotal,
        long rejectedTotal,
        double editRatePct,
        long tokensInToday,
        long tokensOutToday,
        List<DailyStats> history,
        List<MailboxStats> perMailbox) {
}
