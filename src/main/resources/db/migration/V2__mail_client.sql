-- signet :: почтовый клиент — зеркало ящиков (папки + envelope писем)
--
-- Держим ОТДЕЛЬНО от воронки emails: mail_messages — метаданные писем всех папок
-- (тело body_text кэшируется лениво при первом открытии). Синк ничего в IMAP не
-- мутирует — папки открываются READ_ONLY. Схема не указывается: Flyway идёт с
-- search_path на app.datasource.schema (см. V1__init.sql).

-- =========================================================
-- Папки ящика (обновляются при синке)
-- =========================================================
CREATE TABLE mail_folders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id       TEXT NOT NULL,
    name             TEXT NOT NULL,               -- полный путь папки в IMAP
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

-- =========================================================
-- Envelope-метаданные писем; тело — лениво (body_text/body_synced_at)
-- =========================================================
CREATE TABLE mail_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id       TEXT NOT NULL,
    folder           TEXT NOT NULL,
    uid              BIGINT NOT NULL,             -- IMAP UID внутри (folder, uid_validity)
    uid_validity     BIGINT NOT NULL,
    message_id       TEXT,                         -- RFC Message-ID (для связи с воронкой)
    from_addr        TEXT,
    to_addr          TEXT,
    subject          TEXT,
    sent_at          TIMESTAMPTZ,
    size_bytes       INT NOT NULL DEFAULT 0,
    seen             BOOLEAN NOT NULL DEFAULT FALSE,
    answered         BOOLEAN NOT NULL DEFAULT FALSE,
    flagged          BOOLEAN NOT NULL DEFAULT FALSE,
    has_attachments  BOOLEAN NOT NULL DEFAULT FALSE,  -- уточняется при дозагрузке тела
    body_text        TEXT,
    body_synced_at   TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mailbox_id, folder, uid_validity, uid)
);
CREATE INDEX idx_mail_messages_folder ON mail_messages (mailbox_id, folder, uid DESC);
CREATE INDEX idx_mail_messages_msgid  ON mail_messages (message_id);

-- =========================================================
-- Ящик: канал разбора ответов (UI-очередь или Telegram) + папки для синка
-- =========================================================
-- Дефолт TELEGRAM — сохраняет текущее поведение существующих ящиков.
ALTER TABLE mailboxes ADD COLUMN review_channel TEXT NOT NULL DEFAULT 'TELEGRAM';
-- CSV папок для синка; NULL — синкать все selectable.
ALTER TABLE mailboxes ADD COLUMN sync_folders TEXT;
