package com.signet.review;

import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.Attachment;
import com.signet.shared.domain.Draft;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.domain.ReviewChannel;
import com.signet.shared.domain.ReviewTask;
import com.signet.shared.repo.AttachmentRepository;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.ReviewTaskRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Короткие транзакции вокруг ревью — отдельный бин, иначе {@code @Transactional}
 * не сработал бы при вызове изнутри {@link ReviewService} (self-invocation минует прокси).
 */
@Service
public class ReviewTransactions {

    private final EmailRepository emails;
    private final DraftRepository drafts;
    private final ReviewTaskRepository reviews;
    private final AttachmentRepository attachments;
    private final MailboxRegistry mailboxes;

    public ReviewTransactions(EmailRepository emails,
                              DraftRepository drafts,
                              ReviewTaskRepository reviews,
                              AttachmentRepository attachments,
                              MailboxRegistry mailboxes) {
        this.emails = emails;
        this.drafts = drafts;
        this.reviews = reviews;
        this.attachments = attachments;
        this.mailboxes = mailboxes;
    }

    /** Уже есть задача ревью по этому письму — защита от повторной доставки {@code DraftReady}. */
    @Transactional(readOnly = true)
    public boolean reviewAlreadyOpen(UUID emailId) {
        return reviews.findByEmailId(emailId).isPresent();
    }

    @Transactional(readOnly = true)
    public ReviewPayload loadForReview(UUID emailId, UUID draftId) {
        Email email = emails.findById(emailId).orElseThrow();
        Draft draft = drafts.findById(draftId).orElseThrow();
        String label = mailboxes.byId(email.getMailboxId())
                .map(Mailbox::getUsername)
                .orElse(email.getMailboxId());
        return new ReviewPayload(emailId, draftId, label, email.getFromAddr(), email.getSubject(),
                email.getLanguage(), email.getBody(), draft.getAiText(), draft.getAiTextRu());
    }

    /** Только id — тяжёлые файлы грузим по одному. */
    @Transactional(readOnly = true)
    public List<UUID> attachmentIds(UUID emailId) {
        return attachments.findIdsByEmailId(emailId);
    }

    @Transactional(readOnly = true)
    public Optional<Attachment> loadAttachment(UUID attachmentId) {
        return attachments.findById(attachmentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistReviewTask(UUID emailId, UUID draftId, Integer messageId) {
        ReviewTask task = new ReviewTask(emailId, draftId, ReviewChannel.TELEGRAM);
        task.setChatRef(messageId != null ? messageId.toString() : null);
        reviews.save(task);
        emails.findById(emailId).ifPresent(e -> {
            e.setStatus(EmailStatus.PENDING_REVIEW);
            emails.save(e);
        });
    }
}
