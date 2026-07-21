package com.signet.mail;

import com.signet.mail.ImapClient.EnvelopeInfo;
import com.signet.mail.ImapClient.FlagInfo;
import com.signet.mail.ImapClient.FolderInfo;
import com.signet.mail.ImapClient.FolderSync;
import com.signet.shared.domain.MailFolder;
import com.signet.shared.domain.MailMessage;
import com.signet.shared.repo.MailFolderRepository;
import com.signet.shared.repo.MailMessageRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Короткие транзакции вокруг синка — отдельный бин, чтобы {@code @Transactional}
 * применялся через прокси (self-invocation из {@link MailSyncService} минует его).
 */
@Service
public class MailSyncTransactions {

    private final MailFolderRepository folders;
    private final MailMessageRepository messages;

    public MailSyncTransactions(MailFolderRepository folders, MailMessageRepository messages) {
        this.folders = folders;
        this.messages = messages;
    }

    /** Курсор инкрементального синка папки: докуда дочитали и какой UIDVALIDITY. */
    public record Cursor(long lastUid, Long uidValidity) {
    }

    @Transactional
    public void upsertFolders(String mailboxId, List<FolderInfo> infos) {
        for (FolderInfo info : infos) {
            MailFolder f = folders.findByMailboxIdAndName(mailboxId, info.name())
                    .orElseGet(() -> new MailFolder(mailboxId, info.name()));
            f.setDelimiter(info.delimiter());
            f.setSelectable(info.selectable());
            f.setTotalCount(info.total());
            f.setUnreadCount(info.unread());
            folders.save(f);
        }
    }

    @Transactional(readOnly = true)
    public Cursor cursor(String mailboxId, String folderName) {
        return folders.findByMailboxIdAndName(mailboxId, folderName)
                .map(f -> new Cursor(f.getLastSyncedUid(), f.getUidValidity()))
                .orElse(new Cursor(0, null));
    }

    /**
     * Применяет результат синка папки: при смене UIDVALIDITY чистит локальные записи,
     * апсертит новые envelope, обновляет флаги недавних и двигает курсор/счётчики.
     *
     * @return число вставленных новых писем
     */
    @Transactional
    public int apply(String mailboxId, String folderName, FolderSync sync) {
        if (sync.reset()) {
            messages.deleteByMailboxIdAndFolder(mailboxId, folderName);
        }
        long maxUid = 0;
        int inserted = 0;
        for (EnvelopeInfo e : sync.messages()) {
            MailMessage m = messages
                    .findByMailboxIdAndFolderAndUidValidityAndUid(mailboxId, folderName, sync.uidValidity(), e.uid())
                    .orElse(null);
            if (m == null) {
                m = new MailMessage(mailboxId, folderName, e.uid(), sync.uidValidity());
                m.setMessageId(e.messageId());
                m.setFromAddr(e.from());
                m.setToAddr(e.to());
                m.setSubject(e.subject());
                m.setSentAt(e.sentAt());
                m.setSizeBytes(e.size());
                inserted++;
            }
            m.setSeen(e.seen());
            m.setAnswered(e.answered());
            m.setFlagged(e.flagged());
            if (e.bodyFetched()) {                       // предзагруженное тело — открытие без IMAP
                m.setBodyText(e.bodyText() == null ? "" : e.bodyText());
                m.setHasAttachments(e.hasAttachments());
                m.setBodySyncedAt(Instant.now());
            }
            messages.save(m);
            maxUid = Math.max(maxUid, e.uid());
        }

        applyFlags(mailboxId, folderName, sync.uidValidity(), sync.recentFlags());

        MailFolder folder = folders.findByMailboxIdAndName(mailboxId, folderName)
                .orElseGet(() -> new MailFolder(mailboxId, folderName));
        folder.setUidValidity(sync.uidValidity());
        long base = sync.reset() ? 0 : folder.getLastSyncedUid();
        folder.setLastSyncedUid(Math.max(base, maxUid));
        folder.setTotalCount(sync.total());
        folder.setUnreadCount(sync.unread());
        folder.setSyncedAt(Instant.now());
        folders.save(folder);
        return inserted;
    }

    private void applyFlags(String mailboxId, String folderName, long uidValidity, List<FlagInfo> flags) {
        for (FlagInfo fl : flags) {
            messages.findByMailboxIdAndFolderAndUidValidityAndUid(mailboxId, folderName, uidValidity, fl.uid())
                    .ifPresent(m -> {
                        m.setSeen(fl.seen());
                        m.setAnswered(fl.answered());
                        m.setFlagged(fl.flagged());
                        messages.save(m);
                    });
        }
    }
}
