package com.signet.analytics;

import com.signet.settings.MailboxRef;
import com.signet.settings.MailboxRegistry;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.domain.ReviewStatus;
import com.signet.shared.repo.DailyStatsRepository;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.MailboxCountView;
import com.signet.shared.repo.MailboxTokensView;
import com.signet.shared.repo.ReviewTaskRepository;
import com.signet.shared.repo.SendLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Метрики дашборда. Разрез по ящикам считается групповыми запросами
 * (фиксированное число обращений к БД независимо от количества ящиков).
 */
@Service
public class AnalyticsService {

    private final EmailRepository emails;
    private final ReviewTaskRepository reviews;
    private final DraftRepository drafts;
    private final SendLogRepository sendLog;
    private final DailyStatsRepository dailyStats;
    private final MailboxRegistry mailboxes;

    public AnalyticsService(EmailRepository emails,
                            ReviewTaskRepository reviews,
                            DraftRepository drafts,
                            SendLogRepository sendLog,
                            DailyStatsRepository dailyStats,
                            MailboxRegistry mailboxes) {
        this.emails = emails;
        this.reviews = reviews;
        this.drafts = drafts;
        this.sendLog = sendLog;
        this.dailyStats = dailyStats;
        this.mailboxes = mailboxes;
    }

    @Transactional(readOnly = true)
    public AnalyticsSnapshot snapshot() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        // --- Общая аналитика (по всем ящикам) ---
        long received = emails.countByReceivedAtAfter(startOfDay);
        long sent = sendLog.countByStatusAndSentAtAfter("SENT", startOfDay);
        long pending = emails.countByStatus(EmailStatus.PENDING_REVIEW);

        // Решения — тоже за сегодня (как received/sent), иначе edit-rate «за день»
        // считался бы по всей истории.
        long approved = reviews.countByStatusAndDecidedAtAfter(ReviewStatus.APPROVED, startOfDay);
        long edited = reviews.countByStatusAndDecidedAtAfter(ReviewStatus.EDITED, startOfDay);
        long rejected = reviews.countByStatusAndDecidedAtAfter(ReviewStatus.REJECTED, startOfDay);
        long decided = approved + edited + rejected;
        double editRate = decided == 0 ? 0.0 : (100.0 * edited / decided);

        long tokensIn = drafts.sumTokensInSince(startOfDay);
        long tokensOut = drafts.sumTokensOutSince(startOfDay);

        return new AnalyticsSnapshot(
                received, sent, pending,
                approved, edited, rejected,
                Math.round(editRate * 10) / 10.0,
                tokensIn, tokensOut,
                dailyStats.findTop30ByOrderByDayAsc(),
                perMailbox(startOfDay));
    }

    /**
     * Четыре групповых запроса + список ящиков вместо пяти запросов и AES-расшифровки
     * на каждый ящик.
     */
    private List<MailboxStats> perMailbox(Instant startOfDay) {
        Map<String, Long> receivedByBox = counts(emails.countReceivedByMailboxSince(startOfDay));
        Map<String, Long> sentByBox = counts(sendLog.countByMailboxSince("SENT", startOfDay));
        Map<String, Long> pendingByBox =
                counts(emails.countByStatusGroupedByMailbox(EmailStatus.PENDING_REVIEW));
        Map<String, MailboxTokensView> tokensByBox = drafts.sumTokensByMailboxSince(startOfDay).stream()
                .filter(v -> v.getMailboxId() != null)
                .collect(Collectors.toMap(MailboxTokensView::getMailboxId, Function.identity()));

        return mailboxes.refs().stream()
                .map(ref -> toStats(ref, receivedByBox, sentByBox, pendingByBox, tokensByBox))
                .toList();
    }

    private MailboxStats toStats(MailboxRef ref,
                                 Map<String, Long> received,
                                 Map<String, Long> sent,
                                 Map<String, Long> pending,
                                 Map<String, MailboxTokensView> tokens) {
        String id = ref.id();
        MailboxTokensView t = tokens.get(id);
        return new MailboxStats(
                id,
                ref.username(),
                received.getOrDefault(id, 0L),
                sent.getOrDefault(id, 0L),
                pending.getOrDefault(id, 0L),
                t == null ? 0L : t.getTokensIn(),
                t == null ? 0L : t.getTokensOut());
    }

    private Map<String, Long> counts(List<MailboxCountView> rows) {
        return rows.stream()
                .filter(r -> r.getMailboxId() != null)     // письма без ящика в разрез не попадают
                .collect(Collectors.toMap(MailboxCountView::getMailboxId, MailboxCountView::getCnt));
    }
}
