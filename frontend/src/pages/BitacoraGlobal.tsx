import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {
  CalendarDays, CarFront, Clock3, Container, Download, FileSearch2,
  FilterX, MapPin, Search, ShieldCheck, UserRound, Building2, Briefcase
} from 'lucide-react';
import {getUser, type UatUser} from '../api';

type RecordType='PERSON'|'VEHICLE'|'CONTAINER';
type RecordStatus='AUTHORIZED'|'REGISTERED'|'RELEASED'|'UNDER_REVIEW'|'RETAINED';

type TimelineEvent={id:string;at:string;title:string;place:string};

type BitRecord={
  id:string;
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
};

type Scope={label:string;companies:string[]|null;region?:string;zone?:string};

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

const DATA:BitRecord[]=[
  {
    id:'rec-1', type:'PERSON', identifier:'0923456789', label:'Carlos Mendoza', firstName:'Carlos', lastName:'Mendoza',
    client:'Maersk', company:'Seguridad Integral S.A.', companyCode:'SISA', city:'Guayaquil', point:'TPG · Acceso Principal',
    region:'Costa Sur', zone:'Zona Costa', status:'AUTHORIZED', at:'2026-09-19T14:28:00', detailTag:'Persona', subtitle:'CI: 0923456789',
    timeline:[
      {id:'t1',at:'2026-09-19T14:28:00',title:'Ingreso autorizado',place:'TPG · Acceso Principal'},
      {id:'t2',at:'2026-09-19T14:16:00',title:'Validación documental',place:'Oficina de seguridad'},
      {id:'t3',at:'2026-09-19T14:10:00',title:'Registro en sistema',place:'Recepción'}
    ]
  },
  {
    id:'rec-2', type:'VEHICLE', identifier:'GTR-4587', label:'Camión Hino 500',
    client:'DP World', company:'Galvarino', companyCode:'GAL', city:'Guayaquil', point:'Acceso Norte',
    region:'Costa Sur', zone:'Zona Costa', status:'REGISTERED', at:'2026-09-19T14:15:00', detailTag:'Vehículo', subtitle:'Placa: GTR-4587',
    timeline:[
      {id:'t1',at:'2026-09-19T14:15:00',title:'Ingreso registrado',place:'Acceso Norte'},
      {id:'t2',at:'2026-09-19T14:13:00',title:'Inspección visual',place:'Carril 2'},
      {id:'t3',at:'2026-09-19T14:10:00',title:'Acreditación de conductor',place:'Garita principal'}
    ]
  },
  {
    id:'rec-3', type:'CONTAINER', identifier:'MSKU1234567', label:'Contenedor 40 HC',
    client:'Maersk', company:'Galvarino', companyCode:'GAL', city:'Posorja', point:'Patio A',
    region:'Costa Sur', zone:'Zona Costa', status:'RELEASED', at:'2026-09-19T13:50:00', detailTag:'Contenedor', subtitle:'MSKU1234567',
    timeline:[
      {id:'t1',at:'2026-09-19T13:50:00',title:'Liberado',place:'Patio A'},
      {id:'t2',at:'2026-09-19T13:32:00',title:'Verificación de sello',place:'Inspección de contenedores'},
      {id:'t3',at:'2026-09-19T13:20:00',title:'Registro en sistema',place:'Patio A'}
    ]
  },
  {
    id:'rec-4', type:'PERSON', identifier:'1712345678', label:'Ana Torres', firstName:'Ana', lastName:'Torres',
    client:'Contecon', company:'Litoral Custodia', companyCode:'LIT', city:'Guayaquil', point:'Ingreso Peatonal',
    region:'Costa Centro', zone:'Zona Costa', status:'AUTHORIZED', at:'2026-09-19T13:32:00', detailTag:'Persona', subtitle:'CI: 1712345678',
    timeline:[
      {id:'t1',at:'2026-09-19T13:32:00',title:'Ingreso autorizado',place:'Ingreso Peatonal'},
      {id:'t2',at:'2026-09-19T13:28:00',title:'Verificación biométrica',place:'Control de acceso'},
      {id:'t3',at:'2026-09-19T13:25:00',title:'Creación de visita',place:'Recepción'}
    ]
  },
  {
    id:'rec-5', type:'VEHICLE', identifier:'PBB-7621', label:'Furgón Isuzu',
    client:'DHL', company:'Seguridad Integral S.A.', companyCode:'SISA', city:'Durán', point:'Báscula',
    region:'Costa Sur', zone:'Zona Costa', status:'UNDER_REVIEW', at:'2026-09-19T12:48:00', detailTag:'Vehículo', subtitle:'Placa: PBB-7621',
    timeline:[
      {id:'t1',at:'2026-09-19T12:48:00',title:'En revisión',place:'Báscula'},
      {id:'t2',at:'2026-09-19T12:42:00',title:'Diferencia en documentación',place:'Garita de ingreso'},
      {id:'t3',at:'2026-09-19T12:39:00',title:'Ingreso preliminar',place:'Báscula'}
    ]
  },
  {
    id:'rec-6', type:'CONTAINER', identifier:'TCLU9876543', label:'Contenedor 20 DC',
    client:'MSC', company:'Galvarino', companyCode:'GAL', city:'Guayaquil', point:'Patio B',
    region:'Costa Sur', zone:'Zona Costa', status:'RETAINED', at:'2026-09-19T12:10:00', detailTag:'Contenedor', subtitle:'TCLU9876543',
    timeline:[
      {id:'t1',at:'2026-09-19T12:10:00',title:'Retenido',place:'Patio B'},
      {id:'t2',at:'2026-09-19T11:56:00',title:'Alerta por sello inconsistente',place:'Control documental'},
      {id:'t3',at:'2026-09-19T11:50:00',title:'Registro de arribo',place:'Patio B'}
    ]
  },
  {
    id:'rec-7', type:'PERSON', identifier:'1104567890', label:'Jorge Paredes', firstName:'Jorge', lastName:'Paredes',
    client:'Holcim', company:'Andina Protección', companyCode:'AND', city:'Cuenca', point:'Recepción Planta',
    region:'Austro', zone:'Zona Sierra', status:'AUTHORIZED', at:'2026-09-18T09:35:00', detailTag:'Persona', subtitle:'CI: 1104567890',
    timeline:[
      {id:'t1',at:'2026-09-18T09:35:00',title:'Ingreso autorizado',place:'Recepción Planta'},
      {id:'t2',at:'2026-09-18T09:30:00',title:'Verificación de visita',place:'Recepción Planta'}
    ]
  },
  {
    id:'rec-8', type:'VEHICLE', identifier:'QWE-1190', label:'Camioneta Toyota Hilux',
    client:'Holcim', company:'Andina Protección', companyCode:'AND', city:'Cuenca', point:'Portón 2',
    region:'Austro', zone:'Zona Sierra', status:'REGISTERED', at:'2026-09-18T08:12:00', detailTag:'Vehículo', subtitle:'Placa: QWE-1190',
    timeline:[
      {id:'t1',at:'2026-09-18T08:12:00',title:'Registro en sistema',place:'Portón 2'},
      {id:'t2',at:'2026-09-18T08:07:00',title:'Lectura de placa',place:'Portón 2'}
    ]
  },
  {
    id:'rec-9', type:'CONTAINER', identifier:'SEGU4455661', label:'Contenedor reefer 40',
    client:'Bananera El Oro', company:'Sur Custodia', companyCode:'SUR', city:'Machala', point:'Muelle Sur',
    region:'Costa Sur', zone:'Zona Costa', status:'REGISTERED', at:'2026-09-18T11:22:00', detailTag:'Contenedor', subtitle:'SEGU4455661',
    timeline:[
      {id:'t1',at:'2026-09-18T11:22:00',title:'Ingreso registrado',place:'Muelle Sur'},
      {id:'t2',at:'2026-09-18T11:05:00',title:'Captura de número de contenedor',place:'Muelle Sur'}
    ]
  },
  {
    id:'rec-10', type:'PERSON', identifier:'0951112223', label:'María Vizuete', firstName:'María', lastName:'Vizuete',
    client:'Nirsa', company:'Galvarino', companyCode:'GAL', city:'Durán', point:'Acceso Administrativo',
    region:'Costa Sur', zone:'Zona Costa', status:'AUTHORIZED', at:'2026-09-17T16:41:00', detailTag:'Persona', subtitle:'CI: 0951112223',
    timeline:[
      {id:'t1',at:'2026-09-17T16:41:00',title:'Ingreso autorizado',place:'Acceso Administrativo'},
      {id:'t2',at:'2026-09-17T16:35:00',title:'Autorización del anfitrión',place:'Recepción'}
    ]
  },
  {
    id:'rec-11', type:'VEHICLE', identifier:'AAA-9021', label:'Tractocamión Volvo FH',
    client:'Nirsa', company:'Galvarino', companyCode:'GAL', city:'Durán', point:'Carga y descarga',
    region:'Costa Sur', zone:'Zona Costa', status:'RELEASED', at:'2026-09-17T18:05:00', detailTag:'Vehículo', subtitle:'Placa: AAA-9021',
    timeline:[
      {id:'t1',at:'2026-09-17T18:05:00',title:'Salida liberada',place:'Carga y descarga'},
      {id:'t2',at:'2026-09-17T17:40:00',title:'Inspección final',place:'Báscula'}
    ]
  },
  {
    id:'rec-12', type:'CONTAINER', identifier:'OOLU1002003', label:'Contenedor 20 OT',
    client:'DP World', company:'Litoral Custodia', companyCode:'LIT', city:'Guayaquil', point:'Patio C',
    region:'Costa Centro', zone:'Zona Costa', status:'UNDER_REVIEW', at:'2026-09-16T10:18:00', detailTag:'Contenedor', subtitle:'OOLU1002003',
    timeline:[
      {id:'t1',at:'2026-09-16T10:18:00',title:'En revisión',place:'Patio C'},
      {id:'t2',at:'2026-09-16T09:55:00',title:'Diferencia documental',place:'Oficina de control'}
    ]
  }
];

const scopeByUser:Record<UatUser,Scope>={
  presidente:{label:'Alcance: nacional / todas las compañías',companies:null},
  dlatam:{label:'Alcance: nacional / todas las compañías',companies:null},
  don:{label:'Alcance: nacional / todas las compañías',companies:null},
  dnacional:{label:'Alcance: nacional / todas las compañías',companies:null},
  dzonal:{label:'Alcance: 5 compañías / Zona Costa',companies:['GAL','SISA','LIT','SUR'],zone:'Zona Costa'},
  jregional:{label:'Alcance: 3 compañías / Región Costa Sur',companies:['GAL','SISA','SUR'],region:'Costa Sur'},
  coord:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  asistente:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  supervisor:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  agente:{label:'Alcance: 1 compañía / Galvarino',companies:['GAL']},
  cliente:{label:'Alcance restringido / solo su operación',companies:['GAL']},
};

const EMPTY_FILTERS:Filters={
  type:'ALL',query:'',firstName:'',lastName:'',identifier:'',city:'',client:'',company:'ALL',point:'',status:'ALL',dateFrom:'',dateTo:''
};

const typeLabel=(type:RecordType)=>type==='PERSON'?'Persona':type==='VEHICLE'?'Vehículo':'Contenedor';
const statusLabel=(status:RecordStatus)=>({AUTHORIZED:'Autorizado',REGISTERED:'Registrado',RELEASED:'Liberado',UNDER_REVIEW:'En revisión',RETAINED:'Retenido'}[status]);
const statusClass=(status:RecordStatus)=>({AUTHORIZED:'success',REGISTERED:'info',RELEASED:'success',UNDER_REVIEW:'warning',RETAINED:'danger'}[status]);
const iconByType=(type:RecordType)=>type==='PERSON'?<UserRound size={16}/>:type==='VEHICLE'?<CarFront size={16}/>:<Container size={16}/>;

function formatDateTime(value:string){
  return new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));
}
function dateOnly(value:string){return value.slice(0,10)}
function initials(name:string){return name.split(' ').filter(Boolean).map(x=>x[0]).slice(0,2).join('').toUpperCase()}

function withinScope(record:BitRecord,scope:Scope){
  if(scope.companies && !scope.companies.includes(record.companyCode)) return false;
  if(scope.region && record.region!==scope.region) return false;
  if(scope.zone && record.zone!==scope.zone) return false;
  return true;
}

export default function BitacoraGlobal(){
  const [userRevision,setUserRevision]=useState(0);
  const [filters,setFilters]=useState<Filters>(EMPTY_FILTERS);
  const [selectedId,setSelectedId]=useState('');

  useEffect(()=>{
    const onStorage=()=>setUserRevision(x=>x+1);
    window.addEventListener('storage',onStorage);
    return ()=>window.removeEventListener('storage',onStorage);
  },[]);

  const user=getUser();
  const scope=scopeByUser[user];
  const scopedData=useMemo(()=>DATA.filter(record=>withinScope(record,scope)),[scope,userRevision]);

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

  useEffect(()=>{
    if(rows.length && !rows.some(r=>r.id===selectedId)) setSelectedId(rows[0].id);
    if(!rows.length) setSelectedId('');
  },[rows,selectedId]);

  const selected=rows.find(r=>r.id===selectedId) ?? rows[0] ?? null;
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

  return <div className="bit-global-page">
    <div className="bit-global-topbar">
      <div>
        <div className="page-backline">Operaciones / Bitácora</div>
        <h2>Bitácora</h2>
        <p>Buscador global de registros operativos</p>
      </div>
      <div className="bit-global-scope"><ShieldCheck size={16}/><span>{scope.label}</span></div>
    </div>

    <div className="bit-global-kpis">
      <Metric icon={<FileSearch2 size={22}/>} label="Resultados" value={metrics.total} subtitle="registros encontrados" tone="blue"/>
      <Metric icon={<UserRound size={22}/>} label="Personas" value={metrics.people} subtitle="registros" tone="green"/>
      <Metric icon={<CarFront size={22}/>} label="Vehículos" value={metrics.vehicles} subtitle="registros" tone="green"/>
      <Metric icon={<Container size={22}/>} label="Contenedores" value={metrics.containers} subtitle="registros" tone="purple"/>
      <Metric icon={<Clock3 size={22}/>} label="Última actualización" value={new Intl.DateTimeFormat('es-EC',{hour:'2-digit',minute:'2-digit'}).format(new Date(metrics.lastUpdate))} subtitle={new Intl.DateTimeFormat('es-EC',{weekday:'long',day:'2-digit',month:'short'}).format(new Date(metrics.lastUpdate))} tone="gray"/>
    </div>

    <div className="bit-global-layout">
      <section className="bit-global-search-card">
        <div className="bit-global-card-head">
          <div className="bit-global-card-title"><Search size={20}/><h3>Búsqueda global</h3></div>
          <div className="bit-global-tabs">
            <button className={filters.type==='ALL'?'active':''} onClick={()=>set('type','ALL')}>Todos</button>
            <button className={filters.type==='PERSON'?'active':''} onClick={()=>set('type','PERSON')}>Personas</button>
            <button className={filters.type==='VEHICLE'?'active':''} onClick={()=>set('type','VEHICLE')}>Vehículos</button>
            <button className={filters.type==='CONTAINER'?'active':''} onClick={()=>set('type','CONTAINER')}>Contenedores</button>
          </div>
        </div>

        <label className="bit-global-main-search"><Search size={18}/><input value={filters.query} onChange={e=>set('query',e.target.value)} placeholder="Buscar por nombre, apellido, identificación, placa, contenedor…"/></label>

        <div className="bit-global-filter-grid">
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

        <div className="bit-global-search-actions">
          <button className="primary"><Search size={16}/>Buscar</button>
          <button onClick={clear}><FilterX size={16}/>Limpiar filtros</button>
          <button className="export" onClick={exportCsv}><Download size={16}/>Exportar</button>
        </div>

        <div className="bit-global-results-card">
          <div className="bit-global-results-head">
            <h3>Resultados ({rows.length})</h3>
            <div>Ordenado por <strong>Fecha más reciente</strong></div>
          </div>
          <div className="bit-global-table-wrap">
            <table className="bit-global-table">
              <thead><tr><th>Tipo</th><th>Identificador</th><th>Nombre / Descripción</th><th>Cliente</th><th>Ciudad</th><th>Punto</th><th>Fecha / Hora</th><th>Resultado</th><th></th></tr></thead>
              <tbody>
                {rows.map(row=><tr key={row.id} className={selectedId===row.id?'selected':''} onClick={()=>setSelectedId(row.id)}>
                  <td><span className={`bit-global-type type-${row.type.toLowerCase()}`}>{iconByType(row.type)} {typeLabel(row.type)}</span></td>
                  <td>{row.identifier}</td>
                  <td><strong>{row.label}</strong><small>{row.company}</small></td>
                  <td>{row.client}</td>
                  <td>{row.city}</td>
                  <td>{row.point}</td>
                  <td>{formatDateTime(row.at)}</td>
                  <td><span className={`bit-global-status ${statusClass(row.status)}`}>{statusLabel(row.status)}</span></td>
                  <td><button className="ghost">Ver detalle</button></td>
                </tr>)}
                {!rows.length&&<tr><td colSpan={9} className="empty">No se encontraron registros con los filtros aplicados.</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="bit-global-results-footer">Mostrando {rows.length?1:0} a {rows.length} de {rows.length} resultados</div>
        </div>
      </section>

      <aside className="bit-global-detail-card">
        <div className="bit-global-card-title"><Briefcase size={19}/><h3>Detalle rápido</h3></div>
        {selected?<>
          <div className="bit-global-profile">
            <div className="avatar">{initials(selected.label)}</div>
            <div>
              <div className="bit-global-detail-tag">{typeLabel(selected.type)}</div>
              <h4>{selected.label}</h4>
              <p>{selected.subtitle}</p>
              <span className={`bit-global-status ${statusClass(selected.status)}`}>{statusLabel(selected.status)}</span>
            </div>
          </div>
          <dl className="bit-global-detail-list">
            <div><dt><Building2 size={15}/>Cliente</dt><dd>{selected.client}</dd></div>
            <div><dt><Briefcase size={15}/>Compañía</dt><dd>{selected.company}</dd></div>
            <div><dt><MapPin size={15}/>Ciudad</dt><dd>{selected.city}</dd></div>
            <div><dt><MapPin size={15}/>Punto</dt><dd>{selected.point}</dd></div>
            <div><dt><CalendarDays size={15}/>Fecha / Hora</dt><dd>{formatDateTime(selected.at)}</dd></div>
          </dl>
          <div className="bit-global-timeline">
            <h4>Línea de tiempo</h4>
            {selected.timeline.map((event,index)=><article key={event.id}><i className={index===0?'active':''}/><div><span>{formatDateTime(event.at)}</span><strong>{event.title}</strong><small>{event.place}</small></div></article>)}
          </div>
          <button className="bit-global-detail-button"><Search size={16}/>Ver detalle completo</button>
        </>:<div className="bit-global-empty-detail">Seleccione un registro para ver su detalle.</div>}
      </aside>
    </div>
  </div>
}

function Metric({icon,label,value,subtitle,tone}:{icon:ReactNode;label:string;value:string|number;subtitle:string;tone:'blue'|'green'|'purple'|'gray'}){
  return <article className={`bit-global-metric ${tone}`}><span>{icon}</span><div><small>{label}</small><strong>{value}</strong><p>{subtitle}</p></div></article>
}
function Field({label,children}:{label:string;children:ReactNode}){
  return <label className="bit-global-field"><span>{label}</span>{children}</label>
}
