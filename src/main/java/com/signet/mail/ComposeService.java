package com.signet.mail;

import com.signet.send.MailSenderFactory;
import com.signet.send.ReplyBuilder;
import com.signet.send.SendPayload;
import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.MailMessage;
import com.signet.shared.repo.MailMessageRepository;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Ручной ответ из почтового клиента: авторский текст уходит клиенту по SMTP в тот же
 * тред. Переиспользует сборку/отправку из модуля send; воронку ai/review не трогает.
 */
@Service
public class ComposeService {

    private static final Logger log = LoggerFactory.getLogger(ComposeService.class);

    private final MailMessageRepository messages;
    private final MailboxRegistry mailboxes;
    private final MailSenderFactory senderFactory;
    private final ReplyBuilder replyBuilder;
    private final ImapClient imap;

    public ComposeService(MailMessageRepository messages,
                          MailboxRegistry mailboxes,
                          MailSenderFactory senderFactory,
                          ReplyBuilder replyBuilder,
                          ImapClient imap) {
        this.messages = messages;
        this.mailboxes = mailboxes;
        this.senderFactory = senderFactory;
        this.replyBuilder = replyBuilder;
        this.imap = imap;
    }

    /**
     * Отправляет ответ на письмо зеркала. Возвращает false, если письмо/ящик не найдены.
     *
     * <p>Сознательно НЕ {@code @Transactional}: SMTP-отправка и IMAP-APPEND — сетевые
     * операции на секунды, и держать на них соединение с БД нельзя (тот же принцип, что в
     * {@code SendWorkflow} с {@code NOT_SUPPORTED}). Отметку {@code answered} пишем отдельным
     * коротким {@code save} уже после успешной отправки.
     */
    public boolean sendReply(UUID mailMessageId, String text) {
        MailMessage m = messages.findById(mailMessageId).orElse(null);
        if (m == null) {
            return false;
        }
        Mailbox mailbox = mailboxes.byId(m.getMailboxId()).orElse(null);
        if (mailbox == null || m.getFromAddr() == null) {
            return false;
        }
        // messageId/conversationId воронки здесь не участвуют — это авторский ответ вне неё.
        SendPayload payload = new SendPayload(null, null, m.getMessageId(),
                m.getFromAddr(), m.getSubject(), text, mailbox);
        try {
            JavaMailSender sender = senderFactory.forMailbox(mailbox);
            MimeMessage mime = replyBuilder.build(sender, payload, mailbox.getUsername());
            sender.send(mime);                        // вне транзакции
            imap.appendToSent(mailbox, mime);         // best-effort: показать ответ в «Отправленных»
        } catch (Exception ex) {
            log.error("[{}] Не удалось отправить ручной ответ: {}", mailbox.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("send failed", ex);
        }
        // Короткая запись уже после отправки (save сам оборачивается в транзакцию).
        m.setAnswered(true);
        messages.save(m);
        log.info("[{}] Ручной ответ отправлен на {}", mailbox.getId(), m.getFromAddr());
        return true;
    }
}
