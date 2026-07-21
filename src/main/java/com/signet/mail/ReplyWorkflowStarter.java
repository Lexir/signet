package com.signet.mail;

import com.signet.ingest.IngestService;
import com.signet.ingest.ParsedEmail;
import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.MailMessage;
import com.signet.shared.repo.MailMessageRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Запуск AI-ответа по требованию из почтового клиента: читает письмо зеркала по IMAP
 * (вне транзакции) и передаёт в {@link IngestService#ensureEmail}, который заводит письмо
 * воронки и в своей транзакции публикует {@code Events.ReplyRequested} — дальше работает
 * цепочка ai → review, а канал ревью выбирается по ящику.
 */
@Service
public class ReplyWorkflowStarter {

    private static final Logger log = LoggerFactory.getLogger(ReplyWorkflowStarter.class);

    private final MailMessageRepository messages;
    private final MailboxRegistry mailboxes;
    private final ImapClient imap;
    private final IngestService ingest;

    public ReplyWorkflowStarter(MailMessageRepository messages,
                                MailboxRegistry mailboxes,
                                ImapClient imap,
                                IngestService ingest) {
        this.messages = messages;
        this.mailboxes = mailboxes;
        this.imap = imap;
        this.ingest = ingest;
    }

    public boolean startAiReply(UUID mailMessageId) {
        MailMessage m = messages.findById(mailMessageId).orElse(null);
        if (m == null) {
            return false;
        }
        Mailbox mailbox = mailboxes.byId(m.getMailboxId()).orElse(null);
        if (mailbox == null) {
            return false;
        }
        ParsedEmail parsed = imap.fetchParsed(mailbox, m.getFolder(), m.getUidValidity(), m.getUid()).orElse(null);
        if (parsed == null) {
            log.warn("Не удалось прочитать письмо {} для генерации ответа", mailMessageId);
            return false;
        }
        UUID emailId = ingest.ensureEmail(parsed, mailbox);   // публикует ReplyRequested внутри транзакции
        log.info("[{}] Запрошена генерация ответа на письмо {}", mailbox.getId(), emailId);
        return true;
    }
}
