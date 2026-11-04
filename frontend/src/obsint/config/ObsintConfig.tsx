import {useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode} from 'react';
import {AlertTriangle, ArrowLeft, BrushCleaning, CarFront, CheckCircle2, ChevronDown, ChevronLeft, ChevronRight, ClipboardList, Factory, History, Package, Pencil, Plus, Power, Save, Search, ShieldCheck, Trash2, UserRound, UsersRound, X} from 'lucide-react';
import {errorText, obsintConfigApi, type ObsintCameraOption, type ObsintCategory, type ObsintConsignaConfig, type ObsintConsignaSave, type ObsintConsignmentOption, type ObsintProtocol, type ObsintTaskSaved} from '../obsintApi';
import {CATEGORIES, categoryLabel, DAYS, protocolStatusLabel} from '../obsint';
import {ObsintMonitoringPanel} from './ObsintMonitoringPanel';
import {ObsintScheduleEditor, slotsFromWeek, weekFromSlots} from './ObsintScheduleEditor';
import '../obsint.css';
import './obsint-config.css';

/** Lo que entrega Servicios al abrir el módulo. */
export type ObsintPointContext={pointId:string;pointName:string;clientName?:string;serviceName?:string;companyName?:string};

const TIME_LIMITS=[30,60,120,180,300,600];
const timeLimitLabel=(s:number)=>s<60?`${s} segundos`:`${s/60} ${s===60?'minuto':'minutos'}`;
const formatDate=(value:string|null)=>value?new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)):'—';
const CATEGORY_ICONS={SSO:CarFront,PRODUCTION:Factory,LOGISTICS:Package,CLEANING:BrushCleaning,MOBILITY:CarFront,CUSTOMER_SERVICE:UsersRound};
function CategoryIcon({category,size=18}:{category:ObsintCategory;size?:number}){const Icon=CATEGORY_ICONS[category];return <Icon size={size} className={`obs-cfg-category-icon ${category.toLowerCase()}`}/>}
const PAGE_SIZES=[10,20,50];

/**
 * Configuración OBSINT del Punto: "Vincular Consignas – Cámara – Horario" (diapositiva 10 de "OBSINT Estructura").
 * Cada cambio guardado crea y activa una versión; las versiones anteriores quedan disponibles para consulta.
 * Se abre desde Configuración del Punto: <ObsintConfig point={…} onBack={…}/>.
 */
export default function ObsintConfig({point,onBack}:{point:ObsintPointContext;onBack:()=>void}){
  const [protocols,setProtocols]=useState<ObsintProtocol[]>([]);
  const [cameras,setCameras]=useState<ObsintCameraOption[]>([]);
  const [linkable,setLinkable]=useState<ObsintConsignmentOption[]>([]);
  const [versionId,setVersionId]=useState('');
  const [category,setCategory]=useState<ObsintCategory|'ALL'>('ALL');
  const [query,setQuery]=useState('');
  const [page,setPage]=useState(1);
  const [pageSize,setPageSize]=useState(10);
  const [selectedId,setSelectedId]=useState('');
  const [editing,setEditing]=useState<ObsintConsignaConfig|'new'|null>(null);
  const [busy,setBusy]=useState(false);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');

  async function load(preferId?:string){
    try{
      const [list,cams,options]=await Promise.all([obsintConfigApi.protocols(point.pointId),obsintConfigApi.cameras(point.pointId),obsintConfigApi.consignments(point.pointId)]);
      setProtocols(list);setCameras(cams);setLinkable(options);setError('');
      const keep=preferId??versionId;
      const current=list.find(p=>p.id===keep)??list.find(p=>p.status==='ACTIVO')??list.find(p=>p.status!=='BORRADOR')??list[0];
      setVersionId(current?.id??'');
      setSelectedId(previous=>current?.consignas.some(c=>c.id===previous)?previous:current?.consignas[0]?.id??'');
    }catch(e){setError(`No se pudo cargar la configuración OBSINT. ${errorText(e)}`)}
    finally{setLoading(false)}
  }
  useEffect(()=>{void load()},[point.pointId]);
  useEffect(()=>{if(!notice)return;const id=setTimeout(()=>setNotice(''),3200);return()=>clearTimeout(id)},[notice]);

  // El menú de Versión se cierra al hacer clic fuera de él o con Esc.
  const versionMenu=useRef<HTMLDetailsElement>(null);
  useEffect(()=>{
    const close=()=>{if(versionMenu.current)versionMenu.current.open=false};
    const onPointer=(e:PointerEvent)=>{if(versionMenu.current?.open&&!versionMenu.current.contains(e.target as Node))close()};
    const onKey=(e:KeyboardEvent)=>{if(e.key==='Escape')close()};
    document.addEventListener('pointerdown',onPointer);document.addEventListener('keydown',onKey);
    return()=>{document.removeEventListener('pointerdown',onPointer);document.removeEventListener('keydown',onKey)};
  },[]);

  const protocol=protocols.find(p=>p.id===versionId)??null;
  // Guardar = activar: se edita sobre la versión activa (o la última, si se inactivó); las demás versiones son historial de solo lectura.
  const base=protocols.find(p=>p.status==='ACTIVO')??protocols.find(p=>p.status!=='BORRADOR')??null;
  const editable=!protocol||protocol.id===base?.id;
  const consignas=protocol?.consignas??[];
  const visible=useMemo(()=>{const q=query.trim().toLowerCase();
    return consignas.filter(c=>(category==='ALL'||c.category===category)&&(!q||`${c.code} ${c.title} ${c.instruction}`.toLowerCase().includes(q)))},[consignas,category,query]);
  const pageCount=Math.max(1,Math.ceil(visible.length/pageSize));
  const currentPage=Math.min(page,pageCount);
  const pageRows=visible.slice((currentPage-1)*pageSize,currentPage*pageSize);
  const selected=consignas.find(c=>c.id===selectedId)??null;

  async function run(action:()=>Promise<ObsintProtocol|void>,message:string){
    setBusy(true);setError('');
    try{const result=await action();setNotice(message);setEditing(null);await load(result?result.id:undefined)}
    catch(e){setError(errorText(e))}
    finally{setBusy(false)}
  }
  async function removeConsigna(c:ObsintConsignaConfig){
    if(!window.confirm(`¿Eliminar ${c.code} · ${c.title}? El cambio se aplica de inmediato en la Consola.`))return;
    setBusy(true);setError('');
    try{
      const result=await obsintConfigApi.deleteTask(point.pointId,c.code);
      setSelectedId('');setEditing(null);await load(result.id);setNotice(`Tarea eliminada · versión v${result.versionNo} activa.`);
    }catch(e){setError(errorText(e))}finally{setBusy(false)}
  }
  function openEditor(c:ObsintConsignaConfig|'new'){
    setError('');setSelectedId(c==='new'?'':c.id);setEditing(c);
  }
  async function saved({protocol:result,code}:ObsintTaskSaved){
    setEditing(null);await load(result.id);
    setSelectedId(result.consignas.find(c=>c.code===code)?.id??'');
    setNotice(`Tarea guardada · versión v${result.versionNo} activa en la Consola.`);
  }

  return <div className="obs-page obs-cfg">
    <nav className="obs-breadcrumbs"><button type="button" onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración</button><span>/</span><small>Consignas Obsint</small></nav>
    <div className="obs-cfg-titlebar"><div className="obs-hero"><h2>Consignas Obsint</h2><p>Configuración de tareas de OBSINT por servicio</p><small>Defina, edite y administre las tareas de observación e inteligencia del punto.</small></div>
      <div className="obs-cfg-title-actions"><button type="button" disabled title="Modelos de Compañía no disponible"><ClipboardList size={15}/>Modelos de Compañía</button><button type="button" disabled title="Estándares Cajamarca no disponible"><ShieldCheck size={15}/>Estándares Cajamarca</button></div>
    </div>
    <div className="obs-cfg-point">
      {point.companyName&&<div><span>Compañía</span><b>{point.companyName}</b></div>}
      {point.serviceName&&<div><span>Servicio</span><b>{point.serviceName}</b></div>}
      {point.clientName&&<div><span>Cliente</span><b>{point.clientName}</b></div>}
      <div><span>Punto</span><b>{point.pointName}</b></div>
      <div className="obs-cfg-version-cell"><span>Versión</span>
        <details ref={versionMenu} className="obs-cfg-version-menu"><summary aria-label="Opciones de versión" title="Opciones de versión"><b>{protocol?`${protocol.code} v${protocol.versionNo}`:loading?'Cargando…':'Sin protocolo'}</b><ChevronDown size={14}/></summary>
          <div className="obs-cfg-version-popover">
            {loading?<small>Cargando protocolo…</small>:!protocol&&error?<button type="button" className="ghost" onClick={()=>void load()}>Reintentar</button>:!protocol?<>
              <p>Este punto no tiene protocolo OBSINT.</p>
            </>:<>
              <label>Versión del protocolo<select value={protocol.id} onChange={e=>{setVersionId(e.target.value);setSelectedId(protocols.find(p=>p.id===e.target.value)?.consignas[0]?.id??'');setEditing(null)}}>
                {protocols.map(p=><option key={p.id} value={p.id}>v{p.versionNo} · {protocolStatusLabel(p.status)}</option>)}
              </select></label>
              <span className={`obs-cfg-pill ${protocol.status.toLowerCase()}`}>{protocolStatusLabel(protocol.status)}</span>
              <small>{protocol.status==='ACTIVO'?`Activa desde ${formatDate(protocol.activatedAt)}. La Consola OBSINT usa esta versión; cada cambio guardado crea la siguiente.`
                :protocol.status==='BORRADOR'?'Borrador anterior sin publicar. Se descarta al guardar el próximo cambio.'
                :`Versión anterior (${formatDate(protocol.activatedAt??protocol.publishedAt)}). Solo lectura.`}</small>
              <div className="obs-cfg-actions">
                {protocol.status==='INACTIVO'&&<button type="button" className="ghost" disabled={busy} onClick={()=>{if(window.confirm(`¿Volver a la versión v${protocol.versionNo}? La Consola usará sus tareas desde ahora.`))void run(()=>obsintConfigApi.activate(protocol.id),`Versión v${protocol.versionNo} activa.`)}}><History size={15}/>Volver a esta versión</button>}
                {protocol.status==='ACTIVO'&&<button type="button" className="ghost" disabled={busy} onClick={()=>{if(window.confirm('¿Inactivar OBSINT en este punto? La Consola dejará de mostrar sus tareas.'))void run(()=>obsintConfigApi.deactivate(protocol.id),'OBSINT inactivo en este punto.')}}><Power size={15}/>Inactivar</button>}
              </div>
            </>}
          </div>
        </details>
        {protocol&&<small>Actualizado {formatDate(protocol.updatedAt)}</small>}
      </div>
    </div>
    <ObsintMonitoringPanel pointId={point.pointId}/>
    {error&&<div className="obs-error">{error}</div>}

    {!protocol&&(loading||error)&&<section className="obs-card obs-cfg-start" aria-labelledby="obs-cfg-start-title">
      <span className="obs-cfg-start-icon"><ClipboardList size={30}/></span>
      <h3 id="obs-cfg-start-title">{loading?'Cargando consignas OBSINT…':'No se pudo cargar la configuración'}</h3>
      {!loading&&<>
        <p>Revise la conexión y vuelva a cargar la configuración de este punto.</p>
        <button type="button" className="ghost" disabled={busy} onClick={()=>void load()}>Reintentar</button>
      </>}
    </section>}

    {!loading&&(protocol||!error)&&<div className="obs-cfg-layout">
      <section className="obs-card obs-cfg-categories">
        <header><h3>Categorías de tareas</h3><span>Filtre las tareas por categoría</span></header>
        <button type="button" className={category==='ALL'?'active':''} onClick={()=>{setCategory('ALL');setPage(1)}}><UserRound size={18} className="obs-cfg-category-icon all"/><span>Todas las tareas</span><b>{consignas.length}</b></button>
        {CATEGORIES.map(c=><button key={c.value} type="button" className={category===c.value?'active':''} onClick={()=>{setCategory(c.value);setPage(1)}}>
          <CategoryIcon category={c.value}/><span>{c.label}</span><b>{consignas.filter(x=>x.category===c.value).length}</b></button>)}
      </section>

      <section className="obs-card obs-cfg-list">
        <header><div><h3>Tareas OBSINT ({consignas.length})</h3><span>Tareas de observación configuradas para este servicio.</span></div>
          <button type="button" className="obs-cfg-primary" disabled={busy||!editable} title={editable?undefined:'Está viendo una versión anterior; elija la versión activa para editar.'} onClick={()=>openEditor('new')}><Plus size={16}/>Nueva tarea</button></header>
        <label className="obs-cfg-search"><Search size={16}/><input value={query} onChange={e=>{setQuery(e.target.value);setPage(1)}} placeholder="Buscar tarea por nombre, código o descripción…"/></label>
        <table className="obs-cfg-table">
          <thead><tr><th>Código</th><th>Nombre de la tarea</th><th>Categoría</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>{visible.length?pageRows.map(c=><tr key={c.id} className={c.id===selectedId?'selected':''} onClick={()=>{setSelectedId(c.id);setEditing(null)}}>
            <td><span className="obs-cfg-code"><CategoryIcon category={c.category}/><b>{c.code}</b></span></td><td>{c.title}</td><td>{categoryLabel(c.category)}</td>
            <td><span className={`obs-cfg-pill ${c.active?'activo':'inactivo'}`}>{c.active?'Activa':'Inactiva'}</span></td>
            <td className="obs-cfg-row-actions">
              {editable&&<span>
              <button type="button" aria-label={`Editar ${c.code}`} title="Editar" disabled={busy} onClick={e=>{e.stopPropagation();openEditor(c)}}><Pencil size={15}/></button>
              <button type="button" aria-label={`Eliminar ${c.code}`} title="Eliminar" className="danger" disabled={busy} onClick={e=>{e.stopPropagation();void removeConsigna(c)}}><Trash2 size={15}/></button>
              </span>}
            </td>
          </tr>):<tr><td colSpan={5} className="obs-cfg-empty">{consignas.length?'Ninguna tarea coincide con el filtro.':'Agregue la primera tarea con "Nueva tarea".'}</td></tr>}</tbody>
        </table>
        <div className="obs-cfg-pagination"><span>Mostrando {visible.length?(currentPage-1)*pageSize+1:0} a {Math.min(currentPage*pageSize,visible.length)} de {visible.length} tareas</span>
          <div><button type="button" aria-label="Página anterior" disabled={currentPage===1} onClick={()=>setPage(currentPage-1)}><ChevronLeft size={16}/></button>
            <strong>{currentPage}</strong><span>de {pageCount}</span><button type="button" aria-label="Página siguiente" disabled={currentPage===pageCount} onClick={()=>setPage(currentPage+1)}><ChevronRight size={16}/></button>
            <select aria-label="Tareas por página" value={pageSize} onChange={e=>{setPageSize(Number(e.target.value));setPage(1)}}>{PAGE_SIZES.map(n=><option key={n} value={n}>{n} por página</option>)}</select></div></div>
      </section>

      <section className="obs-card obs-cfg-detail">
        {editing
          ?<ConsignaForm key={editing==='new'?'new':editing.id} pointId={point.pointId} initial={editing==='new'?null:editing} cameras={cameras}
              linkable={linkable.filter(o=>!consignas.some(c=>c.consignmentCode===o.code&&(editing==='new'||c.id!==editing.id)))}
              defaultCategory={category==='ALL'?'SSO':category} onSaved={saved} onCancel={()=>setEditing(null)}/>
          :selected?<ConsignaDetail c={selected} cameras={cameras} onEdit={editable?()=>openEditor(selected):undefined}/>
          :<div className="obs-cfg-empty-detail"><ClipboardList size={30}/>Seleccione una tarea para ver su detalle{editable?' o cree una nueva':''}.</div>}
      </section>
    </div>}
    {notice&&<div className="obs-toast" role="status">{notice}</div>}
  </div>;
}

function ConsignaDetail({c,cameras,onEdit}:{c:ObsintConsignaConfig;cameras:ObsintCameraOption[];onEdit?:()=>void}){
  return <>
    <header><div><h3>Detalle de la tarea</h3><span>Vista y edición de la información de la tarea seleccionada.</span></div>{onEdit&&<button type="button" className="ghost" onClick={onEdit}><Pencil size={15}/>Editar</button>}</header>
    <div className="obs-cfg-detail-head"><span className="obs-cfg-task-icon"><UserRound size={16}/></span><b>{c.title}</b><span className={`obs-cfg-pill ${c.active?'activo':'inactivo'}`}>{c.active?'Activa':'Inactiva'}</span></div>
    <div className="obs-cfg-detail-grid">
      <div className="obs-cfg-detail-fields">
        <div className="obs-cfg-read-field"><span>Código</span><div>{c.code}</div></div>
        <div className="obs-cfg-read-field"><span>Consigna</span><div>{c.consignmentCode??'Sin vincular (creada antes del vínculo con Consignas)'}</div></div>
        <div className="obs-cfg-read-field"><span>Nombre de la tarea</span><div>{c.title}</div></div>
        <div className="obs-cfg-read-field"><span>Categoría</span><div><CategoryIcon category={c.category} size={15}/>{categoryLabel(c.category)}</div></div>
        <div className="obs-cfg-read-field obs-cfg-description"><span>Descripción</span><div>{c.description||c.instruction}</div></div>
        <div className="obs-cfg-read-field"><span>Tiempo máximo de ejecución</span><div>{timeLimitLabel(c.timeLimitSeconds)}</div></div>
      </div>
      <div className="obs-cfg-detail-schedule"><h4>Horario de ejecución</h4><table className="obs-cfg-schedule"><thead><tr><th scope="col">Día</th><th scope="col" className="obs-cfg-time-heading">Horario<br/>inicio</th><th scope="col" className="obs-cfg-time-heading">Horario<br/>fin</th></tr></thead><tbody>
        {DAYS.map((day,i)=>{const slot=c.schedule.find(s=>s.dayOfWeek===i+1);return <tr key={day}><td>{day}</td><td>{slot?.start??'--:--'}</td><td>{slot?.end??'--:--'}</td></tr>})}
      </tbody></table></div>
      <div className="obs-cfg-detail-cameras"><h4>Cámaras asociadas</h4><p>Seleccione las cámaras desde los componentes de seguridad configurados.</p><div className="obs-cfg-camera-chips">{cameras.filter(camera=>c.cameraIds.includes(camera.id)).map(camera=><span key={camera.id}>{camera.code} {camera.name}</span>)}</div><div className="obs-cfg-read-camera-list">
        {cameras.map(camera=><label key={camera.id}><input type="checkbox" checked={c.cameraIds.includes(camera.id)} readOnly disabled/><span>{camera.code} {camera.name}</span></label>)}
        {!cameras.length&&<span>Sin cámaras disponibles.</span>}
      </div></div>
    </div>
    <div className="obs-cfg-read-instruction"><span>Instrucciones operativas</span><div>{c.instruction}</div></div>
  </>;
}

function ConsignaForm({pointId,initial,cameras,linkable,defaultCategory,onSaved,onCancel}:{pointId:string;initial:ObsintConsignaConfig|null;cameras:ObsintCameraOption[];
  linkable:ObsintConsignmentOption[];defaultCategory:ObsintCategory;onSaved:(result:ObsintTaskSaved)=>void|Promise<void>;onCancel:()=>void}){
  const [form,setForm]=useState<ObsintConsignaSave>(()=>({consignmentCode:initial?.consignmentCode??null,title:initial?.title??'',description:initial?.description??'',instruction:initial?.instruction??'',category:initial?.category??defaultCategory,
    timeLimitSeconds:initial?.timeLimitSeconds??120,active:initial?.active??true,cameraIds:initial?.cameraIds??[],schedule:initial?.schedule??[]}));
  const [week,setWeek]=useState(()=>weekFromSlots(initial?initial.schedule:null));
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [categoryAbove,setCategoryAbove]=useState(false);
  const categoryMenu=useRef<HTMLDetailsElement>(null);
  useEffect(()=>{
    const closeOutside=(event:PointerEvent)=>{if(categoryMenu.current?.open&&!categoryMenu.current.contains(event.target as Node))categoryMenu.current.open=false};
    const closeOnEscape=(event:KeyboardEvent)=>{if(event.key==='Escape'&&categoryMenu.current?.open){categoryMenu.current.open=false;categoryMenu.current.querySelector('summary')?.focus()}};
    document.addEventListener('pointerdown',closeOutside);document.addEventListener('keydown',closeOnEscape);
    return()=>{document.removeEventListener('pointerdown',closeOutside);document.removeEventListener('keydown',closeOnEscape)};
  },[]);
  const set=<K extends keyof ObsintConsignaSave>(key:K,value:ObsintConsignaSave[K])=>setForm(prev=>({...prev,[key]:value}));
  const toggleCamera=(id:string)=>set('cameraIds',form.cameraIds.includes(id)?form.cameraIds.filter(x=>x!==id):[...form.cameraIds,id]);
  /** Las tareas nuevas (y las ya vinculadas) salen de una consigna marcada OBSINT en Consignas; las anteriores pueden quedar sin vincular. */
  const linkRequired=!initial||Boolean(initial.consignmentCode);
  const linked=Boolean(form.consignmentCode);
  const options=initial?.consignmentCode&&!linkable.some(o=>o.code===initial.consignmentCode)
    ?[{code:initial.consignmentCode,title:`${initial.title} (ya no está vigente con OBSINT)`,instruction:initial.instruction},...linkable]:linkable;
  const chooseConsignment=(code:string)=>{const option=options.find(o=>o.code===code);
    setForm(prev=>option?{...prev,consignmentCode:option.code,title:option.title,instruction:option.instruction,description:prev.description||option.instruction}:{...prev,consignmentCode:null})};
  const timeLimits=TIME_LIMITS.includes(form.timeLimitSeconds)?TIME_LIMITS:[...TIME_LIMITS,form.timeLimitSeconds].sort((a,b)=>a-b);

  async function submit(e:FormEvent){
    e.preventDefault();
    const {slots,error:scheduleError}=slotsFromWeek(week);
    if(scheduleError){setError(scheduleError);return}
    if(linkRequired&&!form.consignmentCode){setError('Seleccione la consigna de Consignas que verificará el monitorista.');return}
    setSaving(true);setError('');
    const body={...form,schedule:slots};
    // Guardar = activar: la Consola usa el cambio desde ya (si algo no cumple, el backend lo rechaza y la versión activa no cambia).
    try{await onSaved(initial?await obsintConfigApi.updateTask(pointId,initial.code,body):await obsintConfigApi.addTask(pointId,body))}
    catch(err){setError(errorText(err))}
    finally{setSaving(false)}
  }

  return <form className="obs-cfg-form" onSubmit={submit}>
    <header><div><h3>Detalle de la tarea</h3><span>Vista y edición de la información de la tarea seleccionada.</span></div><button type="button" className="obs-cfg-icon" aria-label="Cerrar edición" onClick={onCancel}><X size={16}/></button></header>
    <div className="obs-cfg-detail-head"><span className="obs-cfg-task-icon"><UserRound size={16}/></span><b>{form.title||'Nueva tarea'}</b><label className="obs-cfg-status-toggle"><input type="checkbox" checked={form.active} onChange={e=>set('active',e.target.checked)}/>{form.active?'Activa':'Inactiva'}</label></div>
    <div className="obs-cfg-form-main"><div className="obs-cfg-form-fields">
      <Field label="Código"><input value={initial?.code??'Se asigna al guardar'} disabled/></Field>
      <Field label="Consigna" required={linkRequired}><select value={form.consignmentCode??''} onChange={e=>chooseConsignment(e.target.value)} required={linkRequired}>
        <option value="">{linkRequired?'Seleccione una consigna marcada OBSINT':'Sin vincular'}</option>
        {options.map(o=><option key={o.code} value={o.code}>{o.code} · {o.title}</option>)}
      </select>{!options.length&&<small className="obs-cfg-hint"><AlertTriangle size={13}/>No hay consignas con OBSINT. Márquelas en Servicios → Consignas → Alcance y active el protocolo.</small>}</Field>
      <Field label="Nombre de la tarea" required><input value={form.title} onChange={e=>set('title',e.target.value)} required maxLength={160} readOnly={linked} title={linked?'Sale de la consigna; se edita en Consignas.':undefined} placeholder="Verificación de uso de EPP"/></Field>
       <div className="obs-cfg-field"><span id="obs-cfg-category-label">Categoría<RequiredMark/></span>
         <details ref={categoryMenu} className={`obs-cfg-category-select${categoryAbove?' drop-up':''}`}
           onToggle={()=>{const menu=categoryMenu.current;if(menu?.open){const rect=menu.getBoundingClientRect();setCategoryAbove(window.innerHeight-rect.bottom<260&&rect.top>window.innerHeight-rect.bottom)}}}>
           <summary aria-labelledby="obs-cfg-category-label"><CategoryIcon category={form.category} size={17}/><span>{categoryLabel(form.category)}</span><ChevronDown size={15}/></summary>
           <div className="obs-cfg-category-options" role="group" aria-label="Categorías disponibles">
             {CATEGORIES.map(c=><button key={c.value} type="button" className={form.category===c.value?'selected':''} aria-pressed={form.category===c.value}
               onClick={()=>{set('category',c.value);if(categoryMenu.current)categoryMenu.current.open=false}}>
               <CategoryIcon category={c.value} size={17}/><span>{c.label}</span>{form.category===c.value&&<CheckCircle2 size={15} className="obs-cfg-category-check"/>}
             </button>)}
           </div>
         </details>
       </div>
      <Field label="Descripción" required><textarea className="obs-cfg-description-input" value={form.description} onChange={e=>set('description',e.target.value)} required maxLength={1000} rows={4} placeholder="Describa lo que se debe observar en el punto."/></Field>
      <Field label="Tiempo máximo de ejecución" required><select value={form.timeLimitSeconds} onChange={e=>set('timeLimitSeconds',Number(e.target.value))}>{timeLimits.map(s=><option key={s} value={s}>{timeLimitLabel(s)}</option>)}</select></Field>
    </div>
    <ObsintScheduleEditor value={week} onChange={setWeek}/>

    <fieldset className="obs-cfg-fieldset"><legend>Cámaras asociadas<RequiredMark/> <small>{form.cameraIds.length}/4</small></legend>
      <p className="obs-cfg-camera-hint">Seleccione las cámaras desde los componentes de seguridad configurados.</p>
      <div className="obs-cfg-camera-chips">{cameras.filter(c=>form.cameraIds.includes(c.id)).map(c=><button type="button" key={c.id} onClick={()=>toggleCamera(c.id)} title={`Quitar ${c.code}`}>{c.code} {c.name}<X size={11}/></button>)}</div>
      {cameras.length?<div className="obs-cfg-cameras">{cameras.map(c=>{const checked=form.cameraIds.includes(c.id);
        return <label key={c.id} className={checked?'checked':''}><input type="checkbox" checked={checked} disabled={!checked&&form.cameraIds.length>=4} onChange={()=>toggleCamera(c.id)}/>
          <span><b>{c.code}</b> {c.name}{c.location&&<small>{c.location}</small>}</span>{c.status==='OBSERVED'&&<em>Con observación</em>}</label>})}</div>
      :<p className="obs-cfg-hint"><AlertTriangle size={15}/>Este punto no tiene cámaras. Agréguelas en Componentes de Seguridad → Videovigilancia.</p>}
    </fieldset>
    </div>
    <Field label="Instrucciones operativas" required><textarea value={form.instruction} onChange={e=>set('instruction',e.target.value)} required maxLength={1000} rows={4} readOnly={linked} title={linked?'Sale de la consigna; se edita en Consignas.':undefined}
      placeholder="Indique al monitorista qué debe observar, registrar y reportar."/></Field>

    {error&&<div className="obs-error">{error}</div>}
    <div className="obs-cfg-form-actions"><button type="submit" className="obs-cfg-primary" disabled={saving}><Save size={15}/>{saving?'Guardando…':'Guardar y aplicar'}</button><button type="button" className="ghost" onClick={onCancel}>Cancelar</button></div>
  </form>;
}

function RequiredMark(){return <span className="obs-cfg-required" aria-hidden="true">{'\u00a0*'}</span>}
function Field({label,required=false,children}:{label:string;required?:boolean;children:ReactNode}){
  const lastWordStart=label.lastIndexOf(' ')+1;
  return <label className="obs-cfg-field"><span>{required?<>{label.slice(0,lastWordStart)}<span className="obs-cfg-required-tail">{label.slice(lastWordStart)}<RequiredMark/></span></>:label}</span>{children}</label>;
}
