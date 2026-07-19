package com.signet.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EmailParserTest {

    private final EmailParser parser = new EmailParser();
    private final Session session = Session.getInstance(new Properties());

    private MimeMessage newMessage() throws Exception {
        MimeMessage m = new MimeMessage(session);
        m.setFrom(new InternetAddress("ivan@example.com"));
        m.setRecipients(Message.RecipientType.TO, "support@company.com");
        m.setSubject("Вопрос по заказу", "UTF-8");
        return m;
    }

    /**
     * saveChanges() генерирует собственный Message-ID, поэтому свой заголовок
     * выставляем ПОСЛЕ него. У реальных входящих писем Message-ID уже проставлен
     * отправителем, и saveChanges() к ним не применяется.
     */
    private MimeMessage seal(MimeMessage m, String messageId) throws Exception {
        m.saveChanges();
        m.setHeader("Message-ID", messageId);
        return m;
    }

    @Test
    void разбирает_простое_письмо_с_кириллицей() throws Exception {
        MimeMessage m = newMessage();
        m.setText("Привет! Подскажи, пожалуйста, как дела с заказом?", "UTF-8");
        seal(m, "<msg-1@example.com>");

        ParsedEmail parsed = parser.parse(m);

        assertThat(parsed.from()).isEqualTo("ivan@example.com");
        assertThat(parsed.subject()).isEqualTo("Вопрос по заказу");
        assertThat(parsed.messageId()).isEqualTo("<msg-1@example.com>");
        assertThat(parsed.body()).contains("Привет!").contains("как дела с заказом");
        assertThat(parsed.automated()).isFalse();
        assertThat(parsed.attachments()).isEmpty();
    }

    @Test
    void собирает_вложения_из_multipart() throws Exception {
        MimeMessage m = newMessage();

        MimeBodyPart text = new MimeBodyPart();
        text.setText("Смотри файл во вложении", "UTF-8");

        byte[] content = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(content, "application/pdf")));
        attachment.setFileName("contract.pdf");
        attachment.setDisposition(Part.ATTACHMENT);

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(text);
        multipart.addBodyPart(attachment);
        m.setContent(multipart);
        seal(m, "<msg-att@example.com>");

        ParsedEmail parsed = parser.parse(m);

        assertThat(parsed.body()).contains("Смотри файл во вложении");
        assertThat(parsed.attachments()).hasSize(1);
        ParsedAttachment att = parsed.attachments().get(0);
        assertThat(att.filename()).isEqualTo("contract.pdf");
        assertThat(att.contentType()).isEqualTo("application/pdf");
        assertThat(att.data()).isEqualTo(content);
    }

    @Test
    void отрезает_цитаты_и_подпись() {
        String raw = """
                Спасибо, всё получил!

                --
                Иван Петров
                +7 900 000-00-00""";

        assertThat(parser.cleanBody(raw))
                .isEqualTo("Спасибо, всё получил!")
                .doesNotContain("Иван Петров");
    }

    @Test
    void отрезает_процитированный_ответ() {
        String raw = """
                Да, подтверждаю.

                > Вы писали:
                > Проверьте, пожалуйста, детали""";

        assertThat(parser.cleanBody(raw)).isEqualTo("Да, подтверждаю.");
    }

    @Test
    void помечает_рассылку_как_автоматическую() throws Exception {
        MimeMessage m = newMessage();
        m.setText("Скидки только сегодня!", "UTF-8");
        seal(m, "<promo@shop.com>");
        m.setHeader("List-Unsubscribe", "<https://example.com/unsub>");

        assertThat(parser.parse(m).automated()).isTrue();
    }

    @Test
    void помечает_no_reply_адрес_как_автоматический() throws Exception {
        MimeMessage m = new MimeMessage(session);
        m.setFrom(new InternetAddress("no-reply@bank.com"));
        m.setSubject("Выписка", "UTF-8");
        m.setText("Ваша выписка готова", "UTF-8");
        seal(m, "<msg-2@bank.com>");

        assertThat(parser.parse(m).automated()).isTrue();
    }

    @Test
    void корень_треда_берётся_из_references() throws Exception {
        MimeMessage m = newMessage();
        m.setText("Ответ в цепочке", "UTF-8");
        seal(m, "<msg-3@example.com>");
        m.setHeader("References", "<root@example.com> <second@example.com>");

        ParsedEmail parsed = parser.parse(m);

        assertThat(parsed.threadRoot()).isEqualTo("<root@example.com>");
    }

    @Test
    void без_references_корень_треда_это_сам_message_id() throws Exception {
        MimeMessage m = newMessage();
        m.setText("Новое письмо", "UTF-8");
        seal(m, "<msg-1@example.com>");

        assertThat(parser.parse(m).threadRoot()).isEqualTo("<msg-1@example.com>");
    }
}
