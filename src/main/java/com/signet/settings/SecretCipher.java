package com.signet.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Шифрование секретов (пароли ящиков, токен бота, ключ OpenAI) перед записью в БД.
 * Мастер-ключ берётся из переменной окружения SETTINGS_SECRET_KEY.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final String PREFIX = "enc:";

    private final TextEncryptor encryptor;

    public SecretCipher(@Value("${app.settings.secret-key:change-me-please}") String secretKey,
                        @Value("${app.settings.secret-salt:5c0744940b5c369b}") String salt) {
        if ("change-me-please".equals(secretKey)) {
            log.warn("SETTINGS_SECRET_KEY не задан — секреты шифруются дефолтным ключом. "
                    + "Задайте свой ключ в переменных окружения!");
        }
        // AES-256-GCM со случайным IV (Encryptors.delux).
        this.encryptor = Encryptors.delux(secretKey, salt);
    }

    /** Шифрует значение; уже зашифрованное возвращает как есть. */
    public String encrypt(String plain) {
        if (plain == null || plain.isBlank() || isEncrypted(plain)) {
            return plain;
        }
        return PREFIX + encryptor.encrypt(plain);
    }

    /** Расшифровывает значение; незашифрованное возвращает как есть. */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank() || !isEncrypted(stored)) {
            return stored;
        }
        try {
            return encryptor.decrypt(stored.substring(PREFIX.length()));
        } catch (Exception ex) {
            log.error("Не удалось расшифровать значение (сменился SETTINGS_SECRET_KEY?): {}", ex.getMessage());
            return null;
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
