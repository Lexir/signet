package com.signet.mail;

import com.signet.mail.ImapClient.EnvelopeInfo;
import com.signet.mail.ImapClient.FlagInfo;
import com.signet.mail.ImapClient.FolderInfo;
import com.signet.mail.ImapClient.FolderSync;
import com.signet.shared.domain.MailFolder;
import com.signet.shared.domain.MailMembership;
import com.signet.shared.domain.MailMessage;
import com.signet.shared.repo.MailFolderRepository;
import com.signet.shared.repo.MailMembershipRepository;
import com.signet.shared.repo.MailMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Короткие транзакции вокруг синка — отдельный бин, чтобы {@code @Transactional}
 * применялся через прокси (self-invocation из {@link MailSyncService} минует его).
 * Пишет дедуплицированно: контент письма ({@link MailMessage}) один раз на Message-ID,
 * принадлежность к папке ({@link MailMembership}) — на каждую (папка, uid).
 */
@Service
public class MailSyncTransactions {

    private final MailFolderRepository folders;
    private final MailMessageRepository messages;
    private final MailMembershipRepository memberships;

    public MailSyncTransactions(MailFolderRepository folders,
                                MailMessageRepository messages,
                                MailMembershipRepository memberships) {
        this.folders = folders;
        this.messages = messages;
        this.memberships = memberships;
    }

    /** Курсор инкрементального синка папки: докуда дочитали и какой UIDVALIDITY. */
    public record Cursor(long lastUid, Long uidValidity) {
    }

    /** Апсерт списка папок. Счётчики писем НЕ трогаем — их обновляет синк папки (apply). */
    @Transactional
    public void upsertFolders(String mailboxId, List<FolderInfo> infos) {
        for (FolderInfo info : infos) {
            MailFolder f = folders.findByMailboxIdAndName(mailboxId, info.name())
                    .orElseGet(() -> new MailFolder(mailboxId, info.name()));
            f.setDelimiter(info.delimiter());
            f.setSelectable(info.selectable());
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
     * Применяет результат синка папки: при смене UIDVALIDITY чистит членства папки, апсертит
     * контент (дедуп по Message-ID) и членства, обновляет флаги недавних, двигает курсор.
     *
     * @return число вставленных новых членств
     */
    @Transactional
    public int apply(String mailboxId, String folderName, FolderSync sync) {
        if (sync.reset()) {
            memberships.deleteByMailboxIdAndFolder(mailboxId, folderName);
        }
        long maxUid = 0;
        int inserted = 0;
        for (EnvelopeInfo e : sync.messages()) {
            String messageKey = messageKey(mailboxId, folderName, sync.uidValidity(), e);
            upsertContent(mailboxId, messageKey, e);
            if (upsertMembership(mailboxId, folderName, sync.uidValidity(), e, messageKey)) {
                inserted++;
            }
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

    /** Ключ дедупа: Message-ID письма, а для писем без него — синтетический ключ на членство. */
    private String messageKey(String mailboxId, String folderName, long uidValidity, EnvelopeInfo e) {
        if (e.messageId() != null && !e.messageId().isBlank()) {
            return e.messageId();
        }
        return "syn:" + mailboxId + ':' + folderName + ':' + uidValidity + ':' + e.uid();
    }

    private void upsertContent(String mailboxId, String messageKey, EnvelopeInfo e) {
        MailMessage msg = messages.findByMailboxIdAndMessageId(mailboxId, messageKey).orElse(null);
        if (msg == null) {
            msg = new MailMessage(mailboxId, messageKey);
            msg.setFromAddr(e.from());
            msg.setToAddr(e.to());
            msg.setSubject(e.subject());
            msg.setSentAt(e.sentAt());
            msg.setSizeBytes(e.size());
        }
        // Тело кэшируем один раз (общее для всех папок этого письма).
        if (e.bodyFetched() && msg.getBodySyncedAt() == null) {
            msg.setBodyText(e.bodyText() == null ? "" : e.bodyText());
            msg.setHasAttachments(e.hasAttachments());
            msg.setBodySyncedAt(Instant.now());
        }
        messages.save(msg);
    }

    private boolean upsertMembership(String mailboxId, String folderName, long uidValidity,
                                     EnvelopeInfo e, String messageKey) {
        MailMembership mem = memberships
                .findByMailboxIdAndFolderAndUidValidityAndUid(mailboxId, folderName, uidValidity, e.uid())
                .orElse(null);
        boolean isNew = mem == null;
        if (isNew) {
            mem = new MailMembership(mailboxId, folderName, e.uid(), uidValidity, messageKey);
        }
        mem.setSeen(e.seen());
        mem.setAnswered(e.answered());
        mem.setFlagged(e.flagged());
        memberships.save(mem);
        return isNew;
    }

    private void applyFlags(String mailboxId, String folderName, long uidValidity, List<FlagInfo> flags) {
        if (flags.isEmpty()) {
            return;
        }
        // Одним запросом вместо запроса на каждый UID: окно флагов — до сотни писем.
        Map<Long, MailMembership> byUid = memberships
                .findByMailboxIdAndFolderAndUidValidityAndUidIn(mailboxId, folderName, uidValidity,
                        flags.stream().map(FlagInfo::uid).toList())
                .stream().collect(Collectors.toMap(MailMembership::getUid, m -> m));
        for (FlagInfo fl : flags) {
            MailMembership m = byUid.get(fl.uid());
            if (m != null && (m.isSeen() != fl.seen()
                    || m.isAnswered() != fl.answered() || m.isFlagged() != fl.flagged())) {
                m.setSeen(fl.seen());
                m.setAnswered(fl.answered());
                m.setFlagged(fl.flagged());
                memberships.save(m);
            }
        }
    }
}
