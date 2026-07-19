package com.signet.shared.repo;

import com.signet.shared.domain.SendLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SendLogRepository extends JpaRepository<SendLog, UUID> {

    long countByStatusAndSentAtAfter(String status, Instant after);

    /** Была ли письму зафиксирована успешная отправка (для разбора зависших в SENDING). */
    boolean existsByEmailIdAndStatus(UUID emailId, String status);

    /** Групповой агрегат по ящикам — по факту отправки (sent_at), одним запросом. */
    @Query("select s.mailboxId as mailboxId, count(s) as cnt from SendLog s "
            + "where s.status = :status and s.sentAt >= :after group by s.mailboxId")
    List<MailboxCountView> countByMailboxSince(@Param("status") String status,
                                               @Param("after") Instant after);
}
