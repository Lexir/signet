package com.signet.send;

import com.signet.context.ConversationService;
import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.Draft;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.domain.SendLog;
import com.signet.shared.event.Events;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.SendLogRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Короткие транзакции вокруг отправки. Каждый метод — отдельная транзакция
 * (REQUIRES_NEW), чтобы соединение с БД не удерживалось во время работы с SMTP.
 */
@Service
public class SendTransactions {

    private static final Logger log = LoggerFactory.getLogger(SendTransactions.class);

    private final EmailRepository emails;
    private final DraftRepository drafts;
    private final SendLogRepository sendLog;
    private final MailboxRegistry mailboxes;
    private final ConversationService conversations;
    private final ApplicationEventPublisher events;

    public SendTransactions(EmailRepository emails,
                            DraftRepository drafts,
                            SendLogRepository sendLog,
                            MailboxRegistry mailboxes,
                            ConversationService conversations,
                            ApplicationEventPublisher events) {
        this.emails = emails;
        this.drafts = drafts;
        this.sendLog = sendLog;
        this.mailboxes = mailboxes;
        this.conversations = conversations;
        this.events = events;
    }

    /**
     * Захватывает письмо под отправку: переводит в SENDING и СРАЗУ коммитит.
     * Благодаря этому при падении после SMTP, но до фиксации результата,
     * повторная доставка события увидит SENDING и не отправит письмо второй раз.
     *
     * @return данные для отправки, либо empty — если письмо уже занято/отправлено
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SendPayload> claim(UUID emailId) {
        // Блокируем строку письма: конкурентные захваты (async-доставка события или
        // повторная доставка при восстановлении) сериализуются здесь, поэтому проверка
        // статуса ниже надёжна и письмо не отправится дважды.
        Email email = emails.findByIdForUpdate(emailId).orElse(null);
        if (email == null) {
            return Optional.empty();
        }
        if (email.getStatus() == EmailStatus.SENT || email.getStatus() == EmailStatus.SENDING) {
            log.info("Письмо {} уже в статусе {} — повторная отправка не выполняется",
                    emailId, email.getStatus());
            return Optional.empty();
        }
        Draft draft = drafts.findFirstByEmailIdOrderByCreatedAtDesc(emailId)
                .filter(d -> d.getFinalText() != null && !d.getFinalText().isBlank())
                .orElse(null);
        if (draft == null) {
            log.warn("Нет финального текста для письма {} — отправка отменена", emailId);
            return Optional.empty();
        }
        Mailbox mailbox = mailboxes.byId(email.getMailboxId()).filter(Mailbox::hasSmtp).orElse(null);
        if (mailbox == null) {
            log.error("Не найден ящик '{}' с SMTP для письма {}", email.getMailboxId(), emailId);
            failInternal(email, "mailbox not found: " + email.getMailboxId());
            return Optional.empty();
        }

        email.setStatus(EmailStatus.SENDING);
        emails.save(email);

        return Optional.of(new SendPayload(
                emailId,
                email.getConversationId(),
                email.getMessageId(),
                email.getFromAddr(),
                email.getSubject(),
                draft.getFinalText(),
                mailbox));
    }

    /** Фиксирует успешную отправку. Повторная запись отсекается индексом в БД. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(SendPayload payload, String smtpMessageId) {
        try {
            sendLog.saveAndFlush(new SendLog(
                    payload.emailId(), payload.mailbox().getId(), "SENT", smtpMessageId, null));
        } catch (DataIntegrityViolationException ex) {
            // Сработал uq_send_log_sent_once: письмо уже помечено отправленным.
            log.warn("Повторная фиксация отправки письма {} отклонена БД", payload.emailId());
            return;
        }
        emails.findById(payload.emailId()).ifPresent(e -> {
            e.setStatus(EmailStatus.SENT);
            emails.save(e);
        });
        conversations.recordAssistantMessage(payload.conversationId(), payload.finalText());
        events.publishEvent(new Events.EmailSent(payload.emailId()));
    }

    /** Фиксирует неудачу и уведомляет человека — молча терять письма нельзя. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID emailId, String reason) {
        emails.findById(emailId).ifPresent(email -> failInternal(email, reason));
    }

    private void failInternal(Email email, String reason) {
        sendLog.save(new SendLog(email.getId(), email.getMailboxId(), "FAILED", null, reason));
        email.setStatus(EmailStatus.FAILED);
        emails.save(email);
        events.publishEvent(new Events.ProcessingFailed(email.getId(), "send", reason));
    }
}
