-- signet :: дедуп хранения зеркала по Message-ID
--
-- Было: строка mail_messages на каждую (папка, uid) — тело письма дублировалось по всем
-- папкам, где письмо помечено (особенно у ярлыковых провайдеров: Gmail «Вся почта» и т.п.).
-- Стало: контент письма хранится ОДИН раз (ключ mailbox+message_id), а папки — лёгкие строки
-- членства (folder, uid) со ссылкой на контент. Так делают зрелые почтовые клиенты.
--
-- Зеркало — это кэш поверх IMAP, поэтому просто пересоздаём таблицу (пересоберётся синком),
-- без переноса данных. Воронка ответа (emails/drafts) не затрагивается — она по message_id.

DROP TABLE IF EXISTS mail_messages;

-- Контент письма — один раз на (ящик, Message-ID). Тело кэшируется лениво.
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
CREATE INDEX idx_mail_messages_msgid ON mail_messages (message_id);

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
