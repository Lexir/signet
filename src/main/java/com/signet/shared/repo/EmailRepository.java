package com.signet.shared.repo;

import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailRepository extends JpaRepository<Email, UUID> {

    boolean existsByMessageId(String messageId);

    Optional<Email> findByMessageId(String messageId);

    /**
     * Загружает письмо с блокировкой строки (SELECT … FOR UPDATE). Нужен при захвате
     * письма под отправку: два конкурентных {@code claim} сериализуются, второй дождётся
     * коммита первого и увидит статус SENDING — исключая дубль реальной SMTP-отправки.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Email e where e.id = :id")
    Optional<Email> findByIdForUpdate(@Param("id") UUID id);

    List<Email> findByStatus(EmailStatus status);

    long countByStatus(EmailStatus status);

    long countByStatusAndReceivedAtAfter(EmailStatus status, Instant after);

    long countByReceivedAtAfter(Instant after);

    /** «Зависшие» письма для досбора после рестарта. */
    List<Email> findByStatusAndUpdatedAtBefore(EmailStatus status, Instant before);

    // --- Групповые агрегаты для дашборда: один запрос вместо запроса на каждый ящик ---

    @Query("select e.mailboxId as mailboxId, count(e) as cnt from Email e "
            + "where e.receivedAt >= :after group by e.mailboxId")
    List<MailboxCountView> countReceivedByMailboxSince(@Param("after") Instant after);

    @Query("select e.mailboxId as mailboxId, count(e) as cnt from Email e "
            + "where e.status = :status group by e.mailboxId")
    List<MailboxCountView> countByStatusGroupedByMailbox(@Param("status") EmailStatus status);
}
