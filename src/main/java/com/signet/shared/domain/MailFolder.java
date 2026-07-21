package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Папка ящика — зеркало IMAP-папки со счётчиками и границей инкрементального синка. */
@Entity
@Table(name = "mail_folders")
public class MailFolder {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "mailbox_id", nullable = false)
    private String mailboxId;

    @Column(nullable = false)
    private String name;

    private String delimiter;

    @Column(nullable = false)
    private boolean selectable = true;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    @Column(name = "uid_validity")
    private Long uidValidity;

    @Column(name = "last_synced_uid", nullable = false)
    private long lastSyncedUid;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MailFolder() {
    }

    public MailFolder(String mailboxId, String name) {
        this.mailboxId = mailboxId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public String getName() {
        return name;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public Long getUidValidity() {
        return uidValidity;
    }

    public void setUidValidity(Long uidValidity) {
        this.uidValidity = uidValidity;
    }

    public long getLastSyncedUid() {
        return lastSyncedUid;
    }

    public void setLastSyncedUid(long lastSyncedUid) {
        this.lastSyncedUid = lastSyncedUid;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
