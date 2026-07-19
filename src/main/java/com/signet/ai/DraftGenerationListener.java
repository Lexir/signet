package com.signet.ai;

import com.signet.shared.event.Events;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Слушает {@link Events.EmailReceived} и запускает генерацию черновика.
 * Вся работа — в {@link DraftWorkflow}, чтобы вызов шёл через прокси.
 */
@Component
public class DraftGenerationListener {

    private final DraftWorkflow workflow;

    public DraftGenerationListener(DraftWorkflow workflow) {
        this.workflow = workflow;
    }

    @ApplicationModuleListener
    public void on(Events.EmailReceived event) {
        workflow.generate(event.emailId());
    }
}
