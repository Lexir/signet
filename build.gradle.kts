plugins {
    java
    id("org.springframework.boot") version "4.1.0"          // сверить актуальный патч на момент старта
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.signet"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories {
    mavenCentral()
    // Spring milestones — на случай, если нужный патч Spring AI ещё не в central:
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "2.0.0"            // линейка под Spring Boot 4
extra["springModulithVersion"] = "2.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

dependencies {
    // --- AI (через ChatClient). Провайдер выбирается spring.ai.model.chat ---
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")   // локальная модель для теста

    // --- Почта: SMTP исходящая + IMAP входящая (angus-mail) ---
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // --- Модульность + транзакционный outbox (Event Publication Registry на JDBC).
    // JDBC-стартер сам создаёт таблицу event_publication по своей схеме (не Flyway). ---
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")

    // --- Данные + миграции ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Boot 4: автоконфиг Flyway живёт в стартере, голого flyway-core недостаточно
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- Мессенджер (Telegram) ---
    implementation("org.telegram:telegrambots-longpolling:8.0.0")
    implementation("org.telegram:telegrambots-client:8.0.0")

    // --- Веб / дашборд / health ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // --- Тесты ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("signet.jar")
}
