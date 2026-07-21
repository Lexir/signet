package com.signet.settings;

import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.ReviewChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Ящик, настроенный через UI. Пароль хранится зашифрованным. */
@Entity
@Table(name = "mailboxes")
public class MailboxEntity {

    @Id
    private String id;

    @Column(name = "profile")
    private String profile;

    private String username;

    @Column(name = "password_enc", columnDefinition = "text")
    private String passwordEnc;

    @Column(name = "imap_host")
    private String imapHost;

    @Column(name = "imap_port", nullable = false)
    private int imapPort = 993;

    @Column(nullable = false)
    private String folder = "INBOX";

    @Column(name = "processed_folder")
    private String processedFolder;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort = 587;

    @Column(name = "smtp_ssl", nullable = false)
    private boolean smtpSsl;

    @Column(name = "smtp_starttls", nullable = false)
    private boolean smtpStarttls = true;

    @Column(name = "smtp_auth", nullable = false)
    private boolean smtpAuth = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_channel", nullable = false)
    private ReviewChannel reviewChannel = ReviewChannel.TELEGRAM;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MailboxEntity() {
    }

    public MailboxEntity(String id) {
        this.id = id;
    }

    /** Преобразует в POJO, которым пользуются ingest/send/ai (пароль — расшифрованный). */
    public Mailbox toMailbox(String plainPassword) {
        Mailbox m = new Mailbox();
        m.setId(id);
        m.setProfile(profile);
        m.setUsername(username);
        m.setPassword(plainPassword);
        m.setImapHost(imapHost);
        m.setImapPort(imapPort);
        m.setFolder(folder);
        m.setProcessedFolder(processedFolder);
        m.setSmtpHost(smtpHost);
        m.setSmtpPort(smtpPort);
        m.setSmtpSsl(smtpSsl);
        m.setSmtpStarttls(smtpStarttls);
        m.setSmtpAuth(smtpAuth);
        m.setReviewChannel(reviewChannel);
        return m;
    }

    public String getId() {
        return id;
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

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public void setPasswordEnc(String passwordEnc) {
        this.passwordEnc = passwordEnc;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ReviewChannel getReviewChannel() {
        return reviewChannel;
    }

    public void setReviewChannel(ReviewChannel reviewChannel) {
        this.reviewChannel = reviewChannel;
    }
}
