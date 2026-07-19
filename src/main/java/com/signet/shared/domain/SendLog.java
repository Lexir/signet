package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "send_log")
public class SendLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    /** Денормализовано из emails — чтобы считать статистику по ящикам без join. */
    @Column(name = "mailbox_id")
    private String mailboxId;

    @Column(name = "smtp_message_id")
    private String smtpMessageId;

    @Column(nullable = false)
    private String status;   // SENT | FAILED

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    protected SendLog() {
    }

    public SendLog(UUID emailId, String mailboxId, String status, String smtpMessageId, String error) {
        this.emailId = emailId;
        this.mailboxId = mailboxId;
        this.status = status;
        this.smtpMessageId = smtpMessageId;
        this.error = error;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmailId() {
        return emailId;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public String getStatus() {
        return status;
    }

    public String getSmtpMessageId() {
        return smtpMessageId;
    }

    public String getError() {
        return error;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
