package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Контент письма — один раз на (ящик, Message-ID), независимо от того, в скольких папках
 * оно помечено. Тело кэшируется лениво ({@code bodyText}/{@code bodySyncedAt}) и разделяется
 * всеми папками-членствами. Принадлежность к папкам — в {@link MailMembership}.
 */
@Entity
@Table(name = "mail_messages")
public class MailMessage {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "mailbox_id", nullable = false)
    private String mailboxId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "from_addr")
    private String fromAddr;

    @Column(name = "to_addr")
    private String toAddr;

    private String subject;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "has_attachments", nullable = false)
    private boolean hasAttachments;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Column(name = "body_synced_at")
    private Instant bodySyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    protected MailMessage() {
    }

    public MailMessage(String mailboxId, String messageId) {
        this.mailboxId = mailboxId;
        this.messageId = messageId;
    }

    public UUID getId() {
        return id;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getFromAddr() {
        return fromAddr;
    }

    public void setFromAddr(String fromAddr) {
        this.fromAddr = fromAddr;
    }

    public String getToAddr() {
        return toAddr;
    }

    public void setToAddr(String toAddr) {
        this.toAddr = toAddr;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public boolean isHasAttachments() {
        return hasAttachments;
    }

    public void setHasAttachments(boolean hasAttachments) {
        this.hasAttachments = hasAttachments;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public Instant getBodySyncedAt() {
        return bodySyncedAt;
    }

    public void setBodySyncedAt(Instant bodySyncedAt) {
        this.bodySyncedAt = bodySyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
