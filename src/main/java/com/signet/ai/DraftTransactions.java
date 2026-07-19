package com.signet.ai;

import com.signet.context.ConversationService;
import com.signet.context.ThreadContext;
import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.Draft;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.event.Events;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Короткие транзакции вокруг генерации черновика. Сами вызовы модели идут вне
 * транзакции — они длятся десятки секунд и не должны держать соединение с БД.
 */
@Service
public class DraftTransactions {

    private static final Logger log = LoggerFactory.getLogger(DraftTransactions.class);

    private final EmailRepository emails;
    private final DraftRepository drafts;
    private final ConversationService conversations;
    private final MailboxRegistry mailboxes;
    private final AiProperties props;
    private final ApplicationEventPublisher events;

    public DraftTransactions(EmailRepository emails,
                             DraftRepository drafts,
                             ConversationService conversations,
                             MailboxRegistry mailboxes,
                             AiProperties props,
                             ApplicationEventPublisher events) {
        this.emails = emails;
        this.drafts = drafts;
        this.conversations = conversations;
        this.mailboxes = mailboxes;
        this.props = props;
        this.events = events;
    }

    /** Захватывает письмо в работу (RECEIVED → DRAFTING) и отдаёт всё нужное для генерации. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<DraftPayload> claim(UUID emailId) {
        Email email = emails.findById(emailId).orElse(null);
        if (email == null || email.getStatus() != EmailStatus.RECEIVED) {
            return Optional.empty();
        }
        email.setStatus(EmailStatus.DRAFTING);
        emails.save(email);

        ThreadContext ctx = conversations.buildContext(email.getConversationId(), props.getHistoryWindow());
        String profile = mailboxes.byId(email.getMailboxId()).map(Mailbox::getProfile).orElse("");

        return Optional.of(new DraftPayload(
                emailId, email.getFromAddr(), email.getSubject(), email.getBody(), profile, ctx));
    }

    /** Письмо признано неличным — отвечать не будем. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIgnored(UUID emailId, String from) {
        emails.findById(emailId).ifPresent(e -> {
            e.setStatus(EmailStatus.IGNORED);
            emails.save(e);
        });
        log.info("Письмо {} от {} — не личное, ответ не генерируем", emailId, from);
    }

    /** Сохраняет готовый черновик и запускает этап ревью. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveDraft(UUID emailId, DraftService.GenerationResult result) {
        DraftResponse r = result.response();
        Draft draft = new Draft(emailId);
        draft.setAiText(r.answer());
        draft.setAiTextRu(r.answerRu());
        draft.setModel(result.model());
        draft.setTokensIn(result.tokensIn());
        draft.setTokensOut(result.tokensOut());
        drafts.save(draft);

        emails.findById(emailId).ifPresent(e -> {
            e.setLanguage(r.language());
            e.setStatus(EmailStatus.DRAFTED);
            emails.save(e);
        });
        events.publishEvent(new Events.DraftReady(emailId, draft.getId()));
        log.info("Черновик готов для письма {} (язык {})", emailId, r.language());
    }

    /** Генерация сорвалась — фиксируем и уведомляем человека. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID emailId, String reason) {
        emails.findById(emailId).ifPresent(e -> {
            e.setStatus(EmailStatus.FAILED);
            emails.save(e);
        });
        events.publishEvent(new Events.ProcessingFailed(emailId, "draft", reason));
    }
}
