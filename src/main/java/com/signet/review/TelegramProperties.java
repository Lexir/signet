package com.signet.review;

/**
 * @deprecated Настройки Telegram переехали в БД и правятся через /settings
 * (см. {@code SettingsService.telegram()}). Ключи {@code app.telegram.*} в yml
 * остались только как первичный сид. Класс не используется — можно удалить файл.
 */
@Deprecated
final class TelegramProperties {
    private TelegramProperties() {
    }
}
