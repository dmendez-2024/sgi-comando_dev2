import {useEffect, useMemo, useState, type FormEvent, type ReactNode} from 'react';
import {AlertTriangle, ArrowLeft, CheckCircle2, ClipboardList, Edit3, History, Plus, Power, Search, Send, Trash2, X} from 'lucide-react';
import {errorText, obsintConfigApi} from '../obsintApi';
import {CATEGORIES, categoryLabel, DAYS, protocolStatusLabel, type ObsintCameraOption, type ObsintCategory, type ObsintConsignaConfig, type ObsintConsignaSave, type ObsintProtocol, type ObsintSlot} from '../obsint';
import '../obsint.css';
import './obsint-config.css';

/** Lo que entrega Servicios al abrir el módulo. */
export type ObsintPointContext={pointId:string;pointName:string;clientName?:string;serviceName?:string;companyName?:string};

const TIME_LIMITS=[30,60,120,180,300,600];
const timeLimitLabel=(s:number)=>s<60?`${s} segundos`:`${s/60} ${s===60?'minuto':'minutos'}`;
const formatDate=(value:string|null)=>value?new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)):'—';

/**
 * Configuración OBSINT del Punto: "Vincular Consignas – Cámara – Horario" (diapositiva 10 de "OBSINT Estructura").
 * Protocolo versionado como Consignas: se edita un Borrador, se publica (queda inmutable) y se activa; la Consola usa el activo.
 * Se abre desde Configuración del Punto: <ObsintConfig point={…} onBack={…}/>.
 */
export default function ObsintConfig({point,onBack}:{point:ObsintPointContext;onBack:()=>void}){
  const [protocols,setProtocols]=useState<ObsintProtocol[]>([]);
  const [cameras,setCameras]=useState<ObsintCameraOption[]>([]);
  const [versionId,setVersionId]=useState('');
  const [category,setCategory]=useState<ObsintCategory|'ALL'>('ALL');
  const [query,setQuery]=useState('');
  const [selectedId,setSelectedId]=useState('');
  const [editing,setEditing]=useState<ObsintConsignaConfig|'new'|null>(null);
  const [busy,setBusy]=useState(false);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');

  async function load(preferId?:string){
    try{
      const [list,cams]=await Promise.all([obsintConfigApi.protocols(point.pointId),obsintConfigApi.cameras(point.pointId)]);
      setProtocols(list);setCameras(cams);setError('');
      const keep=preferId??versionId;
      if(!list.some(p=>p.id===keep))setVersionId((list.find(p=>p.status==='BORRADOR')??list.find(p=>p.status==='ACTIVO')??list[0])?.id??'');
      else if(preferId)setVersionId(preferId);
    }catch(e){setError(errorText(e))}
    finally{setLoading(false)}
  }
  useEffect(()=>{void load()},[point.pointId]);
  useEffect(()=>{if(!notice)return;const id=setTimeout(()=>setNotice(''),3200);return()=>clearTimeout(id)},[notice]);

  const protocol=protocols.find(p=>p.id===versionId)??null;
  const draft=protocols.find(p=>p.status==='BORRADOR')??null;
  const editable=protocol?.status==='BORRADOR';
  const consignas=protocol?.consignas??[];
  const visible=useMemo(()=>{const q=query.trim().toLowerCase();
    return consignas.filter(c=>(category==='ALL'||c.category===category)&&(!q||`${c.code} ${c.title} ${c.instruction}`.toLowerCase().includes(q)))},[consignas,category,query]);
  const selected=consignas.find(c=>c.id===selectedId)??null;
  const cameraName=(id:string)=>{const c=cameras.find(x=>x.id===id);return c?`${c.code} ${c.name}`:'Cámara no disponible'};

  async function run(action:()=>Promise<ObsintProtocol|void>,message:string){
    setBusy(true);
    try{const result=await action();setNotice(message);setEditing(null);await load(result?result.id:undefined)}
    catch(e){setError(errorText(e))}
    finally{setBusy(false)}
  }
  async function removeConsigna(c:ObsintConsignaConfig){
    if(!window.confirm(`¿Eliminar ${c.code} · ${c.title}?`))return;
    await run(()=>obsintConfigApi.deleteConsigna(c.id),'Consigna eliminada.');
    setSelectedId('');
  }
  function saved(c:ObsintConsignaConfig){
    setProtocols(prev=>prev.map(p=>p.id!==versionId?p:{...p,consignas:p.consignas.some(x=>x.id===c.id)?p.consignas.map(x=>x.id===c.id?c:x):[...p.consignas,c]}));
    setSelectedId(c.id);setEditing(null);setNotice('Consigna guardada.');
  }

  return <div className="obs-page obs-cfg">
    <nav className="obs-breadcrumbs"><button type="button" onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración</button><span>/</span><small>Consignas OBSINT</small></nav>
    <div className="obs-hero"><h2>Consignas OBSINT</h2><p>Configuración de tareas de observación por punto: consigna, cámaras y horario de ejecución.</p></div>
    <div className="obs-cfg-point">
      {point.companyName&&<div><span>Compañía</span><b>{point.companyName}</b></div>}
      {point.serviceName&&<div><span>Servicio</span><b>{point.serviceName}</b></div>}
      {point.clientName&&<div><span>Cliente</span><b>{point.clientName}</b></div>}
      <div><span>Punto</span><b>{point.pointName}</b></div>
      <div><span>Versión</span><b>{protocol?`${protocol.code} v${protocol.versionNo}`:'Sin protocolo'}</b>{protocol&&<small>Actualizado {formatDate(protocol.updatedAt)}</small>}</div>
    </div>
    {error&&<div className="obs-error">{error}</div>}

    <section className="obs-card obs-cfg-protocol">
      {loading?<span>Cargando protocolo…</span>:!protocol?<>
        <div><b>Este punto no tiene protocolo OBSINT.</b><small>Cree el borrador, agregue las consignas con sus cámaras y horario, y publíquelo.</small></div>
        <button type="button" className="obs-cfg-primary" disabled={busy} onClick={()=>void run(()=>obsintConfigApi.draft(point.pointId),'Borrador creado.')}><Plus size={16}/>Crear protocolo</button>
      </>:<>
        <div className="obs-cfg-version">
          <label><History size={15}/><select value={protocol.id} onChange={e=>{setVersionId(e.target.value);setSelectedId('');setEditing(null)}}>
            {protocols.map(p=><option key={p.id} value={p.id}>v{p.versionNo} · {protocolStatusLabel(p.status)}</option>)}
          </select></label>
          <span className={`obs-cfg-pill ${protocol.status.toLowerCase()}`}>{protocolStatusLabel(protocol.status)}</span>
          <small>{protocol.status==='BORRADOR'?'Borrador editable. Al publicar, la versión queda inmutable.'
            :protocol.status==='ACTIVO'?`Activo desde ${formatDate(protocol.activatedAt)}. La Consola OBSINT usa esta versión.`
            :`Publicado ${formatDate(protocol.publishedAt)}. No está en uso.`}</small>
        </div>
        <div className="obs-cfg-actions">
          {protocol.status==='BORRADOR'&&<>
            <button type="button" className="ghost" disabled={busy} onClick={()=>{if(window.confirm('¿Descartar el borrador?'))void run(()=>obsintConfigApi.discard(protocol.id),'Borrador descartado.')}}><Trash2 size={15}/>Descartar</button>
            <button type="button" className="obs-cfg-primary" disabled={busy} onClick={()=>void run(()=>obsintConfigApi.publish(protocol.id),'Versión publicada. Actívela para usarla en la Consola.')}><Send size={15}/>Publicar versión</button>
          </>}
          {protocol.status==='INACTIVO'&&<button type="button" className="obs-cfg-primary" disabled={busy} onClick={()=>void run(()=>obsintConfigApi.activate(protocol.id),'Versión activada.')}><CheckCircle2 size={15}/>Activar</button>}
          {protocol.status==='ACTIVO'&&<button type="button" className="ghost" disabled={busy} onClick={()=>void run(()=>obsintConfigApi.deactivate(protocol.id),'Versión inactivada.')}><Power size={15}/>Inactivar</button>}
          {protocol.status!=='BORRADOR'&&<button type="button" className="ghost" disabled={busy} onClick={()=>void run(()=>obsintConfigApi.draft(point.pointId),draft?'Abriendo el borrador.':'Nueva versión en borrador.')}><Edit3 size={15}/>{draft?'Ir al borrador':'Nueva versión'}</button>}
        </div>
      </>}
    </section>

    {protocol&&<div className="obs-cfg-layout">
      <section className="obs-card obs-cfg-categories">
        <header><h3>Categorías de tareas</h3><span>Filtre las tareas por categoría</span></header>
        <button type="button" className={category==='ALL'?'active':''} onClick={()=>setCategory('ALL')}><ClipboardList size={18}/><span>Todas las tareas</span><b>{consignas.length}</b></button>
        {CATEGORIES.map(c=><button key={c.value} type="button" className={category===c.value?'active':''} onClick={()=>setCategory(c.value)}>
          <i className={`obs-cfg-cat ${c.value.toLowerCase()}`}/><span>{c.label}</span><b>{consignas.filter(x=>x.category===c.value).length}</b></button>)}
      </section>

      <section className="obs-card obs-cfg-list">
        <header><div><h3>Tareas OBSINT ({consignas.length})</h3><span>Tareas de observación configuradas para este punto</span></div>
          {editable&&<button type="button" className="obs-cfg-primary" onClick={()=>{setEditing('new');setSelectedId('')}}><Plus size={16}/>Nueva tarea</button>}</header>
        <label className="obs-cfg-search"><Search size={16}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar tarea por nombre, código o descripción…"/></label>
        <table className="obs-cfg-table">
          <thead><tr><th>Código</th><th>Nombre de la tarea</th><th>Categoría</th><th>Tiempo</th><th>Cámaras</th><th>Estado</th>{editable&&<th>Acciones</th>}</tr></thead>
          <tbody>{visible.length?visible.map(c=><tr key={c.id} className={c.id===selectedId?'selected':''} onClick={()=>{setSelectedId(c.id);setEditing(null)}}>
            <td><b>{c.code}</b></td><td>{c.title}</td><td>{categoryLabel(c.category)}</td><td>{timeLimitLabel(c.timeLimitSeconds)}</td><td>{c.cameraIds.length}</td>
            <td><span className={`obs-cfg-pill ${c.active?'activo':'inactivo'}`}>{c.active?'Activa':'Inactiva'}</span></td>
            {editable&&<td className="obs-cfg-row-actions">
              <button type="button" aria-label="Editar" title="Editar" onClick={e=>{e.stopPropagation();setSelectedId(c.id);setEditing(c)}}><Edit3 size={15}/></button>
              <button type="button" aria-label="Eliminar" title="Eliminar" className="danger" onClick={e=>{e.stopPropagation();void removeConsigna(c)}}><Trash2 size={15}/></button>
            </td>}
          </tr>):<tr><td colSpan={editable?7:6} className="obs-cfg-empty">{consignas.length?'Ninguna tarea coincide con el filtro.':editable?'Agregue la primera tarea con "Nueva tarea".':'Esta versión no tiene tareas.'}</td></tr>}</tbody>
        </table>
      </section>

      <section className="obs-card obs-cfg-detail">
        {editing
          ?<ConsignaForm key={editing==='new'?'new':editing.id} protocolId={protocol.id} initial={editing==='new'?null:editing} cameras={cameras}
              defaultCategory={category==='ALL'?'SSO':category} onSaved={saved} onCancel={()=>setEditing(null)}/>
          :selected?<ConsignaDetail c={selected} cameraName={cameraName} editable={editable} onEdit={()=>setEditing(selected)}/>
          :<div className="obs-cfg-empty-detail"><ClipboardList size={30}/>Seleccione una tarea para ver su detalle{editable?' o cree una nueva':''}.</div>}
      </section>
    </div>}
    {notice&&<div className="obs-toast" role="status">{notice}</div>}
  </div>;
}

function ConsignaDetail({c,cameraName,editable,onEdit}:{c:ObsintConsignaConfig;cameraName:(id:string)=>string;editable:boolean;onEdit:()=>void}){
  return <>
    <header><h3>Detalle de la tarea</h3>{editable&&<button type="button" className="ghost" onClick={onEdit}><Edit3 size={15}/>Editar</button>}</header>
    <div className="obs-cfg-detail-head"><b>{c.code} · {c.title}</b><span className={`obs-cfg-pill ${c.active?'activo':'inactivo'}`}>{c.active?'Activa':'Inactiva'}</span></div>
    <Row label="Categoría">{categoryLabel(c.category)}</Row>
    <Row label="Descripción">{c.instruction}</Row>
    <Row label="Tiempo máximo de ejecución">{timeLimitLabel(c.timeLimitSeconds)}</Row>
    <Row label="Cámaras asociadas">{c.cameraIds.length?<ul className="obs-cfg-tags">{c.cameraIds.map(id=><li key={id}>{cameraName(id)}</li>)}</ul>:'Sin cámaras'}</Row>
    <Row label="Horario de ejecución">{c.schedule.length?<table className="obs-cfg-schedule"><tbody>{c.schedule.map(s=><tr key={s.dayOfWeek}><td>{DAYS[s.dayOfWeek-1]}</td><td>{s.start}</td><td>{s.end}</td></tr>)}</tbody></table>:'Sin horario'}</Row>
  </>;
}

function Row({label,children}:{label:string;children:ReactNode}){return <div className="obs-cfg-row"><span>{label}</span><div>{children}</div></div>}

function ConsignaForm({protocolId,initial,cameras,defaultCategory,onSaved,onCancel}:{protocolId:string;initial:ObsintConsignaConfig|null;cameras:ObsintCameraOption[];
  defaultCategory:ObsintCategory;onSaved:(c:ObsintConsignaConfig)=>void;onCancel:()=>void}){
  const [form,setForm]=useState<ObsintConsignaSave>(()=>({title:initial?.title??'',instruction:initial?.instruction??'',category:initial?.category??defaultCategory,
    timeLimitSeconds:initial?.timeLimitSeconds??120,active:initial?.active??true,cameraIds:initial?.cameraIds??[],schedule:initial?.schedule??[]}));
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const set=<K extends keyof ObsintConsignaSave>(key:K,value:ObsintConsignaSave[K])=>setForm(prev=>({...prev,[key]:value}));
  const slot=(day:number)=>form.schedule.find(s=>s.dayOfWeek===day);
  const setSlot=(day:number,next:ObsintSlot|null)=>set('schedule',[...form.schedule.filter(s=>s.dayOfWeek!==day),...(next?[next]:[])].sort((a,b)=>a.dayOfWeek-b.dayOfWeek));
  const toggleCamera=(id:string)=>set('cameraIds',form.cameraIds.includes(id)?form.cameraIds.filter(x=>x!==id):[...form.cameraIds,id]);
  const timeLimits=TIME_LIMITS.includes(form.timeLimitSeconds)?TIME_LIMITS:[...TIME_LIMITS,form.timeLimitSeconds].sort((a,b)=>a-b);

  async function submit(e:FormEvent){
    e.preventDefault();
    setSaving(true);setError('');
    try{onSaved(initial?await obsintConfigApi.updateConsigna(initial.id,form):await obsintConfigApi.createConsigna(protocolId,form))}
    catch(err){setError(errorText(err))}
    finally{setSaving(false)}
  }

  return <form className="obs-cfg-form" onSubmit={submit}>
    <header><h3>{initial?`Editar ${initial.code}`:'Nueva tarea'}</h3><button type="button" className="obs-cfg-icon" aria-label="Cancelar" onClick={onCancel}><X size={16}/></button></header>
    <Field label="Nombre de la tarea *"><input value={form.title} onChange={e=>set('title',e.target.value)} required maxLength={160} placeholder="Verificación de uso de EPP"/></Field>
    <div className="obs-cfg-form-row">
      <Field label="Categoría *"><select value={form.category} onChange={e=>set('category',e.target.value as ObsintCategory)}>{CATEGORIES.map(c=><option key={c.value} value={c.value}>{c.label}</option>)}</select></Field>
      <Field label="Tiempo máximo de ejecución *"><select value={form.timeLimitSeconds} onChange={e=>set('timeLimitSeconds',Number(e.target.value))}>{timeLimits.map(s=><option key={s} value={s}>{timeLimitLabel(s)}</option>)}</select></Field>
    </div>
    <Field label="Descripción (lo que ve el monitorista) *"><textarea value={form.instruction} onChange={e=>set('instruction',e.target.value)} required maxLength={1000} rows={3}
      placeholder="Observar y reportar el uso correcto de los elementos de protección personal por parte del personal en el punto."/></Field>
    <label className="obs-cfg-check"><input type="checkbox" checked={form.active} onChange={e=>set('active',e.target.checked)}/>Tarea activa</label>

    <fieldset className="obs-cfg-fieldset"><legend>Horario de ejecución *</legend>
      <table className="obs-cfg-schedule edit"><thead><tr><th>Día</th><th>Inicio</th><th>Fin</th></tr></thead><tbody>
        {DAYS.map((name,i)=>{const day=i+1;const s=slot(day);return <tr key={day}>
          <td><label><input type="checkbox" checked={!!s} onChange={e=>setSlot(day,e.target.checked?{dayOfWeek:day,start:'08:00',end:'18:00'}:null)}/>{name}</label></td>
          <td><input type="time" value={s?.start??''} disabled={!s} onChange={e=>s&&setSlot(day,{...s,start:e.target.value})} required={!!s}/></td>
          <td><input type="time" value={s?.end??''} disabled={!s} onChange={e=>s&&setSlot(day,{...s,end:e.target.value})} required={!!s}/></td>
        </tr>})}
      </tbody></table>
    </fieldset>

    <fieldset className="obs-cfg-fieldset"><legend>Cámaras asociadas * <small>{form.cameraIds.length}/4</small></legend>
      {cameras.length?<div className="obs-cfg-cameras">{cameras.map(c=>{const checked=form.cameraIds.includes(c.id);
        return <label key={c.id} className={checked?'checked':''}><input type="checkbox" checked={checked} disabled={!checked&&form.cameraIds.length>=4} onChange={()=>toggleCamera(c.id)}/>
          <span><b>{c.code}</b> {c.name}{c.location&&<small>{c.location}</small>}</span>{c.status==='OBSERVED'&&<em>Con observación</em>}</label>})}</div>
      :<p className="obs-cfg-hint"><AlertTriangle size={15}/>Este punto no tiene cámaras. Agréguelas en Componentes de Seguridad → Videovigilancia.</p>}
    </fieldset>

    {error&&<div className="obs-error">{error}</div>}
    <div className="obs-cfg-form-actions"><button type="submit" className="obs-cfg-primary" disabled={saving}>{saving?'Guardando…':'Guardar tarea'}</button><button type="button" className="ghost" onClick={onCancel}>Cancelar</button></div>
  </form>;
}

function Field({label,children}:{label:string;children:ReactNode}){return <label className="obs-cfg-field"><span>{label}</span>{children}</label>}
