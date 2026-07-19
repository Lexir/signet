package com.signet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа. Модульность обеспечивает Spring Modulith (стартер + доменные
 * события), а Event Publication Registry работает как транзакционный outbox.
 * Границы модулей проверяются тестом {@code ModularityTests}.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class SignetApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignetApplication.class, args);
    }
}
