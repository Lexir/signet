package com.signet.ai;

/**
 * Структурированный ответ модели: ответ клиенту на его языке, определённый язык
 * письма и служебный перевод на русский для валидации менеджером.
 *
 * @param answer   ответ клиенту на языке его письма
 * @param language ISO-код языка письма клиента (например "en", "de", "ru")
 * @param answerRu тот же ответ, переведённый на русский (для менеджера)
 */
public record DraftResponse(
        String answer,
        String language,
        String answerRu) {
}
