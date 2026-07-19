package com.signet.shared.repo;

import com.signet.shared.domain.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByThreadId(String threadId);
}
