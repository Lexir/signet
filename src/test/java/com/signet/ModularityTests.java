package com.signet;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Проверка границ модулей (никто не лезет во внутренности чужого модуля,
 * нет циклов).
 *
 * ВРЕМЕННО ОТКЛЮЧЕНО: ArchUnit/ASM в текущей версии Spring Modulith пока не
 * разбирает байткод Java 25 (major version 69) и не находит классы
 * ("No classes found in packages [com.signet]"). Включить обратно, когда
 * подтянется ArchUnit с поддержкой Java 25, либо временно понизив toolchain до 21.
 */
@Disabled("ArchUnit пока не читает байткод Java 25 — вернуть после апдейта ArchUnit")
class ModularityTests {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(SignetApplication.class).verify();
    }
}
