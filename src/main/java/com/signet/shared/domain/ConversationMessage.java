package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "email_id")
    private UUID emailId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ConversationMessage() {
    }

    public ConversationMessage(UUID conversationId, UUID emailId, MessageRole role, String content) {
        this.conversationId = conversationId;
        this.emailId = emailId;
        this.role = role;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getEmailId() {
        return emailId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
