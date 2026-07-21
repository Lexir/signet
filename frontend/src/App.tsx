import { useCallback, useEffect, useState } from 'react';
import { Route, Routes, useNavigate } from 'react-router-dom';
import { api, UNAUTHORIZED_EVENT } from './api';
import Login from './views/Login';
import Dashboard from './views/Dashboard';
import Settings from './views/Settings';
import MailboxForm from './views/MailboxForm';
import Mail from './views/Mail';
import Reviews from './views/Reviews';

type Phase = 'loading' | 'in' | 'out';

export default function App() {
  const [phase, setPhase] = useState<Phase>('loading');
  const [username, setUsername] = useState('');

  const check = useCallback(async () => {
    const me = await api.me();
    setUsername(me.username);
    setPhase(me.authenticated ? 'in' : 'out');
  }, []);

  useEffect(() => {
    check();
    const onLost = () => setPhase('out');
    window.addEventListener(UNAUTHORIZED_EVENT, onLost);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onLost);
  }, [check]);

  if (phase === 'loading') {
    return <div className="center">Загрузка…</div>;
  }
  if (phase === 'out') {
    return <Login onLoggedIn={check} />;
  }

  return (
    <Routes>
      <Route path="/" element={<Dashboard username={username} onLogout={() => setPhase('out')} />} />
      <Route path="/mail" element={<Mail username={username} onLogout={() => setPhase('out')} />} />
      <Route path="/mail/:mailboxId" element={<Mail username={username} onLogout={() => setPhase('out')} />} />
      <Route path="/reviews" element={<Reviews username={username} onLogout={() => setPhase('out')} />} />
      <Route path="/settings" element={<Settings />} />
      <Route path="/mailbox" element={<MailboxForm />} />
      <Route path="/mailbox/:id" element={<MailboxForm />} />
      <Route path="*" element={<Redirector />} />
    </Routes>
  );
}

function Redirector() {
  const navigate = useNavigate();
  useEffect(() => { navigate('/', { replace: true }); }, [navigate]);
  return null;
}
