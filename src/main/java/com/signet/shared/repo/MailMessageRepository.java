package com.signet.shared.repo;

import com.signet.shared.domain.MailMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MailMessageRepository extends JpaRepository<MailMessage, UUID> {

    Optional<MailMessage> findByMailboxIdAndFolderAndUidValidityAndUid(
            String mailboxId, String folder, long uidValidity, long uid);

    Page<MailMessage> findByMailboxIdAndFolderOrderByUidDesc(String mailboxId, String folder, Pageable pageable);

    long countByMailboxIdAndFolderAndSeenFalse(String mailboxId, String folder);

    /** «Принято сегодня» для дашборда — входящие, синкнутые в папку ящика. */
    long countByMailboxIdAndFolderAndSentAtAfter(String mailboxId, String folder, Instant after);

    /** Всего входящих (INBOX) за период — общий счётчик «принято» для дашборда. */
    @Query("select count(m) from MailMessage m where upper(m.folder) = 'INBOX' and m.sentAt >= :after")
    long countInboxSince(@Param("after") Instant after);

    /** Входящие (INBOX) за период в разрезе ящиков — одним групповым запросом. */
    @Query("select m.mailboxId as mailboxId, count(m) as cnt from MailMessage m "
            + "where upper(m.folder) = 'INBOX' and m.sentAt >= :after group by m.mailboxId")
    List<MailboxCountView> countInboxByMailboxSince(@Param("after") Instant after);

    /** Смена UIDVALIDITY — старые записи папки невалидны, чистим перед пересинком. */
    @Transactional
    void deleteByMailboxIdAndFolder(String mailboxId, String folder);
}
