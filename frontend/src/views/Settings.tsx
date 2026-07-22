import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, SettingsView } from '../api';
import Shell from '../components/Shell';

export default function Settings({ username, onLogout }: { username?: string; onLogout?: () => void }) {
  const [data, setData] = useState<SettingsView | null>(null);

  const load = () => api.getJson<SettingsView>('/api/settings').then(setData);
  useEffect(() => { load(); }, []);

  return (
    <Shell
      title="Настройки"
      sub="Telegram, AI-провайдер, промпт и почтовые ящики"
      username={username}
      onLogout={onLogout}
    >
      {!data && <div className="center">Загрузка…</div>}
      {data && (
        <>
          <TelegramCard data={data} />
          <AiCard data={data} />
          <PromptCard data={data} />
          <MailboxesCard data={data} reload={load} />
        </>
      )}
    </Shell>
  );
}

/** Кнопка «Сохранить» + индикатор для одной секции. */
function useSaver(save: () => Promise<void>) {
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setSaved(false);
    try {
      await save();
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } finally {
      setBusy(false);
    }
  };
  return { submit, saved, busy };
}

function TelegramCard({ data }: { data: SettingsView }) {
  const [botToken, setBotToken] = useState('');
  const [managerChatId, setManagerChatId] = useState(String(data.telegram.managerChatId));
  const [enabled, setEnabled] = useState(data.telegram.enabled);

  const { submit, saved, busy } = useSaver(() =>
    api.postJson('/api/settings/telegram', {
      botToken,
      managerChatId: Number(managerChatId) || 0,
      enabled,
    }));

  return (
    <div className="card pad">
      <h2>Telegram</h2>
      <p className="hint">Бот присылает черновики на валидацию. Токен — у @BotFather, chat_id — у @userinfobot.</p>
      <form onSubmit={submit}>
        <div className="row">
          <label>Токен бота
            <input type="password" value={botToken} onChange={(e) => setBotToken(e.target.value)}
                   placeholder={data.telegram.botTokenSet ? 'Задан — оставьте пустым, чтобы не менять' : 'Не задан'} />
          </label>
          <label>Chat ID менеджера
            <input type="text" value={managerChatId} onChange={(e) => setManagerChatId(e.target.value)} />
          </label>
        </div>
        <label className="inline-check">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} /> Бот включён
        </label>
        <p className="hint">{data.telegram.configured ? 'Статус: настроен' : 'Статус: не настроен'}</p>
        <div className="form-actions">
          <button type="submit" disabled={busy}>Сохранить</button>
          {saved && <span className="saved">Сохранено</span>}
        </div>
      </form>
    </div>
  );
}

function AiCard({ data }: { data: SettingsView }) {
  const [provider, setProvider] = useState(data.ai.provider);
  const [model, setModel] = useState(data.ai.model);
  const [ollamaBaseUrl, setOllamaBaseUrl] = useState(data.ai.ollamaBaseUrl);
  const [openAiApiKey, setOpenAiApiKey] = useState('');
  const [temperature, setTemperature] = useState(String(data.ai.temperature));

  const { submit, saved, busy } = useSaver(() =>
    api.postJson('/api/settings/ai', {
      provider,
      openAiApiKey,
      ollamaBaseUrl,
      model,
      temperature: Number(temperature) || 0,
    }));

  return (
    <div className="card pad">
      <h2>AI-провайдер</h2>
      <p className="hint">Модель генерирует черновики, переводы и классифицирует отправителей.
        Провайдер, ключ, URL, модель и temperature применяются сразу — перезапуск не нужен.
        Ключ хранится в базе в зашифрованном виде; пустое поле означает «не менять».</p>
      <form onSubmit={submit}>
        <div className="row">
          <label>Провайдер
            <select value={provider} onChange={(e) => setProvider(e.target.value)}>
              <option value="ollama">Ollama (локально)</option>
              <option value="openai">OpenAI</option>
            </select>
          </label>
          <label>Модель
            <input type="text" value={model} onChange={(e) => setModel(e.target.value)}
                   placeholder="qwen2.5:14b-instruct / gpt-4o-mini" />
          </label>
        </div>
        <div className="row">
          <label>URL Ollama
            <input type="text" value={ollamaBaseUrl} onChange={(e) => setOllamaBaseUrl(e.target.value)}
                   placeholder="http://localhost:11434" />
          </label>
          <label>Ключ OpenAI
            <input type="password" value={openAiApiKey} onChange={(e) => setOpenAiApiKey(e.target.value)}
                   placeholder={data.ai.openAiKeySet ? 'Задан — оставьте пустым, чтобы не менять' : 'Не задан'} />
          </label>
          <label>Temperature
            <input type="number" step="0.1" min="0" max="2" value={temperature}
                   onChange={(e) => setTemperature(e.target.value)} />
          </label>
        </div>
        <div className="form-actions">
          <button type="submit" disabled={busy}>Сохранить</button>
          {saved && <span className="saved">Сохранено</span>}
        </div>
      </form>
    </div>
  );
}

function PromptCard({ data }: { data: SettingsView }) {
  const [systemPrompt, setSystemPrompt] = useState(data.ai.systemPrompt);
  const { submit, saved, busy } = useSaver(() =>
    api.postJson('/api/settings/prompt', { systemPrompt }));

  async function reset() {
    if (!confirm('Вернуть промпт по умолчанию?')) return;
    await api.postJson('/api/settings/prompt', { systemPrompt: '' });
    const fresh = await api.getJson<SettingsView>('/api/settings');
    setSystemPrompt(fresh.ai.systemPrompt);
  }

  return (
    <div className="card pad">
      <h2>Промпт генерации</h2>
      <p className="hint">Системный промпт, по которому модель пишет черновик.
        Плейсхолдер <code>{'{profile}'}</code> заменяется на профиль автора из настроек ящика.
        Применяется сразу. Пустое поле — вернуть промпт по умолчанию.</p>
      <form onSubmit={submit}>
        <label>
          <textarea rows={18} value={systemPrompt} onChange={(e) => setSystemPrompt(e.target.value)} />
        </label>
        <div className="form-actions">
          <button type="submit" disabled={busy}>Сохранить</button>
          <button type="button" className="ghost" onClick={reset}>Сбросить к дефолту</button>
          {saved && <span className="saved">Сохранено</span>}
        </div>
      </form>
    </div>
  );
}

function MailboxesCard({ data, reload }: { data: SettingsView; reload: () => Promise<void> }) {
  async function toggle(id: string, enabled: boolean) {
    await api.postJson(`/api/mailboxes/${id}/enabled`, { enabled });
    await reload();
  }
  async function remove(id: string) {
    if (!confirm('Удалить ящик?')) return;
    await api.del(`/api/mailboxes/${id}`);
    await reload();
  }

  return (
    <div className="card pad">
      <h2>Почтовые ящики</h2>
      <p className="hint">Ответ уходит через тот ящик, на который пришло письмо.
        Профиль автора используется для генерации ответа от его имени.</p>
      <table>
        <thead>
          <tr><th>ID</th><th>Профиль</th><th>Адрес</th><th>IMAP</th><th>SMTP</th><th>Статус</th><th></th></tr>
        </thead>
        <tbody>
          {data.mailboxes.map((m) => (
            <tr key={m.id}>
              <td>{m.id}</td>
              <td>{m.profile}</td>
              <td>{m.username}</td>
              <td>{m.imapHost}:{m.imapPort}</td>
              <td>{m.smtpHost}:{m.smtpPort}</td>
              <td><span className={m.enabled ? 'tag on' : 'tag off'}>{m.enabled ? 'включён' : 'выключен'}</span></td>
              <td>
                <div className="actions">
                  <Link to={`/mailbox/${m.id}`}>Изменить</Link>
                  <button className="ghost" onClick={() => toggle(m.id, !m.enabled)}>
                    {m.enabled ? 'Выключить' : 'Включить'}
                  </button>
                  <button className="danger" onClick={() => remove(m.id)}>Удалить</button>
                </div>
              </td>
            </tr>
          ))}
          {data.mailboxes.length === 0 && (
            <tr><td colSpan={7} style={{ color: 'var(--muted)' }}>Ящики не настроены</td></tr>
          )}
        </tbody>
      </table>
      <p style={{ marginTop: 16 }}><Link to="/mailbox">+ Добавить ящик</Link></p>
    </div>
  );
}
