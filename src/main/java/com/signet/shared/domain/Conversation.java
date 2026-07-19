package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "thread_id", nullable = false, unique = true)
    private String threadId;

    @Column(name = "client_addr")
    private String clientAddr;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "summary_upto")
    private Instant summaryUpto;

    @Column(name = "last_activity")
    private Instant lastActivity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Conversation() {
    }

    public Conversation(String threadId, String clientAddr) {
        this.threadId = threadId;
        this.clientAddr = clientAddr;
        this.lastActivity = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getClientAddr() {
        return clientAddr;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getSummaryUpto() {
        return summaryUpto;
    }

    public void setSummaryUpto(Instant summaryUpto) {
        this.summaryUpto = summaryUpto;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Instant lastActivity) {
        this.lastActivity = lastActivity;
    }
}
