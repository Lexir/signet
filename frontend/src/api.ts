// Тонкая обёртка над fetch: cookie-сессия + CSRF-заголовок из cookie XSRF-TOKEN.
// На 401 (кроме проверки /api/me) шлём глобальное событие — App показывает экран входа.

export const UNAUTHORIZED_EVENT = 'signet:unauthorized';

function cookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[2]) : null;
}

async function request(path: string, init: RequestInit = {}, notifyOn401 = true): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (method !== 'GET' && method !== 'HEAD') {
    const token = cookie('XSRF-TOKEN');
    if (token) headers.set('X-XSRF-TOKEN', token);
  }
  const res = await fetch(path, { ...init, headers, credentials: 'include' });
  if (res.status === 401 && notifyOn401) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }
  return res;
}

async function getJson<T>(path: string): Promise<T> {
  const res = await request(path, {});
  if (!res.ok) throw new Error(`GET ${path} → ${res.status}`);
  return res.json() as Promise<T>;
}

async function postJson(path: string, body: unknown): Promise<void> {
  const res = await request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`POST ${path} → ${res.status}`);
}

async function del(path: string): Promise<void> {
  const res = await request(path, { method: 'DELETE' });
  if (!res.ok) throw new Error(`DELETE ${path} → ${res.status}`);
}

/** Статус входа. Не шумит событием 401 — это штатная проверка при загрузке. */
async function me(): Promise<Me> {
  const res = await request('/api/me', {}, false);
  if (!res.ok) return { authenticated: false, username: '' };
  return res.json() as Promise<Me>;
}

/** Вход формой: Spring Security ждёт x-www-form-urlencoded на /api/login. */
async function login(username: string, password: string): Promise<boolean> {
  const token = cookie('XSRF-TOKEN');
  const headers = new Headers({ 'Content-Type': 'application/x-www-form-urlencoded' });
  if (token) headers.set('X-XSRF-TOKEN', token);
  const res = await fetch('/api/login', {
    method: 'POST',
    headers,
    credentials: 'include',
    body: new URLSearchParams({ username, password }),
  });
  return res.ok;
}

async function logout(): Promise<void> {
  await request('/api/logout', { method: 'POST' }, false);
}

export const api = { getJson, postJson, del, me, login, logout };

// --- Типы ответов бэкенда ---

export interface Me {
  authenticated: boolean;
  username: string;
}

export interface DailyStats {
  day: string;
  received: number;
  sent: number;
  approved: number;
  edited: number;
  rejected: number;
  tokensIn: number;
  tokensOut: number;
}

export interface MailboxStats {
  id: string;
  label: string;
  receivedToday: number;
  sentToday: number;
  pendingReview: number;
  tokensInToday: number;
  tokensOutToday: number;
}

export interface Stats {
  receivedToday: number;
  sentToday: number;
  pendingReview: number;
  approvedTotal: number;
  editedTotal: number;
  rejectedTotal: number;
  editRatePct: number;
  tokensInToday: number;
  tokensOutToday: number;
  history: DailyStats[];
  perMailbox: MailboxStats[];
}

export interface TelegramView {
  managerChatId: number;
  enabled: boolean;
  botTokenSet: boolean;
  configured: boolean;
}

export interface AiView {
  provider: string;
  ollamaBaseUrl: string;
  model: string;
  temperature: number;
  systemPrompt: string;
  openAiKeySet: boolean;
}

export interface PollingView {
  intervalSeconds: number;
  windowEnabled: boolean;
  zone: string;
  days: string[];
  start: string;
  end: string;
}

export interface MailboxView {
  id: string;
  profile: string;
  username: string;
  imapHost: string;
  imapPort: number;
  folder: string;
  processedFolder: string;
  smtpHost: string;
  smtpPort: number;
  smtpSsl: boolean;
  smtpStarttls: boolean;
  smtpAuth: boolean;
  enabled: boolean;
}

export interface SettingsView {
  telegram: TelegramView;
  ai: AiView;
  polling: PollingView;
  mailboxes: MailboxView[];
}
