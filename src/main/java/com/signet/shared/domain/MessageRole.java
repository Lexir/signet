package com.signet.shared.domain;

/** Роль реплики в диалоге — так контекст корректно ложится в промпт LLM. */
public enum MessageRole {
    USER,       // письмо клиента
    ASSISTANT   // наш отправленный ответ
}
