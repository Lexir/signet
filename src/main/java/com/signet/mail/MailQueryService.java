package com.signet.mail;

import com.signet.ingest.ParsedAttachment;
import com.signet.settings.MailboxRegistry;
import com.signet.shared.domain.Draft;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.MailMembership;
import com.signet.shared.domain.MailMessage;
import com.signet.shared.domain.ReviewChannel;
import com.signet.shared.domain.ReviewTask;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.MailFolderRepository;
import com.signet.shared.repo.MailMembershipRepository;
import com.signet.shared.repo.MailMessageRepository;
import com.signet.shared.repo.ReviewTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Чтение зеркала для UI. Список и открытие письма идут через членство в папке
 * ({@link MailMembership}); контент и тело берутся из общего {@link MailMessage}
 * (один раз на Message-ID), поэтому тело кэшируется единожды и разделяется папками.
 */
@Service
public class MailQueryService {

    private final MailboxRegistry mailboxes;
    private final MailFolderRepository folders;
    private final MailMembershipRepository memberships;
    private final MailMessageRepository messages;
    private final EmailRepository emails;
    private final DraftRepository drafts;
    private final ReviewTaskRepository reviews;
    private final ImapClient imap;

    public MailQueryService(MailboxRegistry mailboxes,
                            MailFolderRepository folders,
                            MailMembershipRepository memberships,
                            MailMessageRepository messages,
                            EmailRepository emails,
                            DraftRepository drafts,
                            ReviewTaskRepository reviews,
                            ImapClient imap) {
        this.mailboxes = mailboxes;
        this.folders = folders;
        this.memberships = memberships;
        this.messages = messages;
        this.emails = emails;
        this.drafts = drafts;
        this.reviews = reviews;
        this.imap = imap;
    }

    // --- DTO ---

    public record MailboxView(String id, String username, ReviewChannel reviewChannel) {
    }

    public record FolderView(String name, String delimiter, boolean selectable, int total, int unread) {
    }

    /** {@code id} — идентификатор членства (по нему открывают/отвечают/генерируют). */
    public record MessageSummary(UUID id, String from, String subject, Instant sentAt,
                                 boolean seen, boolean answered, boolean flagged,
                                 boolean hasAttachments, int sizeBytes) {
    }

    public record MessagePage(List<MessageSummary> content, int page, int size, long total) {
    }

    public record AttachmentView(int index, String filename, String contentType, int size) {
    }

    public record MessageDetail(UUID id, String mailboxId, String folder, String from, String to,
                                String subject, Instant sentAt, boolean seen, boolean answered,
                                boolean flagged, boolean hasAttachments, String body) {
    }

    public record DraftView(UUID emailId, String emailStatus, String reviewStatus,
                            String aiText, String aiTextRu, String finalText) {
    }

    // --- Чтение ---

    public List<MailboxView> mailboxViews() {
        return mailboxes.entities().stream()
                .map(e -> new MailboxView(e.getId(), e.getUsername(), e.getReviewChannel()))
                .toList();
    }

    public List<FolderView> folderViews(String mailboxId) {
        return folders.findByMailboxIdOrderByNameAsc(mailboxId).stream()
                .map(f -> new FolderView(f.getName(), f.getDelimiter(), f.isSelectable(),
                        f.getTotalCount(), f.getUnreadCount()))
                .toList();
    }

    public MessagePage messages(String mailboxId, String folder, int page, int size) {
        Page<MailMembership> p = memberships.findByMailboxIdAndFolderOrderByUidDesc(
                mailboxId, folder, PageRequest.of(page, size));
        List<String> ids = p.getContent().stream().map(MailMembership::getMessageId).distinct().toList();
        Map<String, MailMessage> byKey = ids.isEmpty() ? Map.of()
                : messages.findByMailboxIdAndMessageIdIn(mailboxId, ids).stream()
                        .collect(Collectors.toMap(MailMessage::getMessageId, Function.identity(), (a, b) -> a));

        List<MessageSummary> content = p.getContent().stream().map(m -> {
            MailMessage msg = byKey.get(m.getMessageId());
            return new MessageSummary(m.getId(),
                    msg == null ? null : msg.getFromAddr(),
                    msg == null ? null : msg.getSubject(),
                    msg == null ? null : msg.getSentAt(),
                    m.isSeen(), m.isAnswered(), m.isFlagged(),
                    msg != null && msg.isHasAttachments(),
                    msg == null ? 0 : msg.getSizeBytes());
        }).toList();
        return new MessagePage(content, page, size, p.getTotalElements());
    }

    /** Детали письма по членству; тело дозагружается один раз в общий контент. */
    public Optional<MessageDetail> message(UUID membershipId) {
        MailMembership mem = memberships.findById(membershipId).orElse(null);
        if (mem == null) {
            return Optional.empty();
        }
        MailMessage msg = messages.findByMailboxIdAndMessageId(mem.getMailboxId(), mem.getMessageId()).orElse(null);
        if (msg == null) {
            return Optional.empty();
        }
        if (msg.getBodySyncedAt() == null) {
            mailboxes.byId(mem.getMailboxId()).flatMap(mbx ->
                    imap.fetchBody(mbx, mem.getFolder(), mem.getUidValidity(), mem.getUid())
            ).ifPresent(body -> {
                msg.setBodyText(body.bodyText() == null ? "" : body.bodyText());
                msg.setHasAttachments(body.hasAttachments());
                msg.setBodySyncedAt(Instant.now());
                messages.save(msg);
            });
        }
        return Optional.of(new MessageDetail(mem.getId(), mem.getMailboxId(), mem.getFolder(),
                msg.getFromAddr(), msg.getToAddr(), msg.getSubject(), msg.getSentAt(),
                mem.isSeen(), mem.isAnswered(), mem.isFlagged(), msg.isHasAttachments(),
                msg.getBodyText() == null ? "" : msg.getBodyText()));
    }

    /** Список вложений письма (live-фетч из IMAP; байты не храним). */
    public List<AttachmentView> attachments(UUID membershipId) {
        MailMembership mem = memberships.findById(membershipId).orElse(null);
        if (mem == null) {
            return List.of();
        }
        return mailboxes.byId(mem.getMailboxId())
                .flatMap(mbx -> imap.fetchBody(mbx, mem.getFolder(), mem.getUidValidity(), mem.getUid()))
                .map(body -> {
                    List<AttachmentView> out = new java.util.ArrayList<>();
                    List<ParsedAttachment> atts = body.attachments();
                    for (int i = 0; i < atts.size(); i++) {
                        ParsedAttachment a = atts.get(i);
                        out.add(new AttachmentView(i, a.filename(), a.contentType(),
                                a.data() != null ? a.data().length : 0));
                    }
                    return out;
                })
                .orElse(List.of());
    }

    /** Байты конкретного вложения (live-фетч из IMAP). */
    public Optional<ParsedAttachment> attachment(UUID membershipId, int index) {
        MailMembership mem = memberships.findById(membershipId).orElse(null);
        if (mem == null) {
            return Optional.empty();
        }
        return mailboxes.byId(mem.getMailboxId())
                .flatMap(mbx -> imap.fetchAttachment(mbx, mem.getFolder(), mem.getUidValidity(), mem.getUid(), index));
    }

    /** Черновик ответа на письмо — связь через Message-ID контента → письмо воронки. */
    public Optional<DraftView> draftFor(UUID membershipId) {
        MailMembership mem = memberships.findById(membershipId).orElse(null);
        if (mem == null) {
            return Optional.empty();
        }
        Email email = emails.findByMessageId(mem.getMessageId()).orElse(null);
        if (email == null) {
            return Optional.empty();
        }
        Draft draft = drafts.findFirstByEmailIdOrderByCreatedAtDesc(email.getId()).orElse(null);
        ReviewTask task = reviews.findByEmailId(email.getId()).orElse(null);
        return Optional.of(new DraftView(
                email.getId(),
                email.getStatus().name(),
                task != null ? task.getStatus().name() : null,
                draft != null ? draft.getAiText() : null,
                draft != null ? draft.getAiTextRu() : null,
                draft != null ? draft.getFinalText() : null));
    }
}
