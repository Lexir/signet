package com.signet.send;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Собирает ответное письмо: reply в тот же тред (In-Reply-To / References),
 * тема с префиксом Re:, From = адрес ящика.
 */
@Component
public class ReplyBuilder {

    public MimeMessage build(JavaMailSender sender, SendPayload payload, String fromAddress) throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

        helper.setTo(payload.toAddr());
        if (fromAddress != null && !fromAddress.isBlank()) {
            helper.setFrom(fromAddress);
        }
        helper.setSubject(replySubject(payload.subject()));
        helper.setText(payload.finalText(), false);

        // Привязка к треду — иначе ответ вылетит из цепочки у получателя.
        if (payload.messageId() != null) {
            message.setHeader("In-Reply-To", payload.messageId());
            message.setHeader("References", payload.messageId());
        }
        return message;
    }

    private String replySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "Re:";
        }
        return subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject;
    }
}
