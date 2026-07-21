package com.signet.shared.domain;

public enum ReviewChannel {
    /** Разбор ответов в веб-интерфейсе (очередь ревью на /reviews). */
    UI,
    TELEGRAM,
    SLACK
}
