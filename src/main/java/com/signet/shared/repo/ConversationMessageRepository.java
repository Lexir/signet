package com.signet.shared.repo;

import com.signet.shared.domain.ConversationMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
