package com.signet.ingest;

import com.signet.shared.config.Mailbox;
import com.signet.shared.config.MailboxesProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FlagTerm;
import java.util.Properties;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Читает непрочитанные письма конкретного ящика по IMAP и передаёт их обработчику.
 * Успешно обработанные помечаются прочитанными и (по возможности) переносятся
 * в папку Processed.
 */
@Component
public class ImapReader {

    private static final Logger log = LoggerFactory.getLogger(ImapReader.class);

    private final EmailParser parser;
    private final MailboxesProperties props;

    public ImapReader(EmailParser parser, MailboxesProperties props) {
        this.parser = parser;
        this.props = props;
    }

    /**
     * @param handler возвращает true, если письмо обработано (сохранено/дубликат)
     *                и его можно пометить прочитанным.
     * @return число обработанных писем
     */
    public int poll(Mailbox mailbox, Predicate<ParsedEmail> handler) {
        if (!mailbox.hasImap()) {
            return 0;
        }
        Properties sessionProps = new Properties();
        sessionProps.put("mail.store.protocol", "imaps");
        sessionProps.put("mail.imaps.host", mailbox.getImapHost());
        sessionProps.put("mail.imaps.port", String.valueOf(mailbox.getImapPort()));
        sessionProps.put("mail.imaps.ssl.enable", "true");
        sessionProps.put("mail.imaps.connectiontimeout", "10000");
        sessionProps.put("mail.imaps.timeout", "10000");

        Session session = Session.getInstance(sessionProps);
        int handled = 0;

        try (Store store = session.getStore("imaps")) {
            store.connect(mailbox.getImapHost(), mailbox.getUsername(), mailbox.getPassword());

            Folder inbox = store.getFolder(mailbox.getFolder());
            inbox.open(Folder.READ_WRITE);
            try {
                Message[] unseen = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                int limit = Math.min(unseen.length, props.getMaxBatch());
                for (int i = 0; i < limit; i++) {
                    Message message = unseen[i];
                    try {
                        ParsedEmail parsed = parser.parse(message);
                        if (handler.test(parsed)) {
                            message.setFlag(Flags.Flag.SEEN, true);
                            moveToProcessed(store, inbox, message, mailbox.getProcessedFolder());
                            handled++;
                        }
                    } catch (Exception ex) {
                        log.error("[{}] Не удалось обработать письмо: {}", mailbox.getId(), ex.getMessage(), ex);
                    }
                }
            } finally {
                inbox.close(true);
            }
        } catch (MessagingException ex) {
            log.error("[{}] Ошибка опроса IMAP: {}", mailbox.getId(), ex.getMessage(), ex);
        }
        return handled;
    }

    private void moveToProcessed(Store store, Folder inbox, Message message, String target) {
        if (target == null || target.isBlank()) {
            return;
        }
        try {
            Folder processed = store.getFolder(target);
            if (!processed.exists()) {
                processed.create(Folder.HOLDS_MESSAGES);
            }
            inbox.copyMessages(new Message[]{message}, processed);
            message.setFlag(Flags.Flag.DELETED, true);
        } catch (MessagingException ex) {
            log.warn("Не удалось перенести письмо в {}: {}", target, ex.getMessage());
        }
    }
}
