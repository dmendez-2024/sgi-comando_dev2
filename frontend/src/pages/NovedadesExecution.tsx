import {useMemo, useState, type ReactNode} from 'react';
import {
  AlertTriangle, Building2, CalendarDays, CheckCircle2, ChevronRight, CircleAlert,
  Download, Eye, FileImage, FilterX, MapPin, Search, ShieldCheck, Trash2,
  UserRound, X, Pencil, Save, Clock3, Camera, Navigation, ClipboardCheck
} from 'lucide-react';
import {getUser, type UatUser} from '../api';

type NoveltyType='FINDING'|'VULNERABILITY'|'INCIDENT';
type NoveltyStatus='PENDING'|'APPROVED'|'DISCARDED';
type ReporterRole='AGENT'|'SUPERVISOR';

type AuditEvent={
  id:string;
  at:string;
  title:string;
  detail:string;
  actor:string;
};

type Novelty={
  id:string;
  code:string;
  type:NoveltyType;
  subcategory:string;
  status:NoveltyStatus;
  critical:boolean;
  company:string;
  companyCode:string;
  client:string;
  city:string;
  point:string;
  post:string;
  zone:string;
  region:string;
  reportedBy:string;
  reporterRole:ReporterRole;
  createdAt:string;
  description:string;
  latitude:number|null;
  longitude:number|null;
  photoCount:number;
  reviewComment:string;
  reviewedBy:string|null;
  reviewedAt:string|null;
  clientVisible:boolean;
  sourceSystem:'SGI_OPR';
  audit:AuditEvent[];
};

type Scope={label:string;companies:string[]|null;region?:string;zone?:string};
type Filters={
  type:'ALL'|NoveltyType;
  query:string;
  city:string;
  company:string;
  client:string;
  point:string;
  post:string;
  status:'ALL'|NoveltyStatus;
  reporter:string;
  dateFrom:string;
  dateTo:string;
};

type EditableDraft=Pick<Novelty,'type'|'subcategory'|'company'|'client'|'city'|'point'|'post'|'description'|'latitude'|'longitude'|'photoCount'>;

const FINDING_SUBCATEGORIES=['Orden','Limpieza','Mantenimiento','Estacionamiento','Componentes de Seguridad'];
const VULNERABILITY_SUBCATEGORIES=['Acceso','Perímetro','Interno'];
const INCIDENT_SUBCATEGORIES=['Servicio','Seguridad','Administrativo'];

const INITIAL_DATA:Novelty[]=[
  {
    id:'n1',code:'NOV-2026-0142',type:'FINDING',subcategory:'Limpieza',status:'PENDING',critical:false,
    company:'Galvarino',companyCode:'GAL',client:'Supermercados Andinos',city:'Guayaquil',point:'Local Centro',post:'Puesto Principal',zone:'Zona Costa',region:'Costa Sur',
    reportedBy:'Luis Pérez',reporterRole:'AGENT',createdAt:'2026-09-19T10:24:00',
    description:'Se observa acumulación de basura en el área de estacionamiento, principalmente papeles y envases, lo que genera mala imagen y podría atraer plagas.',
    latitude:-2.1851,longitude:-79.8792,photoCount:3,reviewComment:'',reviewedBy:null,reviewedAt:null,clientVisible:false,sourceSystem:'SGI_OPR',
    audit:[{id:'a1',at:'2026-09-19T10:24:00',title:'Recibido desde SGI: Operador',detail:'Novedad registrada en terreno desde la app SGI: Operador.',actor:'Luis Pérez · Agente de Seguridad'}]
  },
  {
    id:'n2',code:'NOV-2026-0137',type:'VULNERABILITY',subcategory:'Acceso',status:'PENDING',critical:true,
    company:'Galvarino',companyCode:'GAL',client:'Minera Los Andes',city:'Durán',point:'Planta Norte',post:'Control Principal',zone:'Zona Costa',region:'Costa Sur',
    reportedBy:'María González',reporterRole:'SUPERVISOR',createdAt:'2026-09-19T08:48:00',
    description:'Se identificó ingreso lateral sin control visual durante el cambio de turno. El punto permite aproximación sin pasar por el control principal.',
    latitude:-2.1749,longitude:-79.8302,photoCount:2,reviewComment:'',reviewedBy:null,reviewedAt:null,clientVisible:false,sourceSystem:'SGI_OPR',
    audit:[{id:'a1',at:'2026-09-19T08:48:00',title:'Recibido desde SGI: Operador',detail:'Vulnerabilidad levantada durante supervisión.',actor:'María González · Supervisora'}]
  },
  {
    id:'n3',code:'NOV-2026-0135',type:'INCIDENT',subcategory:'Seguridad',status:'PENDING',critical:true,
    company:'Galvarino',companyCode:'GAL',client:'Banco del Sur',city:'Guayaquil',point:'Sucursal Centro',post:'Puesto 1',zone:'Zona Costa',region:'Costa Sur',
    reportedBy:'Diego Torres',reporterRole:'AGENT',createdAt:'2026-09-18T14:30:00',
    description:'Persona agresiva intentó ingresar sin autorización y profirió amenazas al personal de seguridad. Se controló el acceso sin contacto físico.',
    latitude:-2.1946,longitude:-79.8823,photoCount:1,reviewComment:'',reviewedBy:null,reviewedAt:null,clientVisible:false,sourceSystem:'SGI_OPR',
    audit:[{id:'a1',at:'2026-09-18T14:30:00',title:'Recibido desde SGI: Operador',detail:'Incidente reportado desde el Puesto 1.',actor:'Diego Torres · Agente de Seguridad'}]
  },
  {
    id:'n4',code:'NOV-2026-0131',type:'FINDING',subcategory:'Mantenimiento',status:'APPROVED',critical:false,
    company:'Seguridad Integral S.A.',companyCode:'SISA',client:'Retail Plaza',city:'Guayaquil',point:'Local Este',post:'Puesto 2',zone:'Zona Costa',region:'Costa Sur',
    reportedBy:'Carla Méndez',reporterRole:'AGENT',createdAt:'2026-09-18T09:10:00',
    description:'Foco quemado en pasillo lateral próximo al acceso de proveedores.',latitude:-2.1614,longitude:-79.8941,photoCount:2,
    reviewComment:'Validado. Informar al cliente para gestión de mantenimiento.',reviewedBy:'Coordinador UAT',reviewedAt:'2026-09-18T09:35:00',clientVisible:true,sourceSystem:'SGI_OPR',
    audit:[
      {id:'a1',at:'2026-09-18T09:10:00',title:'Recibido desde SGI: Operador',detail:'Hallazgo registrado en terreno.',actor:'Carla Méndez · Agente de Seguridad'},
      {id:'a2',at:'2026-09-18T09:35:00',title:'Aprobada en SGI: Comando',detail:'Novedad habilitada para visualización en SGI: Cliente.',actor:'Coordinador UAT'}
    ]
  },
  {
    id:'n5',code:'NOV-2026-0128',type:'VULNERABILITY',subcategory:'Perímetro',status:'PENDING',critical:false,
    company:'Seguridad Integral S.A.',companyCode:'SISA',client:'Planta Solar',city:'Guayaquil',point:'Acceso Sur',post:'Puesto Perímetro',zone:'Zona Costa',region:'Costa Sur',
    reportedBy:'Jorge Ramírez',reporterRole:'SUPERVISOR',createdAt:'2026-09-17T16:26:00',
    description:'Sección de cerco perimetral presenta daño y pérdida de tensión junto al lindero sur.',latitude:-2.2382,longitude:-79.9031,photoCount:4,
    reviewComment:'',reviewedBy:null,reviewedAt:null,clientVisible:false,sourceSystem:'SGI_OPR',
    audit:[{id:'a1',at:'2026-09-17T16:26:00',title:'Recibido desde SGI: Operador',detail:'Vulnerabilidad identificada durante ronda.',actor:'Jorge Ramírez · Supervisor'}]
  },
  {
    id:'n6',code:'NOV-2026-0123',type:'INCIDENT',subcategory:'Servicio',status:'DISCARDED',critical:false,
    company:'Litoral Custodia',companyCode:'LIT',client:'Edificio Corporativo',city:'Guayaquil',point:'Torre A',post:'Sala de Control',zone:'Zona Costa',region:'Costa Centro',
    reportedBy:'Ana Silva',reporterRole:'AGENT',createdAt:'2026-09-17T11:05:00',
    description:'Se reportó falla de cámara CCTV; luego se verificó que el equipo estaba en mantenimiento programado.',latitude:null,longitude:null,photoCount:1,
    reviewComment:'Se descarta porque corresponde a mantenimiento programado previamente registrado.',reviewedBy:'Jefe Regional UAT',reviewedAt:'2026-09-17T11:20:00',clientVisible:false,sourceSystem:'SGI_OPR',
    audit:[
      {id:'a1',at:'2026-09-17T11:05:00',title:'Recibido desde SGI: Operador',detail:'Incidente reportado por el agente.',actor:'Ana Silva · Agente de Seguridad'},
      {id:'a2',at:'2026-09-17T11:20:00',title:'Descartada en SGI: Comando',detail:'No corresponde a una novedad operativa para publicar al cliente.',actor:'Jefe Regional UAT'}
    ]
  },
  {
    id:'n7',code:'NOV-2026-0120',type:'FINDING',subcategory:'Orden',status:'PENDING',critical:false,
    company:'Litoral Custodia',companyCode:'LIT',client:'Logística del Norte',city:'Guayaquil',point:'Centro Logístico',post:'Bodega 3',zone:'Zona Costa',region:'Costa Centro',
    reportedBy:'Roberto Díaz',reporterRole:'AGENT',createdAt:'2026-09-16T15:45:00',
    description:'Material de embalaje almacenado temporalmente obstruye parcialmente el pasillo de circulación.',latitude:null,longitude:null,photoCount:2,
    reviewComment:'',reviewedBy:null,reviewedAt:null,clientVisible:false,sourceSystem:'SGI_OPR',
    audit:[{id:'a1',at:'2026-09-16T15:45:00',title:'Recibido desde SGI: Operador',detail:'Hallazgo de orden reportado desde Bodega 3.',actor:'Roberto Díaz · Agente de Seguridad'}]
  },
  {
    id:'n8',code:'NOV-2026-0117',type:'VULNERABILITY',subcategory:'Interno',status:'APPROVED',critical:true,
    company:'Sur Custodia',companyCode:'SUR',client:'Bananera El Oro',city:'Machala',point:'Muelle Sur',post:'Control Interno',zone:'Zona Costa',region:'Costa Sur',
    reportedBy:'Pedro Vera',reporterRole:'SUPERVISOR',createdAt:'2026-09-15T18:10:00',
    description:'Puerta interna de acceso a cuarto técnico permanece sin cerradura funcional.',latitude:-3.2585,longitude:-79.9605,photoCount:2,
    reviewComment:'Aprobado para conocimiento y gestión del cliente.',reviewedBy:'Director Zonal UAT',reviewedAt:'2026-09-15T18:30:00',clientVisible:true,sourceSystem:'SGI_OPR',
    audit:[
      {id:'a1',at:'2026-09-15T18:10:00',title:'Recibido desde SGI: Operador',detail:'Vulnerabilidad interna registrada.',actor:'Pedro Vera · Supervisor'},
      {id:'a2',at:'2026-09-15T18:30:00',title:'Aprobada en SGI: Comando',detail:'Publicada para SGI: Cliente.',actor:'Director Zonal UAT'}
    ]
  }
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

const latestDate=new Date(INITIAL_DATA.reduce((max,row)=>row.createdAt>max?row.createdAt:max,INITIAL_DATA[0].createdAt));
const DEFAULT_TO=latestDate.toISOString().slice(0,10);
const defaultFromDate=new Date(latestDate);defaultFromDate.setDate(defaultFromDate.getDate()-30);
const DEFAULT_FROM=defaultFromDate.toISOString().slice(0,10);
const EMPTY_FILTERS:Filters={type:'ALL',query:'',city:'',company:'ALL',client:'',point:'',post:'',status:'ALL',reporter:'',dateFrom:DEFAULT_FROM,dateTo:DEFAULT_TO};

const typeLabel=(type:NoveltyType)=>type==='FINDING'?'Hallazgo':type==='VULNERABILITY'?'Vulnerabilidad':'Incidente';
const typeClass=(type:NoveltyType)=>type==='FINDING'?'finding':type==='VULNERABILITY'?'vulnerability':'incident';
const statusLabel=(status:NoveltyStatus)=>status==='PENDING'?'Pendiente':status==='APPROVED'?'Aprobada':'Descartada';
const roleLabel=(role:ReporterRole)=>role==='SUPERVISOR'?'Supervisor':'Agente de Seguridad';
const statusClass=(status:NoveltyStatus)=>status==='PENDING'?'pending':status==='APPROVED'?'approved':'discarded';
const iconForType=(type:NoveltyType)=>type==='FINDING'?<Eye size={16}/>:type==='VULNERABILITY'?<ShieldCheck size={16}/>:<AlertTriangle size={16}/>;
const dateOnly=(value:string)=>value.slice(0,10);
const formatDateTime=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));
const unique=(values:string[])=>Array.from(new Set(values)).sort((a,b)=>a.localeCompare(b,'es'));
const canReview=(user:UatUser)=>['presidente','dnacional','dzonal','jregional','coord','asistente'].includes(user);
function withinScope(row:Novelty,scope:Scope){if(scope.companies&&!scope.companies.includes(row.companyCode))return false;if(scope.zone&&row.zone!==scope.zone)return false;if(scope.region&&row.region!==scope.region)return false;return true}
function validateRange(from:string,to:string){if(!from||!to)return 'Debe elegir siempre una fecha inicio y una fecha fin.';if(to<from)return 'La fecha fin no puede ser menor que la fecha inicio.';const start=new Date(`${from}T00:00:00`);const max=new Date(start);max.setFullYear(max.getFullYear()+1);const end=new Date(`${to}T23:59:59`);return end>max?'El período consultado no puede ser mayor a 1 año.':''}
function subcategories(type:NoveltyType){return type==='FINDING'?FINDING_SUBCATEGORIES:type==='VULNERABILITY'?VULNERABILITY_SUBCATEGORIES:INCIDENT_SUBCATEGORIES}
function draftFrom(row:Novelty):EditableDraft{return {type:row.type,subcategory:row.subcategory,company:row.company,client:row.client,city:row.city,point:row.point,post:row.post,description:row.description,latitude:row.latitude,longitude:row.longitude,photoCount:row.photoCount}}

export default function NovedadesExecution(){
  const user=getUser();
  const scope=scopeByUser[user];
  const [records,setRecords]=useState<Novelty[]>(INITIAL_DATA);
  const [filters,setFilters]=useState<Filters>(EMPTY_FILTERS);
  const [selectedId,setSelectedId]=useState<string|null>(null);
  const [editing,setEditing]=useState(false);
  const [draft,setDraft]=useState<EditableDraft|null>(null);
  const [reviewComment,setReviewComment]=useState('');
  const [modalError,setModalError]=useState('');

  const scoped=useMemo(()=>records.filter(row=>withinScope(row,scope)),[records,scope]);
  const cities=useMemo(()=>unique(scoped.map(x=>x.city)),[scoped]);
  const companies=useMemo(()=>unique(scoped.map(x=>x.company)),[scoped]);
  const clients=useMemo(()=>unique(scoped.map(x=>x.client)),[scoped]);
  const points=useMemo(()=>filters.client?unique(scoped.filter(x=>x.client===filters.client).map(x=>x.point)):[],[scoped,filters.client]);
  const posts=useMemo(()=>filters.point?unique(scoped.filter(x=>x.point===filters.point).map(x=>x.post)):[],[scoped,filters.point]);
  const reporters=useMemo(()=>filters.company!=='ALL'?unique(scoped.filter(x=>x.company===filters.company).map(x=>x.reportedBy)):[],[scoped,filters.company]);
  const dateError=useMemo(()=>validateRange(filters.dateFrom,filters.dateTo),[filters.dateFrom,filters.dateTo]);

  const rows=useMemo(()=>{
    if(dateError)return [];
    return scoped.filter(row=>{
      if(filters.type!=='ALL'&&row.type!==filters.type)return false;
      const q=filters.query.trim().toLowerCase();
      if(q&&!`${row.code} ${row.description} ${row.subcategory} ${row.client} ${row.company} ${row.city} ${row.point} ${row.post} ${row.reportedBy}`.toLowerCase().includes(q))return false;
      if(filters.city&&row.city!==filters.city)return false;
      if(filters.company!=='ALL'&&row.company!==filters.company)return false;
      if(filters.client&&row.client!==filters.client)return false;
      if(filters.point&&row.point!==filters.point)return false;
      if(filters.post&&row.post!==filters.post)return false;
      if(filters.status!=='ALL'&&row.status!==filters.status)return false;
      if(filters.reporter&&row.reportedBy!==filters.reporter)return false;
      const d=dateOnly(row.createdAt);if(d<filters.dateFrom||d>filters.dateTo)return false;
      return true;
    }).sort((a,b)=>b.createdAt.localeCompare(a.createdAt));
  },[scoped,filters,dateError]);

  const selected=selectedId?records.find(x=>x.id===selectedId)??null:null;
  const metrics=useMemo(()=>({
    pending:scoped.filter(x=>x.status==='PENDING').length,
    findings:scoped.filter(x=>x.status==='PENDING'&&x.type==='FINDING').length,
    vulnerabilities:scoped.filter(x=>x.status==='PENDING'&&x.type==='VULNERABILITY').length,
    incidents:scoped.filter(x=>x.status==='PENDING'&&x.type==='INCIDENT').length,
    approvedToday:scoped.filter(x=>x.status==='APPROVED'&&x.reviewedAt&&dateOnly(x.reviewedAt)===DEFAULT_TO).length,
    critical:scoped.filter(x=>x.status==='PENDING'&&x.critical).length,
  }),[scoped]);

  function set<K extends keyof Filters>(key:K,value:Filters[K]){setFilters(prev=>({...prev,[key]:value}))}
  function setClient(value:string){setFilters(prev=>({...prev,client:value,point:'',post:''}))}
  function setPoint(value:string){setFilters(prev=>({...prev,point:value,post:''}))}
  function setCompany(value:string){setFilters(prev=>({...prev,company:value,reporter:''}))}
  function clearFilters(){setFilters(EMPTY_FILTERS)}
  function openModal(row:Novelty){setSelectedId(row.id);setEditing(false);setDraft(draftFrom(row));setReviewComment(row.reviewComment);setModalError('')}
  function closeModal(){setSelectedId(null);setEditing(false);setDraft(null);setReviewComment('');setModalError('')}
  function updateDraft<K extends keyof EditableDraft>(key:K,value:EditableDraft[K]){setDraft(prev=>prev?{...prev,[key]:value}:prev)}

  function saveEdit(){
    if(!selected||!draft)return;
    if(!draft.description.trim()){setModalError('La descripción de la novedad es obligatoria.');return}
    if(!draft.subcategory){setModalError('La subcategoría es obligatoria.');return}
    setRecords(prev=>prev.map(row=>row.id!==selected.id?row:{...row,...draft,audit:[...row.audit,{id:`edit-${Date.now()}`,at:new Date().toISOString(),title:'Información editada en SGI: Comando',detail:'La novedad fue corregida antes de la decisión de revisión.',actor:userLabel(user)}]}));
    setEditing(false);setModalError('');
  }

  function approve(){
    if(!selected||!canReview(user))return;
    const now=new Date().toISOString();
    setRecords(prev=>prev.map(row=>row.id!==selected.id?row:{...row,status:'APPROVED',reviewComment,reviewedBy:userLabel(user),reviewedAt:now,clientVisible:true,audit:[...row.audit,{id:`approve-${Date.now()}`,at:now,title:'Aprobada en SGI: Comando',detail:'La novedad quedó habilitada para visualización en SGI: Cliente.',actor:userLabel(user)}]}));
    setModalError('');setEditing(false);
  }

  function discard(){
    if(!selected||!canReview(user))return;
    if(!reviewComment.trim()){setModalError('Para descartar una novedad debe ingresar un comentario de revisión.');return}
    const now=new Date().toISOString();
    setRecords(prev=>prev.map(row=>row.id!==selected.id?row:{...row,status:'DISCARDED',reviewComment,reviewedBy:userLabel(user),reviewedAt:now,clientVisible:false,audit:[...row.audit,{id:`discard-${Date.now()}`,at:now,title:'Descartada en SGI: Comando',detail:reviewComment,actor:userLabel(user)}]}));
    setModalError('');setEditing(false);
  }

  function addPhoto(){if(!draft)return;updateDraft('photoCount',Math.min(draft.photoCount+1,6))}
  function exportCsv(){const header=['Tipo','Código','Subcategoría','Descripción','Cliente','Compañía','Ciudad','Punto','Puesto','Reportado por','Fecha / Hora','Estado'];const body=rows.map(r=>[typeLabel(r.type),r.code,r.subcategory,r.description,r.client,r.company,r.city,r.point,r.post,r.reportedBy,formatDateTime(r.createdAt),statusLabel(r.status)]);const csv=[header,...body].map(line=>line.map(v=>`"${String(v).replaceAll('"','""')}"`).join(',')).join('\n');const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='novedades.csv';a.click();URL.revokeObjectURL(url)}

  return <div className="nov-page">
    <div className="nov-topbar"><div><div className="page-backline">Operaciones / Novedades</div><h2>Novedades</h2><p>Revisión y aprobación de novedades reportadas desde SGI: Operador</p></div><div className="nov-scope"><ShieldCheck size={16}/>{scope.label}</div></div>

    <div className="nov-kpis">
      <Metric icon={<ClipboardCheck size={21}/>} label="Pendientes" value={metrics.pending} tone="blue"/>
      <Metric icon={<Eye size={21}/>} label="Hallazgos pendientes" value={metrics.findings} tone="teal"/>
      <Metric icon={<ShieldCheck size={21}/>} label="Vulnerabilidades pendientes" value={metrics.vulnerabilities} tone="amber"/>
      <Metric icon={<AlertTriangle size={21}/>} label="Incidentes pendientes" value={metrics.incidents} tone="red"/>
      <Metric icon={<CheckCircle2 size={21}/>} label="Aprobadas hoy" value={metrics.approvedToday} tone="green"/>
      <Metric icon={<CircleAlert size={21}/>} label="Críticas" value={metrics.critical} tone="red"/>
    </div>

    <section className="nov-main-card">
      <div className="nov-tabs">
        <button className={filters.type==='ALL'?'active':''} onClick={()=>set('type','ALL')}>Todos ({scoped.length})</button>
        <button className={filters.type==='FINDING'?'active':''} onClick={()=>set('type','FINDING')}>Hallazgos ({scoped.filter(x=>x.type==='FINDING').length})</button>
        <button className={filters.type==='VULNERABILITY'?'active':''} onClick={()=>set('type','VULNERABILITY')}>Vulnerabilidades ({scoped.filter(x=>x.type==='VULNERABILITY').length})</button>
        <button className={filters.type==='INCIDENT'?'active':''} onClick={()=>set('type','INCIDENT')}>Incidentes ({scoped.filter(x=>x.type==='INCIDENT').length})</button>
      </div>
      <label className="nov-main-search"><Search size={18}/><input value={filters.query} onChange={e=>set('query',e.target.value)} placeholder="Buscar por código, descripción o palabra clave…"/></label>

      <div className="nov-filter-grid">
        <Field label="Ciudad"><select value={filters.city} onChange={e=>set('city',e.target.value)}><option value="">Todas</option>{cities.map(x=><option key={x}>{x}</option>)}</select></Field>
        <Field label="Compañía"><select value={filters.company} onChange={e=>setCompany(e.target.value)}><option value="ALL">Todas</option>{companies.map(x=><option key={x}>{x}</option>)}</select></Field>
        <Field label="Cliente"><select value={filters.client} onChange={e=>setClient(e.target.value)}><option value="">Todos</option>{clients.map(x=><option key={x}>{x}</option>)}</select></Field>
        <Field label="Punto"><select value={filters.point} onChange={e=>setPoint(e.target.value)} disabled={!filters.client}><option value="">{filters.client?'Todos':'Seleccione cliente primero'}</option>{points.map(x=><option key={x}>{x}</option>)}</select></Field>
        <Field label="Puesto"><select value={filters.post} onChange={e=>set('post',e.target.value)} disabled={!filters.point}><option value="">{filters.point?'Todos':'Seleccione punto primero'}</option>{posts.map(x=><option key={x}>{x}</option>)}</select></Field>
      </div>
      <div className="nov-filter-grid">
        <Field label="Tipo de novedad"><select value={filters.type} onChange={e=>set('type',e.target.value as Filters['type'])}><option value="ALL">Todos</option><option value="FINDING">Hallazgos</option><option value="VULNERABILITY">Vulnerabilidades</option><option value="INCIDENT">Incidentes</option></select></Field>
        <Field label="Estado"><select value={filters.status} onChange={e=>set('status',e.target.value as Filters['status'])}><option value="ALL">Todos</option><option value="PENDING">Pendiente</option><option value="APPROVED">Aprobada</option><option value="DISCARDED">Descartada</option></select></Field>
        <Field label="Reportado por"><select value={filters.reporter} onChange={e=>set('reporter',e.target.value)} disabled={filters.company==='ALL'}><option value="">{filters.company!=='ALL'?'Todos':'Seleccione compañía primero'}</option>{reporters.map(x=><option key={x}>{x}</option>)}</select></Field>
        <Field label="Fecha inicio"><input type="date" value={filters.dateFrom} onChange={e=>set('dateFrom',e.target.value)}/></Field>
        <Field label="Fecha fin"><input type="date" value={filters.dateTo} onChange={e=>set('dateTo',e.target.value)}/></Field>
      </div>
      <div className="nov-rule-note"><CircleAlert size={15}/><span>Para filtrar por Punto primero seleccione un Cliente; para Puesto seleccione un Punto; para Reportado por seleccione una Compañía. Las fechas son obligatorias y el rango máximo es 1 año.</span></div>
      {dateError&&<div className="nov-validation"><AlertTriangle size={16}/>{dateError}</div>}
      <div className="nov-actions"><button className="primary" disabled={!!dateError}><Search size={16}/>Buscar</button><button onClick={clearFilters}><FilterX size={16}/>Limpiar filtros</button><button className="export" onClick={exportCsv} disabled={!!dateError||!rows.length}><Download size={16}/>Exportar</button></div>

      <div className="nov-results-card">
        <div className="nov-results-head"><h3>Novedades ({rows.length})</h3><span>Ordenado por <strong>Fecha más reciente</strong></span></div>
        <div className="nov-table-wrap"><table className="nov-table"><thead><tr><th>Tipo</th><th>Código</th><th>Novedad / Descripción</th><th>Cliente</th><th>Punto / Puesto</th><th>Reportado por</th><th>Fecha / Hora</th><th>Estado</th><th></th></tr></thead><tbody>
          {rows.map(row=><tr key={row.id} className={row.critical&&row.status==='PENDING'?'critical':''} onClick={()=>openModal(row)}><td><span className={`nov-type ${typeClass(row.type)}`}>{iconForType(row.type)}{typeLabel(row.type)}</span></td><td>{row.code}</td><td><strong>{row.description.length>54?`${row.description.slice(0,54)}…`:row.description}</strong><small>{row.subcategory}</small></td><td>{row.client}</td><td><strong>{row.point}</strong><small>{row.post}</small></td><td><strong>{row.reportedBy}</strong><small>{roleLabel(row.reporterRole)}</small></td><td>{formatDateTime(row.createdAt)}</td><td><span className={`nov-status ${statusClass(row.status)}`}>{statusLabel(row.status)}</span></td><td><button onClick={e=>{e.stopPropagation();openModal(row)}}>Ver <ChevronRight size={13}/></button></td></tr>)}
          {!rows.length&&<tr><td colSpan={9} className="empty">{dateError?'Corrija el rango de fechas para consultar resultados.':'No se encontraron novedades con los filtros aplicados.'}</td></tr>}
        </tbody></table></div>
        <div className="nov-results-footer">Mostrando {rows.length?1:0} a {rows.length} de {rows.length} novedades</div>
      </div>
    </section>

    {selected&&draft&&<div className="nov-modal-backdrop" onClick={closeModal}>
      <aside className="nov-modal" onClick={e=>e.stopPropagation()}>
        <header><div><h3>{selected.code} · {typeLabel(selected.type)}</h3><span>Revisar y editar la novedad antes de aprobar o descartar.</span></div><button onClick={closeModal}><X size={19}/></button></header>

        <div className="nov-modal-body">
          <div className="nov-edit-grid three">
            <ModalField label="Tipo de novedad"><select value={draft.type} disabled={!editing} onChange={e=>{const next=e.target.value as NoveltyType;updateDraft('type',next);updateDraft('subcategory',subcategories(next)[0])}}>{(['FINDING','VULNERABILITY','INCIDENT'] as NoveltyType[]).map(x=><option key={x} value={x}>{typeLabel(x)}</option>)}</select></ModalField>
            <ModalField label="Subcategoría"><select value={draft.subcategory} disabled={!editing} onChange={e=>updateDraft('subcategory',e.target.value)}>{subcategories(draft.type).map(x=><option key={x}>{x}</option>)}</select></ModalField>
            <ModalField label="Estado"><input value={statusLabel(selected.status)} disabled/></ModalField>
          </div>
          <div className="nov-edit-grid three">
            <ModalField label="Cliente"><input value={draft.client} disabled={!editing} onChange={e=>updateDraft('client',e.target.value)}/></ModalField>
            <ModalField label="Ciudad"><input value={draft.city} disabled={!editing} onChange={e=>updateDraft('city',e.target.value)}/></ModalField>
            <ModalField label="Compañía"><input value={draft.company} disabled={!editing} onChange={e=>updateDraft('company',e.target.value)}/></ModalField>
          </div>
          <div className="nov-edit-grid two">
            <ModalField label="Punto"><input value={draft.point} disabled={!editing} onChange={e=>updateDraft('point',e.target.value)}/></ModalField>
            <ModalField label="Puesto"><input value={draft.post} disabled={!editing} onChange={e=>updateDraft('post',e.target.value)}/></ModalField>
          </div>
          <div className="nov-edit-grid two">
            <ModalField label="Reportado por"><input value={selected.reportedBy} disabled/></ModalField>
            <ModalField label="Cargo"><input value={roleLabel(selected.reporterRole)} disabled/></ModalField>
          </div>
          <div className="nov-edit-grid two">
            <ModalField label="Fecha y hora"><input value={formatDateTime(selected.createdAt)} disabled/></ModalField>
            <ModalField label="Coordenadas (opcional)"><div className="nov-coordinates"><Navigation size={15}/><input value={draft.latitude??''} disabled={!editing} onChange={e=>updateDraft('latitude',e.target.value===''?null:Number(e.target.value))}/><input value={draft.longitude??''} disabled={!editing} onChange={e=>updateDraft('longitude',e.target.value===''?null:Number(e.target.value))}/></div></ModalField>
          </div>

          <ModalField label="Descripción de la novedad"><textarea value={draft.description} disabled={!editing} maxLength={500} onChange={e=>updateDraft('description',e.target.value)}/><small className="nov-count">{draft.description.length}/500</small></ModalField>

          <div className="nov-photo-block"><div className="nov-section-title"><strong>Evidencia fotográfica ({draft.photoCount})</strong>{editing&&draft.photoCount<6&&<button onClick={addPhoto}><Camera size={14}/>Agregar foto</button>}</div><div className="nov-photo-grid">{Array.from({length:draft.photoCount}).map((_,i)=><article key={i}><FileImage size={25}/><span>Foto {i+1}</span><small>SGI: Operador</small></article>)}</div></div>

          <div className="nov-audit"><h4>Historial / Trazabilidad</h4>{selected.audit.map(event=><article key={event.id}><i/><div><strong>{event.title}</strong><span>{formatDateTime(event.at)} · {event.actor}</span><small>{event.detail}</small></div></article>)}</div>

          <ModalField label="Comentario de revisión"><textarea value={reviewComment} disabled={selected.status!=='PENDING'} maxLength={500} placeholder="Ingrese un comentario (opcional al aprobar; obligatorio al descartar)…" onChange={e=>setReviewComment(e.target.value)}/><small className="nov-count">{reviewComment.length}/500</small></ModalField>
          {selected.status==='APPROVED'&&<div className="nov-publication approved"><CheckCircle2 size={16}/><span>Aprobada: esta novedad está habilitada para SGI: Cliente.</span></div>}
          {selected.status==='DISCARDED'&&<div className="nov-publication discarded"><Trash2 size={16}/><span>Descartada: se conserva en histórico y no se publica a SGI: Cliente.</span></div>}
          {modalError&&<div className="nov-modal-error"><AlertTriangle size={15}/>{modalError}</div>}
        </div>

        <footer>
          {selected.status==='PENDING'&&canReview(user)&&<>
            {!editing?<button onClick={()=>setEditing(true)}><Pencil size={15}/>Editar</button>:<button onClick={saveEdit}><Save size={15}/>Guardar cambios</button>}
            <button className="discard" onClick={discard}><Trash2 size={15}/>Descartar</button>
            <button className="approve" onClick={approve}><CheckCircle2 size={15}/>Aprobar</button>
          </>}
          <button className="close" onClick={closeModal}><X size={15}/>Cerrar</button>
        </footer>
        <div className="nov-modal-hints">{selected.status==='PENDING'&&<><span className="discard-hint">Al descartar, el comentario de revisión es obligatorio.</span><span className="approve-hint">Al aprobar, la novedad será visible en SGI: Cliente.</span></>}</div>
      </aside>
    </div>}
  </div>
}

function userLabel(user:UatUser){return ({presidente:'Presidencia UAT',dlatam:'Director Operaciones LATAM UAT',don:'Director Operaciones Nacional UAT',dnacional:'Director Nacional UAT',dzonal:'Director Zonal UAT',jregional:'Jefe Regional UAT',coord:'Coordinador UAT',asistente:'Asistente de Coordinación UAT',supervisor:'Supervisor UAT',agente:'Agente UAT',cliente:'Cliente UAT'}[user])}
function Metric({icon,label,value,tone}:{icon:ReactNode;label:string;value:number;tone:'blue'|'teal'|'amber'|'red'|'green'}){return <article className={`nov-metric ${tone}`}><span>{icon}</span><div><small>{label}</small><strong>{value}</strong></div></article>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="nov-field"><span>{label}</span>{children}</label>}
function ModalField({label,children}:{label:string;children:ReactNode}){return <label className="nov-modal-field"><span>{label}</span>{children}</label>}
