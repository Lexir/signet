import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Line } from 'react-chartjs-2';
import {
  CategoryScale, Chart, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip,
} from 'chart.js';
import { api, Stats } from '../api';

Chart.register(CategoryScale, LinearScale, PointElement, LineElement, Filler, Tooltip, Legend);

export default function Dashboard({ username, onLogout }: { username: string; onLogout: () => void }) {
  const [s, setS] = useState<Stats | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.getJson<Stats>('/api/stats').then(setS).catch(() => setError('Не удалось загрузить метрики'));
  }, []);

  async function logout() {
    await api.logout();
    onLogout();
  }

  return (
    <>
      <header className="top">
        <div>
          <h1>Signet</h1>
          <p className="sub">Дашборд ответов на почту · метрики за сегодня и история</p>
        </div>
        <nav>
          {username && <span className="sub">{username}</span>}
          <Link to="/mail">Почта</Link>
          <Link to="/reviews">Ревью</Link>
          <Link to="/settings">⚙ Настройки</Link>
          <button className="ghost" onClick={logout}>Выйти</button>
        </nav>
      </header>

      {error && <p className="error" style={{ margin: '16px 28px' }}>{error}</p>}
      {!s && !error && <div className="center">Загрузка…</div>}

      {s && (
        <>
          <div className="grid">
            <Metric label="Принято сегодня" value={s.receivedToday} />
            <Metric label="Отправлено сегодня" value={s.sentToday} accent />
            <Metric label="Ждут ревью" value={s.pendingReview} />
            <Metric label="Доля правок" value={`${s.editRatePct} %`} accent />
            <Metric label="Одобрено" value={s.approvedTotal} />
            <Metric label="Отредактировано" value={s.editedTotal} />
            <Metric label="Отклонено" value={s.rejectedTotal} />
            <Metric label="Токены сегодня (in/out)" value={`${s.tokensInToday} / ${s.tokensOutToday}`} />
          </div>

          <div className="chart-wrap">
            <h2>Поток писем по дням (все ящики)</h2>
            <FlowChart stats={s} />
          </div>

          <div className="chart-wrap">
            <h2>По ящикам за сегодня</h2>
            <table>
              <thead>
                <tr>
                  <th>Ящик</th>
                  <th className="num">Принято</th>
                  <th className="num">Отправлено</th>
                  <th className="num">В очереди</th>
                  <th className="num">Токены (in/out)</th>
                </tr>
              </thead>
              <tbody>
                {s.perMailbox.map((m) => (
                  <tr key={m.id}>
                    <td>{m.label} ({m.id})</td>
                    <td className="num">{m.receivedToday}</td>
                    <td className="num">{m.sentToday}</td>
                    <td className="num">{m.pendingReview}</td>
                    <td className="num">{m.tokensInToday} / {m.tokensOutToday}</td>
                  </tr>
                ))}
                {s.perMailbox.length === 0 && (
                  <tr><td colSpan={5} style={{ color: 'var(--muted)' }}>Ящики не настроены</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}

function Metric({ label, value, accent }: { label: string; value: number | string; accent?: boolean }) {
  return (
    <div className="card">
      <div className="label">{label}</div>
      <div className={accent ? 'value accent' : 'value'}>{value}</div>
    </div>
  );
}

function FlowChart({ stats }: { stats: Stats }) {
  const labels = stats.history.map((d) => d.day);
  return (
    <Line
      height={90}
      data={{
        labels,
        datasets: [
          {
            label: 'Принято',
            data: stats.history.map((d) => d.received),
            borderColor: '#94a3b8',
            backgroundColor: 'rgba(148,163,184,.15)',
            tension: 0.3,
            fill: true,
          },
          {
            label: 'Отправлено',
            data: stats.history.map((d) => d.sent),
            borderColor: '#38bdf8',
            backgroundColor: 'rgba(56,189,248,.15)',
            tension: 0.3,
            fill: true,
          },
        ],
      }}
      options={{
        responsive: true,
        maintainAspectRatio: true,
        plugins: { legend: { labels: { color: '#e2e8f0' } } },
        scales: {
          x: { ticks: { color: '#94a3b8' }, grid: { color: '#334155' } },
          y: { ticks: { color: '#94a3b8' }, grid: { color: '#334155' }, beginAtZero: true },
        },
      }}
    />
  );
}
