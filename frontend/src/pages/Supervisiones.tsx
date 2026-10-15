import {useCallback, useEffect, useMemo, useState, type ReactNode} from 'react';
import {AlertTriangle, Camera, ChevronLeft, ChevronRight, CircleAlert, ClipboardCheck, FilterX, Search, ShieldCheck, X} from 'lucide-react';
import {api} from '../api';
import {OperationalDrawer} from '../components/OperationalDrawer';

type Level='LOW'|'MEDIUM'|'HIGH'|'VERY_HIGH';
type Row={id:string;capturedAt:string;companyId:string;companyName:string|null;pointId:string;pointName:string;clientName:string;postId:string;postCode:string;postName:string;
  supervisorEmployeeId:string;supervisorName:string;agentEmployeeId:string;agentName:string;vigilanceLevel:Level;cleanlinessLevel:Level;consignmentKnowledgeLevel:Level;situationalLevel:Level;inventoryOverallState:string;status:string};
type Detail={row:Row;supervisorComments:string|null;pointPostNotes:string|null;inventoryComments:string|null;consignmentVersion:number|null;consignmentSnapshot:string|null;hasPhoto:boolean;createdBy:string;createdAt:string;
  inventory:{assetId:string;description:string;observedState:string;comment:string|null}[];
  decisions:{noveltyId:string;noveltyType:string;title:string;severity:string|null;occurredAt:string|null;decision:string;decidedBy:string;decidedAt:string}[]};
type Option={id:string;name:string;parentId:string|null};
type Options={companies:Option[];points:Option[];posts:Option[];supervisors:Option[];agents:Option[]};
type Filters={companyId:string;pointId:string;postId:string;supervisorEmployeeId:string;agentEmployeeId:string;from:string;to:string};

const PAGE_SIZE=50;
const today=new Date();const monthAgo=new Date(today);monthAgo.setDate(monthAgo.getDate()-30);
const localDate=(d:Date)=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const EMPTY_FILTERS:Filters={companyId:'',pointId:'',postId:'',supervisorEmployeeId:'',agentEmployeeId:'',from:localDate(monthAgo),to:localDate(today)};
const EMPTY_OPTIONS:Options={companies:[],points:[],posts:[],supervisors:[],agents:[]};
const LEVELS:Record<Level,string>={LOW:'Bajo',MEDIUM:'Medio',HIGH:'Alto',VERY_HIGH:'Muy alto'};
const INVENTORY:Record<string,string>={COMPLETE:'Completo',OBSERVATIONS:'Con observaciones',INCOMPLETE:'Incompleto',CRITICAL:'Crítico'};
const ITEM_STATES:Record<string,string>={GOOD:'Bueno',DAMAGED:'Dañado',MISSING:'Faltante',NOT_VERIFIED:'No verificado'};
const NOVELTY_TYPES:Record<string,string>={FINDING:'Hallazgo',VULNERABILITY:'Vulnerabilidad',INCIDENT:'Incidente'};
const inventoryClass=(s:string)=>s==='COMPLETE'?'approved':s==='OBSERVATIONS'?'pending':'critical';
const formatDateTime=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));
const errorText=(e:unknown,fallback:string)=>e instanceof Error&&e.message?`${fallback} (${e.message.slice(0,160)})`:fallback;

export default function Supervisiones(){
  const [options,setOptions]=useState<Options>(EMPTY_OPTIONS);
  const [filters,setFilters]=useState<Filters>(EMPTY_FILTERS);
  const [applied,setApplied]=useState<Filters>(EMPTY_FILTERS);
  const [page,setPage]=useState(0);
  const [data,setData]=useState<{items:Row[];total:number}>({items:[],total:0});
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');
  const [selectedId,setSelectedId]=useState<string|null>(null);

  useEffect(()=>{api.supervisionFilters().then(setOptions).catch(()=>setOptions(EMPTY_OPTIONS))},[]);
  const load=useCallback(async(f:Filters,p:number)=>{
    setLoading(true);
    try{const res=await api.supervisionEvaluations({...f,page:p,size:PAGE_SIZE});setData({items:res.items,total:res.total});setError('')}
    catch(e){setData({items:[],total:0});setError(errorText(e,'No fue posible consultar las evaluaciones de supervisión.'))}
    finally{setLoading(false)}
  },[]);
  useEffect(()=>{void load(applied,page)},[load,applied,page]);

  const dateError=!filters.from||!filters.to?'Debe elegir una fecha inicio y una fecha fin.':filters.to<filters.from?'La fecha fin no puede ser menor que la fecha inicio.':'';
  const points=useMemo(()=>options.points.filter(x=>!filters.companyId||x.parentId===filters.companyId),[options.points,filters.companyId]);
  const posts=useMemo(()=>options.posts.filter(x=>x.parentId===filters.pointId),[options.posts,filters.pointId]);
  const pages=Math.max(1,Math.ceil(data.total/PAGE_SIZE));
  const metrics=useMemo(()=>({critical:data.items.filter(x=>x.inventoryOverallState==='CRITICAL'||x.inventoryOverallState==='INCOMPLETE').length,
    low:data.items.filter(x=>[x.vigilanceLevel,x.cleanlinessLevel,x.consignmentKnowledgeLevel,x.situationalLevel].includes('LOW')).length}),[data.items]);

  function set<K extends keyof Filters>(key:K,value:Filters[K]){setFilters(prev=>({...prev,[key]:value}))}
  function search(){if(dateError)return;setPage(0);setApplied({...filters})}
  function clear(){setFilters(EMPTY_FILTERS);setPage(0);setApplied(EMPTY_FILTERS)}

  return <div className="nov-page">
    <div className="nov-topbar"><div><div className="page-backline">Operaciones / Supervisiones</div><h2>Supervisiones</h2><p>Evaluaciones de Puesto registradas por los Supervisores desde SGI: Operador · Solo lectura</p></div><div className="nov-scope"><ShieldCheck size={16}/>Según el alcance territorial del usuario</div></div>

    <div className="coord-kpis nov-kpis">
      <Metric icon={<ClipboardCheck size={21}/>} label="Evaluaciones" detail="con los filtros aplicados" value={data.total} tone="blue"/>
      <Metric icon={<AlertTriangle size={21}/>} label="Inventario incompleto o crítico" detail="en esta página" value={metrics.critical} tone="red"/>
      <Metric icon={<CircleAlert size={21}/>} label="Con algún nivel bajo" detail="en esta página" value={metrics.low} tone="amber"/>
    </div>

    <section className="nov-main-card">
      <div className="nov-filter-grid">
        <Field label="Compañía"><select value={filters.companyId} onChange={e=>setFilters(prev=>({...prev,companyId:e.target.value,pointId:'',postId:''}))}><option value="">Todas</option>{options.companies.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field>
        <Field label="Punto"><select value={filters.pointId} onChange={e=>setFilters(prev=>({...prev,pointId:e.target.value,postId:''}))}><option value="">Todos</option>{points.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field>
        <Field label="Puesto"><select value={filters.postId} onChange={e=>set('postId',e.target.value)} disabled={!filters.pointId}><option value="">{filters.pointId?'Todos':'Seleccione punto primero'}</option>{posts.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field>
        <Field label="Supervisor"><select value={filters.supervisorEmployeeId} onChange={e=>set('supervisorEmployeeId',e.target.value)}><option value="">Todos</option>{options.supervisors.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field>
        <Field label="Agente"><select value={filters.agentEmployeeId} onChange={e=>set('agentEmployeeId',e.target.value)}><option value="">Todos</option>{options.agents.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field>
      </div>
      <div className="nov-filter-grid">
        <Field label="Fecha inicio"><input type="date" value={filters.from} onChange={e=>set('from',e.target.value)}/></Field>
        <Field label="Fecha fin"><input type="date" value={filters.to} onChange={e=>set('to',e.target.value)}/></Field>
      </div>
      {dateError&&<div className="nov-validation"><AlertTriangle size={16}/>{dateError}</div>}
      {error&&<div className="nov-validation"><AlertTriangle size={16}/>{error}</div>}
      <div className="nov-actions"><button className="primary" disabled={!!dateError||loading} onClick={search}><Search size={16}/>Buscar</button><button onClick={clear}><FilterX size={16}/>Limpiar filtros</button></div>

      <div className="nov-results-card">
        <div className="nov-results-head"><h3>Evaluaciones ({data.total})</h3><span>Ordenado por <strong>Fecha más reciente</strong></span></div>
        <div className="nov-table-wrap"><table className="nov-table"><thead><tr><th>Fecha / Hora</th><th>Cliente / Punto</th><th>Puesto</th><th>Supervisor</th><th>Agente</th><th>Vigilancia</th><th>Limpieza</th><th>Consigna</th><th>Situacional</th><th>Inventario</th><th></th></tr></thead><tbody>
          {data.items.map(row=><tr key={row.id} className={row.inventoryOverallState==='CRITICAL'?'critical':''} onClick={()=>setSelectedId(row.id)}>
            <td>{formatDateTime(row.capturedAt)}</td><td><strong>{row.clientName}</strong><small>{row.pointName}</small></td><td><strong>{row.postCode}</strong><small>{row.postName}</small></td>
            <td>{row.supervisorName}</td><td>{row.agentName}</td>
            <td><LevelChip level={row.vigilanceLevel}/></td><td><LevelChip level={row.cleanlinessLevel}/></td><td><LevelChip level={row.consignmentKnowledgeLevel}/></td><td><LevelChip level={row.situationalLevel}/></td>
            <td><span className={`nov-status ${inventoryClass(row.inventoryOverallState)}`}>{INVENTORY[row.inventoryOverallState]??row.inventoryOverallState}</span></td>
            <td><button onClick={e=>{e.stopPropagation();setSelectedId(row.id)}}>Ver <ChevronRight size={13}/></button></td></tr>)}
          {!data.items.length&&<tr><td colSpan={11} className="empty">{loading?'Consultando evaluaciones…':'No se encontraron evaluaciones con los filtros aplicados.'}</td></tr>}
        </tbody></table></div>
        <div className="nov-results-footer sup-pager"><span>Mostrando {data.items.length?page*PAGE_SIZE+1:0} a {page*PAGE_SIZE+data.items.length} de {data.total} evaluaciones</span>
          <span><button disabled={page===0||loading} onClick={()=>setPage(p=>p-1)}><ChevronLeft size={14}/>Anterior</button><b>Página {page+1} de {pages}</b><button disabled={page+1>=pages||loading} onClick={()=>setPage(p=>p+1)}>Siguiente<ChevronRight size={14}/></button></span></div>
      </div>
    </section>

    {selectedId&&<EvaluationDrawer id={selectedId} onClose={()=>setSelectedId(null)}/>}
  </div>
}

function EvaluationDrawer({id,onClose}:{id:string;onClose:()=>void}){
  const [detail,setDetail]=useState<Detail|null>(null);
  const [photo,setPhoto]=useState('');
  const [error,setError]=useState('');
  useEffect(()=>{let url='';let live=true;
    api.supervisionEvaluation(id).then(d=>{if(!live)return;setDetail(d);if(d.hasPhoto)api.supervisionPhoto(id).then(b=>{url=URL.createObjectURL(b);if(live)setPhoto(url)}).catch(()=>live&&setError('No fue posible cargar la foto del Agente.'))})
      .catch(e=>live&&setError(errorText(e,'No fue posible cargar la evaluación.')));
    return ()=>{live=false;if(url)URL.revokeObjectURL(url)}},[id]);
  const r=detail?.row;
  return <OperationalDrawer title={r?`Supervisión ${r.postCode} · ${formatDateTime(r.capturedAt)}`:'Supervisión'} subtitle="Registro recibido desde SGI Operador · Solo lectura" onClose={onClose} footer={<button className="close" onClick={onClose}><X size={15}/>Cerrar</button>}>
    {error&&<div className="nov-modal-error"><AlertTriangle size={15}/>{error}</div>}
    {!detail&&!error&&<div className="nov-results-footer">Cargando evaluación…</div>}
    {r&&detail&&<>
      <div className="sup-head">
        <div className="sup-photo">{photo?<img src={photo} alt={`Foto de ${r.agentName}`}/>:<span><Camera size={26}/>{detail.hasPhoto?'Cargando foto…':'Sin foto'}</span>}</div>
        <div className="nov-edit-grid two">
          <ModalField label="Compañía"><input value={r.companyName??'—'} disabled/></ModalField>
          <ModalField label="Cliente"><input value={r.clientName} disabled/></ModalField>
          <ModalField label="Punto"><input value={r.pointName} disabled/></ModalField>
          <ModalField label="Puesto"><input value={`${r.postCode} · ${r.postName}`} disabled/></ModalField>
          <ModalField label="Supervisor"><input value={r.supervisorName} disabled/></ModalField>
          <ModalField label="Agente evaluado"><input value={r.agentName} disabled/></ModalField>
        </div>
      </div>
      <div className="sup-levels">
        {([['Vigilancia',r.vigilanceLevel],['Limpieza',r.cleanlinessLevel],['Conocimiento de consigna',r.consignmentKnowledgeLevel],['Situacional',r.situationalLevel]] as [string,Level][]).map(([label,level])=><article key={label}><small>{label}</small><LevelChip level={level}/></article>)}
        <article><small>Inventario</small><span className={`nov-status ${inventoryClass(r.inventoryOverallState)}`}>{INVENTORY[r.inventoryOverallState]??r.inventoryOverallState}</span></article>
      </div>
      <ModalField label="Comentarios del Supervisor"><textarea value={detail.supervisorComments??''} placeholder="Sin comentarios" disabled/></ModalField>
      <ModalField label="Notas del Punto / Puesto"><textarea value={detail.pointPostNotes??''} placeholder="Sin notas" disabled/></ModalField>
      <ModalField label={`Consigna vigente${detail.consignmentVersion?` (versión ${detail.consignmentVersion})`:''}`}><textarea value={detail.consignmentSnapshot??''} placeholder="Sin consigna registrada" disabled/></ModalField>

      <div className="nov-photo-block"><div className="nov-section-title"><strong>Inventario revisado ({detail.inventory.length})</strong></div>
        {detail.inventoryComments&&<p className="sup-note">{detail.inventoryComments}</p>}
        {detail.inventory.length?<table className="nov-table"><thead><tr><th>Activo</th><th>Estado</th><th>Comentario</th></tr></thead><tbody>
          {detail.inventory.map(x=><tr key={x.assetId}><td>{x.description}</td><td><span className={`nov-status ${x.observedState==='GOOD'?'approved':x.observedState==='NOT_VERIFIED'?'discarded':'pending'}`}>{ITEM_STATES[x.observedState]??x.observedState}</span></td><td>{x.comment||'—'}</td></tr>)}
        </tbody></table>:<p className="sup-note">Sin ítems de inventario registrados.</p>}
      </div>

      <div className="nov-photo-block"><div className="nov-section-title"><strong>Novedades del Agente revisadas ({detail.decisions.length})</strong></div>
        {detail.decisions.length?<table className="nov-table"><thead><tr><th>Novedad</th><th>Tipo</th><th>Decisión</th><th>Decidida por</th></tr></thead><tbody>
          {detail.decisions.map(x=><tr key={x.noveltyId}><td><strong>{x.title}</strong>{x.occurredAt&&<small>{formatDateTime(x.occurredAt)}</small>}</td><td>{NOVELTY_TYPES[x.noveltyType]??x.noveltyType}</td>
            <td><span className={`nov-status ${x.decision==='CONFIRMED'?'approved':'discarded'}`}>{x.decision==='CONFIRMED'?'Confirmada':'Descartada'}</span></td><td><strong>{x.decidedBy}</strong><small>{formatDateTime(x.decidedAt)}</small></td></tr>)}
        </tbody></table>:<p className="sup-note">El Supervisor no revisó novedades del Agente en esta evaluación.</p>}
      </div>
      <p className="sup-note">Registrada por {detail.createdBy} el {formatDateTime(detail.createdAt)}.</p>
    </>}
  </OperationalDrawer>
}

function LevelChip({level}:{level:Level}){return <span className={`sup-level ${level.toLowerCase()}`}>{LEVELS[level]??level}</span>}
function Metric({icon,label,detail,value,tone}:{icon:ReactNode;label:string;detail:string;value:number;tone:'blue'|'red'|'amber'}){return <article><span className={tone}>{icon}</span><div><small>{label}</small><strong>{value}</strong><em>{detail}</em></div></article>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="nov-field"><span>{label}</span>{children}</label>}
function ModalField({label,children}:{label:string;children:ReactNode}){return <label className="nov-modal-field"><span>{label}</span>{children}</label>}
