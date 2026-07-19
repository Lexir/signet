package com.signet.shared.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ящики из application.yml. Используются только как первичный сид: после первого
 * старта источник истины — таблица {@code mailboxes}, правится через /settings.
 */
@ConfigurationProperties(prefix = "app")
public class MailboxesProperties {

    private List<Mailbox> mailboxes = new ArrayList<>();

    /** Сколько писем разбирать за один заход опроса. */
    private int maxBatch = 25;

    public List<Mailbox> getMailboxes() {
        return mailboxes;
    }

    public void setMailboxes(List<Mailbox> mailboxes) {
        this.mailboxes = mailboxes;
    }

    public int getMaxBatch() {
        return maxBatch;
    }

    public void setMaxBatch(int maxBatch) {
        this.maxBatch = maxBatch;
    }
}
