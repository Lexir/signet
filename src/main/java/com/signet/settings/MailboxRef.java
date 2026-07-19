package com.signet.settings;

/**
 * Лёгкая ссылка на ящик: только идентификатор и адрес.
 * Используется там, где креды не нужны (аналитика, списки) — без расшифровки пароля.
 */
public record MailboxRef(String id, String username) {
}
