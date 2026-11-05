import { useEffect, useState } from 'react';
import Sidebar, { SIMULATOR_MENU } from './components/Sidebar';
import { api, isMonitor } from './api';
import { installBackGuard } from './security/identAuth';

import Header from './components/Header';
import Dashboard from './pages/Dashboard';
import Companies from './pages/Companies';
import Services from './pages/Services';
import SettingsPage from './pages/SettingsPage';
import Coordination from './pages/Coordination';
import Assignments from './pages/Assignments';
import Territory from './pages/Territory';
import BitacoraGlobal from './pages/BitacoraGlobal';
import ConsolaMonitor from './pages/ConsolaMonitor';
import ConsignasExecution from './pages/ConsignasExecution';
import NovedadesExecution from './pages/NovedadesExecution';
import Supervisiones from './pages/Supervisiones';
import Placeholder from './pages/Placeholder';
import AgentSimulator from './pages/AgentSimulator';
import UniformColumnBadges from './components/UniformColumnBadges';
import './responsive.css';

export default function App() {
  // El monitorista (rol MONITOR) entra directo a la Consola; el resto, al Dashboard.
  const home = () => isMonitor() ? 'Consola' : 'Dashboard';
  const [active, setActive] = useState(home);
  // "Atras" del navegador con la sesion abierta: se queda en SGI y vuelve al inicio.
  useEffect(() => { installBackGuard(() => setActive(home())); }, []);
  const [userRevision, setUserRevision] = useState(0);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [uatTools, setUatTools] = useState(false);
  useEffect(() => { api.features().then(f => setUatTools(f.uatTools)).catch(() => setUatTools(false)); }, [userRevision]);
  const [contextReload, setContextReload] = useState(0);
  const [coreContext, setCoreContext] = useState<any>(null);
  const [coreError, setCoreError] = useState('');
  const [coreLoaded, setCoreLoaded] = useState(false);

  useEffect(() => {
    let current = true;
    setCoreLoaded(false);
    setCoreContext(null);
    setCoreError('');
    void api.context().then(context => {
      if (current) { setCoreContext(context); setCoreLoaded(true); }
    }).catch(error => {
      if (current) { setCoreError(error instanceof Error ? error.message : 'No se pudo cargar el contexto de CORE.'); setCoreLoaded(true); }
    });
    return () => { current = false; };
  }, [userRevision,contextReload]);

  const monitor = isMonitor();
  const navigate = (next:string) => { setActive(next); setMobileNavOpen(false); };

  useEffect(() => {
    document.body.classList.toggle('mobile-nav-open', mobileNavOpen);
    const closeOnEscape = (event:KeyboardEvent) => { if (event.key === 'Escape') setMobileNavOpen(false); };
    const closeOnDesktop = () => { if (window.innerWidth > 700) setMobileNavOpen(false); };
    window.addEventListener('keydown', closeOnEscape);
    window.addEventListener('resize', closeOnDesktop);
    return () => {
      document.body.classList.remove('mobile-nav-open');
      window.removeEventListener('keydown', closeOnEscape);
      window.removeEventListener('resize', closeOnDesktop);
    };
  }, [mobileNavOpen]);

  const page = monitor
    ? <ConsolaMonitor />
    : active === 'Dashboard'
    ? <Dashboard />
    : active === 'Territorio'
      ? <Territory coreContext={coreContext} />
    : active === 'Compañías'
      ? <Companies />
      : active === 'Servicios'
        ? <Services />
      : active === 'Coordinación'
        ? <Coordination />
      : active === 'Asignaciones'
        ? <Assignments />
      : active === 'Consola'
        ? <ConsolaMonitor />
      : active === 'Bitácora'
        ? <BitacoraGlobal />
      : active === 'Consignas'
        ? <ConsignasExecution />
      : active === 'Novedades'
        ? <NovedadesExecution />
      : active === 'Supervisiones'
        ? <Supervisiones />
      : active === 'Configuración'
        ? <SettingsPage />
      : active === SIMULATOR_MENU
        ? (uatTools ? <AgentSimulator /> : <Placeholder name="Herramienta UAT deshabilitada" />)
        : <Placeholder name={active} />;

  const title = monitor ? 'Consola' : active === 'Dashboard' ? 'Comando Operacional' : active;

  return (
    <div className="app">
      <UniformColumnBadges />
      <Sidebar active={monitor ? 'Consola' : active} onChange={navigate} uatTools={uatTools} monitorOnly={monitor} open={mobileNavOpen} onClose={() => setMobileNavOpen(false)} />
      <button className={`mobile-nav-backdrop ${mobileNavOpen ? 'open' : ''}`} type="button" aria-label="Cerrar menú de navegación" onClick={() => setMobileNavOpen(false)} />
      <main>
        <Header title={title} coreContext={coreContext} navOpen={mobileNavOpen} onMenuToggle={() => setMobileNavOpen(value => !value)} onUserChange={() => {setCoreLoaded(false);setActive(home());setUserRevision((x) => x + 1)}} />
        {coreError&&<div className="core-bootstrap-message"><span>{coreError}</span><button onClick={()=>{setCoreLoaded(false);setContextReload(value=>value+1)}}>Reintentar conexión</button></div>}
        {coreContext?.coreSyncStatus==='STALE'&&<div className="core-bootstrap-message stale">Mostrando el último catálogo CORE sincronizado ({coreContext.territorialDatasetVersion}). {coreContext.coreSyncMessage||''}</div>}
        <section key={`${active}-${userRevision}-${coreContext?.coreCatalogSyncedAt||coreError}`}>{coreLoaded?page:<div className="core-bootstrap-message">Cargando contexto del país y catálogos de CORE…</div>}</section>
      </main>
    </div>
  );
}
