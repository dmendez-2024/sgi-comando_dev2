import { useEffect, useState } from 'react';
import Sidebar, { SIMULATOR_MENU } from './components/Sidebar';
import { api } from './api';

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
import Placeholder from './pages/Placeholder';
import AgentSimulator from './pages/AgentSimulator';

export default function App() {
  const [active, setActive] = useState('Dashboard');
  const [userRevision, setUserRevision] = useState(0);
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

  const page = active === 'Dashboard'
    ? <Dashboard />
    : active === 'Territorio'
      ? <Territory />
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
      : active === 'Configuración'
        ? <SettingsPage />
      : active === SIMULATOR_MENU
        ? (uatTools ? <AgentSimulator /> : <Placeholder name="Herramienta UAT deshabilitada" />)
        : <Placeholder name={active} />;

  const title = active === 'Dashboard' ? 'Comando Operacional' : active;

  return (
    <div className="app">
      <Sidebar active={active} onChange={setActive} uatTools={uatTools} />
      <main>
        <Header title={title} coreContext={coreContext} onUserChange={() => {setCoreLoaded(false);setUserRevision((x) => x + 1)}} />
        {coreError&&<div className="core-bootstrap-message"><span>{coreError}</span><button onClick={()=>{setCoreLoaded(false);setContextReload(value=>value+1)}}>Reintentar conexión</button></div>}
        {coreContext?.coreSyncStatus==='STALE'&&<div className="core-bootstrap-message stale">Mostrando el último catálogo CORE sincronizado ({coreContext.territorialDatasetVersion}). {coreContext.coreSyncMessage||''}</div>}
        <section key={`${active}-${userRevision}-${coreContext?.coreCatalogSyncedAt||coreError}`}>{coreLoaded?page:<div className="core-bootstrap-message">Cargando contexto del país y catálogos de CORE…</div>}</section>
      </main>
    </div>
  );
}
