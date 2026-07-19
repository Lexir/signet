package com.signet.ingest;

import com.signet.context.ConversationService;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.Attachment;
import com.signet.shared.domain.Conversation;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.event.Events;
import com.signet.shared.repo.AttachmentRepository;
import com.signet.shared.repo.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Приём письма: дедуп, фильтрация авто-ответов/рассылок и петель, сохранение
 * с привязкой к ящику ({@code mailbox_id}) и публикация {@link Events.EmailReceived}.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final EmailRepository emails;
    private final AttachmentRepository attachments;
    private final ConversationService conversations;
    private final ApplicationEventPublisher events;

    public IngestService(EmailRepository emails,
                         AttachmentRepository attachments,
                         ConversationService conversations,
                         ApplicationEventPublisher events) {
        this.emails = emails;
        this.attachments = attachments;
        this.conversations = conversations;
        this.events = events;
    }

    /**
     * @return true, если письмо обработано (сохранено, дубликат или пропущено) —
     *         можно помечать прочитанным.
     */
    @Transactional
    public boolean ingest(ParsedEmail parsed, Mailbox mailbox) {
        if (parsed.messageId() == null) {
            log.warn("Письмо без Message-ID — пропущено");
            return true;
        }
        if (emails.existsByMessageId(parsed.messageId())) {
            return true; // дубликат
        }
        if (parsed.automated()) {
            log.info("[{}] Авто-письмо/рассылка/no-reply от {} — не отвечаем", mailbox.getId(), parsed.from());
            return true;
        }
        if (isOwnAddress(parsed.from(), mailbox)) {
            log.info("[{}] Письмо от собственного адреса — игнор (петля)", mailbox.getId());
            return true;
        }

        Conversation conv = conversations.getOrCreate(parsed.threadRoot(), parsed.from());

        Email email = new Email();
        email.setMessageId(parsed.messageId());
        email.setMailboxId(mailbox.getId());
        email.setConversationId(conv.getId());
        email.setThreadId(parsed.threadRoot());
        email.setFromAddr(parsed.from());
        email.setToAddr(parsed.to());
        email.setSubject(parsed.subject());
        email.setBody(parsed.body());
        email.setReceivedAt(parsed.receivedAt());
        email.setStatus(EmailStatus.RECEIVED);
        emails.save(email);

        for (ParsedAttachment att : parsed.attachments()) {
            attachments.save(new Attachment(email.getId(), att.filename(), att.contentType(), att.data()));
        }

        conversations.recordClientMessage(conv.getId(), email.getId(), parsed.body());

        events.publishEvent(new Events.EmailReceived(email.getId()));
        log.info("[{}] Принято письмо {} от {}", mailbox.getId(), email.getId(), parsed.from());
        return true;
    }

    private boolean isOwnAddress(String from, Mailbox mailbox) {
        return from != null && mailbox.getUsername() != null
                && from.equalsIgnoreCase(mailbox.getUsername());
    }
}
