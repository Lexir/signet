import { useEffect, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  api, AttachmentView, DraftView, MailboxLite, MailFolder, MessageDetail, MessagePage,
} from '../api';
import Shell from '../components/Shell';

const PAGE_SIZE = 50;

// Человеческие названия стандартных IMAP-папок. Ключ — имя без префикса «[Gmail]/»
// в верхнем регистре; неизвестные папки показываем как есть (тоже без префикса).
const FOLDER_LABELS: Record<string, string> = {
  'INBOX': 'Входящие',
  'SENT': 'Отправленные',
  'SENT MAIL': 'Отправленные',
  'SENT ITEMS': 'Отправленные',
  'ОТПРАВЛЕННЫЕ': 'Отправленные',
  'DRAFTS': 'Черновики',
  'ЧЕРНОВИКИ': 'Черновики',
  'TRASH': 'Корзина',
  'BIN': 'Корзина',
  'КОРЗИНА': 'Корзина',
  'SPAM': 'Спам',
  'JUNK': 'Спам',
  'СПАМ': 'Спам',
  'STARRED': 'Помеченные',
  'ПОМЕЧЕННЫЕ': 'Помеченные',
  'IMPORTANT': 'Важное',
  'ALL MAIL': 'Вся почта',
  'ВСЯ ПОЧТА': 'Вся почта',
  'ARCHIVE': 'Архив',
  'PROCESSED': 'Обработанные',
};

/** Отображаемое имя папки: без «[Gmail]/», стандартные — по-русски. */
function folderLabel(name: string): string {
  const short = name.replace(/^\[Gmail\]\//i, '');
  return FOLDER_LABELS[short.toUpperCase()] ?? short;
}

export default function Mail({ username, onLogout }: { username: string; onLogout: () => void }) {
  const qc = useQueryClient();
  const [mbx, setMbx] = useState('');
  const [folder, setFolder] = useState('');
  const [pageNo, setPageNo] = useState(0);
  const [selectedId, setSelectedId] = useState('');
  const [composeOpen, setComposeOpen] = useState(false);
  const [toast, setToast] = useState('');
  const flash = (m: string) => { setToast(m); setTimeout(() => setToast(''), 3000); };

  // --- Запросы: React Query кэширует, дедуплицирует и решает, когда перезапросить ---

  const mailboxesQ = useQuery({
    queryKey: ['mail', 'mailboxes'],
    queryFn: () => api.getJson<MailboxLite[]>('/api/mail/mailboxes'),
    staleTime: 5 * 60_000,
  });

  const foldersQ = useQuery({
    queryKey: ['mail', 'folders', mbx],
    queryFn: () => api.getJson<MailFolder[]>(`/api/mail/${mbx}/folders`),
    enabled: !!mbx,
  });

  const messagesQ = useQuery({
    queryKey: ['mail', 'messages', mbx, folder, pageNo],
    queryFn: () => api.getJson<MessagePage>(
      `/api/mail/${mbx}/messages?folder=${encodeURIComponent(folder)}&page=${pageNo}&size=${PAGE_SIZE}`),
    enabled: !!mbx && !!folder,
    placeholderData: keepPreviousData,   // при листании не мигаем пустотой
    staleTime: 15_000,                   // список — свежий: показывает новую почту и непрочитанное
  });

  const messageQ = useQuery({
    queryKey: ['mail', 'message', selectedId],
    queryFn: () => api.getJson<MessageDetail>(`/api/mail/messages/${selectedId}`),
    enabled: !!selectedId,
    staleTime: Infinity,                 // тело письма неизменно — кэшируем на всю сессию
  });

  const attnQ = useQuery({
    queryKey: ['mail', 'attachments', selectedId],
    queryFn: () => api.getJson<AttachmentView[]>(`/api/mail/messages/${selectedId}/attachments`),
    enabled: !!selectedId && !!messageQ.data?.hasAttachments,
    staleTime: Infinity,
  });

  const draftQ = useQuery({
    queryKey: ['mail', 'draft', selectedId],
    queryFn: () => api.getJsonOrNull<DraftView>(`/api/mail/messages/${selectedId}/draft`),
    enabled: !!selectedId,
    staleTime: 0,                        // черновик динамический — не кэшируем
    // Пока идёт генерация — опрашиваем; как только текст готов (или ошибка) — стоп.
    refetchInterval: (query) => {
      const d = query.state.data;
      return d && !d.aiText && (d.emailStatus === 'RECEIVED' || d.emailStatus === 'DRAFTING') ? 2000 : false;
    },
  });

  // --- Автовыбор ящика и папки ---
  useEffect(() => {
    if (!mbx && mailboxesQ.data?.length) setMbx(mailboxesQ.data[0].id);
  }, [mbx, mailboxesQ.data]);

  useEffect(() => {
    const selectable = foldersQ.data?.filter((f) => f.selectable) ?? [];
    if (!selectable.length) return;
    const inbox = selectable.find((f) => f.name.toUpperCase() === 'INBOX') ?? selectable[0];
    setFolder((cur) => (selectable.some((f) => f.name === cur) ? cur : inbox.name));
  }, [foldersQ.data]);

  // Смена ящика/папки — сбрасываем страницу и выбранное письмо.
  useEffect(() => { setPageNo(0); setSelectedId(''); }, [mbx, folder]);

  // --- Мутации ---
  const syncM = useMutation({
    mutationFn: () => api.postJson(`/api/mail/${mbx}/sync`, {}),
    onSuccess: () => {
      flash('Синхронизировано');
      qc.invalidateQueries({ queryKey: ['mail', 'folders', mbx] });
      qc.invalidateQueries({ queryKey: ['mail', 'messages', mbx] });
      qc.invalidateQueries({ queryKey: ['mail', 'mailboxes'] });
    },
    onError: () => flash('Синхронизация не удалась'),
  });

  const generateM = useMutation({
    mutationFn: (m: MessageDetail) => api.postJson(`/api/mail/${m.mailboxId}/messages/${m.id}/generate`, {}),
    onSuccess: (_r, m) => { flash('Генерация запущена'); qc.invalidateQueries({ queryKey: ['mail', 'draft', m.id] }); },
    onError: () => flash('Не удалось запустить генерацию'),
  });

  const replyM = useMutation({
    mutationFn: (v: { m: MessageDetail; text: string }) =>
      api.postJson(`/api/mail/${v.m.mailboxId}/messages/${v.m.id}/reply`, { text: v.text }),
    onSuccess: () => {
      setComposeOpen(false);
      flash('Ответ отправлен');
      qc.invalidateQueries({ queryKey: ['mail', 'messages', mbx, folder] });
    },
  });

  const reviewM = useMutation({
    mutationFn: (v: { emailId: string; action: 'approve' | 'reject' }) =>
      api.postJson(`/api/reviews/${v.emailId}/${v.action}`, {}),
    onSuccess: (_r, v) => {
      flash(v.action === 'approve' ? 'Одобрено, отправляю' : 'Отклонено');
      qc.invalidateQueries({ queryKey: ['mail', 'draft', selectedId] });
    },
    onError: () => flash('Действие не удалось'),
  });

  const editM = useMutation({
    mutationFn: (v: { emailId: string; text: string }) => api.postJson(`/api/reviews/${v.emailId}/edit`, { text: v.text }),
    onSuccess: () => { flash('Правка отправлена'); qc.invalidateQueries({ queryKey: ['mail', 'draft', selectedId] }); },
  });

  // --- Производные значения ---
  const mailboxes = mailboxesQ.data ?? [];
  // Входящие всегда сверху, дальше — по алфавиту отображаемых имён.
  const foldersList = (foldersQ.data?.filter((f) => f.selectable) ?? [])
    .slice()
    .sort((a, b) => {
      const ai = a.name.toUpperCase() === 'INBOX' ? 0 : 1;
      const bi = b.name.toUpperCase() === 'INBOX' ? 0 : 1;
      return ai - bi || folderLabel(a.name).localeCompare(folderLabel(b.name), 'ru');
    });
  const page = messagesQ.data ?? null;
  const selected = messageQ.data ?? null;
  const attachments = attnQ.data ?? [];
  const draft = draftQ.data ?? null;
  const generating = generateM.isPending
    || (!!draft && !draft.aiText && (draft.emailStatus === 'RECEIVED' || draft.emailStatus === 'DRAFTING'));
  const error = [mailboxesQ, foldersQ, messagesQ].some((q) => q.isError) ? 'Ошибка загрузки данных' : '';

  return (
    <Shell dense username={username} onLogout={onLogout} toast={toast}>
      {error && <p className="error" style={{ margin: '12px 24px 0' }}>{error}</p>}

      <div className="mail-shell">
        {/* Ящик + папки */}
        <div className="mail-col">
          <div className="mail-select">
            <select value={mbx} onChange={(e) => setMbx(e.target.value)}>
              {mailboxes.map((m) => <option key={m.id} value={m.id}>{m.username || m.id}</option>)}
            </select>
            <button className="ghost" style={{ width: '100%', marginTop: 8 }}
              disabled={!mbx || syncM.isPending} onClick={() => syncM.mutate()}>
              {syncM.isPending ? '⏳ Синхронизирую…' : '🔄 Синхронизировать'}
            </button>
          </div>
          <h3>Папки</h3>
          {foldersList.map((f) => (
            <button
              key={f.name}
              className={`folder-item ${f.name === folder ? 'active' : ''}`}
              onClick={() => setFolder(f.name)}
            >
              <span>{folderLabel(f.name)}</span>
              {f.unread > 0 && <span className="badge">{f.unread}</span>}
            </button>
          ))}
          {foldersQ.isSuccess && foldersList.length === 0
            && <p className="hint" style={{ padding: '0 16px' }}>Папки ещё не синхронизированы</p>}
        </div>

        {/* Список писем */}
        <div className="mail-col">
          <h3>{folder ? folderLabel(folder) : 'Письма'}</h3>
          {page?.content.map((m) => (
            <button
              key={m.id}
              className={`msg-item ${m.seen ? '' : 'unread'} ${selectedId === m.id ? 'active' : ''}`}
              onClick={() => setSelectedId(m.id)}
            >
              <div className="msg-top">
                <span className="msg-from">{m.from || '—'}</span>
                <span className="msg-date">{fmtDate(m.sentAt)}</span>
              </div>
              <div className="msg-subj">
                {m.hasAttachments && <span className="paperclip">📎 </span>}
                {m.subject || '(без темы)'}
              </div>
            </button>
          ))}
          {page && page.content.length === 0 && <p className="hint" style={{ padding: '0 16px' }}>Писем нет</p>}
          {page && page.total > PAGE_SIZE && (
            <div className="pager">
              <button className="ghost" disabled={pageNo === 0} onClick={() => setPageNo((n) => n - 1)}>← Назад</button>
              <span>{pageNo + 1} / {Math.ceil(page.total / PAGE_SIZE)}</span>
              <button className="ghost" disabled={(pageNo + 1) * PAGE_SIZE >= page.total}
                onClick={() => setPageNo((n) => n + 1)}>Вперёд →</button>
            </div>
          )}
        </div>

        {/* Чтение */}
        <div className="mail-col mail-read">
          {!selectedId && <p className="hint">Выберите письмо</p>}
          {selectedId && !selected && <p className="hint">Загрузка…</p>}
          {selected && (
            <>
              <h2 className="rd-subject">{selected.subject || '(без темы)'}</h2>
              <p className="rd-meta">От: {selected.from || '—'}</p>
              <p className="rd-meta">Кому: {selected.to || '—'}</p>
              <p className="rd-meta">{fmtDate(selected.sentAt)}</p>
              <div className="rd-actions">
                <button onClick={() => setComposeOpen(true)}>✉ Ответить</button>
                <button className="ghost" disabled={generating} onClick={() => generateM.mutate(selected)}>
                  {generating ? '⏳ Генерирую…' : (draft?.aiText ? '✨ Перегенерировать' : '✨ Сгенерировать ответ')}
                </button>
              </div>
              <div className="rd-body">{selected.body || '(пустое тело)'}</div>

              <DraftPanel draft={draft} genBusy={generating}
                onApprove={() => draft && reviewM.mutate({ emailId: draft.emailId, action: 'approve' })}
                onReject={() => draft && reviewM.mutate({ emailId: draft.emailId, action: 'reject' })}
                onEdit={(text) => (draft ? editM.mutateAsync({ emailId: draft.emailId, text }) : Promise.resolve())} />

              {attachments.length > 0 && (
                <div className="rd-attn">
                  {attachments.map((a) => (
                    <a key={a.index} href={`/api/mail/messages/${selected.id}/attachments/${a.index}`}>
                      📎 {a.filename || 'attachment'} <span className="sub">({fmtSize(a.size)})</span>
                    </a>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {composeOpen && selected && (
        <ComposeModal
          to={selected.from || ''}
          subject={selected.subject || ''}
          onClose={() => setComposeOpen(false)}
          onSend={(text) => replyM.mutateAsync({ m: selected, text })}
        />
      )}
    </Shell>
  );
}

function ComposeModal({
  to, subject, onClose, onSend,
}: { to: string; subject: string; onClose: () => void; onSend: (text: string) => Promise<void> }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function send() {
    if (!text.trim()) return;
    setBusy(true); setErr('');
    try { await onSend(text); } catch { setErr('Не удалось отправить'); setBusy(false); }
  }

  return (
    <div className="modal-back" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>Ответ</h2>
        <p className="rd-meta">Кому: {to}</p>
        <p className="rd-meta">Тема: {subject.replace(/^(re:\s*)?/i, 'Re: ')}</p>
        <textarea rows={12} value={text} onChange={(e) => setText(e.target.value)}
          placeholder="Текст ответа…" style={{ marginTop: 12 }} />
        {err && <p className="error">{err}</p>}
        <div className="form-actions" style={{ marginTop: 12 }}>
          <button disabled={busy || !text.trim()} onClick={send}>Отправить</button>
          <button className="ghost" onClick={onClose}>Отмена</button>
        </div>
      </div>
    </div>
  );
}

const REVIEW_LABEL: Record<string, string> = {
  APPROVED: 'Одобрен — отправляется',
  EDITED: 'Отредактирован — отправляется',
  REJECTED: 'Отклонён',
};

function DraftPanel({
  draft, genBusy, onApprove, onReject, onEdit,
}: {
  draft: DraftView | null; genBusy: boolean;
  onApprove: () => void; onReject: () => void; onEdit: (text: string) => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  const baseText = draft?.aiTextRu || draft?.aiText || '';
  useEffect(() => { setEditing(false); setText(baseText); }, [draft?.emailId, baseText]);

  // Ничего не генерировали и не генерим — панель не показываем.
  if (!draft && !genBusy) return null;

  if (!draft?.aiText) {
    const status = draft?.emailStatus;
    let msg = '⏳ Генерирую черновик…';
    if (status === 'FAILED') msg = '⚠️ Генерация не удалась — проверьте AI-провайдера в настройках.';
    else if (status === 'IGNORED') msg = 'Помечено как не личное — черновик не создан.';
    return <div className="review-card"><div className="rc-label">Черновик ответа</div><p className="rc-text">{msg}</p></div>;
  }

  const pending = draft.reviewStatus === 'PENDING';

  async function save() {
    if (!text.trim()) return;
    setBusy(true);
    try { await onEdit(text); } finally { setBusy(false); }
  }

  return (
    <div className="review-card" style={{ margin: '20px 0 0' }}>
      <div className="rc-head">
        <strong>✨ Черновик ответа</strong>
        {!pending && draft.reviewStatus && (
          <span className="tag on">{REVIEW_LABEL[draft.reviewStatus] ?? draft.reviewStatus}</span>
        )}
      </div>

      <div className="rc-block">
        <div className="rc-label">Ответ (язык собеседника)</div>
        <div className="rc-text">{draft.finalText || draft.aiText}</div>
      </div>
      {draft.aiTextRu && (
        <div className="rc-block">
          <div className="rc-label">Перевод (RU)</div>
          <div className="rc-text">{draft.aiTextRu}</div>
        </div>
      )}

      {pending && (editing ? (
        <>
          <textarea rows={8} value={text} onChange={(e) => setText(e.target.value)} style={{ marginTop: 10 }} />
          <div className="form-actions" style={{ marginTop: 10 }}>
            <button disabled={busy || !text.trim()} onClick={save}>Отправить правку</button>
            <button className="ghost" onClick={() => setEditing(false)}>Отмена</button>
          </div>
        </>
      ) : (
        <div className="form-actions" style={{ marginTop: 12 }}>
          <button onClick={onApprove}>✅ Одобрить и отправить</button>
          <button className="ghost" onClick={() => setEditing(true)}>✏️ Редактировать</button>
          <button className="danger" onClick={onReject}>❌ Отклонить</button>
        </div>
      ))}
    </div>
  );
}

function fmtDate(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} Б`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} КБ`;
  return `${(bytes / 1024 / 1024).toFixed(1)} МБ`;
}
