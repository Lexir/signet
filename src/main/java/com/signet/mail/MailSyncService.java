package com.signet.mail;

import com.signet.mail.ImapClient.FolderInfo;
import com.signet.shared.config.Mailbox;
import jakarta.mail.MessagingException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Синхронизация зеркала ящика: список папок + инкрементальный envelope-синк писем.
 * Сетевой обмен с IMAP идёт здесь (вне транзакции), запись в БД — короткими
 * транзакциями в {@link MailSyncTransactions}. Ничего в ящике не мутируется.
 */
@Service
public class MailSyncService {

    private static final Logger log = LoggerFactory.getLogger(MailSyncService.class);

    /** Сколько свежих писем тянуть за цикл на папку (и потолок первичного backfill). */
    private static final int NEW_LIMIT = 200;
    /** Окно обновления флагов (seen/answered) для недавних писем. */
    private static final int FLAG_WINDOW = 300;

    private final ImapClient imap;
    private final MailSyncTransactions tx;

    public MailSyncService(ImapClient imap, MailSyncTransactions tx) {
        this.imap = imap;
        this.tx = tx;
    }

    public void syncMailbox(Mailbox mailbox) {
        try (ImapClient.ImapSession session = imap.open(mailbox)) {
            List<FolderInfo> folders = session.listFolders();
            if (folders.isEmpty()) {
                return;
            }
            tx.upsertFolders(mailbox.getId(), folders);

            int totalNew = 0;
            for (FolderInfo folder : folders) {
                if (!folder.selectable()) {
                    continue;
                }
                MailSyncTransactions.Cursor cur = tx.cursor(mailbox.getId(), folder.name());
                var sync = session.syncFolder(folder.name(),
                        cur.lastUid(), cur.uidValidity(), NEW_LIMIT, FLAG_WINDOW);
                if (sync.isPresent()) {
                    totalNew += tx.apply(mailbox.getId(), folder.name(), sync.get());
                }
            }
            if (totalNew > 0) {
                log.info("[{}] синк: +{} писем", mailbox.getId(), totalNew);
            }
        } catch (MessagingException ex) {
            log.error("[{}] не удалось подключиться к IMAP: {}", mailbox.getId(), ex.getMessage());
        }
    }
}
