package com.signet.shared.repo;

import com.signet.shared.domain.MailFolder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailFolderRepository extends JpaRepository<MailFolder, UUID> {

    Optional<MailFolder> findByMailboxIdAndName(String mailboxId, String name);

    List<MailFolder> findByMailboxIdOrderByNameAsc(String mailboxId);
}
