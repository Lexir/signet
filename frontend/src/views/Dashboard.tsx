import { useEffect, useState } from 'react';
import { Line } from 'react-chartjs-2';
import {
  CategoryScale, Chart, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip,
} from 'chart.js';
import { api, Stats } from '../api';
import Shell from '../components/Shell';

Chart.register(CategoryScale, LinearScale, PointElement, LineElement, Filler, Tooltip, Legend);

export default function Dashboard({ username, onLogout }: { username: string; onLogout: () => void }) {
  const [s, setS] = useState<Stats | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.getJson<Stats>('/api/stats').then(setS).catch(() => setError('Не удалось загрузить метрики'));
  }, []);

  return (
    <Shell
      title="Дашборд"
      sub="Метрики ответов на почту за сегодня и история"
      username={username}
      onLogout={onLogout}
    >
      {error && <p className="error">{error}</p>}
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
    </Shell>
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
            borderColor: '#8f8f9c',
            backgroundColor: 'rgba(143,143,156,.1)',
            tension: 0.35,
            fill: true,
            pointRadius: 2,
          },
          {
            label: 'Отправлено',
            data: stats.history.map((d) => d.sent),
            borderColor: '#a78bfa',
            backgroundColor: 'rgba(139,92,246,.14)',
            tension: 0.35,
            fill: true,
            pointRadius: 2,
          },
        ],
      }}
      options={{
        responsive: true,
        maintainAspectRatio: true,
        plugins: { legend: { labels: { color: '#ededf0', usePointStyle: true, boxHeight: 6 } } },
        scales: {
          x: { ticks: { color: '#8f8f9c' }, grid: { color: 'rgba(255,255,255,.05)' } },
          y: { ticks: { color: '#8f8f9c' }, grid: { color: 'rgba(255,255,255,.05)' }, beginAtZero: true },
        },
      }}
    />
  );
}
