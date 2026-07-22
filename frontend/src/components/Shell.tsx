import { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { api } from '../api';

/**
 * Каркас приложения: тёмный сайдбар слева (бренд, навигация, пользователь),
 * контентная область справа. `dense` — без внутренних отступов и ограничения
 * ширины (почтовый клиент на всю высоту/ширину).
 */
export default function Shell({
  title, sub, username, toast, onLogout, dense, children,
}: {
  title?: string;
  sub?: string;
  username?: string;
  toast?: string;
  onLogout?: () => void;
  dense?: boolean;
  children: ReactNode;
}) {
  async function logout() {
    await api.logout();
    onLogout?.();
  }

  return (
    <div className="app">
      <aside className="side">
        <NavLink to="/" className="side-brand">
          <span className="logo">S</span>
          <span className="name">Signet</span>
        </NavLink>

        <nav>
          <NavLink to="/" end className={navCls}><IconGrid /><span>Дашборд</span></NavLink>
          <NavLink to="/mail" className={navCls}><IconMail /><span>Почта</span></NavLink>
          <NavLink to="/reviews" className={navCls}><IconCheck /><span>Ревью</span></NavLink>
          <NavLink to="/settings" className={navCls}><IconGear /><span>Настройки</span></NavLink>
        </nav>

        <div className="side-bottom">
          {username && <div className="side-user" title={username}>{username}</div>}
          {onLogout && (
            <button className="ghost sm side-logout" onClick={logout}>
              <IconExit /><span>Выйти</span>
            </button>
          )}
        </div>
      </aside>

      <div className={`content${dense ? ' dense' : ''}`}>
        {(title || sub) && (
          <div className="page-head">
            {title && <h1>{title}</h1>}
            {sub && <p className="sub">{sub}</p>}
          </div>
        )}
        {children}
      </div>

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}

const navCls = ({ isActive }: { isActive: boolean }) => `side-link${isActive ? ' active' : ''}`;

/* Иконки — инлайн-SVG, чтобы не тянуть иконочный пакет */

function IconGrid() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  );
}

function IconMail() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 6 9-6" />
    </svg>
  );
}

function IconCheck() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="m8.5 12 2.5 2.5 5-5" />
    </svg>
  );
}

function IconGear() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1.03 1.56V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.11-1.56 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .34-1.87 1.7 1.7 0 0 0-1.56-1.03H3a2 2 0 1 1 0-4h.09a1.7 1.7 0 0 0 1.56-1.11 1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.87.34h.01A1.7 1.7 0 0 0 10 4.09V4a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1.03 1.56 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87v.01A1.7 1.7 0 0 0 20.91 11H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1Z" />
    </svg>
  );
}

function IconExit() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="m16 17 5-5-5-5" />
      <path d="M21 12H9" />
    </svg>
  );
}
