package com.signet.settings;

import com.signet.shared.config.Mailbox;
import com.signet.shared.config.MailboxesProperties;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реестр почтовых ящиков: источник истины — таблица {@code mailboxes} (правится через UI).
 * При первом старте заполняется из application.yml, чтобы не потерять текущую настройку.
 */
@Service
public class MailboxRegistry {

    private static final Logger log = LoggerFactory.getLogger(MailboxRegistry.class);

    private final MailboxEntityRepository repo;
    private final SecretCipher cipher;
    private final MailboxesProperties seed;
    private final ApplicationEventPublisher events;

    public MailboxRegistry(MailboxEntityRepository repo,
                           SecretCipher cipher,
                           MailboxesProperties seed,
                           ApplicationEventPublisher events) {
        this.repo = repo;
        this.cipher = cipher;
        this.seed = seed;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        if (repo.count() > 0) {
            return;
        }
        // Без адреса ящик бесполезен — иначе из пустых env создавалась бы «пустышка».
        List<Mailbox> fromYml = seed.getMailboxes().stream()
                .filter(m -> m.getId() != null && !m.getId().isBlank())
                .filter(m -> m.getUsername() != null && !m.getUsername().isBlank())
                .toList();
        if (fromYml.isEmpty()) {
            return;
        }
        log.info("Ящики пусты — переношу {} шт. из application.yml", fromYml.size());
        for (Mailbox m : fromYml) {
            save(m, m.getPassword());
        }
    }

    /** Активные ящики (включены и с заданным IMAP). */
    @Transactional(readOnly = true)
    public List<Mailbox> active() {
        return repo.findByEnabledTrue().stream()
                .map(this::toMailbox)
                .filter(Mailbox::hasImap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Mailbox> all() {
        return repo.findAll().stream().map(this::toMailbox).toList();
    }

    /**
     * Список ящиков без расшифровки паролей — для аналитики и списков,
     * где креды не нужны (AES-расшифровка на каждый показ дашборда была лишней).
     */
    @Transactional(readOnly = true)
    public List<MailboxRef> refs() {
        return repo.findAll().stream()
                .map(e -> new MailboxRef(e.getId(), e.getUsername()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Mailbox> byId(String id) {
        return id == null ? Optional.empty() : repo.findById(id).map(this::toMailbox);
    }

    /** Сущности для UI (пароль не расшифровывается). */
    @Transactional(readOnly = true)
    public List<MailboxEntity> entities() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<MailboxEntity> entity(String id) {
        return repo.findById(id);
    }

    /**
     * Создаёт или обновляет ящик.
     *
     * @param plainPassword новый пароль; {@code null}/пусто — оставить прежний
     */
    @Transactional
    public void save(Mailbox m, String plainPassword) {
        MailboxEntity e = repo.findById(m.getId()).orElseGet(() -> new MailboxEntity(m.getId()));
        e.setProfile(m.getProfile());
        e.setUsername(m.getUsername());
        if (plainPassword != null && !plainPassword.isBlank()) {
            e.setPasswordEnc(cipher.encrypt(plainPassword));
        }
        e.setImapHost(m.getImapHost());
        e.setImapPort(m.getImapPort());
        e.setFolder(m.getFolder());
        e.setProcessedFolder(m.getProcessedFolder());
        e.setSmtpHost(m.getSmtpHost());
        e.setSmtpPort(m.getSmtpPort());
        e.setSmtpSsl(m.isSmtpSsl());
        e.setSmtpStarttls(m.isSmtpStarttls());
        e.setSmtpAuth(m.isSmtpAuth());
        if (m.getReviewChannel() != null) {
            e.setReviewChannel(m.getReviewChannel());
        }
        repo.save(e);
        events.publishEvent(new SettingsChangedEvent(SettingsChangedEvent.MAILBOXES));
    }

    @Transactional
    public void setEnabled(String id, boolean enabled) {
        repo.findById(id).ifPresent(e -> {
            e.setEnabled(enabled);
            repo.save(e);
            events.publishEvent(new SettingsChangedEvent(SettingsChangedEvent.MAILBOXES));
        });
    }

    @Transactional
    public void delete(String id) {
        repo.deleteById(id);
        events.publishEvent(new SettingsChangedEvent(SettingsChangedEvent.MAILBOXES));
    }

    private Mailbox toMailbox(MailboxEntity e) {
        return e.toMailbox(cipher.decrypt(e.getPasswordEnc()));
    }
}
