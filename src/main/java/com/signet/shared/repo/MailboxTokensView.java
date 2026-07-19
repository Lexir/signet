package com.signet.shared.repo;

/** Строка агрегата «ящик → израсходованные токены». */
public interface MailboxTokensView {

    String getMailboxId();

    long getTokensIn();

    long getTokensOut();
}
