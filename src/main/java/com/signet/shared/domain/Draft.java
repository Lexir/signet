package com.signet.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drafts")
public class Draft {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    /** Ответ на языке клиента, как предложил LLM. */
    @Column(name = "ai_text", columnDefinition = "text")
    private String aiText;

    /** Служебный перевод на русский для валидации менеджером. */
    @Column(name = "ai_text_ru", columnDefinition = "text")
    private String aiTextRu;

    /** Финальный текст на языке клиента (после правок), который уходит клиенту. */
    @Column(name = "final_text", columnDefinition = "text")
    private String finalText;

    private String model;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Draft() {
    }

    public Draft(UUID emailId) {
        this.emailId = emailId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmailId() {
        return emailId;
    }

    public String getAiText() {
        return aiText;
    }

    public void setAiText(String aiText) {
        this.aiText = aiText;
    }

    public String getAiTextRu() {
        return aiTextRu;
    }

    public void setAiTextRu(String aiTextRu) {
        this.aiTextRu = aiTextRu;
    }

    public String getFinalText() {
        return finalText;
    }

    public void setFinalText(String finalText) {
        this.finalText = finalText;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(Integer tokensIn) {
        this.tokensIn = tokensIn;
    }

    public Integer getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(Integer tokensOut) {
        this.tokensOut = tokensOut;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
