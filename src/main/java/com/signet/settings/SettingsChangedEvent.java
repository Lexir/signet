package com.signet.settings;

/**
 * Настройки изменились — потребители пересобирают клиентов (бот, ChatClient, SMTP).
 *
 * @param area "telegram", "ai" или "mailboxes"
 */
public record SettingsChangedEvent(String area) {

    public static final String TELEGRAM = "telegram";
    public static final String AI = "ai";
    public static final String MAILBOXES = "mailboxes";
}
