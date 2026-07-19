package com.signet.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String SALT = "5c0744940b5c369b";   // salt должен быть hex

    private final SecretCipher cipher = new SecretCipher("test-master-key", SALT);

    @Test
    void шифрует_и_расшифровывает_обратно() {
        String plain = "p@ss w0rd со спецсимволами: $ { } \" \\";

        String encrypted = cipher.encrypt(plain);

        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(cipher.isEncrypted(encrypted)).isTrue();
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void одинаковые_значения_шифруются_по_разному() {
        // Encryptors.delux использует случайный IV — иначе по шифротексту
        // можно было бы понять, что у двух ящиков одинаковый пароль.
        assertThat(cipher.encrypt("secret")).isNotEqualTo(cipher.encrypt("secret"));
    }

    @Test
    void не_шифрует_повторно_уже_зашифрованное() {
        String once = cipher.encrypt("secret");

        assertThat(cipher.encrypt(once)).isEqualTo(once);
    }

    @Test
    void пустые_значения_проходят_насквозь() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("")).isEmpty();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("открытый текст")).isEqualTo("открытый текст");
    }

    @Test
    void при_смене_мастер_ключа_возвращает_null_а_не_падает() {
        String encrypted = cipher.encrypt("secret");
        SecretCipher другойКлюч = new SecretCipher("another-master-key", SALT);

        // Тихая деградация вместо исключения: приложение стартует,
        // но пароль ящика окажется пустым — это видно в логах.
        assertThat(другойКлюч.decrypt(encrypted)).isNull();
    }
}
