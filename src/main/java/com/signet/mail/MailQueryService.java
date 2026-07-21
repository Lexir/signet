package com.signet.mail;

import com.signet.ingest.ParsedAttachment;
import com.signet.settings.MailboxRegistry;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.Draft;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.MailFolder;
import com.signet.shared.domain.MailMessage;
import com.signet.shared.domain.ReviewChannel;
import com.signet.shared.domain.ReviewTask;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.MailFolderRepository;
import com.signet.shared.repo.MailMessageRepository;
import com.signet.shared.repo.ReviewTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Чтение зеркала ящика для UI: ящики, папки, письма (пагинация), письмо с ленивой
 * дозагрузкой тела и вложения. Тело кэшируется в {@code body_text} при первом открытии.
 */
@Service
public class MailQueryService {

    private final MailboxRegistry mailboxes;
    private final MailFolderRepository folders;
    private final MailMessageRepository messages;
    private final EmailRepository emails;
    private final DraftRepository drafts;
    private final ReviewTaskRepository reviews;
    private final ImapClient imap;

    public MailQueryService(MailboxRegistry mailboxes,
                            MailFolderRepository folders,
                            MailMessageRepository messages,
                            EmailRepository emails,
                            DraftRepository drafts,
                            ReviewTaskRepository reviews,
                            ImapClient imap) {
        this.mailboxes = mailboxes;
        this.folders = folders;
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

    /**
     * Черновик ответа на письмо (если генерация запускалась). {@code emailStatus} даёт статус
     * воронки (DRAFTING — ещё генерится, FAILED — ошибка модели, IGNORED — не личное),
     * {@code reviewStatus} — решение по ревью (PENDING/APPROVED/EDITED/REJECTED), null — нет задачи.
     */
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
        Page<MailMessage> p = messages.findByMailboxIdAndFolderOrderByUidDesc(
                mailboxId, folder, PageRequest.of(page, size));
        List<MessageSummary> content = p.getContent().stream()
                .map(m -> new MessageSummary(m.getId(), m.getFromAddr(), m.getSubject(), m.getSentAt(),
                        m.isSeen(), m.isAnswered(), m.isFlagged(), m.isHasAttachments(), m.getSizeBytes()))
                .toList();
        return new MessagePage(content, page, size, p.getTotalElements());
    }

    /** Детали письма с ленивой дозагрузкой и кэшированием тела. */
    public Optional<MessageDetail> message(UUID id) {
        Optional<MailMessage> found = messages.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MailMessage m = found.get();
        if (m.getBodySyncedAt() == null) {
            mailboxes.byId(m.getMailboxId()).flatMap(mbx ->
                    imap.fetchBody(mbx, m.getFolder(), m.getUidValidity(), m.getUid())
            ).ifPresent(body -> {
                m.setBodyText(body.bodyText() == null ? "" : body.bodyText());
                m.setHasAttachments(body.hasAttachments());
                m.setBodySyncedAt(Instant.now());
                messages.save(m);
            });
        }
        return Optional.of(new MessageDetail(m.getId(), m.getMailboxId(), m.getFolder(), m.getFromAddr(),
                m.getToAddr(), m.getSubject(), m.getSentAt(), m.isSeen(), m.isAnswered(), m.isFlagged(),
                m.isHasAttachments(), m.getBodyText() == null ? "" : m.getBodyText()));
    }

    /** Список вложений письма (live-фетч из IMAP; байты не храним). */
    public List<AttachmentView> attachments(UUID id) {
        MailMessage m = messages.findById(id).orElse(null);
        if (m == null) {
            return List.of();
        }
        return mailboxes.byId(m.getMailboxId())
                .flatMap(mbx -> imap.fetchBody(mbx, m.getFolder(), m.getUidValidity(), m.getUid()))
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
    public Optional<ParsedAttachment> attachment(UUID id, int index) {
        MailMessage m = messages.findById(id).orElse(null);
        if (m == null) {
            return Optional.empty();
        }
        return mailboxes.byId(m.getMailboxId())
                .flatMap(mbx -> imap.fetchAttachment(mbx, m.getFolder(), m.getUidValidity(), m.getUid(), index));
    }

    /**
     * Черновик ответа на письмо зеркала — связь через Message-ID (письмо зеркала → письмо
     * воронки → последний черновик). Пусто, если генерацию не запускали.
     */
    public Optional<DraftView> draftFor(UUID mailMessageId) {
        MailMessage m = messages.findById(mailMessageId).orElse(null);
        if (m == null || m.getMessageId() == null || m.getMessageId().isBlank()) {
            return Optional.empty();
        }
        Email email = emails.findByMessageId(m.getMessageId()).orElse(null);
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

    /** Исходное письмо зеркала (для флоу ответа). */
    public Optional<MailMessage> raw(UUID id) {
        return messages.findById(id);
    }

    /** POJO ящика по id (для отправки/дозагрузки). */
    public Optional<Mailbox> mailbox(String mailboxId) {
        return mailboxes.byId(mailboxId);
    }
}
