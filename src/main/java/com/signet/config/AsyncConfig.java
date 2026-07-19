package com.signet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Включает асинхронное выполнение для {@code @ApplicationModuleListener}.
 * Пул потоков — виртуальные потоки (spring.threads.virtual.enabled=true).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
