package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "daily_stats")
public class DailyStats {

    @Id
    private LocalDate day;

    private int received;
    private int sent;
    private int approved;
    private int edited;
    private int rejected;

    @Column(name = "tokens_in")
    private long tokensIn;

    @Column(name = "tokens_out")
    private long tokensOut;

    protected DailyStats() {
    }

    public DailyStats(LocalDate day) {
        this.day = day;
    }

    public LocalDate getDay() {
        return day;
    }

    public int getReceived() {
        return received;
    }

    public void setReceived(int received) {
        this.received = received;
    }

    public int getSent() {
        return sent;
    }

    public void setSent(int sent) {
        this.sent = sent;
    }

    public int getApproved() {
        return approved;
    }

    public void setApproved(int approved) {
        this.approved = approved;
    }

    public int getEdited() {
        return edited;
    }

    public void setEdited(int edited) {
        this.edited = edited;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public long getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(long tokensIn) {
        this.tokensIn = tokensIn;
    }

    public long getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(long tokensOut) {
        this.tokensOut = tokensOut;
    }
}
