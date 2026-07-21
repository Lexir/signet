import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, MailboxView, ReviewChannel, SettingsView } from '../api';

interface FormState {
  id: string;
  profile: string;
  username: string;
  password: string;
  imapHost: string;
  imapPort: number;
  folder: string;
  processedFolder: string;
  smtpHost: string;
  smtpPort: number;
  smtpSsl: boolean;
  smtpStarttls: boolean;
  smtpAuth: boolean;
  reviewChannel: ReviewChannel;
}

const BLANK: FormState = {
  id: '', profile: '', username: '', password: '',
  imapHost: '', imapPort: 993, folder: 'INBOX', processedFolder: '',
  smtpHost: '', smtpPort: 465, smtpSsl: true, smtpStarttls: false, smtpAuth: true,
  reviewChannel: 'TELEGRAM',
};

function fromView(m: MailboxView): FormState {
  return { ...m, password: '' };
}

export default function MailboxForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const editing = Boolean(id);
  const [form, setForm] = useState<FormState | null>(editing ? null : BLANK);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!editing) return;
    api.getJson<SettingsView>('/api/settings').then((s) => {
      const found = s.mailboxes.find((m) => m.id === id);
      setForm(found ? fromView(found) : { ...BLANK, id: id! });
    });
  }, [editing, id]);

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!form) return;
    setBusy(true);
    setError('');
    try {
      await api.postJson('/api/mailboxes', form);
      navigate('/settings');
    } catch {
      setError('Не удалось сохранить ящик');
      setBusy(false);
    }
  }

  if (!form) return <div className="center">Загрузка…</div>;

  return (
    <>
      <header className="top">
        <h1>{editing ? 'Редактирование ящика' : 'Новый ящик'}</h1>
        <nav><Link to="/settings">← Настройки</Link></nav>
      </header>

      <div className="card pad" style={{ maxWidth: 900 }}>
        <h2>Параметры ящика</h2>
        <p className="hint">Gmail: IMAP <code>imap.gmail.com:993</code>, SMTP <code>smtp.gmail.com:465</code> + SSL.
          Пароль — «пароль приложения» Google (16 символов без пробелов).</p>

        <form onSubmit={submit}>
          <div className="row">
            <label>ID (латиницей, уникальный)
              <input type="text" required value={form.id} readOnly={editing}
                     onChange={(e) => set('id', e.target.value)} />
            </label>
          </div>
          <label>Профиль автора — от чьего имени пишем
            <textarea rows={5} value={form.profile} onChange={(e) => set('profile', e.target.value)}
                      placeholder="Например: Меня зовут Алекс, 32 года, Москва. Люблю горы, кино и настолки.&#10;Пишу спокойно и с юмором, без пафоса." />
          </label>
          <p className="hint">Модель пишет от первого лица по этому описанию. Факты, которых здесь нет,
            она выдумывать не будет — задаст вопрос собеседнику. Черновик всё равно приходит вам на проверку.</p>
          <div className="row">
            <label>Адрес / логин
              <input type="text" value={form.username} onChange={(e) => set('username', e.target.value)}
                     placeholder="alex@gmail.com" />
            </label>
            <label>Пароль
              <input type="password" value={form.password} onChange={(e) => set('password', e.target.value)}
                     placeholder="Оставьте пустым, чтобы не менять" />
            </label>
          </div>

          <h2 style={{ marginTop: 20 }}>IMAP (приём)</h2>
          <div className="row">
            <label>Хост <input type="text" value={form.imapHost} onChange={(e) => set('imapHost', e.target.value)}
                               placeholder="imap.gmail.com" /></label>
            <label>Порт <input type="number" value={form.imapPort}
                               onChange={(e) => set('imapPort', Number(e.target.value))} /></label>
            <label>Папка <input type="text" value={form.folder}
                                onChange={(e) => set('folder', e.target.value)} /></label>
            <label>Папка «обработано» (пусто — не переносить)
              <input type="text" value={form.processedFolder}
                     onChange={(e) => set('processedFolder', e.target.value)} /></label>
          </div>

          <h2 style={{ marginTop: 20 }}>SMTP (отправка)</h2>
          <div className="row">
            <label>Хост <input type="text" value={form.smtpHost} onChange={(e) => set('smtpHost', e.target.value)}
                               placeholder="smtp.gmail.com" /></label>
            <label>Порт <input type="number" value={form.smtpPort}
                               onChange={(e) => set('smtpPort', Number(e.target.value))} /></label>
          </div>
          <label className="inline-check">
            <input type="checkbox" checked={form.smtpSsl} onChange={(e) => set('smtpSsl', e.target.checked)} /> SSL (порт 465)
          </label>
          <label className="inline-check">
            <input type="checkbox" checked={form.smtpStarttls} onChange={(e) => set('smtpStarttls', e.target.checked)} /> STARTTLS (порт 587)
          </label>
          <label className="inline-check">
            <input type="checkbox" checked={form.smtpAuth} onChange={(e) => set('smtpAuth', e.target.checked)} /> Аутентификация SMTP
          </label>

          <h2 style={{ marginTop: 20 }}>Разбор ответов</h2>
          <div className="row">
            <label>Куда приходит AI-черновик на проверку
              <select value={form.reviewChannel}
                      onChange={(e) => set('reviewChannel', e.target.value as ReviewChannel)}>
                <option value="TELEGRAM">Telegram-бот</option>
                <option value="UI">Веб-очередь (/reviews)</option>
              </select>
            </label>
          </div>
          <p className="hint">При «Сгенерировать ответ» черновик уходит выбранным каналом:
            в Telegram-бот или в веб-очередь ревью. Ручной ответ доступен всегда прямо из письма.</p>

          {error && <p className="error">{error}</p>}
          <div className="form-actions">
            <button type="submit" disabled={busy}>Сохранить</button>
          </div>
        </form>
      </div>
    </>
  );
}
