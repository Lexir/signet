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
    private static final int FLAG_WINDOW = 100;
    /** Сколько самых свежих писем INBOX предзагружать вместе с телом (мгновенное открытие).
     *  Каждое тело — полная выкачка MIME (с вложениями), поэтому число маленькое:
     *  остальные тела дозагружаются лениво при открытии письма. */
    private static final int INBOX_BODY_PREFETCH = 5;

    private final ImapClient imap;
    private final MailSyncTransactions tx;

    public MailSyncService(ImapClient imap, MailSyncTransactions tx) {
        this.imap = imap;
        this.tx = tx;
    }

    /** Полный синк ящика: список папок + инкрементальный синк каждой selectable-папки. */
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
                totalNew += syncOne(session, mailbox, folder.name());
            }
            if (totalNew > 0) {
                log.info("[{}] синк: +{} писем", mailbox.getId(), totalNew);
            }
        } catch (MessagingException ex) {
            log.error("[{}] не удалось подключиться к IMAP: {}", mailbox.getId(), ex.getMessage());
        }
    }

    /**
     * Быстрый синк ОДНОЙ папки (для автообновления текущей папки в UI):
     * одно соединение, один SELECT — без обхода остальных папок ящика.
     */
    public void syncFolder(Mailbox mailbox, String folderName) {
        try (ImapClient.ImapSession session = imap.open(mailbox)) {
            int added = syncOne(session, mailbox, folderName);
            if (added > 0) {
                log.info("[{}] синк {}: +{} писем", mailbox.getId(), folderName, added);
            }
        } catch (MessagingException ex) {
            log.error("[{}] не удалось подключиться к IMAP: {}", mailbox.getId(), ex.getMessage());
        }
    }

    private int syncOne(ImapClient.ImapSession session, Mailbox mailbox, String folderName) {
        MailSyncTransactions.Cursor cur = tx.cursor(mailbox.getId(), folderName);
        // Тело предзагружаем только для INBOX — «недавно пришедшие» письма.
        int bodyPrefetch = "INBOX".equalsIgnoreCase(folderName) ? INBOX_BODY_PREFETCH : 0;
        var sync = session.syncFolder(folderName,
                cur.lastUid(), cur.uidValidity(), NEW_LIMIT, FLAG_WINDOW, bodyPrefetch);
        return sync.map(s -> tx.apply(mailbox.getId(), folderName, s)).orElse(0);
    }
}
