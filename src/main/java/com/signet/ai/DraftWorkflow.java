package com.signet.ai;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сценарий генерации черновика. Отдельный бин от слушателя, чтобы
 * {@code NOT_SUPPORTED} применился через прокси: вызовы модели длятся десятки
 * секунд и не должны удерживать соединение с БД.
 */
@Service
public class DraftWorkflow {

    private static final Logger log = LoggerFactory.getLogger(DraftWorkflow.class);

    private final DraftTransactions tx;
    private final DraftService draftService;
    private final SenderClassifier senderClassifier;
    private final AiProperties props;

    public DraftWorkflow(DraftTransactions tx,
                         DraftService draftService,
                         SenderClassifier senderClassifier,
                         AiProperties props) {
        this.tx = tx;
        this.draftService = draftService;
        this.senderClassifier = senderClassifier;
        this.props = props;
    }

    /** Авто-путь (EmailReceived): применяется классификатор «личное/не личное». */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void generate(UUID emailId) {
        generate(emailId, false);
    }

    /**
     * @param forced запрос из UI («Сгенерировать») — классификатор пропускаем: намерение явное.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void generate(UUID emailId, boolean forced) {
        Optional<DraftPayload> claimed = tx.claim(emailId);
        if (claimed.isEmpty()) {
            return;
        }
        DraftPayload payload = claimed.get();
        try {
            // Отвечаем только на личные письма людей — вызов модели вне транзакции.
            if (!forced && props.isOnlyHumanSenders()
                    && !senderClassifier.isPersonalHuman(payload.from(), payload.subject(), payload.body())) {
                tx.markIgnored(emailId, payload.from());
                return;
            }
            DraftService.GenerationResult result =
                    draftService.draft(payload.context(), payload.subject(), payload.profile());
            log.info("Draft: {}", result);
            tx.saveDraft(emailId, result);
        } catch (Exception ex) {
            log.error("Не удалось сгенерировать черновик для {}: {}", emailId, ex.getMessage(), ex);
            tx.markFailed(emailId, ex.getMessage());
        }
    }
}
