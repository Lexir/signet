package com.signet.ingest;

import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Опрос всех активных ящиков. Список ящиков и интервал берутся из настроек (UI),
 * поэтому изменения применяются без перезапуска.
 * Расписание задаётся в {@link PollingSchedulerConfig}.
 */
@Component
public class MailPoller {

    private static final Logger log = LoggerFactory.getLogger(MailPoller.class);

    private final ImapReader reader;
    private final IngestService ingest;
    private final MailboxRegistry mailboxes;

    public MailPoller(ImapReader reader, IngestService ingest, MailboxRegistry mailboxes) {
        this.reader = reader;
        this.ingest = ingest;
        this.mailboxes = mailboxes;
    }

    public void poll() {
        for (Mailbox mailbox : mailboxes.active()) {
            int handled = reader.poll(mailbox, parsed -> ingest.ingest(parsed, mailbox));
            if (handled > 0) {
                log.info("[{}] обработано {} писем", mailbox.getId(), handled);
            }
        }
    }
}
