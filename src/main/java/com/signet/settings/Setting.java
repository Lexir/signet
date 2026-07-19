package com.signet.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Пара ключ-значение настройки. Секреты хранятся зашифрованными. */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(columnDefinition = "text")
    private String value;

    @Column(nullable = false)
    private boolean encrypted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Setting() {
    }

    public Setting(String key, String value, boolean encrypted) {
        this.key = key;
        this.value = value;
        this.encrypted = encrypted;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }
}
