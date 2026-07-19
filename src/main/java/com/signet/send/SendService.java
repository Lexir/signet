package com.signet.send;

import com.signet.shared.event.Events;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Слушает одобрение и запускает отправку.
 *
 * <p>Тонкий слушатель: вся работа в {@link SendWorkflow}, чтобы вызов через прокси
 * действительно применил {@code NOT_SUPPORTED} и освободил соединение с БД
 * на время SMTP.
 */
@Service
public class SendService {

    private final SendWorkflow workflow;

    public SendService(SendWorkflow workflow) {
        this.workflow = workflow;
    }

    @ApplicationModuleListener
    public void on(Events.ReviewApproved event) {
        workflow.deliver(event.emailId());
    }
}
