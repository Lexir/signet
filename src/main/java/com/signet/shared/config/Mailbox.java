package com.signet.shared.config;

import com.signet.shared.domain.ReviewChannel;

/**
 * Настройки одного почтового ящика: приём (IMAP), отправка (SMTP) и контекст
 * генерации (название компании). Отправка ответа идёт через тот же ящик.
 */
public class Mailbox {

    /** Уникальный идентификатор ящика (хранится в emails.mailbox_id). */
    private String id;

    /** Профиль автора: имя, о себе, тон общения — используется в промпте. */
    private String profile = "";

    /** Логин/адрес ящика (один для IMAP и SMTP; он же From при ответе). */
    private String username;
    private String password;

    // --- IMAP ---
    private String imapHost;
    private int imapPort = 993;
    private String folder = "INBOX";
    private String processedFolder = "Processed";

    // --- SMTP ---
    private String smtpHost;
    private int smtpPort = 587;
    private boolean smtpSsl = false;
    private boolean smtpStarttls = true;
    private boolean smtpAuth = true;

    /** Куда уходит AI-черновик на разбор: веб-очередь (UI) или Telegram-бот. */
    private ReviewChannel reviewChannel = ReviewChannel.TELEGRAM;

    public boolean hasImap() {
        return imapHost != null && !imapHost.isBlank();
    }

    public boolean hasSmtp() {
        return smtpHost != null && !smtpHost.isBlank();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    public int getImapPort() {
        return imapPort;
    }

    public void setImapPort(int imapPort) {
        this.imapPort = imapPort;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getProcessedFolder() {
        return processedFolder;
    }

    public void setProcessedFolder(String processedFolder) {
        this.processedFolder = processedFolder;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public boolean isSmtpSsl() {
        return smtpSsl;
    }

    public void setSmtpSsl(boolean smtpSsl) {
        this.smtpSsl = smtpSsl;
    }

    public boolean isSmtpStarttls() {
        return smtpStarttls;
    }

    public void setSmtpStarttls(boolean smtpStarttls) {
        this.smtpStarttls = smtpStarttls;
    }

    public boolean isSmtpAuth() {
        return smtpAuth;
    }

    public void setSmtpAuth(boolean smtpAuth) {
        this.smtpAuth = smtpAuth;
    }

    public ReviewChannel getReviewChannel() {
        return reviewChannel;
    }

    public void setReviewChannel(ReviewChannel reviewChannel) {
        this.reviewChannel = reviewChannel;
    }
}
