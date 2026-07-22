package com.signet.mail;

import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Синк зеркала всех активных ящиков. Фонового опроса по расписанию нет — синк идёт
 * один раз при старте приложения и по кнопке «Синхронизировать» в UI
 * (POST /api/mail/{mailbox}/sync). Зеркалит папки и письма, ничего не отправляя и не помечая.
 */
@Component
public class MailPoller {

    private static final Logger log = LoggerFactory.getLogger(MailPoller.class);

    private final MailSyncService sync;
    private final MailboxRegistry mailboxes;

    public MailPoller(MailSyncService sync, MailboxRegistry mailboxes) {
        this.sync = sync;
        this.mailboxes = mailboxes;
    }

    /**
     * Первый синк сразу после старта — чтобы зеркало наполнилось немедленно, а не через
     * интервал опроса. Уходит в фон (не задерживает готовность приложения); к моменту его
     * реального запуска синхронные {@code ApplicationReadyEvent}-слушатели, включая сид
     * ящиков в {@link MailboxRegistry}, уже отработали.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("Старт: первичный синк зеркала");
        CompletableFuture.runAsync(this::poll);
    }

    public void poll() {
        for (Mailbox mailbox : mailboxes.active()) {
            try {
                sync.syncMailbox(mailbox);
            } catch (Exception ex) {
                log.error("[{}] цикл синка сорвался: {}", mailbox.getId(), ex.getMessage(), ex);
            }
        }
    }
}
