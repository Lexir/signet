-- signet :: полная схема (консолидация V1–V4)
--
-- Все объекты создаются без указания схемы: Flyway выполняет миграцию с
-- search_path, выставленным на app.datasource.schema (см. application.yml),
-- поэтому таблицы попадают туда, а не в public.
--
-- gen_random_uuid() живёт в pg_catalog начиная с PostgreSQL 13, расширение
-- pgcrypto не требуется и остаётся доступным при любом search_path.

-- =========================================================
-- Диалоги (треды) как единица памяти переписки
-- =========================================================
CREATE TABLE conversations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id      TEXT NOT NULL UNIQUE,          -- корень цепочки (References)
    client_addr    TEXT,
    summary        TEXT,                          -- сжатое резюме старой части
    summary_upto   TIMESTAMPTZ,
    last_activity  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_client ON conversations (client_addr);

-- =========================================================
-- Входящие письма и их состояние (стейт-машина)
-- =========================================================
CREATE TABLE emails (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id       TEXT NOT NULL UNIQUE,        -- IMAP Message-ID, дедуп
    conversation_id  UUID REFERENCES conversations (id),
    thread_id        TEXT,
    mailbox_id       TEXT,                        -- из какого ящика письмо
    from_addr        TEXT NOT NULL,
    to_addr          TEXT,
    subject          TEXT,
    body             TEXT,
    language         TEXT,                        -- определённый язык письма
    received_at      TIMESTAMPTZ,
    status           TEXT NOT NULL,               -- RECEIVED, DRAFTING, ...
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_emails_status  ON emails (status);
CREATE INDEX idx_emails_thread  ON emails (thread_id);
CREATE INDEX idx_emails_mailbox ON emails (mailbox_id);
-- Быстрый поиск «висящих» писем при восстановлении после падения.
CREATE INDEX idx_emails_status_updated ON emails (status, updated_at);

-- =========================================================
-- Реплики диалога (то, что уходит в контекст LLM)
-- =========================================================
CREATE TABLE conversation_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations (id),
    email_id         UUID REFERENCES emails (id),  -- null для нашего исходящего ответа
    role             TEXT NOT NULL,                -- USER | ASSISTANT
    content          TEXT NOT NULL,                -- очищенное тело
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conv_messages ON conversation_messages (conversation_id, created_at);

-- =========================================================
-- Вложения входящих писем (уходят человеку в Telegram при валидации)
-- =========================================================
CREATE TABLE attachments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id      UUID NOT NULL REFERENCES emails (id),
    filename      TEXT,
    content_type  TEXT,
    size_bytes    INT,
    data          BYTEA NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachments_email ON attachments (email_id);

-- =========================================================
-- Черновики и финальные версии ответов
-- =========================================================
CREATE TABLE drafts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id     UUID NOT NULL REFERENCES emails (id),
    ai_text      TEXT,                             -- что сгенерил LLM (язык клиента)
    ai_text_ru   TEXT,                             -- служебный перевод для проверки
    final_text   TEXT,                             -- что реально уйдёт (после правок)
    model        TEXT,
    tokens_in    INT,
    tokens_out   INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_drafts_email ON drafts (email_id);

-- =========================================================
-- Задачи ревью в мессенджере
-- =========================================================
CREATE TABLE review_tasks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id      UUID NOT NULL REFERENCES emails (id),
    draft_id      UUID REFERENCES drafts (id),
    channel       TEXT NOT NULL,                   -- TELEGRAM | SLACK
    chat_ref      TEXT,                            -- chat_id:message_id
    status        TEXT NOT NULL,                   -- PENDING | APPROVED | EDITED | REJECTED
    reviewer      TEXT,
    awaiting_edit BOOLEAN NOT NULL DEFAULT FALSE,  -- ждём текст правки ответным сообщением
    reminded_at   TIMESTAMPTZ,                     -- когда напомнили, чтобы не слать в каждом цикле
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_chat_ref ON review_tasks (chat_ref);
CREATE INDEX idx_review_status   ON review_tasks (status);

-- =========================================================
-- Журнал отправок (аудит + идемпотентность)
-- =========================================================
CREATE TABLE send_log (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id         UUID NOT NULL REFERENCES emails (id),
    mailbox_id       TEXT,                         -- разрез статистики по ящикам
    smtp_message_id  TEXT,
    status           TEXT NOT NULL,                -- SENT | FAILED
    error            TEXT,
    sent_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_send_log_email        ON send_log (email_id);
CREATE INDEX idx_send_log_mailbox_sent ON send_log (mailbox_id, status, sent_at);

-- Защита от повторной отправки на уровне БД: успешная отправка письма может быть
-- зафиксирована только один раз. Неудачных попыток (FAILED) может быть сколько угодно.
CREATE UNIQUE INDEX uq_send_log_sent_once
    ON send_log (email_id) WHERE status = 'SENT';

-- =========================================================
-- Настройки интеграций через UI (секреты лежат зашифрованными)
-- =========================================================
CREATE TABLE settings (
    setting_key  TEXT PRIMARY KEY,
    value        TEXT,
    encrypted    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Почтовые ящики, управляемые через UI (application.yml — только первичный сид).
-- profile: имя, факты о себе и тон общения человека, от чьего лица идёт ответ.
-- review_channel: куда уходит AI-черновик на проверку — TELEGRAM | UI.
CREATE TABLE mailboxes (
    id                TEXT PRIMARY KEY,
    profile           TEXT,
    username          TEXT,
    password_enc      TEXT,
    imap_host         TEXT,
    imap_port         INT     NOT NULL DEFAULT 993,
    folder            TEXT    NOT NULL DEFAULT 'INBOX',
    processed_folder  TEXT,
    smtp_host         TEXT,
    smtp_port         INT     NOT NULL DEFAULT 587,
    smtp_ssl          BOOLEAN NOT NULL DEFAULT FALSE,
    smtp_starttls     BOOLEAN NOT NULL DEFAULT TRUE,
    smtp_auth         BOOLEAN NOT NULL DEFAULT TRUE,
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    review_channel    TEXT    NOT NULL DEFAULT 'TELEGRAM',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================================
-- Дневные агрегаты для дашборда (ночной rollup + пересчёт по событиям)
-- =========================================================
CREATE TABLE daily_stats (
    day            DATE PRIMARY KEY,
    received       INT    NOT NULL DEFAULT 0,
    sent           INT    NOT NULL DEFAULT 0,
    approved       INT    NOT NULL DEFAULT 0,
    edited         INT    NOT NULL DEFAULT 0,
    rejected       INT    NOT NULL DEFAULT 0,
    tokens_in      BIGINT NOT NULL DEFAULT 0,
    tokens_out     BIGINT NOT NULL DEFAULT 0
);

-- =========================================================
-- Почтовый клиент — зеркало ящиков (кэш поверх IMAP, папки открываются READ_ONLY)
-- =========================================================

-- Папки ящика (обновляются при синке)
CREATE TABLE mail_folders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id       TEXT NOT NULL,
    name             TEXT NOT NULL,                -- полный путь папки в IMAP
    delimiter        TEXT,                         -- разделитель иерархии ('/', '.')
    selectable       BOOLEAN NOT NULL DEFAULT TRUE,
    total_count      INT NOT NULL DEFAULT 0,
    unread_count     INT NOT NULL DEFAULT 0,
    uid_validity     BIGINT,                       -- смена → пересинк папки
    last_synced_uid  BIGINT NOT NULL DEFAULT 0,    -- граница инкрементального синка
    synced_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mailbox_id, name)
);
CREATE INDEX idx_mail_folders_mailbox ON mail_folders (mailbox_id);

-- Контент письма — один раз на (ящик, Message-ID), как в зрелых почтовых клиентах:
-- у ярлыковых провайдеров (Gmail) одно письмо лежит в нескольких папках, тело
-- не дублируем. Кэшируется лениво при первом открытии.
CREATE TABLE mail_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id       TEXT NOT NULL,
    message_id       TEXT NOT NULL,               -- RFC Message-ID (или синтетический для писем без него)
    from_addr        TEXT,
    to_addr          TEXT,
    subject          TEXT,
    sent_at          TIMESTAMPTZ,
    size_bytes       INT NOT NULL DEFAULT 0,
    has_attachments  BOOLEAN NOT NULL DEFAULT FALSE,
    body_text        TEXT,
    body_synced_at   TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mailbox_id, message_id)
);

-- Членство письма в папке: индекс (folder, uid) со ссылкой на контент. Флаги — здесь
-- (в IMAP они у копии в папке), контент из прошлого не меняется.
CREATE TABLE mail_memberships (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id       TEXT NOT NULL,
    folder           TEXT NOT NULL,
    uid              BIGINT NOT NULL,
    uid_validity     BIGINT NOT NULL,
    message_id       TEXT NOT NULL,               -- связь с mail_messages по (mailbox_id, message_id)
    seen             BOOLEAN NOT NULL DEFAULT FALSE,
    answered         BOOLEAN NOT NULL DEFAULT FALSE,
    flagged          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mailbox_id, folder, uid_validity, uid)
);
CREATE INDEX idx_mail_membership_folder ON mail_memberships (mailbox_id, folder, uid DESC);
CREATE INDEX idx_mail_membership_msg    ON mail_memberships (mailbox_id, message_id);

-- Таблицу event_publication (Event Publication Registry) создаёт сам Spring Modulith
-- через spring.modulith.events.jdbc.schema-initialization.enabled=true — здесь её нет,
-- чтобы схема всегда соответствовала версии Modulith. Она попадёт в ту же схему,
-- потому что search_path пула выставлен на неё (spring.datasource.hikari.schema).
