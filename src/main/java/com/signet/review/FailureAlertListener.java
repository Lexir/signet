package com.signet.review;

import com.signet.shared.event.Events;
import com.signet.shared.repo.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Сообщает человеку о сорвавшейся обработке письма. Без этого сбой генерации или
 * отправки остаётся незамеченным, и человек считает, что ответ ушёл.
 */
@Component
public class FailureAlertListener {

    private static final Logger log = LoggerFactory.getLogger(FailureAlertListener.class);

    private final TelegramGateway telegram;
    private final EmailRepository emails;

    public FailureAlertListener(TelegramGateway telegram, EmailRepository emails) {
        this.telegram = telegram;
        this.emails = emails;
    }

    @ApplicationModuleListener
    public void on(Events.ProcessingFailed event) {
        String from = emails.findById(event.emailId())
                .map(e -> e.getFromAddr() + " — " + nz(e.getSubject()))
                .orElse(event.emailId().toString());

        String stage = "send".equals(event.stage())
                ? "не удалось ОТПРАВИТЬ ответ"
                : "не удалось подготовить черновик";

        telegram.sendText("""
                ⚠️ Внимание: %s
                Письмо: %s
                Причина: %s

                Ответ клиенту НЕ отправлен — разберитесь вручную.""".formatted(
                stage, from, nz(event.reason())));

        log.warn("Сбой обработки письма {} на этапе {}: {}",
                event.emailId(), event.stage(), event.reason());
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
