package com.signet.ai;

import com.signet.shared.event.Events;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Слушает {@link Events.ReplyRequested} (ручной запрос ответа из почтового клиента)
 * и запускает генерацию черновика в форсированном режиме — без классификатора.
 */
@Component
public class ReplyRequestedListener {

    private final DraftWorkflow workflow;

    public ReplyRequestedListener(DraftWorkflow workflow) {
        this.workflow = workflow;
    }

    @ApplicationModuleListener
    public void on(Events.ReplyRequested event) {
        workflow.generate(event.emailId(), true);
    }
}
