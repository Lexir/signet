import { useCallback, useEffect, useState } from 'react';
import { api, ReviewItem } from '../api';
import Shell from '../components/Shell';

export default function Reviews({ username, onLogout }: { username: string; onLogout: () => void }) {
  const [items, setItems] = useState<ReviewItem[]>([]);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [busy, setBusy] = useState<string>('');

  const load = useCallback(() => {
    api.getJson<ReviewItem[]>('/api/reviews').then(setItems).catch(() => setError('Не удалось загрузить очередь'));
  }, []);

  useEffect(() => { load(); }, [load]);

  function flash(msg: string) { setToast(msg); setTimeout(() => setToast(''), 3000); }

  async function act(emailId: string, action: 'approve' | 'reject') {
    setBusy(emailId);
    try {
      await api.postJson(`/api/reviews/${emailId}/${action}`, {});
      flash(action === 'approve' ? 'Одобрено, отправляю' : 'Отклонено');
      setItems((xs) => xs.filter((x) => x.emailId !== emailId));
    } catch { setError('Действие не удалось'); } finally { setBusy(''); }
  }

  async function saveEdit(emailId: string, text: string) {
    setBusy(emailId);
    try {
      await api.postJson(`/api/reviews/${emailId}/edit`, { text });
      flash('Правка отправлена');
      setItems((xs) => xs.filter((x) => x.emailId !== emailId));
    } catch { setError('Не удалось сохранить правку'); } finally { setBusy(''); }
  }

  return (
    <Shell
      title="Ревью"
      sub="Очередь AI-черновиков на веб-разбор (ящики с каналом UI)"
      username={username}
      onLogout={onLogout}
      toast={toast}
    >
      {error && <p className="error">{error}</p>}
      {items.length === 0 && !error && (
        <p className="hint">Очередь пуста — новых черновиков на разбор нет.</p>
      )}

      {items.map((it) => (
        <ReviewCard key={it.emailId} item={it} busy={busy === it.emailId}
          onApprove={() => act(it.emailId, 'approve')}
          onReject={() => act(it.emailId, 'reject')}
          onEdit={(text) => saveEdit(it.emailId, text)} />
      ))}
    </Shell>
  );
}

function ReviewCard({
  item, busy, onApprove, onReject, onEdit,
}: {
  item: ReviewItem; busy: boolean;
  onApprove: () => void; onReject: () => void; onEdit: (text: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(item.aiTextRu ?? item.aiText);

  return (
    <div className="review-card">
      <div className="rc-head">
        <div>
          <strong>{item.subject || '(без темы)'}</strong>
          <div className="sub">{item.from} · {item.mailboxLabel} · язык: {item.language || '—'}</div>
        </div>
      </div>

      <div className="rc-block">
        <div className="rc-label">Письмо</div>
        <div className="rc-text">{item.clientBody}</div>
      </div>
      <div className="rc-block">
        <div className="rc-label">Черновик ответа</div>
        <div className="rc-text">{item.aiText}</div>
      </div>
      {item.aiTextRu && (
        <div className="rc-block">
          <div className="rc-label">Перевод ответа (RU)</div>
          <div className="rc-text">{item.aiTextRu}</div>
        </div>
      )}

      {editing ? (
        <>
          <textarea rows={8} value={text} onChange={(e) => setText(e.target.value)} style={{ marginTop: 10 }} />
          <div className="form-actions" style={{ marginTop: 10 }}>
            <button disabled={busy || !text.trim()} onClick={() => onEdit(text)}>Отправить правку</button>
            <button className="ghost" onClick={() => setEditing(false)}>Отмена</button>
          </div>
        </>
      ) : (
        <div className="form-actions" style={{ marginTop: 12 }}>
          <button disabled={busy} onClick={onApprove}>✅ Одобрить</button>
          <button className="ghost" disabled={busy} onClick={() => setEditing(true)}>✏️ Редактировать</button>
          <button className="danger" disabled={busy} onClick={onReject}>❌ Отклонить</button>
        </div>
      )}
    </div>
  );
}
