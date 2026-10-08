import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {
  AlertTriangle, ArrowLeft, BellRing, Building2, CalendarDays, ClipboardList, Clock3,
  Download, FileWarning, FilterX, MapPin, Monitor,
  RefreshCw, Save, Search, ShieldAlert, ShieldCheck, UserRound, Zap, CircleAlert, ChevronRight, Filter, ChevronDown, Plus, X
} from 'lucide-react';
import {api, getUser, type UatUser} from '../api';
import IncidentNotificationPanel, {type IncidentRecord, type LocationOption, type IncidentSeverity} from '../components/IncidentNotificationPanel';
import {OperationalDrawer} from '../components/OperationalDrawer';

type ItemCategory='CONSIGNAS'|'NOVEDADES'|'ALARMAS';
type ItemStatus='PENDING'|'IN_PROGRESS'|'OVERDUE'|'COMPLETED'|'APPROVED'|'DISCARDED'|'NEW'|'ACKNOWLEDGED'|'ESCALATED'|'RESOLVED'|'CRITICAL'|'DRAFT'|'FINALIZED';
type Priority='LOW'|'MEDIUM'|'HIGH'|'CRITICAL';

type ConsoleItem={
  id:string;
  category:ItemCategory;
  subtype:string;
  code:string;
  title:string;
  company:string;
  companyCode:string;
  companyId?:string;
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
type ClientOption={id:string;code:string;name:string};
type ServiceLocationRow={clientId:string;pointId:string;postId:string;companyId:string|null;companyName:string;clientName:string;pointName:string;postName:string};
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
const statusLabel=(value:ItemStatus)=>({PENDING:'Pendiente',IN_PROGRESS:'En curso',OVERDUE:'Vencida',COMPLETED:'Completada',APPROVED:'Aprobada',DISCARDED:'Descartada',NEW:'Nueva',ACKNOWLEDGED:'Reconocida',ESCALATED:'Escalada',RESOLVED:'Resuelta',CRITICAL:'Crítica',DRAFT:'Borrador',FINALIZED:'Finalizado'}[value]);
const statusClass=(value:ItemStatus)=>({PENDING:'warning',IN_PROGRESS:'info',OVERDUE:'danger',COMPLETED:'success',APPROVED:'success',DISCARDED:'neutral',NEW:'info',ACKNOWLEDGED:'info',ESCALATED:'warning',RESOLVED:'success',CRITICAL:'danger',DRAFT:'neutral',FINALIZED:'success'}[value]);
const priorityClass=(value:Priority)=>({LOW:'neutral',MEDIUM:'info',HIGH:'warning',CRITICAL:'danger'}[value]);
const priorityLabel=(value:Priority)=>({LOW:'Baja',MEDIUM:'Media',HIGH:'Alta',CRITICAL:'Crítica'}[value]);
const formatDateTime=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));
const dateOnly=(value:string)=>value.slice(0,10);
const unique=(values:string[])=>Array.from(new Set(values)).sort((a,b)=>a.localeCompare(b,'es'));
function withinScope(row:ConsoleItem,scope:Scope,allowedCompanyIds?:Set<string>){ if(row.id.startsWith('incident:'))return true; if(row.companyId)return allowedCompanyIds?.has(row.companyId)??false; if(scope.companies && !scope.companies.includes(row.companyCode)) return false; if(scope.zone && row.zone!==scope.zone) return false; if(scope.region && row.region!==scope.region) return false; return true; }
function validateRange(from:string,to:string){ if(!from||!to) return 'Debe elegir siempre una fecha inicio y una fecha fin.'; if(to<from) return 'La fecha fin no puede ser menor que la fecha inicio.'; const start=new Date(`${from}T00:00:00`); const max=new Date(start); max.setFullYear(max.getFullYear()+1); const end=new Date(`${to}T23:59:59`); return end>max?'El período consultado no puede ser mayor a 1 año.':''; }

const severityToPriority=(value:IncidentSeverity|''):Priority=>value==='CRITICAL'?'CRITICAL':value==='MAJOR'?'HIGH':value==='MODERATE'?'MEDIUM':'LOW';
const incidentToConsoleItem=(record:IncidentRecord):ConsoleItem=>({
  id:`incident:${record.id}`,category:'NOVEDADES',subtype:'Incidente',code:record.code,title:record.title||'Incidente sin título',
  company:record.company||'—',companyCode:record.companyCode||'',companyId:record.companyId,client:record.client||'—',city:record.city||'—',point:record.point||'—',post:record.post||'—',
  responsible:record.collaboratorNames.join(', ')||'Operador de Consola',zone:'Zona Costa',region:'Costa Sur',status:record.status,priority:severityToPriority(record.severity),
  createdAt:record.createdAt||record.updatedAt,updatedAt:record.updatedAt,summary:record.description||'Incidente en elaboración.',source:'NOV',originLabel:`Novedades · Incidentes · ${record.incidentType||record.subcategory||'Sin clasificar'}`,
  recommendedAction:record.status==='DRAFT'?'Completar y finalizar la notificación del incidente.':'Incidente finalizado; puede reabrirse para edición desde Consola.'
});

export default function ConsolaMonitor(){
  const user=getUser();
  const scope=scopeByUser[user];
  const [filters,setFilters]=useState<Filters>(EMPTY_FILTERS);
  const [selectedId,setSelectedId]=useState('');
  const [detailId,setDetailId]=useState('');
  const [filtersOpen,setFiltersOpen]=useState(false);
  const [incidents,setIncidents]=useState<IncidentRecord[]>([]);
  const [incidentsError,setIncidentsError]=useState('');
  const [incidentsRefresh,setIncidentsRefresh]=useState(0);
  const [incidentEditorOpen,setIncidentEditorOpen]=useState(false);
  const [editingIncidentId,setEditingIncidentId]=useState('');
  const [locationOptions,setLocationOptions]=useState<LocationOption[]>([]);
  const [locationsLoading,setLocationsLoading]=useState(true);
  const [locationsError,setLocationsError]=useState('');
  const [locationsRefresh,setLocationsRefresh]=useState(0);

  useEffect(()=>{
    let current=true;
    setIncidents([]);
    void api.incidents().then(records=>{if(current){setIncidents(records as IncidentRecord[]);setIncidentsError('')}})
      .catch(()=>{if(current)setIncidentsError('No se pudieron cargar los incidentes guardados en SGI: Comando.')});
    return ()=>{current=false};
  },[user,incidentsRefresh]);

  useEffect(()=>{
    let current=true;
    setLocationsLoading(true);
    setLocationsError('');
    void Promise.all([api.clients(),api.serviceOverview()]).then(([catalog,overview])=>{
      if(!current)return;
      const clients=new Map((catalog as ClientOption[]).map(client=>[client.id,client]));
      const seen=new Set<string>();
      const locations=(overview.rows as ServiceLocationRow[]).flatMap(row=>{
        const client=clients.get(row.clientId);
        if(!client||!row.postId||seen.has(row.postId))return [];
        seen.add(row.postId);
        return [{clientId:client.id,client:client.name,pointId:row.pointId,point:row.pointName,postId:row.postId,post:row.postName,companyId:row.companyId,company:row.companyName,companyCode:'',city:''}];
      }).sort((a,b)=>a.client.localeCompare(b.client,'es')||a.point.localeCompare(b.point,'es')||a.post.localeCompare(b.post,'es'));
      setLocationOptions(locations);
    }).catch(()=>{
      if(!current)return;
      setLocationOptions([]);
      setLocationsError('No se pudo cargar el catálogo de clientes y ubicaciones.');
    }).finally(()=>{if(current)setLocationsLoading(false)});
    return ()=>{current=false};
  },[user,locationsRefresh]);

  const incidentRows=useMemo(()=>incidents.map(incidentToConsoleItem),[incidents]);
  const allRows=useMemo(()=>[...DATA,...incidentRows],[incidentRows]);
  const allowedCompanyIds=useMemo(()=>new Set(locationOptions.map(x=>x.companyId).filter((id):id is string=>!!id)),[locationOptions]);
  const scoped=useMemo(()=>allRows.filter(x=>withinScope(x,scope,allowedCompanyIds)),[allRows,scope,allowedCompanyIds]);
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

  const inspected=detailId?rows.find(x=>x.id===detailId)??null:null;
  const editingIncident=editingIncidentId?incidents.find(x=>x.id===editingIncidentId)??null:null;
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

  function openNewIncident(){setDetailId('');setEditingIncidentId('');setIncidentEditorOpen(true);}
  function openRow(row:ConsoleItem){
    setSelectedId(row.id);
    setDetailId(row.id);setEditingIncidentId('');setIncidentEditorOpen(false);
  }
  async function saveIncident(record:IncidentRecord):Promise<IncidentRecord>{
    const normalized=await (record.id?api.updateIncident(record.id,record):api.createIncident(record)) as IncidentRecord;
    setIncidents(prev=>prev.some(x=>x.id===normalized.id)?prev.map(x=>x.id===normalized.id?normalized:x):[normalized,...prev]);
    setIncidentsError('');
    const rowId=`incident:${normalized.id}`;
    setSelectedId(rowId);
    if(normalized.status==='DRAFT'){
      setEditingIncidentId(normalized.id);
      return normalized;
    }
    setDetailId(rowId);
    setIncidentEditorOpen(false);
    setEditingIncidentId('');
    const today=normalized.updatedAt.slice(0,10);
    const start=new Date(`${today}T00:00:00`); start.setDate(start.getDate()-30);
    setFilters(prev=>({...prev,category:'ALL',query:'',status:'ALL',dateFrom:start.toISOString().slice(0,10),dateTo:today}));
    return normalized;
  }
  const nextIncidentCode=`INC-${new Date().getFullYear()}-${String(incidents.length+1).padStart(4,'0')}`;

  if(incidentEditorOpen) return <div className="csl-page csl-incident-page">
    <div className="ser-breadcrumbs"><button type="button" onClick={()=>{setIncidentEditorOpen(false);setEditingIncidentId('');setDetailId('')}}><ArrowLeft size={15}/>Volver a Consola</button><span>/</span><small>Notificación de Incidente</small></div>
    {incidentsError&&<div className="csl-incident-error"><AlertTriangle size={16}/>{incidentsError}<button type="button" onClick={()=>setIncidentsRefresh(value=>value+1)}>Reintentar</button></div>}
    <div className="ser-config-hero">
      <div><div className="ser-title-row"><h2>Notificar Incidente</h2><span>Registro operativo</span></div><small>Operaciones · Consola</small></div>
      <span className={`csl-incident-page-status ${editingIncident?.status.toLowerCase()??'new'}`}><i/>{editingIncident?statusLabel(editingIncident.status):'Nuevo'}</span>
    </div>
    <div className="ser-config-context panel csl-incident-context">
      <div><span>Código</span><strong>{editingIncident?.code??nextIncidentCode}</strong></div>
      <div><span>Estado</span><strong>{editingIncident?statusLabel(editingIncident.status):'Sin registrar'}</strong></div>
      <div><span>Origen</span><strong>Operaciones · Consola</strong></div>
      <div><span>Alcance</span><strong>{scope.label.replace('Alcance: ','')}</strong></div>
    </div>
    <IncidentNotificationPanel
      key={editingIncident?.id??`new-${nextIncidentCode}`}
      initial={editingIncident}
      nextCode={nextIncidentCode}
      locations={locationOptions}
      locationsLoading={locationsLoading}
      locationsError={locationsError}
      onRetryLocations={()=>setLocationsRefresh(value=>value+1)}
      onCancel={()=>{setIncidentEditorOpen(false);setEditingIncidentId('');setDetailId('')}}
      onSave={saveIncident}
    />
  </div>;

  return <div className="csl-page nov-page">
    <div className="csl-topbar nov-topbar">
      <div><div className="page-backline">Operaciones / Consola</div><h2>Consola</h2><p>Workspace operativo para Monitores: consignas, novedades, reasignaciones y alarmas electrónicas.</p></div>
      <div className="csl-scope nov-scope"><Monitor size={16}/>{scope.label}</div>
    </div>
    {incidentsError&&<div className="csl-incident-error"><AlertTriangle size={16}/>{incidentsError}<button type="button" onClick={()=>setIncidentsRefresh(value=>value+1)}>Reintentar</button></div>}

    <div className="csl-kpis coord-kpis nov-kpis">
      <Metric icon={<CircleAlert size={20}/>} label="Atención requerida" value={metrics.attention} subtitle="Casos priorizados" tone="red"/>
      <Metric icon={<RefreshCw size={20}/>} label="Reasignaciones activas" value={metrics.reassign} subtitle="Cobertura a resolver" tone="orange"/>
      <Metric icon={<ClipboardList size={20}/>} label="Consignas abiertas" value={metrics.consignas} subtitle="Relevos / patrullas / ad-hoc" tone="blue"/>
      <Metric icon={<FileWarning size={20}/>} label="Novedades pendientes" value={metrics.novedades} subtitle="Aprobación / descarte" tone="purple"/>
      <Metric icon={<BellRing size={20}/>} label="Alarmas activas" value={metrics.alarmas} subtitle="Nuevas / reconocidas / escaladas" tone="green"/>
      <Metric icon={<ShieldAlert size={20}/>} label="Críticas" value={metrics.critical} subtitle="Requieren acción inmediata" tone="red"/>
    </div>

    <div className="csl-workspace">
      <section className="csl-main-card nov-main-card">
        <div className="csl-card-head">
          <div className="csl-title"><Search size={20}/><h3>Bandeja operativa unificada</h3></div> {/* <img src="/assets/csl-incidents/incident.png" alt=""/> */}
          <div className="csl-head-actions">
            <div className="csl-tabs">
              <button className={filters.category==='ALL'?'active':''} onClick={()=>set('category','ALL')}>Todos</button>
              <button type="button" className="csl-notify-incident" onClick={openNewIncident}><span className="orange"><AlertTriangle size={14}/></span><span><Plus size={14}/>Notificar Incidente</span></button>
              <button className={filters.category==='CONSIGNAS'?'active':''} onClick={()=>set('category','CONSIGNAS')}><ClipboardList size={14}/>Consignas</button>
              <button className={filters.category==='NOVEDADES'?'active':''} onClick={()=>set('category','NOVEDADES')}><FileWarning size={14}/>Novedades</button>
              <button className={filters.category==='ALARMAS'?'active':''} onClick={()=>set('category','ALARMAS')}><BellRing size={14}/>Alarmas electrónicas</button>
            </div>
          </div>
        </div>
        <div className={`csl-filter-accordion ${filtersOpen?'open':''}`}>
          <button
            type="button"
            className="csl-filter-toggle"
            aria-expanded={filtersOpen}
            aria-controls="csl-search-filters"
            onClick={()=>setFiltersOpen(open=>!open)}
          >
            <span><Filter size={18}/>Filtros de búsqueda</span>
            <ChevronDown size={18} className="csl-filter-chevron" aria-hidden="true"/>
          </button>
          <div id="csl-search-filters" className="csl-filter-panel" aria-hidden={!filtersOpen}>
            <div className="csl-filter-panel-inner">
              <label className="csl-main-search nov-main-search"><Search size={18}/><input value={filters.query} onChange={e=>set('query',e.target.value)} placeholder="Buscar por código, título, cliente, punto, puesto o responsable…"/></label>

              <div className="csl-filter-grid">
                <Field label="Ciudad"><select value={filters.city} onChange={e=>set('city',e.target.value)}><option value="">Todas</option>{cities.map(x=><option key={x}>{x}</option>)}</select></Field>
                <Field label="Compañía"><select value={filters.company} onChange={e=>setCompany(e.target.value)}><option value="ALL">Todas</option>{companies.map(x=><option key={x}>{x}</option>)}</select></Field>
                <Field label="Cliente"><select value={filters.client} onChange={e=>setClient(e.target.value)}><option value="">Todos</option>{clients.map(x=><option key={x}>{x}</option>)}</select></Field>
                <Field label="Punto"><select value={filters.point} onChange={e=>setPoint(e.target.value)} disabled={!filters.client}><option value="">{filters.client?'Todos':'Seleccione un cliente primero'}</option>{points.map(x=><option key={x}>{x}</option>)}</select></Field>
                <Field label="Puesto"><select value={filters.post} onChange={e=>set('post',e.target.value)} disabled={!filters.point}><option value="">{filters.point?'Todos':'Seleccione un punto primero'}</option>{posts.map(x=><option key={x}>{x}</option>)}</select></Field>
                <Field label="Estado"><select value={filters.status} onChange={e=>set('status',e.target.value as Filters['status'])}><option value="ALL">Todos</option><option value="PENDING">Pendiente</option><option value="IN_PROGRESS">En curso</option><option value="OVERDUE">Vencida</option><option value="COMPLETED">Completada</option><option value="APPROVED">Aprobada</option><option value="DISCARDED">Descartada</option><option value="NEW">Nueva</option><option value="ACKNOWLEDGED">Reconocida</option><option value="ESCALATED">Escalada</option><option value="RESOLVED">Resuelta</option><option value="CRITICAL">Crítica</option><option value="DRAFT">Borrador</option><option value="FINALIZED">Finalizado</option></select></Field>
                <Field label="Responsable"><select value={filters.responsible} onChange={e=>set('responsible',e.target.value)} disabled={filters.company==='ALL'}><option value="">{filters.company!=='ALL'?'Todos':'Seleccione una compañía primero'}</option>{responsibles.map(x=><option key={x}>{x}</option>)}</select></Field>
                <Field label="Fecha inicio"><input type="date" value={filters.dateFrom} onChange={e=>set('dateFrom',e.target.value)}/></Field>
                <Field label="Fecha fin"><input type="date" value={filters.dateTo} onChange={e=>set('dateTo',e.target.value)}/></Field>
              </div>
              <div className="csl-rules-note"><CircleAlert size={15}/><span>Fechas obligatorias. Rango máximo: 1 año. Punto depende de Cliente; Puesto depende de Punto; Responsable depende de Compañía.</span></div>
              {dateError && <div className="csl-validation-error"><AlertTriangle size={16}/><span>{dateError}</span></div>}
              <div className="csl-search-actions"><button className="primary" disabled={!!dateError}><Search size={16}/>Buscar</button><button onClick={clear}><FilterX size={16}/>Limpiar filtros</button><button className="export" onClick={exportCsv} disabled={!!dateError || !rows.length}><Download size={16}/>Exportar</button></div>
            </div>
          </div>
        </div>

        <div className="csl-results-card nov-results-card">
          <div className="csl-results-head nov-results-head"><h3>Casos operativos ({rows.length})</h3><div>Ordenado por <strong>Última actualización</strong></div></div>
          <div className="csl-table-wrap nov-table-wrap">
            <table className="csl-table nov-table">
              <thead><tr><th>Categoría</th><th>Subtipo</th><th>Código</th><th>Título</th><th>Cliente</th><th>Punto / Puesto</th><th>Responsable</th><th>Estado</th><th>Prioridad</th><th>Última actualización</th><th></th></tr></thead>
              <tbody>
                {rows.map(row=><tr key={row.id} className={selectedId===row.id?'selected':''} onClick={()=>openRow(row)}>
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
                  <td><button className="csl-view-action" onClick={e=>{e.stopPropagation();openRow(row)}}>Ver <ChevronRight size={13}/></button></td>
                </tr>)}
                {!rows.length && <tr><td className="empty" colSpan={11}>{dateError?'Corrija el rango de fechas para consultar resultados.':'No se encontraron casos con los filtros aplicados.'}</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="csl-results-footer nov-results-footer">Mostrando {rows.length?1:0} a {rows.length} de {rows.length} resultados</div>
        </div>
      </section>

      {inspected&&<OperationalDrawer title={`${inspected.code} · ${categoryLabel(inspected.category)}`} subtitle={`${inspected.originLabel} · Detalle operativo`} onClose={()=>setDetailId('')} className="csl-view-modal" bodyClassName="csl-view-body" footer={<>
          {inspected.id.startsWith('incident:')&&<button type="button" onClick={()=>{setEditingIncidentId(inspected.id.slice('incident:'.length));setIncidentEditorOpen(true)}}><Save size={15}/>Editar incidente</button>}
          <button className="close" type="button" onClick={()=>setDetailId('')}><X size={15}/>Cerrar</button>
        </>}>
          <div className="csl-view-summary"><span className={`csl-category ${categoryClass(inspected.category)}`}>{categoryIcon(inspected.category)}{categoryLabel(inspected.category)}</span><strong>{inspected.title}</strong><span className={`csl-priority ${priorityClass(inspected.priority)}`}>{priorityLabel(inspected.priority)}</span><span className={`csl-status ${statusClass(inspected.status)}`}>{statusLabel(inspected.status)}</span></div>
          <dl className="csl-detail-list csl-view-details"><Detail icon={<Building2 size={14}/>} label="Compañía" value={inspected.company}/><Detail icon={<Building2 size={14}/>} label="Cliente" value={inspected.client}/><Detail icon={<MapPin size={14}/>} label="Ciudad" value={inspected.city}/><Detail icon={<MapPin size={14}/>} label="Punto" value={inspected.point}/><Detail icon={<MapPin size={14}/>} label="Puesto" value={inspected.post}/><Detail icon={<UserRound size={14}/>} label="Responsable" value={inspected.responsible}/><Detail icon={<ShieldCheck size={14}/>} label="Subtipo" value={inspected.subtype}/><Detail icon={<Zap size={14}/>} label="Origen" value={inspected.originLabel}/><Detail icon={<CalendarDays size={14}/>} label="Creado" value={formatDateTime(inspected.createdAt)}/><Detail icon={<Clock3 size={14}/>} label="Actualizado" value={formatDateTime(inspected.updatedAt)}/></dl>
          <section className="csl-view-note"><h4>Resumen operativo</h4><p>{inspected.summary}</p></section><section className="csl-view-note"><h4>Acción recomendada</h4><p>{inspected.recommendedAction}</p></section>
        </OperationalDrawer>}
    </div>
  </div>
}

function Metric({icon,label,value,subtitle,tone}:{icon:ReactNode;label:string;value:string|number;subtitle:string;tone:'blue'|'green'|'purple'|'red'|'orange'}){return <article><span className={tone}>{icon}</span><div><small>{label}</small><strong>{value}</strong><em>{subtitle}</em></div></article>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="csl-field"><span>{label}</span>{children}</label>}
function Detail({icon,label,value}:{icon:ReactNode;label:string;value:string}){return <div><dt>{icon}{label}</dt><dd>{value}</dd></div>}
