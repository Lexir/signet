import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  api, AttachmentView, DraftView, MailboxLite, MailFolder, MessageDetail, MessagePage, MessageSummary,
} from '../api';

const PAGE_SIZE = 50;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export default function Mail({ username, onLogout }: { username: string; onLogout: () => void }) {
  const [mailboxes, setMailboxes] = useState<MailboxLite[]>([]);
  const [mbx, setMbx] = useState<string>('');
  const [folders, setFolders] = useState<MailFolder[]>([]);
  const [folder, setFolder] = useState<string>('');
  const [page, setPage] = useState<MessagePage | null>(null);
  const [pageNo, setPageNo] = useState(0);
  const [selected, setSelected] = useState<MessageDetail | null>(null);
  const [attachments, setAttachments] = useState<AttachmentView[]>([]);
  const [draft, setDraft] = useState<DraftView | null>(null);
  const [genBusy, setGenBusy] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [composeOpen, setComposeOpen] = useState(false);
  const openId = useRef<string>('');   // какое письмо открыто сейчас — чтобы поздний poll не перезаписал чужой черновик

  // Ящики → выбрать первый.
  useEffect(() => {
    api.getJson<MailboxLite[]>('/api/mail/mailboxes')
      .then((list) => { setMailboxes(list); if (list.length) setMbx(list[0].id); })
      .catch(() => setError('Не удалось загрузить ящики'));
  }, []);

  // Папки ящика → выбрать INBOX (или первуюselectable).
  useEffect(() => {
    if (!mbx) return;
    setFolders([]); setFolder(''); setPage(null); setSelected(null);
    api.getJson<MailFolder[]>(`/api/mail/${mbx}/folders`)
      .then((fs) => {
        setFolders(fs);
        const sel = fs.filter((f) => f.selectable);
        const inbox = sel.find((f) => f.name.toUpperCase() === 'INBOX') ?? sel[0];
        if (inbox) setFolder(inbox.name);
      })
      .catch(() => setError('Не удалось загрузить папки'));
  }, [mbx]);

  const loadMessages = useCallback((p: number) => {
    if (!mbx || !folder) return;
    const q = `folder=${encodeURIComponent(folder)}&page=${p}&size=${PAGE_SIZE}`;
    api.getJson<MessagePage>(`/api/mail/${mbx}/messages?${q}`)
      .then(setPage)
      .catch(() => setError('Не удалось загрузить письма'));
  }, [mbx, folder]);

  useEffect(() => { setPageNo(0); setSelected(null); loadMessages(0); }, [loadMessages]);

  const loadDraft = useCallback(async (messageId: string) => {
    const d = await api.getJsonOrNull<DraftView>(`/api/mail/messages/${messageId}/draft`);
    if (openId.current === messageId) setDraft(d);   // не перезаписываем, если уже переключились
    return d;
  }, []);

  function openMessage(m: MessageSummary) {
    openId.current = m.id;
    setSelected(null); setAttachments([]); setDraft(null); setGenBusy(false);
    api.getJson<MessageDetail>(`/api/mail/messages/${m.id}`)
      .then((d) => {
        setSelected(d);
        if (d.hasAttachments) {
          api.getJson<AttachmentView[]>(`/api/mail/messages/${m.id}/attachments`).then(setAttachments).catch(() => {});
        }
        loadDraft(m.id).catch(() => {});
      })
      .catch(() => setError('Не удалось открыть письмо'));
  }

  async function generateReply() {
    if (!selected) return;
    const id = selected.id;
    try {
      await api.postJson(`/api/mail/${selected.mailboxId}/messages/${id}/generate`, {});
      setGenBusy(true);
      // Генерация асинхронная — опрашиваем черновик, пока не появится текст (или не упадёт).
      for (let i = 0; i < 30 && openId.current === id; i++) {
        await sleep(2000);
        const d = await loadDraft(id).catch(() => null);
        if (d && (d.aiText || d.emailStatus === 'FAILED' || d.emailStatus === 'IGNORED')) break;
      }
    } catch {
      setError('Не удалось запустить генерацию');
    } finally {
      if (openId.current === id) setGenBusy(false);
    }
  }

  async function reviewAction(action: 'approve' | 'reject') {
    if (!draft || !selected) return;
    try {
      await api.postJson(`/api/reviews/${draft.emailId}/${action}`, {});
      flash(action === 'approve' ? 'Одобрено, отправляю' : 'Отклонено');
      await loadDraft(selected.id);
    } catch { setError('Действие не удалось'); }
  }

  async function reviewEdit(text: string) {
    if (!draft || !selected) return;
    await api.postJson(`/api/reviews/${draft.emailId}/edit`, { text });
    flash('Правка отправлена');
    await loadDraft(selected.id);
  }

  async function sendReply(text: string) {
    if (!selected) return;
    await api.postJson(`/api/mail/${selected.mailboxId}/messages/${selected.id}/reply`, { text });
    setComposeOpen(false);
    flash('Ответ отправлен');
    loadMessages(pageNo);
  }

  function flash(msg: string) { setToast(msg); setTimeout(() => setToast(''), 3000); }

  async function logout() { await api.logout(); onLogout(); }

  return (
    <>
      <header className="top">
        <div>
          <h1>Почта</h1>
          <p className="sub">Папки и письма по ящикам · синхронизация зеркала</p>
        </div>
        <nav>
          {toast && <span className="saved">{toast}</span>}
          {username && <span className="sub">{username}</span>}
          <Link to="/">Дашборд</Link>
          <Link to="/reviews">Ревью</Link>
          <Link to="/settings">⚙ Настройки</Link>
          <button className="ghost" onClick={logout}>Выйти</button>
        </nav>
      </header>

      {error && <p className="error" style={{ margin: '12px 28px' }}>{error}</p>}

      <div className="mail-shell">
        {/* Ящик + папки */}
        <div className="mail-col">
          <div className="mail-select">
            <select value={mbx} onChange={(e) => setMbx(e.target.value)}>
              {mailboxes.map((m) => <option key={m.id} value={m.id}>{m.username || m.id}</option>)}
            </select>
          </div>
          <h3>Папки</h3>
          {folders.filter((f) => f.selectable).map((f) => (
            <button
              key={f.name}
              className={`folder-item ${f.name === folder ? 'active' : ''}`}
              onClick={() => setFolder(f.name)}
            >
              <span>{f.name}</span>
              {f.unread > 0 && <span className="badge">{f.unread}</span>}
            </button>
          ))}
          {folders.length === 0 && <p className="hint" style={{ padding: '0 16px' }}>Папки ещё не синхронизированы</p>}
        </div>

        {/* Список писем */}
        <div className="mail-col">
          <h3>{folder || 'Письма'}</h3>
          {page?.content.map((m) => (
            <button
              key={m.id}
              className={`msg-item ${m.seen ? '' : 'unread'} ${selected?.id === m.id ? 'active' : ''}`}
              onClick={() => openMessage(m)}
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
              <button className="ghost" disabled={pageNo === 0}
                onClick={() => { const n = pageNo - 1; setPageNo(n); loadMessages(n); }}>← Назад</button>
              <span>{pageNo + 1} / {Math.ceil(page.total / PAGE_SIZE)}</span>
              <button className="ghost" disabled={(pageNo + 1) * PAGE_SIZE >= page.total}
                onClick={() => { const n = pageNo + 1; setPageNo(n); loadMessages(n); }}>Вперёд →</button>
            </div>
          )}
        </div>

        {/* Чтение */}
        <div className="mail-col mail-read">
          {!selected && <p className="hint">Выберите письмо</p>}
          {selected && (
            <>
              <h2 className="rd-subject">{selected.subject || '(без темы)'}</h2>
              <p className="rd-meta">От: {selected.from || '—'}</p>
              <p className="rd-meta">Кому: {selected.to || '—'}</p>
              <p className="rd-meta">{fmtDate(selected.sentAt)}</p>
              <div className="rd-actions">
                <button onClick={() => setComposeOpen(true)}>✉ Ответить</button>
                <button className="ghost" disabled={genBusy} onClick={generateReply}>
                  {genBusy ? '⏳ Генерирую…' : (draft?.aiText ? '✨ Перегенерировать' : '✨ Сгенерировать ответ')}
                </button>
              </div>
              <div className="rd-body">{selected.body || '(пустое тело)'}</div>

              <DraftPanel draft={draft} genBusy={genBusy}
                onApprove={() => reviewAction('approve')}
                onReject={() => reviewAction('reject')}
                onEdit={reviewEdit} />
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
          onSend={sendReply}
        />
      )}
    </>
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
