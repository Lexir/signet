package com.signet.shared.repo;

import com.signet.shared.domain.MailMembership;
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

public interface MailMembershipRepository extends JpaRepository<MailMembership, UUID> {

    Optional<MailMembership> findByMailboxIdAndFolderAndUidValidityAndUid(
            String mailboxId, String folder, long uidValidity, long uid);

    Page<MailMembership> findByMailboxIdAndFolderOrderByUidDesc(String mailboxId, String folder, Pageable pageable);

    /** Смена UIDVALIDITY — членства папки невалидны, чистим перед пересинком. */
    @Transactional
    void deleteByMailboxIdAndFolder(String mailboxId, String folder);

    // --- «Принято сегодня» для дашборда: письма в INBOX за период (по дате письма) ---

    @Query("select count(m) from MailMembership m, MailMessage msg "
            + "where m.mailboxId = msg.mailboxId and m.messageId = msg.messageId "
            + "and upper(m.folder) = 'INBOX' and msg.sentAt >= :after")
    long countInboxSince(@Param("after") Instant after);

    @Query("select m.mailboxId as mailboxId, count(m) as cnt from MailMembership m, MailMessage msg "
            + "where m.mailboxId = msg.mailboxId and m.messageId = msg.messageId "
            + "and upper(m.folder) = 'INBOX' and msg.sentAt >= :after group by m.mailboxId")
    List<MailboxCountView> countInboxByMailboxSince(@Param("after") Instant after);
}
