# syntax=docker/dockerfile:1

# --- Стадия сборки фронтенда (Vite SPA) ---
# Отдельная node-стадия собирает статику; хостовый Node не нужен, а Gradle не зависит
# от связки node-плагина. Результат (frontend/dist) уезжает в стадию сборки бэкенда.
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci
COPY frontend/ ./
RUN npm run build

# --- Стадия сборки бэкенда ---
# Образ gradle уже содержит JDK 25, поэтому toolchain ничего не докачивает.
FROM gradle:9.6.1-jdk25 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
# Собранный SPA кладём туда, где его ждёт задача copyWebApp (frontend/dist → static/).
COPY --from=web /web/dist ./frontend/dist

# Кэш Gradle живёт между сборками (BuildKit), но не попадает в итоговый образ:
# правка в src больше не приводит к повторной выкачке половины Maven Central.
# bootJar не тянет за собой задачу test — тесты гоняем в CI, а не при сборке образа.
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle bootJar --no-daemon

# --- Стадия рантайма ---
FROM eclipse-temurin:25-jre

# curl нужен только для HEALTHCHECK; ставим без рекомендаций и чистим списки пакетов
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Приложению не нужен root: если кто-то доберётся до выполнения кода,
# он окажется под непривилегированным пользователем без прав на систему.
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY --from=build --chown=app:app /app/build/libs/signet.jar app.jar
USER app

ENV TZ=Europe/Moscow \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# /actuator/health открыт без авторизации (см. SecurityConfig), поэтому проба
# не требует кредов. start-period с запасом: первый старт ждёт миграции Flyway.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
