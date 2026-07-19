package com.signet.shared.domain;

/** Стейт-машина жизненного цикла письма. */
public enum EmailStatus {
    RECEIVED,
    DRAFTING,
    DRAFTED,
    PENDING_REVIEW,
    APPROVED,
    EDITED,
    REJECTED,
    SENDING,
    SENT,
    FAILED,
    IGNORED   // не личное письмо (рассылка/компания/спам) — ответ не генерируем
}
