package com.signet.send;

import com.signet.settings.SettingsChangedEvent;
import com.signet.shared.config.Mailbox;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Строит и кэширует по одному {@link JavaMailSender} на ящик — чтобы ответ уходил
 * через SMTP того ящика, на который пришло письмо.
 */
@Component
public class MailSenderFactory {

    private final ConcurrentHashMap<String, JavaMailSender> cache = new ConcurrentHashMap<>();

    public JavaMailSender forMailbox(Mailbox mailbox) {
        return cache.computeIfAbsent(mailbox.getId(), id -> build(mailbox));
    }

    /** Настройки ящиков изменились в UI — пересоберём отправителей заново. */
    @EventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        if (SettingsChangedEvent.MAILBOXES.equals(event.area())) {
            cache.clear();
        }
    }

    private JavaMailSender build(Mailbox m) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(m.getSmtpHost());
        sender.setPort(m.getSmtpPort());
        sender.setUsername(m.getUsername());
        sender.setPassword(m.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", String.valueOf(m.isSmtpAuth()));
        p.put("mail.smtp.ssl.enable", String.valueOf(m.isSmtpSsl()));
        p.put("mail.smtp.starttls.enable", String.valueOf(m.isSmtpStarttls()));
        p.put("mail.smtp.starttls.required", String.valueOf(m.isSmtpStarttls()));
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        return sender;
    }
}
