import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {
  AlertTriangle, CheckCircle2, ClipboardList, Clock3, Download,
  FileText, FilterX, MapPin, RefreshCw, Search, ShieldCheck, UserRound,
  Building2, Briefcase, CarFront, AlertCircle
} from 'lucide-react';
import {getUser, type UatUser} from '../api';

type ExecutionType='RELIEF'|'PATROL'|'ADHOC';
type ExecutionStatus='IN_PROGRESS'|'COMPLETED'|'OVERDUE'|'PENDING'|'CRITICAL';
type Priority='LOW'|'MEDIUM'|'HIGH'|'CRITICAL';

type TimelineEvent={id:string;at:string;title:string;detail:string;state:'DONE'|'ACTIVE'|'PENDING'};
type Execution={
  id:string;
  type:ExecutionType;
  code:string;
  name:string;
  client:string;
  company:string;
  companyCode:string;
  city:string;
  point:string;
  post:string;
  responsible:string;
  zone:string;
  region:string;
  status:ExecutionStatus;
  priority:Priority;
  start:string;
  end:string;
  progressCurrent:number;
  progressTotal:number;
  progressUnit:string;
  result:string;
  timeline:TimelineEvent[];
};

type Scope={label:string;companies:string[]|null;region?:string;zone?:string;monitoredPosts:number};
type Filters={
  type:'ALL'|ExecutionType;
  query:string;
  company:string;
  client:string;
  city:string;
  point:string;
  post:string;
  status:'ALL'|ExecutionStatus;
  responsible:string;
  dateFrom:string;
  dateTo:string;
};

const DATA:Execution[]=[
  {id:'e1',type:'RELIEF',code:'REV-2026-0412',name:'Relevo turno diurno',client:'Maersk',company:'Galvarino',companyCode:'GAL',city:'Guayaquil',point:'Portería Principal',post:'Control de Acceso Principal',responsible:'Luis García',zone:'Zona Costa',region:'Costa Sur',status:'IN_PROGRESS',priority:'MEDIUM',start:'2026-09-19T08:00:00',end:'2026-09-19T08:30:00',progressCurrent:2,progressTotal:4,progressUnit:'pasos',result:'2/4 pasos',timeline:[
    {id:'t1',at:'2026-09-19T08:00:00',title:'Llegada del personal entrante',detail:'Luis García · Completado',state:'DONE'},
    {id:'t2',at:'2026-09-19T08:05:00',title:'Entrega de novedades',detail:'Completado',state:'DONE'},
    {id:'t3',at:'2026-09-19T08:15:00',title:'Revisión de instalaciones',detail:'En proceso…',state:'ACTIVE'},
    {id:'t4',at:'2026-09-19T08:30:00',title:'Cierre de relevo',detail:'Pendiente',state:'PENDING'}]},
  {id:'e2',type:'PATROL',code:'PAT-2026-0842',name:'Ronda Centro Comercial',client:'Maersk',company:'Seguridad Integral S.A.',companyCode:'SISA',city:'Guayaquil',point:'Mall del Sol',post:'Patrulla Perimetral',responsible:'Ana Torres',zone:'Zona Costa',region:'Costa Sur',status:'IN_PROGRESS',priority:'MEDIUM',start:'2026-09-19T08:00:00',end:'2026-09-19T16:00:00',progressCurrent:7,progressTotal:10,progressUnit:'hitos',result:'7/10 hitos',timeline:[
    {id:'t1',at:'2026-09-19T08:00:00',title:'Inicio de patrulla',detail:'Patrulla iniciada por Ana Torres',state:'DONE'},
    {id:'t2',at:'2026-09-19T08:15:00',title:'Hito 1 completado',detail:'Acceso principal',state:'DONE'},
    {id:'t3',at:'2026-09-19T11:45:00',title:'GPS validado',detail:'Ubicación dentro del rango permitido',state:'ACTIVE'},
    {id:'t4',at:'2026-09-19T16:00:00',title:'Hitos restantes',detail:'3 pendientes',state:'PENDING'}]},
  {id:'e3',type:'ADHOC',code:'ADH-2026-0127',name:'Verificación de acceso',client:'Contecon',company:'Litoral Custodia',companyCode:'LIT',city:'Guayaquil',point:'Puerta 3',post:'Acceso Vehicular 3',responsible:'Carlos Ruiz',zone:'Zona Costa',region:'Costa Centro',status:'COMPLETED',priority:'HIGH',start:'2026-09-19T11:00:00',end:'2026-09-19T14:00:00',progressCurrent:3,progressTotal:3,progressUnit:'acciones',result:'Con evidencia',timeline:[
    {id:'t1',at:'2026-09-19T11:00:00',title:'Consigna recibida',detail:'Lectura confirmada',state:'DONE'},
    {id:'t2',at:'2026-09-19T11:20:00',title:'Verificación ejecutada',detail:'Acceso validado',state:'DONE'},
    {id:'t3',at:'2026-09-19T11:25:00',title:'Evidencia cargada',detail:'Fotografía registrada',state:'DONE'}]},
  {id:'e4',type:'RELIEF',code:'REV-2026-0411',name:'Relevo turno nocturno',client:'DP World',company:'Galvarino',companyCode:'GAL',city:'Guayaquil',point:'Patio 3',post:'Control Patio 3',responsible:'Miguel Castro',zone:'Zona Costa',region:'Costa Sur',status:'OVERDUE',priority:'HIGH',start:'2026-09-19T06:00:00',end:'2026-09-19T06:30:00',progressCurrent:1,progressTotal:4,progressUnit:'pasos',result:'1/4 pasos',timeline:[
    {id:'t1',at:'2026-09-19T06:00:00',title:'Inicio de relevo',detail:'Registrado',state:'DONE'},
    {id:'t2',at:'2026-09-19T06:30:00',title:'Tiempo objetivo vencido',detail:'Faltan 3 pasos',state:'ACTIVE'}]},
  {id:'e5',type:'PATROL',code:'PAT-2026-0829',name:'Ronda Perimetral',client:'TPG',company:'Seguridad Integral S.A.',companyCode:'SISA',city:'Guayaquil',point:'Báscula',post:'Perímetro Norte',responsible:'Fernanda López',zone:'Zona Costa',region:'Costa Sur',status:'COMPLETED',priority:'MEDIUM',start:'2026-09-19T06:00:00',end:'2026-09-19T14:00:00',progressCurrent:10,progressTotal:10,progressUnit:'hitos',result:'10/10 hitos',timeline:[
    {id:'t1',at:'2026-09-19T06:00:00',title:'Inicio de patrulla',detail:'Completado',state:'DONE'},
    {id:'t2',at:'2026-09-19T13:42:00',title:'Último hito',detail:'Hito 10 validado',state:'DONE'},
    {id:'t3',at:'2026-09-19T13:45:00',title:'Cierre de patrulla',detail:'Completado',state:'DONE'}]},
  {id:'e6',type:'ADHOC',code:'ADH-2026-0126',name:'Apertura de bodega',client:'DHL',company:'Galvarino',companyCode:'GAL',city:'Durán',point:'Bodega 4',post:'Bodega 4',responsible:'Diego Moreira',zone:'Zona Costa',region:'Costa Sur',status:'IN_PROGRESS',priority:'MEDIUM',start:'2026-09-19T09:15:00',end:'2026-09-19T12:00:00',progressCurrent:3,progressTotal:5,progressUnit:'acciones',result:'3/5 acciones',timeline:[
    {id:'t1',at:'2026-09-19T09:15:00',title:'Consigna recibida',detail:'Confirmada por Diego Moreira',state:'DONE'},
    {id:'t2',at:'2026-09-19T09:42:00',title:'Apertura iniciada',detail:'Registro fotográfico cargado',state:'DONE'},
    {id:'t3',at:'2026-09-19T10:05:00',title:'Validación de inventario',detail:'En proceso',state:'ACTIVE'}]},
  {id:'e7',type:'PATROL',code:'PAT-2026-0827',name:'Ronda Zona Norte',client:'MSC',company:'Galvarino',companyCode:'GAL',city:'Guayaquil',point:'Patio B',post:'Patio B · Guardia 1',responsible:'Sofía Ramírez',zone:'Zona Costa',region:'Costa Sur',status:'PENDING',priority:'MEDIUM',start:'2026-09-19T14:00:00',end:'2026-09-19T22:00:00',progressCurrent:0,progressTotal:10,progressUnit:'hitos',result:'0/10 hitos',timeline:[
    {id:'t1',at:'2026-09-19T14:00:00',title:'Inicio programado',detail:'Pendiente de inicio',state:'PENDING'}]},
  {id:'e8',type:'ADHOC',code:'ADH-2026-0125',name:'Revisión de contenedor',client:'Maersk',company:'Galvarino',companyCode:'GAL',city:'Posorja',point:'Patio A',post:'Inspección Patio A',responsible:'Sofía Ramírez',zone:'Zona Costa',region:'Costa Sur',status:'CRITICAL',priority:'CRITICAL',start:'2026-09-19T10:30:00',end:'2026-09-19T13:00:00',progressCurrent:1,progressTotal:3,progressUnit:'acciones',result:'Sin evidencia',timeline:[
    {id:'t1',at:'2026-09-19T10:30:00',title:'Consigna emitida',detail:'Prioridad crítica',state:'DONE'},
    {id:'t2',at:'2026-09-19T10:40:00',title:'Lectura confirmada',detail:'Sofía Ramírez',state:'DONE'},
    {id:'t3',at:'2026-09-19T13:00:00',title:'Evidencia pendiente',detail:'No se cargó evidencia requerida',state:'ACTIVE'}]},
  {id:'e9',type:'RELIEF',code:'REV-2026-0409',name:'Relevo fin de semana',client:'Contecon',company:'Litoral Custodia',companyCode:'LIT',city:'Guayaquil',point:'Puente 1',post:'Control Puente 1',responsible:'Jorge Lima',zone:'Zona Costa',region:'Costa Centro',status:'COMPLETED',priority:'LOW',start:'2026-09-19T06:00:00',end:'2026-09-19T06:30:00',progressCurrent:4,progressTotal:4,progressUnit:'pasos',result:'4/4 pasos',timeline:[{id:'t1',at:'2026-09-19T06:30:00',title:'Relevo completado',detail:'Sin novedades',state:'DONE'}]},
  {id:'e10',type:'PATROL',code:'PAT-2026-0815',name:'Ronda Planta Cuenca',client:'Holcim',company:'Andina Protección',companyCode:'AND',city:'Cuenca',point:'Planta Principal',post:'Patrulla Interna A',responsible:'Jorge Paredes',zone:'Zona Sierra',region:'Austro',status:'IN_PROGRESS',priority:'MEDIUM',start:'2026-09-19T07:00:00',end:'2026-09-19T15:00:00',progressCurrent:5,progressTotal:8,progressUnit:'hitos',result:'5/8 hitos',timeline:[{id:'t1',at:'2026-09-19T07:00:00',title:'Inicio de patrulla',detail:'Planta Principal',state:'DONE'},{id:'t2',at:'2026-09-19T10:25:00',title:'Hito 5 validado',detail:'Sector de despacho',state:'ACTIVE'}]},
  {id:'e11',type:'ADHOC',code:'ADH-2026-0119',name:'Cierre de portón secundario',client:'Holcim',company:'Andina Protección',companyCode:'AND',city:'Cuenca',point:'Portón 2',post:'Portón 2',responsible:'María León',zone:'Zona Sierra',region:'Austro',status:'COMPLETED',priority:'HIGH',start:'2026-09-18T18:00:00',end:'2026-09-18T18:30:00',progressCurrent:2,progressTotal:2,progressUnit:'acciones',result:'Confirmada',timeline:[{id:'t1',at:'2026-09-18T18:05:00',title:'Consigna leída',detail:'Confirmado',state:'DONE'},{id:'t2',at:'2026-09-18T18:22:00',title:'Cierre confirmado',detail:'Evidencia fotográfica cargada',state:'DONE'}]},
  {id:'e12',type:'RELIEF',code:'REV-2026-0398',name:'Relevo turno tarde',client:'Bananera El Oro',company:'Sur Custodia',companyCode:'SUR',city:'Machala',point:'Muelle Sur',post:'Acceso Muelle Sur',responsible:'Pedro Vera',zone:'Zona Costa',region:'Costa Sur',status:'COMPLETED',priority:'LOW',start:'2026-09-18T18:00:00',end:'2026-09-18T18:30:00',progressCurrent:4,progressTotal:4,progressUnit:'pasos',result:'4/4 pasos',timeline:[{id:'t1',at:'2026-09-18T18:30:00',title:'Relevo completado',detail:'Muelle Sur',state:'DONE'}]},
  {id:'e13',type:'PATROL',code:'PAT-2026-0802',name:'Ronda Muelle Sur',client:'Bananera El Oro',company:'Sur Custodia',companyCode:'SUR',city:'Machala',point:'Muelle Sur',post:'Perímetro Muelle Sur',responsible:'Pedro Vera',zone:'Zona Costa',region:'Costa Sur',status:'OVERDUE',priority:'HIGH',start:'2026-09-18T20:00:00',end:'2026-09-19T04:00:00',progressCurrent:4,progressTotal:9,progressUnit:'hitos',result:'4/9 hitos',timeline:[{id:'t1',at:'2026-09-18T20:00:00',title:'Inicio de patrulla',detail:'Muelle Sur',state:'DONE'},{id:'t2',at:'2026-09-19T04:00:00',title:'Ventana vencida',detail:'5 hitos pendientes',state:'ACTIVE'}]},
  {id:'e14',type:'ADHOC',code:'ADH-2026-0108',name:'Revisión de documentos',client:'TPG',company:'Seguridad Integral S.A.',companyCode:'SISA',city:'Guayaquil',point:'Acceso Administrativo',post:'Recepción Administrativa',responsible:'Valeria Molina',zone:'Zona Costa',region:'Costa Sur',status:'PENDING',priority:'LOW',start:'2026-09-19T15:00:00',end:'2026-09-19T16:00:00',progressCurrent:0,progressTotal:2,progressUnit:'acciones',result:'0/2 acciones',timeline:[{id:'t1',at:'2026-09-19T15:00:00',title:'Consigna pendiente',detail:'Aún no ha sido leída',state:'PENDING'}]},
];

const scopeByUser:Record<UatUser,Scope>={
  presidente:{label:'Alcance: nacional / todas las compañías',companies:null,monitoredPosts:152},
  dlatam:{label:'Alcance: nacional / todas las compañías',companies:null,monitoredPosts:152},
  don:{label:'Alcance: nacional / todas las compañías',companies:null,monitoredPosts:152},
  dnacional:{label:'Alcance: nacional / todas las compañías',companies:null,monitoredPosts:152},
  dzonal:{label:'Alcance: 4 compañías / Zona Costa',companies:['GAL','SISA','LIT','SUR'],zone:'Zona Costa',monitoredPosts:86},
  jregional:{label:'Alcance: 3 compañías / Región Costa Sur',companies:['GAL','SISA','SUR'],region:'Costa Sur',monitoredPosts:52},
  coord:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL'],monitoredPosts:18},
  asistente:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL'],monitoredPosts:18},
  supervisor:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL'],monitoredPosts:18},
  agente:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL'],monitoredPosts:18},
  cliente:{label:'Alcance restringido / solo su operación',companies:['GAL'],monitoredPosts:18},
};

const latestStart=DATA.reduce((max,row)=>row.start>max?row.start:max,DATA[0].start);
const latestDate=new Date(latestStart);
const defaultTo=latestDate.toISOString().slice(0,10);
const tempFrom=new Date(latestDate); tempFrom.setDate(tempFrom.getDate()-30);
const defaultFrom=tempFrom.toISOString().slice(0,10);
const EMPTY_FILTERS:Filters={type:'ALL',query:'',company:'ALL',client:'',city:'',point:'',post:'',status:'ALL',responsible:'',dateFrom:defaultFrom,dateTo:defaultTo};

const typeLabel=(type:ExecutionType)=>type==='RELIEF'?'Relevo':type==='PATROL'?'Patrulla':'Consigna ad-hoc';
const statusLabel=(status:ExecutionStatus)=>({IN_PROGRESS:'En curso',COMPLETED:'Completada',OVERDUE:'Vencida',PENDING:'Pendiente',CRITICAL:'Crítica'}[status]);
const priorityLabel=(priority:Priority)=>({LOW:'Baja',MEDIUM:'Media',HIGH:'Alta',CRITICAL:'Crítica'}[priority]);
const typeClass=(type:ExecutionType)=>type==='RELIEF'?'relief':type==='PATROL'?'patrol':'adhoc';
const statusClass=(status:ExecutionStatus)=>({IN_PROGRESS:'info',COMPLETED:'success',OVERDUE:'danger',PENDING:'warning',CRITICAL:'danger'}[status]);
const typeIcon=(type:ExecutionType)=>type==='RELIEF'?<RefreshCw size={15}/>:type==='PATROL'?<CarFront size={15}/>:<FileText size={15}/>;
const formatDateTime=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}).format(new Date(value));
const dateOnly=(value:string)=>value.slice(0,10);
const formatTime=(value:string)=>new Intl.DateTimeFormat('es-EC',{hour:'2-digit',minute:'2-digit'}).format(new Date(value));
function withinScope(row:Execution,scope:Scope){if(scope.companies&&!scope.companies.includes(row.companyCode))return false;if(scope.zone&&row.zone!==scope.zone)return false;if(scope.region&&row.region!==scope.region)return false;return true}
function unique(values:string[]){return Array.from(new Set(values)).sort((a,b)=>a.localeCompare(b,'es'))}
function initials(value:string){return value.split(' ').filter(Boolean).slice(0,2).map(x=>x[0]).join('').toUpperCase()}
function validateDateRange(dateFrom:string,dateTo:string){
  if(!dateFrom || !dateTo) return 'Debe elegir siempre una fecha de inicio y una fecha de fin.';
  if(dateTo < dateFrom) return 'La fecha fin no puede ser menor que la fecha inicio.';
  const from=new Date(`${dateFrom}T00:00:00`);
  const max=new Date(from); max.setFullYear(max.getFullYear()+1);
  const to=new Date(`${dateTo}T23:59:59`);
  if(to > max) return 'El período consultado no puede ser mayor a 1 año.';
  return '';
}

export default function ConsignasExecution(){
  const user=getUser();
  const scope=scopeByUser[user];
  const [filters,setFilters]=useState<Filters>(EMPTY_FILTERS);
  const [selectedId,setSelectedId]=useState('');

  const scoped=useMemo(()=>DATA.filter(row=>withinScope(row,scope)),[scope]);

  const companies=useMemo(()=>unique(scoped.map(x=>x.company)),[scoped]);
  const cities=useMemo(()=>unique(scoped.map(x=>x.city)),[scoped]);
  const clients=useMemo(()=>unique(scoped.map(x=>x.client)),[scoped]);
  const points=useMemo(()=>filters.client ? unique(scoped.filter(x=>x.client===filters.client).map(x=>x.point)) : [],[scoped,filters.client]);
  const posts=useMemo(()=>filters.point ? unique(scoped.filter(x=>x.point===filters.point).map(x=>x.post)) : [],[scoped,filters.point]);
  const responsibles=useMemo(()=>filters.company!=='ALL' ? unique(scoped.filter(x=>x.company===filters.company).map(x=>x.responsible)) : [],[scoped,filters.company]);

  const dateError=useMemo(()=>validateDateRange(filters.dateFrom,filters.dateTo),[filters.dateFrom,filters.dateTo]);

  const rows=useMemo(()=>{
    if(dateError) return [];
    return scoped.filter(row=>{
      if(filters.type!=='ALL' && row.type!==filters.type) return false;
      const q=filters.query.trim().toLowerCase();
      if(q){
        const haystack=`${row.code} ${row.name} ${row.client} ${row.point} ${row.post} ${row.responsible} ${row.company} ${row.city}`.toLowerCase();
        if(!haystack.includes(q)) return false;
      }
      if(filters.city && row.city!==filters.city) return false;
      if(filters.company!=='ALL' && row.company!==filters.company) return false;
      if(filters.client && row.client!==filters.client) return false;
      if(filters.point && row.point!==filters.point) return false;
      if(filters.post && row.post!==filters.post) return false;
      if(filters.status!=='ALL' && row.status!==filters.status) return false;
      if(filters.responsible && row.responsible!==filters.responsible) return false;
      const rowDate=dateOnly(row.start);
      if(rowDate < filters.dateFrom) return false;
      if(rowDate > filters.dateTo) return false;
      return true;
    }).sort((a,b)=>b.start.localeCompare(a.start));
  },[scoped,filters,dateError]);

  useEffect(()=>{
    if(rows.length && !rows.some(r=>r.id===selectedId)) setSelectedId(rows[0].id);
    if(!rows.length) setSelectedId('');
  },[rows,selectedId]);

  const selected=rows.find(x=>x.id===selectedId) ?? rows[0] ?? null;

  const metrics=useMemo(()=>({
    reliefRunning:scoped.filter(x=>x.type==='RELIEF'&&x.status==='IN_PROGRESS').length,
    reliefOverdue:scoped.filter(x=>x.type==='RELIEF'&&x.status==='OVERDUE').length,
    patrolRunning:scoped.filter(x=>x.type==='PATROL'&&x.status==='IN_PROGRESS').length,
    adhocActive:scoped.filter(x=>x.type==='ADHOC'&&['IN_PROGRESS','PENDING','CRITICAL'].includes(x.status)).length,
    critical:scoped.filter(x=>x.status==='CRITICAL'||(x.status==='OVERDUE'&&x.priority==='HIGH')).length,
  }),[scoped]);

  function set<K extends keyof Filters>(key:K,value:Filters[K]){setFilters(prev=>({...prev,[key]:value}))}
  function clear(){setFilters(EMPTY_FILTERS)}
  function setClient(value:string){setFilters(prev=>({...prev,client:value,point:'',post:''}))}
  function setPoint(value:string){setFilters(prev=>({...prev,point:value,post:''}))}
  function setCompany(value:string){setFilters(prev=>({...prev,company:value,responsible:''}))}
  function exportCsv(){
    const header=['Tipo','Código','Nombre / Descripción','Cliente','Compañía','Ciudad','Punto','Puesto','Responsable','Inicio','Fin','Estado','Progreso'];
    const body=rows.map(r=>[typeLabel(r.type),r.code,r.name,r.client,r.company,r.city,r.point,r.post,r.responsible,formatDateTime(r.start),formatDateTime(r.end),statusLabel(r.status),r.result]);
    const csv=[header,...body].map(line=>line.map(v=>`"${String(v).replaceAll('"','""')}"`).join(',')).join('\n');
    const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='consignas_ejecuciones.csv';a.click();URL.revokeObjectURL(url);
  }

  return <div className="cns-page">
    <div className="cns-topbar"><div><div className="page-backline">Operaciones / Consignas</div><h2>Consignas</h2><p>Estado de ejecución de relevos, patrullas y consignas ad-hoc</p></div><div className="cns-scope"><ShieldCheck size={16}/>{scope.label}</div></div>

    <div className="cns-kpis">
      <Metric icon={<ShieldCheck size={21}/>} label="Puestos monitoreados" value={scope.monitoredPosts} subtitle="Puntos activos hoy" tone="blue"/>
      <Metric icon={<RefreshCw size={21}/>} label="Relevos en curso" value={metrics.reliefRunning} subtitle="En ejecución" tone="blue"/>
      <Metric icon={<Clock3 size={21}/>} label="Relevos vencidos" value={metrics.reliefOverdue} subtitle="Requieren atención" tone="red"/>
      <Metric icon={<CarFront size={21}/>} label="Patrullas en curso" value={metrics.patrolRunning} subtitle="En ejecución" tone="green"/>
      <Metric icon={<ClipboardList size={21}/>} label="Consignas ad-hoc activas" value={metrics.adhocActive} subtitle="Pendientes / en curso" tone="purple"/>
      <Metric icon={<AlertTriangle size={21}/>} label="Alertas críticas" value={metrics.critical} subtitle="Requieren acción inmediata" tone="red"/>
    </div>

    <div className="cns-layout">
      <section className="cns-main-card">
        <div className="cns-card-head"><div className="cns-title"><Search size={20}/><h3>Búsqueda de consignas</h3></div><div className="cns-tabs"><button className={filters.type==='ALL'?'active':''} onClick={()=>set('type','ALL')}>Todos</button><button className={filters.type==='RELIEF'?'active':''} onClick={()=>set('type','RELIEF')}><RefreshCw size={14}/>Relevos</button><button className={filters.type==='PATROL'?'active':''} onClick={()=>set('type','PATROL')}><CarFront size={14}/>Patrullas</button><button className={filters.type==='ADHOC'?'active':''} onClick={()=>set('type','ADHOC')}><FileText size={14}/>Consignas ad-hoc</button></div></div>
        <label className="cns-main-search"><Search size={18}/><input value={filters.query} onChange={e=>set('query',e.target.value)} placeholder="Buscar por código, nombre, cliente, punto, responsable…"/></label>
        <div className="cns-filter-grid row-2">
          <Field label="Ciudad"><select value={filters.city} onChange={e=>set('city',e.target.value)}><option value="">Todas</option>{cities.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Compañía"><select value={filters.company} onChange={e=>setCompany(e.target.value)}><option value="ALL">Todas</option>{companies.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Cliente"><select value={filters.client} onChange={e=>setClient(e.target.value)}><option value="">Todos</option>{clients.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Punto"><select value={filters.point} onChange={e=>setPoint(e.target.value)} disabled={!filters.client}><option value="">{filters.client?'Todos':'Seleccione un cliente primero'}</option>{points.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Puesto"><select value={filters.post} onChange={e=>set('post',e.target.value)} disabled={!filters.point}><option value="">{filters.point?'Todos':'Seleccione un punto primero'}</option>{posts.map(x=><option key={x}>{x}</option>)}</select></Field>
        </div>
        <div className="cns-filter-grid row-3">
          <Field label="Tipo de consigna"><select value={filters.type} onChange={e=>set('type',e.target.value as Filters['type'])}><option value="ALL">Todos</option><option value="RELIEF">Relevos</option><option value="PATROL">Patrullas</option><option value="ADHOC">Consignas ad-hoc</option></select></Field>
          <Field label="Estado"><select value={filters.status} onChange={e=>set('status',e.target.value as Filters['status'])}><option value="ALL">Todos</option><option value="IN_PROGRESS">En curso</option><option value="COMPLETED">Completada</option><option value="OVERDUE">Vencida</option><option value="PENDING">Pendiente</option><option value="CRITICAL">Crítica</option></select></Field>
          <Field label="Responsable"><select value={filters.responsible} onChange={e=>set('responsible',e.target.value)} disabled={filters.company==='ALL'}><option value="">{filters.company!=='ALL'?'Todos':'Seleccione una compañía primero'}</option>{responsibles.map(x=><option key={x}>{x}</option>)}</select></Field>
          <Field label="Fecha inicio"><input type="date" value={filters.dateFrom} onChange={e=>set('dateFrom',e.target.value)}/></Field>
          <Field label="Fecha fin"><input type="date" value={filters.dateTo} onChange={e=>set('dateTo',e.target.value)}/></Field>
        </div>
        <div className="cns-rules-note"><AlertCircle size={15}/><span>Las fechas son obligatorias. El rango consultado puede ser máximo de 1 año.</span></div>
        {dateError && <div className="cns-validation-error"><AlertTriangle size={16}/><span>{dateError}</span></div>}
        <div className="cns-search-actions"><button className="primary" disabled={!!dateError}><Search size={16}/>Buscar</button><button onClick={clear}><FilterX size={16}/>Limpiar filtros</button><button className="export" onClick={exportCsv} disabled={!!dateError || !rows.length}><Download size={16}/>Exportar</button></div>

        <div className="cns-results-card"><div className="cns-results-head"><h3>Ejecuciones ({rows.length})</h3><div>Ordenar por: <strong>Fecha más reciente</strong></div></div><div className="cns-table-wrap"><table className="cns-table"><thead><tr><th>Tipo</th><th>Código</th><th>Nombre / Descripción</th><th>Cliente</th><th>Punto / Puesto</th><th>Responsable</th><th>Ventana / Hora</th><th>Estado de ejecución</th><th>Resultado / Progreso</th><th></th></tr></thead><tbody>{rows.map(row=>{
          const pct=Math.round((row.progressCurrent/Math.max(row.progressTotal,1))*100);
          return <tr key={row.id} className={selected?.id===row.id?'selected':''} onClick={()=>setSelectedId(row.id)}><td><span className={`cns-type ${typeClass(row.type)}`}>{typeIcon(row.type)}{typeLabel(row.type)}</span></td><td>{row.code}</td><td><strong>{row.name}</strong><small>{row.company}</small></td><td>{row.client}</td><td><strong>{row.point}</strong><small>{row.post}</small></td><td>{row.responsible}</td><td>{formatDateTime(row.start)} – {formatTime(row.end)}</td><td><span className={`cns-status ${statusClass(row.status)}`}>{statusLabel(row.status)}</span></td><td><div className="cns-progress-cell"><span>{row.result}</span><div><i style={{width:`${pct}%`}}/></div></div></td><td><button>Ver</button></td></tr>
        })}{!rows.length&&<tr><td className="empty" colSpan={10}>{dateError ? 'Corrija el rango de fechas para consultar resultados.' : 'No se encontraron ejecuciones con los filtros aplicados.'}</td></tr>}</tbody></table></div><div className="cns-results-footer">Mostrando {rows.length?1:0} a {rows.length} de {rows.length} resultados</div></div>
      </section>

      <aside className="cns-detail-card"><div className="cns-title"><FileText size={19}/><h3>Detalle de ejecución</h3></div>{selected?<><div className="cns-detail-head"><span className={`cns-type ${typeClass(selected.type)}`}>{typeIcon(selected.type)}{typeLabel(selected.type)}</span><div><strong>{selected.code}</strong><h4>{selected.name}</h4></div><span className={`cns-status ${statusClass(selected.status)}`}>{statusLabel(selected.status)}</span></div><dl className="cns-detail-list">
        <Detail icon={<Briefcase size={14}/>} label="Cliente" value={selected.client}/><Detail icon={<Building2 size={14}/>} label="Compañía" value={selected.company}/><Detail icon={<MapPin size={14}/>} label="Ciudad" value={selected.city}/><Detail icon={<MapPin size={14}/>} label="Punto" value={selected.point}/><Detail icon={<MapPin size={14}/>} label="Puesto" value={selected.post}/><Detail icon={<UserRound size={14}/>} label="Responsable" value={selected.responsible}/><Detail icon={<ShieldCheck size={14}/>} label="Prioridad" value={priorityLabel(selected.priority)}/><Detail icon={<Clock3 size={14}/>} label="Hora de inicio" value={formatDateTime(selected.start)}/><Detail icon={<Clock3 size={14}/>} label="Hora de fin" value={formatDateTime(selected.end)}/><Detail icon={<CheckCircle2 size={14}/>} label="Estado" value={statusLabel(selected.status)}/>
      </dl><div className="cns-detail-progress"><div><strong>Progreso {selected.type==='RELIEF'?'del relevo':selected.type==='PATROL'?'de hitos':'de acciones'}</strong><b>{selected.progressCurrent}/{selected.progressTotal}</b></div><div className="bar"><i style={{width:`${Math.round(selected.progressCurrent/Math.max(selected.progressTotal,1)*100)}%`}}/></div><span>{Math.round(selected.progressCurrent/Math.max(selected.progressTotal,1)*100)}% completado</span></div><div className="cns-timeline"><div className="cns-timeline-head"><h4>{selected.type==='RELIEF'?'Hitos del relevo':'Últimos eventos'}</h4><button>Ver todos</button></div>{selected.timeline.map(event=><article key={event.id}><i className={event.state.toLowerCase()}/><div><span>{formatTime(event.at)}</span><strong>{event.title}</strong><small>{event.detail}</small></div></article>)}</div><button className="cns-detail-button"><Search size={16}/>Ver detalle completo</button></>:<div className="cns-empty">Seleccione una ejecución para ver su detalle.</div>}</aside>
    </div>
  </div>
}

function Metric({icon,label,value,subtitle,tone}:{icon:ReactNode;label:string;value:string|number;subtitle:string;tone:'blue'|'green'|'purple'|'red'}){return <article className={`cns-metric ${tone}`}><span>{icon}</span><div><small>{label}</small><strong>{value}</strong><p>{subtitle}</p></div></article>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="cns-field"><span>{label}</span>{children}</label>}
function Detail({icon,label,value}:{icon:ReactNode;label:string;value:string}){return <div><dt>{icon}{label}</dt><dd>{value}</dd></div>}
