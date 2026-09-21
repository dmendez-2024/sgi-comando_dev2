import {useMemo, useState, type ReactNode} from 'react';
import {
  AlertTriangle, BellRing, Building2, CheckCircle2, ClipboardList,
  Download, Eye, FileWarning, FilterX, MapPin, Monitor,
  RefreshCw, Search, ShieldAlert, ShieldCheck, UserRound, Zap, CircleAlert
} from 'lucide-react';
import {getUser, type UatUser} from '../api';

type ItemCategory='CONSIGNAS'|'NOVEDADES'|'ALARMAS';
type ItemStatus='PENDING'|'IN_PROGRESS'|'OVERDUE'|'COMPLETED'|'APPROVED'|'DISCARDED'|'NEW'|'ACKNOWLEDGED'|'ESCALATED'|'RESOLVED'|'CRITICAL';
type Priority='LOW'|'MEDIUM'|'HIGH'|'CRITICAL';

type ConsoleItem={
  id:string;
  category:ItemCategory;
  subtype:string;
  code:string;
  title:string;
  company:string;
  companyCode:string;
  client:string;
  city:string;
  point:string;
  post:string;
  responsible:string;
  zone:string;
  region:string;
  status:ItemStatus;
  priority:Priority;
  createdAt:string;
  updatedAt:string;
  summary:string;
  source:'CNS'|'NOV'|'ALM';
  originLabel:string;
  recommendedAction:string;
};

type Scope={label:string;companies:string[]|null;region?:string;zone?:string};
type Filters={
  category:'ALL'|ItemCategory;
  query:string;
  city:string;
  company:string;
  client:string;
  point:string;
  post:string;
  status:'ALL'|ItemStatus;
  responsible:string;
  dateFrom:string;
  dateTo:string;
};

const DATA:ConsoleItem[]=[
  {id:'c1',category:'CONSIGNAS',subtype:'Relevo',code:'REV-2026-0412',title:'Relevo turno diurno',company:'Galvarino',companyCode:'GAL',client:'Maersk',city:'Guayaquil',point:'Portería Principal',post:'Control de Acceso Principal',responsible:'Luis García',zone:'Zona Costa',region:'Costa Sur',status:'IN_PROGRESS',priority:'MEDIUM',createdAt:'2026-09-19T08:00:00',updatedAt:'2026-09-19T08:12:00',summary:'2/4 pasos del relevo completados.',source:'CNS',originLabel:'Consignas · Relevos',recommendedAction:'Dar seguimiento al cierre del relevo.'},
  {id:'c2',category:'CONSIGNAS',subtype:'Patrulla',code:'PAT-2026-0842',title:'Ronda Centro Comercial',company:'Seguridad Integral S.A.',companyCode:'SISA',client:'Maersk',city:'Guayaquil',point:'Mall del Sol',post:'Patrulla Perimetral',responsible:'Ana Torres',zone:'Zona Costa',region:'Costa Sur',status:'OVERDUE',priority:'HIGH',createdAt:'2026-09-19T08:00:00',updatedAt:'2026-09-19T11:45:00',summary:'Patrulla con hitos pendientes fuera de ventana.',source:'CNS',originLabel:'Consignas · Patrullas',recommendedAction:'Contactar al responsable y verificar cobertura.'},
  {id:'c3',category:'CONSIGNAS',subtype:'Consigna ad-hoc',code:'ADH-2026-0126',title:'Apertura de bodega',company:'Galvarino',companyCode:'GAL',client:'DHL',city:'Durán',point:'Bodega 4',post:'Bodega 4',responsible:'Diego Moreira',zone:'Zona Costa',region:'Costa Sur',status:'PENDING',priority:'MEDIUM',createdAt:'2026-09-19T09:15:00',updatedAt:'2026-09-19T09:20:00',summary:'Consigna recibida y pendiente de completar acciones.',source:'CNS',originLabel:'Consignas · Ad-hoc',recommendedAction:'Monitorear avance y evidencias.'},
  {id:'c4',category:'NOVEDADES',subtype:'Hallazgo',code:'NOV-2026-0142',title:'Acumulación de basura en estacionamiento',company:'Galvarino',companyCode:'GAL',client:'Supermercados Andinos',city:'Guayaquil',point:'Local Centro',post:'Puesto Principal',responsible:'Luis Pérez',zone:'Zona Costa',region:'Costa Sur',status:'PENDING',priority:'LOW',createdAt:'2026-09-19T10:24:00',updatedAt:'2026-09-19T10:24:00',summary:'Hallazgo de limpieza pendiente de revisión.',source:'NOV',originLabel:'Novedades · Hallazgos',recommendedAction:'Revisar y aprobar o descartar.'},
  {id:'c5',category:'NOVEDADES',subtype:'Vulnerabilidad',code:'NOV-2026-0137',title:'Ingreso lateral sin control visual',company:'Galvarino',companyCode:'GAL',client:'Minera Los Andes',city:'Durán',point:'Planta Norte',post:'Control Principal',responsible:'María González',zone:'Zona Costa',region:'Costa Sur',status:'CRITICAL',priority:'CRITICAL',createdAt:'2026-09-19T08:48:00',updatedAt:'2026-09-19T08:48:00',summary:'Vulnerabilidad crítica de acceso pendiente de revisión.',source:'NOV',originLabel:'Novedades · Vulnerabilidades',recommendedAction:'Escalar y aprobar con prioridad.'},
  {id:'c6',category:'NOVEDADES',subtype:'Incidente',code:'NOV-2026-0135',title:'Persona agresiva intentó ingresar',company:'Galvarino',companyCode:'GAL',client:'Banco del Sur',city:'Guayaquil',point:'Sucursal Centro',post:'Puesto 1',responsible:'Diego Torres',zone:'Zona Costa',region:'Costa Sur',status:'PENDING',priority:'HIGH',createdAt:'2026-09-18T14:30:00',updatedAt:'2026-09-18T14:31:00',summary:'Incidente de seguridad en espera de aprobación.',source:'NOV',originLabel:'Novedades · Incidentes',recommendedAction:'Validar hechos y definir publicación al cliente.'},
  {id:'c7',category:'NOVEDADES',subtype:'Reasignación',code:'REA-2026-0048',title:'Falto en Control Patio 3',company:'Galvarino',companyCode:'GAL',client:'DP World',city:'Guayaquil',point:'Patio 3',post:'Control Patio 3',responsible:'Coordinación Galvarino',zone:'Zona Costa',region:'Costa Sur',status:'ESCALATED',priority:'HIGH',createdAt:'2026-09-19T05:52:00',updatedAt:'2026-09-19T06:03:00',summary:'Se requiere cobertura por ausencia de agente asignado.',source:'NOV',originLabel:'Novedades · Reasignaciones',recommendedAction:'Confirmar reemplazo y restablecer cobertura.'},
  {id:'c8',category:'ALARMAS',subtype:'Intrusión',code:'ALM-2026-0203',title:'Sensor perimetral activado',company:'Seguridad Integral S.A.',companyCode:'SISA',client:'TPG',city:'Guayaquil',point:'Acceso Sur',post:'Cerco Perimetral',responsible:'Operador de Consola 1',zone:'Zona Costa',region:'Costa Sur',status:'NEW',priority:'HIGH',createdAt:'2026-09-19T11:02:00',updatedAt:'2026-09-19T11:02:00',summary:'Alarma electrónica recién recibida desde panel perimetral.',source:'ALM',originLabel:'Alarmas electrónicas',recommendedAction:'Reconocer y verificar con puesto.'},
  {id:'c9',category:'ALARMAS',subtype:'Control de acceso',code:'ALM-2026-0198',title:'Puerta de cuarto técnico abierta',company:'Galvarino',companyCode:'GAL',client:'Bananera El Oro',city:'Machala',point:'Muelle Sur',post:'Cuarto Técnico',responsible:'Operador de Consola 2',zone:'Zona Costa',region:'Costa Sur',status:'ACKNOWLEDGED',priority:'MEDIUM',createdAt:'2026-09-19T10:41:00',updatedAt:'2026-09-19T10:43:00',summary:'Alarma reconocida, esperando confirmación desde el punto.',source:'ALM',originLabel:'Alarmas electrónicas',recommendedAction:'Dar seguimiento hasta resolución o incidente.'},
  {id:'c10',category:'ALARMAS',subtype:'CCTV',code:'ALM-2026-0189',title:'Pérdida de video en cámara de acceso',company:'Litoral Custodia',companyCode:'LIT',client:'Edificio Corporativo',city:'Guayaquil',point:'Torre A',post:'Sala de Control',responsible:'Operador de Consola 3',zone:'Zona Costa',region:'Costa Centro',status:'ESCALATED',priority:'HIGH',createdAt:'2026-09-19T09:25:00',updatedAt:'2026-09-19T09:44:00',summary:'Escalada a técnico por pérdida sostenida de video.',source:'ALM',originLabel:'Alarmas electrónicas',recommendedAction:'Esperar respuesta técnica y mantener vigilancia alterna.'},
  {id:'c11',category:'CONSIGNAS',subtype:'Reasignación',code:'REA-2026-0047',title:'Cobertura de turno noche',company:'Sur Custodia',companyCode:'SUR',client:'Bananera El Oro',city:'Machala',point:'Muelle Sur',post:'Acceso Muelle Sur',responsible:'Pedro Vera',zone:'Zona Costa',region:'Costa Sur',status:'COMPLETED',priority:'MEDIUM',createdAt:'2026-09-18T17:40:00',updatedAt:'2026-09-18T18:05:00',summary:'Reasignación resuelta y puesto cubierto.',source:'CNS',originLabel:'Consignas · Reasignaciones',recommendedAction:'Sin acción inmediata.'},
  {id:'c12',category:'NOVEDADES',subtype:'Hallazgo',code:'NOV-2026-0131',title:'Foco quemado en pasillo lateral',company:'Seguridad Integral S.A.',companyCode:'SISA',client:'Retail Plaza',city:'Guayaquil',point:'Local Este',post:'Puesto 2',responsible:'Carla Méndez',zone:'Zona Costa',region:'Costa Sur',status:'APPROVED',priority:'LOW',createdAt:'2026-09-18T09:10:00',updatedAt:'2026-09-18T09:35:00',summary:'Hallazgo aprobado y visible para el cliente.',source:'NOV',originLabel:'Novedades · Hallazgos',recommendedAction:'Monitorear cierre por parte del cliente.'},
];

const scopeByUser:Record<UatUser,Scope>={
  presidente:{label:'Alcance: nacional / todas las compañías',companies:null},
  dlatam:{label:'Alcance: nacional / todas las compañías',companies:null},
  don:{label:'Alcance: nacional / todas las compañías',companies:null},
  dnacional:{label:'Alcance: nacional / todas las compañías',companies:null},
  dzonal:{label:'Alcance: compañías / Zona Costa',companies:['GAL','SISA','LIT','SUR'],zone:'Zona Costa'},
  jregional:{label:'Alcance: compañías / Región Costa Sur',companies:['GAL','SISA','SUR'],region:'Costa Sur'},
  coord:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  asistente:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  supervisor:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  agente:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  cliente:{label:'Alcance restringido',companies:['GAL']},
};

const latestDate=new Date(DATA.reduce((m,x)=>x.updatedAt>m?x.updatedAt:m,DATA[0].updatedAt));
const DEFAULT_TO=latestDate.toISOString().slice(0,10);
const from=new Date(latestDate); from.setDate(from.getDate()-30);
const DEFAULT_FROM=from.toISOString().slice(0,10);
const EMPTY_FILTERS:Filters={category:'ALL',query:'',city:'',company:'ALL',client:'',point:'',post:'',status:'ALL',responsible:'',dateFrom:DEFAULT_FROM,dateTo:DEFAULT_TO};

const categoryLabel=(value:ItemCategory)=>value==='CONSIGNAS'?'Consignas':value==='NOVEDADES'?'Novedades':'Alarmas electrónicas';
const categoryClass=(value:ItemCategory)=>value==='CONSIGNAS'?'consignas':value==='NOVEDADES'?'novedades':'alarmas';
const categoryIcon=(value:ItemCategory)=>value==='CONSIGNAS'?<ClipboardList size={15}/>:value==='NOVEDADES'?<FileWarning size={15}/>:<BellRing size={15}/>;
const statusLabel=(value:ItemStatus)=>({PENDING:'Pendiente',IN_PROGRESS:'En curso',OVERDUE:'Vencida',COMPLETED:'Completada',APPROVED:'Aprobada',DISCARDED:'Descartada',NEW:'Nueva',ACKNOWLEDGED:'Reconocida',ESCALATED:'Escalada',RESOLVED:'Resuelta',CRITICAL:'Crítica'}[value]);
const statusClass=(value:ItemStatus)=>({PENDING:'warning',IN_PROGRESS:'info',OVERDUE:'danger',COMPLETED:'success',APPROVED:'success',DISCARDED:'neutral',NEW:'info',ACKNOWLEDGED:'info',ESCALATED:'warning',RESOLVED:'success',CRITICAL:'danger'}[value]);
const priorityClass=(value:Priority)=>({LOW:'neutral',MEDIUM:'info',HIGH:'warning',CRITICAL:'danger'}[value]);
const priorityLabel=(value:Priority)=>({LOW:'Baja',MEDIUM:'Media',HIGH:'Alta',CRITICAL:'Crítica'}[value]);
const formatDateTime=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));
const dateOnly=(value:string)=>value.slice(0,10);
const unique=(values:string[])=>Array.from(new Set(values)).sort((a,b)=>a.localeCompare(b,'es'));
function withinScope(row:ConsoleItem,scope:Scope){ if(scope.companies && !scope.companies.includes(row.companyCode)) return false; if(scope.zone && row.zone!==scope.zone) return false; if(scope.region && row.region!==scope.region) return false; return true; }
function validateRange(from:string,to:string){ if(!from||!to) return 'Debe elegir siempre una fecha inicio y una fecha fin.'; if(to<from) return 'La fecha fin no puede ser menor que la fecha inicio.'; const start=new Date(`${from}T00:00:00`); const max=new Date(start); max.setFullYear(max.getFullYear()+1); const end=new Date(`${to}T23:59:59`); return end>max?'El período consultado no puede ser mayor a 1 año.':''; }

export default function ConsolaMonitor(){
  const user=getUser();
  const scope=scopeByUser[user];
  const [filters,setFilters]=useState<Filters>(EMPTY_FILTERS);
  const [selectedId,setSelectedId]=useState('');

  const scoped=useMemo(()=>DATA.filter(x=>withinScope(x,scope)),[scope]);
  const cities=useMemo(()=>unique(scoped.map(x=>x.city)),[scoped]);
  const companies=useMemo(()=>unique(scoped.map(x=>x.company)),[scoped]);
  const clients=useMemo(()=>unique(scoped.map(x=>x.client)),[scoped]);
  const points=useMemo(()=>filters.client ? unique(scoped.filter(x=>x.client===filters.client).map(x=>x.point)) : [],[scoped,filters.client]);
  const posts=useMemo(()=>filters.point ? unique(scoped.filter(x=>x.point===filters.point).map(x=>x.post)) : [],[scoped,filters.point]);
  const responsibles=useMemo(()=>filters.company!=='ALL' ? unique(scoped.filter(x=>x.company===filters.company).map(x=>x.responsible)) : [],[scoped,filters.company]);
  const dateError=useMemo(()=>validateRange(filters.dateFrom,filters.dateTo),[filters.dateFrom,filters.dateTo]);

  const rows=useMemo(()=>{
    if(dateError) return [];
    return scoped.filter(row=>{
      if(filters.category!=='ALL' && row.category!==filters.category) return false;
      const q=filters.query.trim().toLowerCase();
      if(q && !`${row.code} ${row.title} ${row.client} ${row.point} ${row.post} ${row.responsible} ${row.summary} ${row.subtype}`.toLowerCase().includes(q)) return false;
      if(filters.city && row.city!==filters.city) return false;
      if(filters.company!=='ALL' && row.company!==filters.company) return false;
      if(filters.client && row.client!==filters.client) return false;
      if(filters.point && row.point!==filters.point) return false;
      if(filters.post && row.post!==filters.post) return false;
      if(filters.status!=='ALL' && row.status!==filters.status) return false;
      if(filters.responsible && row.responsible!==filters.responsible) return false;
      const d=dateOnly(row.updatedAt); if(d<filters.dateFrom || d>filters.dateTo) return false;
      return true;
    }).sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));
  },[scoped,filters,dateError]);

  const selected=rows.find(x=>x.id===selectedId) ?? rows[0] ?? null;
  const metrics=useMemo(()=>({
    attention: scoped.filter(x=>['PENDING','OVERDUE','NEW','CRITICAL','ESCALATED'].includes(x.status)).length,
    reassign: scoped.filter(x=>x.subtype==='Reasignación' && !['COMPLETED','RESOLVED','APPROVED','DISCARDED'].includes(x.status)).length,
    consignas: scoped.filter(x=>x.category==='CONSIGNAS' && ['PENDING','IN_PROGRESS','OVERDUE'].includes(x.status)).length,
    novedades: scoped.filter(x=>x.category==='NOVEDADES' && ['PENDING','CRITICAL','ESCALATED'].includes(x.status)).length,
    alarmas: scoped.filter(x=>x.category==='ALARMAS' && ['NEW','ACKNOWLEDGED','ESCALATED','CRITICAL'].includes(x.status)).length,
    critical: scoped.filter(x=>x.priority==='CRITICAL' || x.status==='CRITICAL').length,
  }),[scoped]);

  function set<K extends keyof Filters>(key:K,value:Filters[K]){ setFilters(prev=>({...prev,[key]:value})); }
  function setClient(value:string){ setFilters(prev=>({...prev,client:value,point:'',post:''})); }
  function setPoint(value:string){ setFilters(prev=>({...prev,point:value,post:''})); }
  function setCompany(value:string){ setFilters(prev=>({...prev,company:value,responsible:''})); }
  function clear(){ setFilters(EMPTY_FILTERS); }
  function exportCsv(){
    const header=['Categoría','Subtipo','Código','Título','Cliente','Compañía','Ciudad','Punto','Puesto','Responsable','Estado','Prioridad','Última actualización'];
    const body=rows.map(r=>[categoryLabel(r.category),r.subtype,r.code,r.title,r.client,r.company,r.city,r.point,r.post,r.responsible,statusLabel(r.status),priorityLabel(r.priority),formatDateTime(r.updatedAt)]);
    const csv=[header,...body].map(line=>line.map(v=>`"${String(v).replaceAll('"','""')}"`).join(',')).join('\n');
    const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'}); const url=URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download='consola_operativa.csv'; a.click(); URL.revokeObjectURL(url);
  }

  return <div className="csl-page">
    <div className="csl-topbar">
      <div><div className="page-backline">Operaciones / Consola</div><h2>Consola</h2><p>Workspace operativo para Monitores: consignas, novedades, reasignaciones y alarmas electrónicas.</p></div>
      <div className="csl-scope"><Monitor size={16}/>{scope.label}</div>
    </div>

    <div className="csl-kpis">
      <Metric icon={<CircleAlert size={20}/>} label="Atención requerida" value={metrics.attention} subtitle="Casos priorizados" tone="red"/>
      <Metric icon={<RefreshCw size={20}/>} label="Reasignaciones activas" value={metrics.reassign} subtitle="Cobertura a resolver" tone="orange"/>
      <Metric icon={<ClipboardList size={20}/>} label="Consignas abiertas" value={metrics.consignas} subtitle="Relevos / patrullas / ad-hoc" tone="blue"/>
      <Metric icon={<FileWarning size={20}/>} label="Novedades pendientes" value={metrics.novedades} subtitle="Aprobación / descarte" tone="purple"/>
      <Metric icon={<BellRing size={20}/>} label="Alarmas activas" value={metrics.alarmas} subtitle="Nuevas / reconocidas / escaladas" tone="green"/>
      <Metric icon={<ShieldAlert size={20}/>} label="Críticas" value={metrics.critical} subtitle="Requieren acción inmediata" tone="red"/>
    </div>

    <div className="csl-workspace">
      <section className="csl-main-card">
        <div className="csl-card-head">
          <div className="csl-title"><Search size={20}/><h3>Bandeja operativa unificada</h3></div>
          <div className="csl-tabs">
            <button className={filters.category==='ALL'?'active':''} onClick={()=>set('category','ALL')}>Todos</button>
            <button className={filters.category==='CONSIGNAS'?'active':''} onClick={()=>set('category','CONSIGNAS')}><ClipboardList size={14}/>Consignas</button>
            <button className={filters.category==='NOVEDADES'?'active':''} onClick={()=>set('category','NOVEDADES')}><FileWarning size={14}/>Novedades</button>
            <button className={filters.category==='ALARMAS'?'active':''} onClick={()=>set('category','ALARMAS')}><BellRing size={14}/>Alarmas electrónicas</button>
          </div>
        </div>
        <label className="csl-main-search"><Search size={18}/><input value={filters.query} onChange={e=>set('query',e.target.value)} placeholder="Buscar por código, título, cliente, punto, puesto o responsable…"/></label>

        <div className="csl-filter-grid">
          <Field label="Ciudad"><select value={filters.city} onChange={e=>set('city',e.target.value)}><option value="">Todas</option>{cities.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Compañía"><select value={filters.company} onChange={e=>setCompany(e.target.value)}><option value="ALL">Todas</option>{companies.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Cliente"><select value={filters.client} onChange={e=>setClient(e.target.value)}><option value="">Todos</option>{clients.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Punto"><select value={filters.point} onChange={e=>setPoint(e.target.value)} disabled={!filters.client}><option value="">{filters.client?'Todos':'Seleccione un cliente primero'}</option>{points.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Puesto"><select value={filters.post} onChange={e=>set('post',e.target.value)} disabled={!filters.point}><option value="">{filters.point?'Todos':'Seleccione un punto primero'}</option>{posts.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Estado"><select value={filters.status} onChange={e=>set('status',e.target.value as Filters['status'])}><option value="ALL">Todos</option><option value="PENDING">Pendiente</option><option value="IN_PROGRESS">En curso</option><option value="OVERDUE">Vencida</option><option value="COMPLETED">Completada</option><option value="APPROVED">Aprobada</option><option value="DISCARDED">Descartada</option><option value="NEW">Nueva</option><option value="ACKNOWLEDGED">Reconocida</option><option value="ESCALATED">Escalada</option><option value="RESOLVED">Resuelta</option><option value="CRITICAL">Crítica</option></select></Field>
          <Field label="Responsable"><select value={filters.responsible} onChange={e=>set('responsible',e.target.value)} disabled={filters.company==='ALL'}><option value="">{filters.company!=='ALL'?'Todos':'Seleccione una compañía primero'}</option>{responsibles.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Fecha inicio"><input type="date" value={filters.dateFrom} onChange={e=>set('dateFrom',e.target.value)}/></Field>
          <Field label="Fecha fin"><input type="date" value={filters.dateTo} onChange={e=>set('dateTo',e.target.value)}/></Field>
        </div>
        <div className="csl-rules-note"><CircleAlert size={15}/><span>Fechas obligatorias. Rango máximo: 1 año. Punto depende de Cliente; Puesto depende de Punto; Responsable depende de Compañía.</span></div>
        {dateError && <div className="csl-validation-error"><AlertTriangle size={16}/><span>{dateError}</span></div>}
        <div className="csl-search-actions"><button className="primary" disabled={!!dateError}><Search size={16}/>Buscar</button><button onClick={clear}><FilterX size={16}/>Limpiar filtros</button><button className="export" onClick={exportCsv} disabled={!!dateError || !rows.length}><Download size={16}/>Exportar</button></div>

        <div className="csl-results-card">
          <div className="csl-results-head"><h3>Casos operativos ({rows.length})</h3><div>Ordenar por: <strong>Última actualización</strong></div></div>
          <div className="csl-table-wrap">
            <table className="csl-table">
              <thead><tr><th>Categoría</th><th>Subtipo</th><th>Código</th><th>Título</th><th>Cliente</th><th>Punto / Puesto</th><th>Responsable</th><th>Estado</th><th>Prioridad</th><th>Última actualización</th><th></th></tr></thead>
              <tbody>
                {rows.map(row=><tr key={row.id} className={selected?.id===row.id?'selected':''} onClick={()=>setSelectedId(row.id)}>
                  <td><span className={`csl-category ${categoryClass(row.category)}`}>{categoryIcon(row.category)}{categoryLabel(row.category)}</span></td>
                  <td>{row.subtype}</td>
                  <td>{row.code}</td>
                  <td><strong>{row.title}</strong><small>{row.originLabel}</small></td>
                  <td>{row.client}</td>
                  <td><strong>{row.point}</strong><small>{row.post}</small></td>
                  <td>{row.responsible}</td>
                  <td><span className={`csl-status ${statusClass(row.status)}`}>{statusLabel(row.status)}</span></td>
                  <td><span className={`csl-priority ${priorityClass(row.priority)}`}>{priorityLabel(row.priority)}</span></td>
                  <td>{formatDateTime(row.updatedAt)}</td>
                  <td><button>Ver</button></td>
                </tr>)}
                {!rows.length && <tr><td className="empty" colSpan={11}>{dateError?'Corrija el rango de fechas para consultar resultados.':'No se encontraron casos con los filtros aplicados.'}</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="csl-results-footer">Mostrando {rows.length?1:0} a {rows.length} de {rows.length} resultados</div>
        </div>
      </section>

      <aside className="csl-detail-card">
        <div className="csl-title"><Eye size={19}/><h3>Detalle</h3></div>
        {selected ? <>
          <div className="csl-detail-head">
            <span className={`csl-category ${categoryClass(selected.category)}`}>{categoryIcon(selected.category)}{categoryLabel(selected.category)}</span>
            <div><strong>{selected.code}</strong><h4>{selected.title}</h4></div>
            <span className={`csl-status ${statusClass(selected.status)}`}>{statusLabel(selected.status)}</span>
          </div>
          <dl className="csl-detail-list">
            <Detail icon={<Building2 size={14}/>} label="Compañía" value={selected.company}/>
            <Detail icon={<Building2 size={14}/>} label="Cliente" value={selected.client}/>
            <Detail icon={<MapPin size={14}/>} label="Ciudad" value={selected.city}/>
            <Detail icon={<MapPin size={14}/>} label="Punto" value={selected.point}/>
            <Detail icon={<MapPin size={14}/>} label="Puesto" value={selected.post}/>
            <Detail icon={<UserRound size={14}/>} label="Responsable" value={selected.responsible}/>
            <Detail icon={<ShieldCheck size={14}/>} label="Subtipo" value={selected.subtype}/>
            <Detail icon={<ShieldAlert size={14}/>} label="Prioridad" value={priorityLabel(selected.priority)}/>
            <Detail icon={<Zap size={14}/>} label="Origen" value={selected.originLabel}/>
            <Detail icon={<CheckCircle2 size={14}/>} label="Estado" value={statusLabel(selected.status)}/>
          </dl>
          <div className="csl-detail-text"><h4>Resumen operativo</h4><p>{selected.summary}</p></div>
          <div className="csl-detail-text"><h4>Acción recomendada</h4><p>{selected.recommendedAction}</p></div>
          <div className="csl-detail-meta">
            <div><span>Creado</span><strong>{formatDateTime(selected.createdAt)}</strong></div>
            <div><span>Última actualización</span><strong>{formatDateTime(selected.updatedAt)}</strong></div>
          </div>
          <div className="csl-actions">
            {selected.category==='CONSIGNAS' && <><button className="primary">Abrir Consignas</button><button>Ver ejecución</button></>}
            {selected.category==='NOVEDADES' && <><button className="primary">Abrir Novedades</button><button>{selected.subtype==='Reasignación'?'Gestionar reasignación':'Revisar novedad'}</button></>}
            {selected.category==='ALARMAS' && <><button className="primary">Reconocer alarma</button><button>Escalar</button><button>Cerrar</button></>}
          </div>
        </> : <div className="csl-empty">Seleccione un caso para ver su detalle.</div>}
      </aside>
    </div>
  </div>
}

function Metric({icon,label,value,subtitle,tone}:{icon:ReactNode;label:string;value:string|number;subtitle:string;tone:'blue'|'green'|'purple'|'red'|'orange'}){return <article className={`csl-metric ${tone}`}><span>{icon}</span><div><small>{label}</small><strong>{value}</strong><p>{subtitle}</p></div></article>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="csl-field"><span>{label}</span>{children}</label>}
function Detail({icon,label,value}:{icon:ReactNode;label:string;value:string}){return <div><dt>{icon}{label}</dt><dd>{value}</dd></div>}
