package com.signet.mail;

import com.signet.ingest.EmailParser;
import com.signet.ingest.ParsedAttachment;
import com.signet.ingest.ParsedEmail;
import com.signet.shared.config.Mailbox;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Тонкий IMAP-клиент для зеркала ящика. Все папки открываются {@code READ_ONLY} —
 * синк ничего в ящике не меняет (никаких mark-seen/переносов). Работа с Jakarta Mail
 * инкапсулирована здесь: наружу отдаются плоские записи, не привязанные к соединению.
 */
@Component
public class ImapClient {

    private static final Logger log = LoggerFactory.getLogger(ImapClient.class);

    private final EmailParser parser;

    public ImapClient(EmailParser parser) {
        this.parser = parser;
    }

    // --- Плоские записи для слоя синка/чтения ---

    public record FolderInfo(String name, String delimiter, boolean selectable, int total, int unread) {
    }

    public record EnvelopeInfo(long uid, String messageId, String from, String to, String subject,
                               Instant sentAt, int size, boolean seen, boolean answered, boolean flagged,
                               boolean bodyFetched, String bodyText, boolean hasAttachments) {
    }

    public record FlagInfo(long uid, boolean seen, boolean answered, boolean flagged) {
    }

    public record FolderSync(long uidValidity, boolean reset, int total, int unread,
                             List<EnvelopeInfo> messages, List<FlagInfo> recentFlags) {
    }

    public record BodyInfo(String bodyText, boolean hasAttachments, List<ParsedAttachment> attachments) {
    }

    /**
     * Открывает соединение с ящиком на весь цикл синка. Список папок и синк каждой папки
     * идут по ОДНОМУ {@link Store} — меньше IMAP-логинов на провайдере за цикл (важно
     * против блокировок). Закрывать через try-with-resources.
     */
    public ImapSession open(Mailbox mailbox) throws MessagingException {
        return new ImapSession(connect(mailbox), mailbox.getId());
    }

    /** Сессия синка: несколько операций над папками ящика по одному соединению. */
    public final class ImapSession implements AutoCloseable {

        private final Store store;
        private final String mailboxId;

        private ImapSession(Store store, String mailboxId) {
            this.store = store;
            this.mailboxId = mailboxId;
        }

        /** Список папок ящика со счётчиками (STATUS, без SELECT). */
        public List<FolderInfo> listFolders() {
            List<FolderInfo> out = new ArrayList<>();
            try {
                for (Folder f : store.getDefaultFolder().list("*")) {
                    boolean selectable = (f.getType() & Folder.HOLDS_MESSAGES) != 0;
                    int total = 0;
                    int unread = 0;
                    if (selectable) {
                        try {
                            total = f.getMessageCount();
                            unread = f.getUnreadMessageCount();
                        } catch (MessagingException ex) {
                            log.debug("[{}] счётчики папки {}: {}", mailboxId, f.getFullName(), ex.getMessage());
                        }
                    }
                    char sep = separator(f);
                    out.add(new FolderInfo(f.getFullName(),
                            sep == 0 ? null : String.valueOf(sep), selectable, total, unread));
                }
            } catch (MessagingException ex) {
                log.error("[{}] не удалось получить список папок: {}", mailboxId, ex.getMessage());
            }
            return out;
        }

        /**
         * Инкрементальный синк папки за одно открытие: новые письма (uid &gt; sinceUid) и
         * обновление флагов последних {@code flagWindow} писем. При смене UIDVALIDITY
         * помечает {@code reset=true} и отдаёт все письма (полный пересинк).
         */
        public Optional<FolderSync> syncFolder(String folderName, long sinceUid,
                                               Long expectedUidValidity, int newLimit, int flagWindow,
                                               int bodyPrefetch) {
            try {
                Folder folder = store.getFolder(folderName);
                if ((folder.getType() & Folder.HOLDS_MESSAGES) == 0) {
                    return Optional.empty();
                }
                folder.open(Folder.READ_ONLY);
                try {
                    UIDFolder uf = (UIDFolder) folder;
                    long uidValidity = uf.getUIDValidity();
                    boolean reset = expectedUidValidity != null && expectedUidValidity != uidValidity;
                    long from = reset ? 1 : sinceUid + 1;
                    int total = folder.getMessageCount();

                    // Кандидаты на envelope-загрузку. Ограничиваем ВЫБОРКУ до fetch, чтобы на большой
                    // папке не тянуть заголовки всех писем: при первом синке берём newLimit самых свежих
                    // по номеру, дальше — только новые UID (их в устоявшемся режиме мало).
                    Message[] candidates;
                    if (from <= 1) {
                        int start = Math.max(1, total - newLimit + 1);
                        candidates = total == 0 ? new Message[0] : folder.getMessages(start, total);
                    } else {
                        candidates = uf.getMessagesByUID(from, UIDFolder.LASTUID);
                    }
                    List<Message> selected = new ArrayList<>();
                    for (Message m : candidates) {
                        if (uf.getUID(m) >= from) {     // Angus может вернуть лишнее письмо при пустом диапазоне
                            selected.add(m);
                        }
                    }
                    if (selected.size() > newLimit) {   // оставляем самые свежие
                        selected = selected.subList(selected.size() - newLimit, selected.size());
                    }

                    Message[] toFetch = selected.toArray(new Message[0]);
                    fetchEnvelopes(folder, toFetch);
                    // Тело тянем только у самых свежих bodyPrefetch писем (хвост — по возрастанию UID).
                    int bodyFrom = Math.max(0, toFetch.length - Math.max(0, bodyPrefetch));
                    List<EnvelopeInfo> messages = new ArrayList<>(toFetch.length);
                    for (int i = 0; i < toFetch.length; i++) {
                        messages.add(toEnvelope(uf, toFetch[i], bodyPrefetch > 0 && i >= bodyFrom));
                    }

                    List<FlagInfo> recentFlags = fetchRecentFlags(folder, uf, flagWindow);
                    int unread = folder.getUnreadMessageCount();
                    return Optional.of(new FolderSync(uidValidity, reset, total, unread, messages, recentFlags));
                } finally {
                    folder.close(false);
                }
            } catch (Exception ex) {
                log.error("[{}] синк папки {} сорвался: {}", mailboxId, folderName, ex.getMessage());
                return Optional.empty();
            }
        }

        @Override
        public void close() {
            try {
                store.close();
            } catch (MessagingException ex) {
                log.debug("[{}] закрытие IMAP: {}", mailboxId, ex.getMessage());
            }
        }
    }

    /** Полный разбор письма по UID (для заведения ответа из UI). */
    public Optional<ParsedEmail> fetchParsed(Mailbox mailbox, String folderName, long uidValidity, long uid) {
        return withMessage(mailbox, folderName, uidValidity, uid, parser::parse);
    }

    /** Дозагрузка тела и вложений письма по UID (ленивая, при открытии в UI). */
    public Optional<BodyInfo> fetchBody(Mailbox mailbox, String folderName, long uidValidity, long uid) {
        return withMessage(mailbox, folderName, uidValidity, uid, message -> {
            ParsedEmail parsed = parser.parse(message);
            return new BodyInfo(parsed.body(),
                    !parsed.attachments().isEmpty(), parsed.attachments());
        });
    }

    /** Скачивание одного вложения по индексу (порядок как в {@link EmailParser}). */
    public Optional<ParsedAttachment> fetchAttachment(Mailbox mailbox, String folderName,
                                                      long uidValidity, long uid, int index) {
        return withMessage(mailbox, folderName, uidValidity, uid, message -> {
            List<ParsedAttachment> atts = parser.parse(message).attachments();
            return (index >= 0 && index < atts.size()) ? atts.get(index) : null;
        });
    }

    /** Кладёт наш отправленный ответ в папку «Отправленные» (best-effort). */
    public void appendToSent(Mailbox mailbox, MimeMessage message) {
        try (Store store = connect(mailbox)) {
            for (String candidate : List.of("Sent", "Sent Items", "Sent Messages", "[Gmail]/Sent Mail")) {
                Folder sent = store.getFolder(candidate);
                if (sent.exists()) {
                    message.setFlag(Flags.Flag.SEEN, true);
                    sent.appendMessages(new Message[]{message});
                    return;
                }
            }
            log.debug("[{}] папка «Отправленные» не найдена — пропускаю APPEND", mailbox.getId());
        } catch (Exception ex) {
            log.debug("[{}] APPEND в Sent не удался: {}", mailbox.getId(), ex.getMessage());
        }
    }

    // --- Внутреннее ---

    @FunctionalInterface
    private interface MessageFn<T> {
        T apply(Message message) throws Exception;
    }

    private <T> Optional<T> withMessage(Mailbox mailbox, String folderName, long uidValidity, long uid,
                                        MessageFn<T> fn) {
        try (Store store = connect(mailbox)) {
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uf = (UIDFolder) folder;
                if (uf.getUIDValidity() != uidValidity) {
                    return Optional.empty();   // папка пересоздана — UID больше не валиден
                }
                Message message = uf.getMessageByUID(uid);
                return message == null ? Optional.empty() : Optional.ofNullable(fn.apply(message));
            } finally {
                folder.close(false);
            }
        } catch (Exception ex) {
            log.error("[{}] чтение письма uid={} из {}: {}", mailbox.getId(), uid, folderName, ex.getMessage());
            return Optional.empty();
        }
    }

    private void fetchEnvelopes(Folder folder, Message[] messages) throws MessagingException {
        if (messages.length == 0) {
            return;
        }
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.FLAGS);
        fp.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(messages, fp);
    }

    private List<FlagInfo> fetchRecentFlags(Folder folder, UIDFolder uf, int flagWindow) throws MessagingException {
        int total = folder.getMessageCount();
        if (total == 0 || flagWindow <= 0) {
            return List.of();
        }
        int start = Math.max(1, total - flagWindow + 1);
        Message[] recent = folder.getMessages(start, total);
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.FLAGS);
        fp.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(recent, fp);
        List<FlagInfo> flags = new ArrayList<>(recent.length);
        for (Message m : recent) {
            Flags f = m.getFlags();
            flags.add(new FlagInfo(uf.getUID(m),
                    f.contains(Flags.Flag.SEEN), f.contains(Flags.Flag.ANSWERED), f.contains(Flags.Flag.FLAGGED)));
        }
        return flags;
    }

    private EnvelopeInfo toEnvelope(UIDFolder uf, Message m, boolean withBody) throws MessagingException {
        MimeMessage mime = (MimeMessage) m;
        Flags f = mime.getFlags();
        Instant sentAt = mime.getSentDate() != null ? mime.getSentDate().toInstant()
                : (mime.getReceivedDate() != null ? mime.getReceivedDate().toInstant() : null);
        int size = Math.max(0, mime.getSize());

        // Предзагрузка тела для самых свежих писем — чтобы открытие было мгновенным (без IMAP).
        boolean bodyFetched = false;
        String bodyText = null;
        boolean hasAttachments = false;
        if (withBody) {
            try {
                ParsedEmail parsed = parser.parse(m);
                bodyText = parsed.body();
                hasAttachments = !parsed.attachments().isEmpty();
                bodyFetched = true;
            } catch (Exception ex) {
                log.debug("предзагрузка тела uid={} не удалась: {}", uf.getUID(m), ex.getMessage());
            }
        }

        return new EnvelopeInfo(uf.getUID(m), safeMessageId(mime),
                addressOf(mimeFrom(mime)), addressOf(mime.getAllRecipients()), mime.getSubject(),
                sentAt, size,
                f.contains(Flags.Flag.SEEN), f.contains(Flags.Flag.ANSWERED), f.contains(Flags.Flag.FLAGGED),
                bodyFetched, bodyText, hasAttachments);
    }

    private jakarta.mail.Address[] mimeFrom(MimeMessage mime) throws MessagingException {
        return mime.getFrom();
    }

    private String safeMessageId(MimeMessage mime) {
        try {
            return mime.getMessageID();
        } catch (MessagingException ex) {
            return null;
        }
    }

    private String addressOf(jakarta.mail.Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        if (addresses[0] instanceof InternetAddress ia) {
            return ia.getAddress();
        }
        return addresses[0].toString();
    }

    private char separator(Folder folder) {
        try {
            return folder.getSeparator();
        } catch (MessagingException ex) {
            return 0;
        }
    }

    private Store connect(Mailbox mailbox) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", mailbox.getImapHost());
        props.put("mail.imaps.port", String.valueOf(mailbox.getImapPort()));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "15000");
        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(mailbox.getImapHost(), mailbox.getUsername(), mailbox.getPassword());
        return store;
    }
}
