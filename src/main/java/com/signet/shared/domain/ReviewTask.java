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
@Table(name = "review_tasks")
public class ReviewTask {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    @Column(name = "draft_id")
    private UUID draftId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewChannel channel;

    /** Идентификатор сообщения в мессенджере: "chatId:messageId". */
    @Column(name = "chat_ref")
    private String chatRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    private String reviewer;

    /** Ждём от менеджера текст правки следующим сообщением. */
    @Column(name = "awaiting_edit", nullable = false)
    private boolean awaitingEdit = false;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Когда отправлено напоминание о зависшей задаче (null — ещё не напоминали). */
    @Column(name = "reminded_at")
    private Instant remindedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ReviewTask() {
    }

    public ReviewTask(UUID emailId, UUID draftId, ReviewChannel channel) {
        this.emailId = emailId;
        this.draftId = draftId;
        this.channel = channel;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmailId() {
        return emailId;
    }

    public UUID getDraftId() {
        return draftId;
    }

    public ReviewChannel getChannel() {
        return channel;
    }

    public String getChatRef() {
        return chatRef;
    }

    public void setChatRef(String chatRef) {
        this.chatRef = chatRef;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public boolean isAwaitingEdit() {
        return awaitingEdit;
    }

    public void setAwaitingEdit(boolean awaitingEdit) {
        this.awaitingEdit = awaitingEdit;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(Instant remindedAt) {
        this.remindedAt = remindedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
