package com.signet.review;

import com.signet.shared.event.Events;
import com.signet.shared.repo.EmailRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Напоминает о письме, по которому долго нет решения. */
@Component
public class ReviewReminderListener {

    private final TelegramGateway telegram;
    private final EmailRepository emails;

    public ReviewReminderListener(TelegramGateway telegram, EmailRepository emails) {
        this.telegram = telegram;
        this.emails = emails;
    }

    @ApplicationModuleListener
    public void on(Events.ReviewReminder event) {
        String from = emails.findById(event.emailId())
                .map(e -> e.getFromAddr() + " — " + (e.getSubject() == null ? "" : e.getSubject()))
                .orElse(event.emailId().toString());

        telegram.sendText("⏰ Ждёт вашего решения %d мин: %s".formatted(event.waitingMinutes(), from));
    }
}
