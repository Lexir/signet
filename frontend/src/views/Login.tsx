import { FormEvent, useState } from 'react';
import { api } from '../api';

/** Экран входа. При успехе дергает onLoggedIn — App перепроверяет /api/me. */
export default function Login({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const ok = await api.login(username, password);
      if (ok) {
        onLoggedIn();
      } else {
        setError('Неверный логин или пароль');
      }
    } catch {
      setError('Сервер недоступен');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <div className="logo">S</div>
        <h1>Signet</h1>
        <p className="sub">Ассистент ответов на почту — вход в панель</p>
        <label>
          Логин
          <input type="text" autoComplete="username" value={username}
                 onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          Пароль
          <input type="password" autoComplete="current-password" value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>{busy ? 'Вход…' : 'Войти'}</button>
      </form>
    </div>
  );
}
