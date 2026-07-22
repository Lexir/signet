package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Членство письма в папке: индекс {@code (folder, uid)} со ссылкой на контент
 * {@link MailMessage} по {@code (mailboxId, messageId)}. Флаги хранятся здесь — в IMAP
 * они относятся к копии письма в папке. Это то, что открывает и листает UI.
 */
@Entity
@Table(name = "mail_memberships")
public class MailMembership {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "mailbox_id", nullable = false)
    private String mailboxId;

    @Column(nullable = false)
    private String folder;

    @Column(nullable = false)
    private long uid;

    @Column(name = "uid_validity", nullable = false)
    private long uidValidity;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(nullable = false)
    private boolean seen;

    @Column(nullable = false)
    private boolean answered;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    protected MailMembership() {
    }

    public MailMembership(String mailboxId, String folder, long uid, long uidValidity, String messageId) {
        this.mailboxId = mailboxId;
        this.folder = folder;
        this.uid = uid;
        this.uidValidity = uidValidity;
        this.messageId = messageId;
    }

    public UUID getId() {
        return id;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public String getFolder() {
        return folder;
    }

    public long getUid() {
        return uid;
    }

    public long getUidValidity() {
        return uidValidity;
    }

    public String getMessageId() {
        return messageId;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public boolean isAnswered() {
        return answered;
    }

    public void setAnswered(boolean answered) {
        this.answered = answered;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
