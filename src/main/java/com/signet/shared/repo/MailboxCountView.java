package com.signet.shared.repo;

/** Строка агрегата «ящик → количество» для групповых запросов аналитики. */
public interface MailboxCountView {

    String getMailboxId();

    long getCnt();
}
