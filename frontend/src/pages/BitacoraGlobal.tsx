import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {
  Building2, Briefcase, CalendarDays, CarFront, Clock3, Container, Download, Eye, FileSearch2,
  FilterX, MapPin, Search, ShieldCheck, UserRound, X, ChevronRight
} from 'lucide-react';
import {api, getUser} from '../api';
import {PhotoEvidenceGallery, ReferenceImageGallery} from '../components/PhotoEvidenceGallery';

type RecordType='PERSON'|'VEHICLE'|'CONTAINER';
type RecordStatus='AUTHORIZED'|'REGISTERED'|'RELEASED'|'UNDER_REVIEW'|'RETAINED';

type TimelineEvent={id:string;at:string;title:string;place:string};
type DynamicField={fieldId:string;name:string;value:string};

type BitRecord={
  id:string;
  photoCount?:number;
  type:RecordType;
  identifier:string;
  label:string;
  firstName?:string;
  lastName?:string;
  client:string;
  company:string;
  companyCode:string;
  city:string;
  point:string;
  region:string;
  zone:string;
  status:RecordStatus;
  at:string;
  detailTag:string;
  subtitle:string;
  timeline:TimelineEvent[];
  dynamicFields?:DynamicField[];
  applicationType?:'INGRESO'|'EGRESO'|'AMBOS';
};

type Filters={
  type:'ALL'|RecordType;
  query:string;
  firstName:string;
  lastName:string;
  identifier:string;
  city:string;
  client:string;
  company:string;
  point:string;
  status:'ALL'|RecordStatus;
  dateFrom:string;
  dateTo:string;
};

const EMPTY_FILTERS:Filters={
  type:'ALL',query:'',firstName:'',lastName:'',identifier:'',city:'',client:'',company:'ALL',point:'',status:'ALL',dateFrom:'',dateTo:''
};

const typeLabel=(type:RecordType)=>type==='PERSON'?'Persona':type==='VEHICLE'?'Vehículo':'Contenedor';
const statusLabel=(status:RecordStatus)=>({AUTHORIZED:'Autorizado',REGISTERED:'Ingreso',RELEASED:'Salida',UNDER_REVIEW:'En revisión',RETAINED:'Retenido'}[status]);
const statusClass=(status:RecordStatus)=>({AUTHORIZED:'success',REGISTERED:'info',RELEASED:'success',UNDER_REVIEW:'warning',RETAINED:'danger'}[status]);
const iconByType=(type:RecordType)=>type==='PERSON'?<UserRound size={16}/>:type==='VEHICLE'?<CarFront size={16}/>:<Container size={16}/>;

function formatDateTime(value:string){
  return new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));
}
function dateOnly(value:string){return value.slice(0,10)}

export default function BitacoraGlobal(){
  const [userRevision,setUserRevision]=useState(0);
  const [filters,setFilters]=useState<Filters>(EMPTY_FILTERS);
  const [viewedId,setViewedId]=useState('');
  const [liveData,setLiveData]=useState<BitRecord[]>([]);
  const [liveLoaded,setLiveLoaded]=useState(false);
  const [loadError,setLoadError]=useState('');

  useEffect(()=>{
    const onStorage=()=>setUserRevision(x=>x+1);
    window.addEventListener('storage',onStorage);
    return ()=>window.removeEventListener('storage',onStorage);
  },[]);

  const user=getUser();
  useEffect(()=>{
    let active=true;
    setLiveData([]);
    setLiveLoaded(false);
    setLoadError('');
    api.logbookRecords()
      .then(records=>{if(active){setLiveData(records as BitRecord[]);setLiveLoaded(true)}})
      .catch(error=>{if(active){setLiveData([]);setLiveLoaded(true);setLoadError(error instanceof Error?error.message:'No se pudo consultar Bitácora')}});
    return()=>{active=false};
  },[user,userRevision]);
  const scopedData=liveData;

  const companyOptions=useMemo(()=>Array.from(new Map(scopedData.map(r=>[r.companyCode,r.company])).values()),[scopedData]);
  const cityOptions=useMemo(()=>Array.from(new Set(scopedData.map(r=>r.city))).sort(),[scopedData]);
  const clientOptions=useMemo(()=>Array.from(new Set(scopedData.map(r=>r.client))).sort(),[scopedData]);

  const rows=useMemo(()=>scopedData.filter(record=>{
    if(filters.type!=='ALL' && record.type!==filters.type) return false;
    const q=filters.query.trim().toLowerCase();
    if(q){
      const haystack=`${record.label} ${record.identifier} ${record.client} ${record.point} ${record.city}`.toLowerCase();
      if(!haystack.includes(q)) return false;
    }
    if(filters.firstName && !(record.firstName??'').toLowerCase().includes(filters.firstName.toLowerCase())) return false;
    if(filters.lastName && !(record.lastName??'').toLowerCase().includes(filters.lastName.toLowerCase())) return false;
    if(filters.identifier && !record.identifier.toLowerCase().includes(filters.identifier.toLowerCase())) return false;
    if(filters.city && record.city!==filters.city) return false;
    if(filters.client && record.client!==filters.client) return false;
    if(filters.company!=='ALL' && record.company!==filters.company) return false;
    if(filters.point && !record.point.toLowerCase().includes(filters.point.toLowerCase())) return false;
    if(filters.status!=='ALL' && record.status!==filters.status) return false;
    if(filters.dateFrom && dateOnly(record.at) < filters.dateFrom) return false;
    if(filters.dateTo && dateOnly(record.at) > filters.dateTo) return false;
    return true;
  }).sort((a,b)=>b.at.localeCompare(a.at)),[scopedData,filters]);

  const viewed=scopedData.find(r=>r.id===viewedId) ?? null;
  const metrics=useMemo(()=>({
    total:rows.length,
    people:rows.filter(r=>r.type==='PERSON').length,
    vehicles:rows.filter(r=>r.type==='VEHICLE').length,
    containers:rows.filter(r=>r.type==='CONTAINER').length,
    lastUpdate:rows[0]?.at ?? new Date().toISOString(),
  }),[rows]);

  function set<K extends keyof Filters>(key:K,value:Filters[K]){setFilters(prev=>({...prev,[key]:value}))}
  function clear(){setFilters(EMPTY_FILTERS)}
  function exportCsv(){
    const header=['Tipo','Identificador','Nombre / Descripción','Cliente','Compañía','Ciudad','Punto','Fecha / Hora','Resultado'];
    const body=rows.map(r=>[typeLabel(r.type),r.identifier,r.label,r.client,r.company,r.city,r.point,formatDateTime(r.at),statusLabel(r.status)]);
    const csv=[header,...body].map(line=>line.map(value=>`"${String(value).replaceAll('"','""')}"`).join(',')).join('\n');
    const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'});
    const url=URL.createObjectURL(blob);
    const a=document.createElement('a');
    a.href=url; a.download='bitacora_global.csv'; a.click();
    URL.revokeObjectURL(url);
  }

  return <div className="bit-global-page nov-page">
    <div className="bit-global-topbar nov-topbar">
      <div>
        <div className="page-backline">Operaciones / Bitácora</div>
        <h2>Bitácora</h2>
        <p>Buscador global de registros operativos</p>
      </div>
      <div className="bit-global-scope nov-scope"><ShieldCheck size={16}/><span>Alcance operativo del usuario</span></div>
    </div>
    {loadError&&<div className="form-error">No fue posible cargar los registros reales: {loadError}</div>}

    <div className="bit-global-kpis coord-kpis nov-kpis">
      <Metric icon={<FileSearch2 size={22}/>} label="Resultados" value={metrics.total} subtitle="registros encontrados" tone="blue"/>
      <Metric icon={<UserRound size={22}/>} label="Personas" value={metrics.people} subtitle="registros" tone="green"/>
      <Metric icon={<CarFront size={22}/>} label="Vehículos" value={metrics.vehicles} subtitle="registros" tone="green"/>
      <Metric icon={<Container size={22}/>} label="Contenedores" value={metrics.containers} subtitle="registros" tone="purple"/>
      <Metric icon={<Clock3 size={22}/>} label="Última actualización" value={new Intl.DateTimeFormat('es-EC',{hour:'2-digit',minute:'2-digit'}).format(new Date(metrics.lastUpdate))} subtitle={new Intl.DateTimeFormat('es-EC',{weekday:'long',day:'2-digit',month:'short'}).format(new Date(metrics.lastUpdate))} tone="gray"/>
    </div>

    <div className="bit-global-layout">
      <section className="bit-global-search-card nov-main-card">
        <div className="bit-global-card-head">
          <div className="bit-global-card-title"><Search size={20}/><h3>Búsqueda global</h3></div>
          <div className="bit-global-tabs">
            <button className={filters.type==='ALL'?'active':''} onClick={()=>set('type','ALL')}>Todos</button>
            <button className={filters.type==='PERSON'?'active':''} onClick={()=>set('type','PERSON')}>Personas</button>
            <button className={filters.type==='VEHICLE'?'active':''} onClick={()=>set('type','VEHICLE')}>Vehículos</button>
            <button className={filters.type==='CONTAINER'?'active':''} onClick={()=>set('type','CONTAINER')}>Contenedores</button>
          </div>
        </div>

        <label className="bit-global-main-search nov-main-search"><Search size={18}/><input value={filters.query} onChange={e=>set('query',e.target.value)} placeholder="Buscar por nombre, apellido, identificación, placa, contenedor…"/></label>

        <div className="bit-global-filter-grid nov-filter-grid">
          <Field label="Nombres"><input value={filters.firstName} onChange={e=>set('firstName',e.target.value)} placeholder="Ej. Juan"/></Field>
          <Field label="Apellidos"><input value={filters.lastName} onChange={e=>set('lastName',e.target.value)} placeholder="Ej. Pérez"/></Field>
          <Field label="Identificación"><input value={filters.identifier} onChange={e=>set('identifier',e.target.value)} placeholder="CI / Placa / Contenedor"/></Field>
          <Field label="Ciudad"><select value={filters.city} onChange={e=>set('city',e.target.value)}><option value="">Todas</option>{cityOptions.map(c=><option key={c} value={c}>{c}</option>)}</select></Field>
          <Field label="Cliente"><select value={filters.client} onChange={e=>set('client',e.target.value)}><option value="">Todos</option>{clientOptions.map(c=><option key={c} value={c}>{c}</option>)}</select></Field>
          <Field label="Compañía"><select value={filters.company} onChange={e=>set('company',e.target.value)}><option value="ALL">Todas</option>{companyOptions.map(c=><option key={c} value={c}>{c}</option>)}</select></Field>
          <Field label="Punto"><input value={filters.point} onChange={e=>set('point',e.target.value)} placeholder="Ej. Acceso Principal"/></Field>
          <Field label="Fecha desde"><input type="date" value={filters.dateFrom} onChange={e=>set('dateFrom',e.target.value)}/></Field>
          <Field label="Fecha hasta"><input type="date" value={filters.dateTo} onChange={e=>set('dateTo',e.target.value)}/></Field>
          <Field label="Estado / Resultado"><select value={filters.status} onChange={e=>set('status',e.target.value as Filters['status'])}><option value="ALL">Todos</option><option value="AUTHORIZED">Autorizado</option><option value="REGISTERED">Registrado</option><option value="RELEASED">Liberado</option><option value="UNDER_REVIEW">En revisión</option><option value="RETAINED">Retenido</option></select></Field>
        </div>

        <div className="bit-global-search-actions nov-actions">
          <button className="primary"><Search size={16}/>Buscar</button>
          <button onClick={clear}><FilterX size={16}/>Limpiar filtros</button>
          <button className="export" onClick={exportCsv}><Download size={16}/>Exportar</button>
        </div>

        <div className="bit-global-results-card nov-results-card">
          <div className="bit-global-results-head nov-results-head">
            <h3>Resultados ({rows.length})</h3>
            <span>Ordenado por <strong>Fecha más reciente</strong></span>
          </div>
          <div className="bit-global-table-wrap nov-table-wrap">
            <table className="bit-global-table nov-table">
              <thead><tr><th>Tipo</th><th>Identificador</th><th>Nombre / Descripción</th><th>Cliente</th><th>Ciudad</th><th>Punto</th><th>Fecha / Hora</th><th>Resultado</th><th></th></tr></thead>
              <tbody>
                {rows.map(row=><tr key={row.id} onClick={()=>setViewedId(row.id)}>
                  <td><span className={`bit-global-type type-${row.type.toLowerCase()}`}>{iconByType(row.type)} {typeLabel(row.type)}</span></td>
                  <td>{row.identifier}</td>
                  <td><strong>{row.label}</strong><small>{row.company}</small></td>
                  <td>{row.client}</td>
                  <td>{row.city}</td>
                  <td>{row.point}</td>
                  <td>{formatDateTime(row.at)}</td>
                  <td><span className={`bit-global-status ${statusClass(row.status)}`}>{statusLabel(row.status)}</span></td>
                  <td><button onClick={e=>{e.stopPropagation();setViewedId(row.id)}}>Ver <ChevronRight size={13}/></button></td>
                </tr>)}
                {!rows.length&&<tr><td colSpan={9} className="empty">No se encontraron registros con los filtros aplicados.</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="bit-global-results-footer nov-results-footer">Mostrando {rows.length?1:0} a {rows.length} de {rows.length} resultados</div>
        </div>
      </section>


    </div>
    {viewed&&<div className="nov-modal-backdrop record-view-backdrop" onClick={()=>setViewedId('')}><aside className="nov-modal record-view-modal" role="dialog" aria-modal="true" aria-labelledby="record-view-title" onClick={e=>e.stopPropagation()}><header><div><h3 id="record-view-title">{viewed.identifier} · {typeLabel(viewed.type)}</h3><span>Detalle del registro · Solo lectura</span></div><button aria-label="Cerrar detalle" onClick={()=>setViewedId('')}><X size={19}/></button></header><div className="nov-modal-body record-view-body"><div className="record-view-summary"><span className={`bit-global-type type-${viewed.type.toLowerCase()}`}>{iconByType(viewed.type)}{typeLabel(viewed.type)}</span><strong>{viewed.label}</strong><span className={`bit-global-status ${statusClass(viewed.status)}`}>{statusLabel(viewed.status)}</span></div><dl className="bit-global-detail-list record-view-details"><div><dt><Building2 size={15}/>Cliente</dt><dd>{viewed.client}</dd></div><div><dt><Briefcase size={15}/>Compañía</dt><dd>{viewed.company}</dd></div><div><dt><MapPin size={15}/>Ciudad</dt><dd>{viewed.city}</dd></div><div><dt><MapPin size={15}/>Punto</dt><dd>{viewed.point}</dd></div><div><dt><Eye size={15}/>Identificador</dt><dd>{viewed.identifier}</dd></div><div><dt><CalendarDays size={15}/>Fecha / Hora</dt><dd>{formatDateTime(viewed.at)}</dd></div><div><dt><ShieldCheck size={15}/>Estado</dt><dd>{statusLabel(viewed.status)}</dd></div></dl>{Boolean(viewed.dynamicFields?.length)&&<div className="bit-global-timeline record-view-timeline"><h4>Campos registrados</h4>{viewed.dynamicFields!.map(field=><article key={field.fieldId}><i/><div><strong>{field.name}</strong><small>{field.value||'—'}</small></div></article>)}</div>}<PhotoEvidenceGallery eventId={liveLoaded?viewed.id:undefined} declaredCount={viewed.photoCount}/><ReferenceImageGallery eventId={liveLoaded?viewed.id:undefined}/><div className="bit-global-timeline record-view-timeline"><h4>Ingreso y salida</h4>{viewed.timeline.map((event,index)=><article key={event.id}><i className={index===0?'active':''}/><div><span>{formatDateTime(event.at)}</span><strong>{event.title}</strong><small>{event.place}</small></div></article>)}</div></div><footer><button className="close" onClick={()=>setViewedId('')}><X size={15}/>Cerrar</button></footer></aside></div>}
  </div>
}

function Metric({icon,label,value,subtitle,tone}:{icon:ReactNode;label:string;value:string|number;subtitle:string;tone:'blue'|'green'|'purple'|'gray'}){
  return <article><span className={tone}>{icon}</span><div><small>{label}</small><strong>{value}</strong><em>{subtitle}</em></div></article>
}
function Field({label,children}:{label:string;children:ReactNode}){
  return <label className="bit-global-field"><span>{label}</span>{children}</label>
}
function ReadOnlyField({label,value}:{label:string;value:string}){
  return <label className="nov-modal-field"><span>{label}</span><input value={value} disabled readOnly/></label>
}
