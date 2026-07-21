package com.signet.shared.event;

import java.util.UUID;

/**
 * Доменные события — контракт обмена между модулями. Публикуются через
 * ApplicationEventPublisher и надёжно доставляются Event Publication Registry.
 */
public final class Events {

    private Events() {
    }

    /** Принято новое письмо. Публикует ingest, слушает ai. */
    public record EmailReceived(UUID emailId) {
    }

    /**
     * Запрошена генерация ответа вручную (кнопка «Сгенерировать» в почтовом клиенте).
     * В отличие от {@link EmailReceived}, классификатор «личное/не личное» пропускается —
     * это явное намерение человека. Публикует mail, слушает ai.
     */
    public record ReplyRequested(UUID emailId) {
    }

    /** Черновик (и перевод) готов. Публикует ai, слушает review. */
    public record DraftReady(UUID emailId, UUID draftId) {
    }

    /** Менеджер одобрил (или отредактировал) ответ. Публикует review, слушает send. */
    public record ReviewApproved(UUID emailId) {
    }

    /** Менеджер отклонил ответ. Публикует review. */
    public record ReviewRejected(UUID emailId, String reason) {
    }

    /** Ответ отправлен клиенту. Публикует send (для аналитики/аудита). */
    public record EmailSent(UUID emailId) {
    }

    /**
     * Обработка письма сорвалась — нужно сообщить человеку, иначе он будет думать,
     * что ответ ушёл. Публикуют ai/send, слушает review (алерт в Telegram).
     *
     * @param stage  этап: "draft" или "send"
     * @param reason краткое описание причины
     */
    public record ProcessingFailed(UUID emailId, String stage, String reason) {
    }

    /**
     * По письму давно нет решения — напомнить человеку.
     * Публикует recovery, слушает review.
     */
    public record ReviewReminder(UUID emailId, long waitingMinutes) {
    }
}
