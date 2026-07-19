package com.signet.analytics;

import com.signet.shared.domain.DailyStats;
import com.signet.shared.repo.DailyStatsRepository;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.SendLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Пересчёт дневных агрегатов за текущий день в {@code daily_stats}.
 * Вызывается событийно (после приёма/отправки) и планировщиком (страховка).
 */
@Service
public class StatsRollupService {

    private final EmailRepository emails;
    private final SendLogRepository sendLog;
    private final DraftRepository drafts;
    private final DailyStatsRepository dailyStats;

    public StatsRollupService(EmailRepository emails,
                              SendLogRepository sendLog,
                              DraftRepository drafts,
                              DailyStatsRepository dailyStats) {
        this.emails = emails;
        this.sendLog = sendLog;
        this.drafts = drafts;
        this.dailyStats = dailyStats;
    }

    @Transactional
    public void rollupToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();

        DailyStats stats = dailyStats.findById(today).orElseGet(() -> new DailyStats(today));
        stats.setReceived((int) emails.countByReceivedAtAfter(startOfDay));
        stats.setSent((int) sendLog.countByStatusAndSentAtAfter("SENT", startOfDay));
        stats.setTokensIn(drafts.sumTokensInSince(startOfDay));
        stats.setTokensOut(drafts.sumTokensOutSince(startOfDay));
        dailyStats.save(stats);
    }
}
