package com.signet.ingest;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Разбирает Jakarta Mail {@link Message} в плоский {@link ParsedEmail}. */
@Component
public class EmailParser {

    private static final Logger log = LoggerFactory.getLogger(EmailParser.class);

    /** Лимит на одно вложение (Telegram-бот принимает до 50 МБ; берём с запасом). */
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    // Строки-цитаты (> ...) и типичные разделители ответов/подписей.
    private static final Pattern QUOTE_LINE = Pattern.compile("^\\s*>.*$");
    private static final Pattern REPLY_MARKER = Pattern.compile(
            "(?i)^(-{2,}\\s*original message|on .+ wrote:|-{2,} ?forwarded message|\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}.+wrote:).*");
    private static final Pattern SIGNATURE = Pattern.compile("^--\\s*$");

    public ParsedEmail parse(Message message) throws MessagingException, IOException {
        MimeMessage mime = (MimeMessage) message;

        String messageId = firstHeader(mime, "Message-ID");
        List<String> references = collectReferences(mime);
        String from = addressOf(mime.getFrom());
        String to = addressOf(mime.getAllRecipients());
        String subject = mime.getSubject();
        Instant receivedAt = mime.getReceivedDate() != null
                ? mime.getReceivedDate().toInstant()
                : (mime.getSentDate() != null ? mime.getSentDate().toInstant() : Instant.now());

        String rawBody = extractText(mime);
        String body = cleanBody(rawBody);

        boolean automated = isAutomated(mime, from);

        List<ParsedAttachment> attachments = new ArrayList<>();
        collectAttachments(mime, attachments);

        return new ParsedEmail(messageId, references, from, to, subject, body, receivedAt, automated, attachments);
    }

    /** Рекурсивно собирает вложения (части с именем файла или disposition=attachment). */
    private void collectAttachments(Part part, List<ParsedAttachment> out) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                collectAttachments(mp.getBodyPart(i), out);
            }
            return;
        }
        String filename = part.getFileName();
        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || (filename != null && !filename.isBlank());
        if (!isAttachment) {
            return; // это часть тела, не вложение
        }
        try (java.io.InputStream is = part.getInputStream()) {
            byte[] data = is.readNBytes((int) (MAX_ATTACHMENT_BYTES + 1));
            if (data.length > MAX_ATTACHMENT_BYTES) {
                log.warn("Вложение {} больше лимита {} байт — пропущено", filename, MAX_ATTACHMENT_BYTES);
                return;
            }
            out.add(new ParsedAttachment(
                    filename != null ? filename : "attachment",
                    contentTypeOnly(part.getContentType()),
                    data));
        }
    }

    private String contentTypeOnly(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int sep = contentType.indexOf(';');
        return (sep > 0 ? contentType.substring(0, sep) : contentType).trim();
    }

    private List<String> collectReferences(MimeMessage mime) throws MessagingException {
        List<String> refs = new ArrayList<>();
        String references = firstHeader(mime, "References");
        if (references != null) {
            for (String r : references.split("\\s+")) {
                if (!r.isBlank()) {
                    refs.add(r.trim());
                }
            }
        }
        String inReplyTo = firstHeader(mime, "In-Reply-To");
        if (inReplyTo != null && !refs.contains(inReplyTo.trim())) {
            refs.add(inReplyTo.trim());
        }
        return refs;
    }

    // Локальная часть адреса, характерная для автоматических/сервисных отправителей.
    private static final Pattern NO_REPLY_ADDRESS = Pattern.compile(
            "(?i)^(no[-_.]?reply|do[-_.]?not[-_.]?reply|noreply|mailer[-_.]?daemon|bounce|postmaster|"
            + "notifications?|notify|newsletter|mailing|marketing|promo|"
            + "automated?|autoreply|no[-_.]?response|donotreply|robot|billing|receipts?|invoice)@");

    /** Определяет, что письмо — авто/рассылка/сервис, а не личное сообщение человека. */
    private boolean isAutomated(MimeMessage mime, String from) throws MessagingException {
        // 1. Явные заголовки автоматических писем и рассылок.
        String autoSubmitted = firstHeader(mime, "Auto-Submitted");
        if (autoSubmitted != null && !autoSubmitted.equalsIgnoreCase("no")) {
            return true;
        }
        String precedence = firstHeader(mime, "Precedence");
        if (precedence != null && (precedence.equalsIgnoreCase("bulk")
                || precedence.equalsIgnoreCase("auto_reply")
                || precedence.equalsIgnoreCase("junk")
                || precedence.equalsIgnoreCase("list"))) {
            return true;
        }
        // 2. Маркеры массовых рассылок / ESP.
        if (firstHeader(mime, "List-Unsubscribe") != null
                || firstHeader(mime, "List-Id") != null
                || firstHeader(mime, "List-Post") != null
                || firstHeader(mime, "Feedback-ID") != null
                || firstHeader(mime, "X-Campaign") != null
                || firstHeader(mime, "X-Campaignid") != null
                || firstHeader(mime, "X-Mailchimp-Id") != null
                || firstHeader(mime, "X-CSA-Complaints") != null) {
            return true;
        }
        // 3. Адрес отправителя вида no-reply@ / notifications@ / marketing@ и т.п.
        return from != null && NO_REPLY_ADDRESS.matcher(from.trim()).find();
    }

    private String firstHeader(MimeMessage mime, String name) throws MessagingException {
        String[] values = mime.getHeader(name);
        return (values != null && values.length > 0) ? values[0] : null;
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

    /** Извлекает текстовое тело, предпочитая text/plain, иначе снимает html-теги. */
    private String extractText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("text/html")) {
            String html = (String) part.getContent();
            return html.replaceAll("(?s)<[^>]+>", " ").replaceAll("&nbsp;", " ");
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String htmlFallback = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    return (String) bp.getContent();
                }
                if (bp.isMimeType("multipart/*")) {
                    String nested = extractText(bp);
                    if (nested != null && !nested.isBlank()) {
                        return nested;
                    }
                }
                if (bp.isMimeType("text/html") && htmlFallback == null) {
                    htmlFallback = ((String) bp.getContent())
                            .replaceAll("(?s)<[^>]+>", " ").replaceAll("&nbsp;", " ");
                }
            }
            return htmlFallback;
        }
        return null;
    }

    /** Убирает цитаты, подпись и «On … wrote:» — экономит токены и убирает шум. */
    String cleanBody(String raw) {
        if (raw == null) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (String line : Arrays.asList(raw.replace("\r\n", "\n").split("\n"))) {
            if (REPLY_MARKER.matcher(line).matches() || SIGNATURE.matcher(line).matches()) {
                break; // всё, что ниже — цитата/подпись
            }
            if (QUOTE_LINE.matcher(line).matches()) {
                continue;
            }
            out.add(line);
        }
        return String.join("\n", out).strip();
    }
}
