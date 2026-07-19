package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Вложение входящего письма (хранится в БД, отправляется менеджеру в Telegram). */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private int sizeBytes;

    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Attachment() {
    }

    public Attachment(UUID emailId, String filename, String contentType, byte[] data) {
        this.emailId = emailId;
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data != null ? data.length : 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmailId() {
        return emailId;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getData() {
        return data;
    }
}
