package com.signet.shared.repo;

import com.signet.shared.domain.MailMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailMessageRepository extends JpaRepository<MailMessage, UUID> {

    Optional<MailMessage> findByMailboxIdAndMessageId(String mailboxId, String messageId);

    List<MailMessage> findByMailboxIdAndMessageIdIn(String mailboxId, Collection<String> messageIds);
}
