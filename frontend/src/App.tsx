import { useState } from 'react';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import Dashboard from './pages/Dashboard';
import Companies from './pages/Companies';
import Services from './pages/Services';
import Assignments from './pages/Assignments';
import Territory from './pages/Territory';
import Placeholder from './pages/Placeholder';

export default function App() {
  const [active, setActive] = useState('Dashboard');
  const [userRevision, setUserRevision] = useState(0);

  const page = active === 'Dashboard'
    ? <Dashboard />
    : active === 'Territorio'
      ? <Territory />
    : active === 'Compañías'
      ? <Companies />
      : active === 'Servicios'
        ? <Services />
      : active === 'Asignaciones'
        ? <Assignments />
        : <Placeholder name={active} />;

  const title = active === 'Dashboard' ? 'Comando Operacional' : active;

  return (
    <div className="app">
      <Sidebar active={active} onChange={setActive} />
      <main>
        <Header title={title} onUserChange={() => setUserRevision((x) => x + 1)} />
        <section key={`${active}-${userRevision}`}>{page}</section>
      </main>
    </div>
  );
}
