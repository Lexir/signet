package com.signet.mail;

import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Периодический синк зеркала всех активных ящиков. Список ящиков берётся из настроек,
 * расписание/окно — из {@link PollingSchedulerConfig}. Пришло на смену ingest-воронке:
 * теперь опрос лишь зеркалит папки и письма, ничего не отправляя и не помечая.
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
