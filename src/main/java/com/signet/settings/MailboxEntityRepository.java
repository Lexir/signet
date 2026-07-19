package com.signet.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxEntityRepository extends JpaRepository<MailboxEntity, String> {

    List<MailboxEntity> findByEnabledTrue();
}
