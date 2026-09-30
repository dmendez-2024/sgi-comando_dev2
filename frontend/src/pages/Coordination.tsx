import {useEffect,useMemo,useState,type MouseEvent} from 'react';
import {createPortal} from 'react-dom';
import {
  AlertTriangle, ArrowLeft, Building2, ChevronRight, ClipboardList,
  Clock3, Copy, Eye, GripVertical, History, Info, MapPin, Monitor, MoreVertical, Plus,
  Power, RefreshCw, Route, Save, Search, ShieldCheck, Users, X
} from 'lucide-react';
import {api} from '../api';

type Company={id:string;code:string;name:string;companyType:string;kaibil:boolean};
type Shift={code:string;label:string;start:string;end:string};
type RouteSummary={id:string;code:string;name:string;versionNo:number;status:string;pointCount:number};
type CoordinationPost={id:string;companyId:string;companyName:string;code:string;name:string;postType:'MONITORING'|'SUPERVISION';format:'24/7'|'12/7'|'12/5';rotation:'6-2'|'5-2';shiftStartTime:string;dayMask:number;status:'DRAFT'|'INACTIVE'|'ACTIVE';shifts:Shift[];route?:RouteSummary|null;updatedBy:string};
type OperationalPost={id:string;code:string;name:string;format:string;shifts:Shift[]};
type Point={id:string;code:string;name:string;clientName:string;companyId:string;companyName:string;status:string;posts:OperationalPost[];coveredByActiveRoute:boolean;newOrUncovered:boolean};
type RoutePoint={id:string;pointId:string;sortOrder:number;pointCodeSnapshot:string;pointNameSnapshot:string;currentlyOperational:boolean};
type SupervisionRoute={id:string;seriesId:string;basedOnRouteId?:string|null;coordinationPostId:string;code:string;name:string;versionNo:number;status:'DRAFT'|'ACTIVE'|'REPLACED';publishedAt?:string|null;updatedBy:string;points:RoutePoint[]};
type Coverage={totalOperationalPoints:number;coveredPoints:number;uncoveredPoints:number;uncovered:Point[]};
type PreviewPoint={pointId:string;code:string;name:string;activePostCount:number;totalPostCount:number;activePosts:{postId:string;code:string;name:string;format:string}[];included:boolean};
type PreviewShift={code:string;label:string;start:string;end:string;points:PreviewPoint[]};
type Preview={routeId:string;routeCode:string;versionNo:number;shifts:PreviewShift[]};
type PageMode='LIST'|'VIEW'|'EDIT';

type FormState={id?:string;postType:'MONITORING'|'SUPERVISION';name:string;format:'24/7'|'12/7'|'12/5';shiftStartTime:string;dayMask:number;status:'DRAFT'|'INACTIVE'|'ACTIVE';code?:string};

const DAYS=[['L',1],['M',2],['X',4],['J',8],['V',16],['S',32],['D',64]] as const;
const statusLabel=(s:string)=>s==='DRAFT'?'Borrador':s==='ACTIVE'?'Activo':s==='INACTIVE'?'Inactivo':s==='REPLACED'?'Reemplazada':s;
const typeLabel=(s:string)=>s==='SUPERVISION'?'Supervisión':'Monitoreo';
const stateClass=(s:string)=>s.toLowerCase();
const errorText=(e:unknown)=>e instanceof Error?e.message:String(e);
const defaultForm=():FormState=>({postType:'MONITORING',name:'',format:'24/7',shiftStartTime:'06:00',dayMask:127,status:'DRAFT'});
const end12=(start:string)=>{const [h,m]=start.split(':').map(Number);const mins=((h||0)*60+(m||0)+720)%1440;return `${String(Math.floor(mins/60)).padStart(2,'0')}:${String(mins%60).padStart(2,'0')}`};

export default function Coordination(){
  const [companies,setCompanies]=useState<Company[]>([]);
  const [companyId,setCompanyId]=useState('');
  const [posts,setPosts]=useState<CoordinationPost[]>([]);
  const [points,setPoints]=useState<Point[]>([]);
  const [coverage,setCoverage]=useState<Coverage|null>(null);
  const [mode,setMode]=useState<PageMode>('LIST');
  const [selectedId,setSelectedId]=useState('');
  const [form,setForm]=useState<FormState>(defaultForm());
  const [route,setRoute]=useState<SupervisionRoute|null>(null);
  const [routeName,setRouteName]=useState('');
  const [routePointIds,setRoutePointIds]=useState<string[]>([]);
  const [preview,setPreview]=useState<Preview|null>(null);
  const [previewShift,setPreviewShift]=useState(0);
  const [routeHistory,setRouteHistory]=useState<SupervisionRoute[]|null>(null);
  const [query,setQuery]=useState('');
  const [typeFilter,setTypeFilter]=useState('ALL');
  const [statusFilter,setStatusFilter]=useState('ALL');
  const [formatFilter,setFormatFilter]=useState('ALL');
  const [pointQuery,setPointQuery]=useState('');
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [busy,setBusy]=useState(false);
  const [dragId,setDragId]=useState<string|null>(null);
  const [actionMenu,setActionMenu]=useState<{postId:string;top:number;left:number}|null>(null);
  const [pendingStatusChange,setPendingStatusChange]=useState<CoordinationPost|null>(null);

  const selected=posts.find(p=>p.id===selectedId)??null;
  const company=companies.find(c=>c.id===companyId)??null;

  useEffect(()=>{void bootstrap()},[]);
  useEffect(()=>{if(companyId)void loadCompany(companyId)},[companyId]);

  async function bootstrap(){
    setError('');
    try{
      const rows=await api.coordinationCompanies() as Company[];
      setCompanies(rows);
      if(rows[0])setCompanyId(rows[0].id);
    }catch(e){setError(errorText(e))}
  }

  async function loadCompany(id:string){
    setError('');
    try{
      const [p,pts,cov]=await Promise.all([api.coordinationPosts(id),api.coordinationPoints(id),api.coordinationCoverage(id)]) as [CoordinationPost[],Point[],Coverage];
      setPosts(p);setPoints(pts);setCoverage(cov);
      if(selectedId&&!p.some(x=>x.id===selectedId)){setSelectedId('');setMode('LIST')}
    }catch(e){setError(errorText(e))}
  }

  const filteredPosts=useMemo(()=>posts.filter(p=>{
    const q=query.trim().toLowerCase();
    if(q&&!`${p.code} ${p.name}`.toLowerCase().includes(q))return false;
    if(typeFilter!=='ALL'&&p.postType!==typeFilter)return false;
    if(statusFilter!=='ALL'&&p.status!==statusFilter)return false;
    if(formatFilter!=='ALL'&&p.format!==formatFilter)return false;
    return true;
  }),[posts,query,typeFilter,statusFilter,formatFilter]);

  const metrics=useMemo(()=>({
    total:posts.length,
    monitoring:posts.filter(p=>p.postType==='MONITORING').length,
    supervision:posts.filter(p=>p.postType==='SUPERVISION').length,
    routes:posts.filter(p=>p.route?.status==='ACTIVE').length,
    drafts:posts.filter(p=>p.status==='DRAFT').length,
  }),[posts]);

  function newPost(){setSelectedId('');setForm(defaultForm());setRoute(null);setRouteName('');setRoutePointIds([]);setPreview(null);setMode('EDIT');setNotice('');setError('')}

  async function openPost(p:CoordinationPost,pageMode:'VIEW'|'EDIT'){
    setSelectedId(p.id);setForm({id:p.id,code:p.code,postType:p.postType,name:p.name,format:p.format,shiftStartTime:p.shiftStartTime,dayMask:p.dayMask,status:p.status});setMode(pageMode);setError('');setNotice('');
    if(p.postType==='SUPERVISION')await loadRoute(p.id);else{setRoute(null);setPreview(null);setRoutePointIds([]);setRouteName('')}
  }

  async function editPost(p:CoordinationPost){await openPost(p,'EDIT')}
  async function viewPost(p:CoordinationPost){await openPost(p,'VIEW')}

  async function loadRoute(postId:string){
    try{
      const r=await api.coordinationRouteForPost(postId) as SupervisionRoute|null;
      setRoute(r);setRouteName(r?.name??'');setRoutePointIds(r?.points.map(x=>x.pointId)??[]);setPreview(null);setPreviewShift(0);
      if(r)void loadPreview(r.id);
    }catch(e){setError(errorText(e))}
  }

  async function savePost(){
    if(!companyId){setError('Seleccione una Compañía.');return}
    if(!form.name.trim()){setError('El nombre del Puesto es obligatorio.');return}
    if(form.format==='12/5'&&IntegerBitCount(form.dayMask)!==5){setError('12/5 requiere seleccionar exactamente 5 días.');return}
    setBusy(true);setError('');
    try{
      let saved:CoordinationPost;
      if(form.id)saved=await api.saveCoordinationPost(form.id,{name:form.name,format:form.format,shiftStartTime:form.shiftStartTime,dayMask:form.dayMask,status:form.status}) as CoordinationPost;
      else saved=await api.createCoordinationPost({companyId,postType:form.postType,name:form.name,format:form.format,shiftStartTime:form.shiftStartTime,dayMask:form.dayMask}) as CoordinationPost;
      setSelectedId(saved.id);setForm({id:saved.id,code:saved.code,postType:saved.postType,name:saved.name,format:saved.format,shiftStartTime:saved.shiftStartTime,dayMask:saved.dayMask,status:saved.status});
      await loadCompany(companyId);
      setQuery('');setTypeFilter('ALL');setStatusFilter('ALL');setFormatFilter('ALL');setMode('LIST');
      setNotice(`${saved.code} guardado correctamente.`);
    }catch(e){setError(errorText(e))}finally{setBusy(false)}
  }

  async function duplicatePost(post:CoordinationPost){
    setBusy(true);setError('');setActionMenu(null);
    try{
      const copy=await api.createCoordinationPost({companyId:post.companyId,postType:post.postType,name:`Copia de ${post.name}`.slice(0,160),format:post.format,shiftStartTime:post.shiftStartTime,dayMask:post.dayMask}) as CoordinationPost;
      await loadCompany(companyId);
      setQuery('');setTypeFilter('ALL');setStatusFilter('ALL');setFormatFilter('ALL');setSelectedId(copy.id);setMode('LIST');
      setNotice(`${copy.code} creado como borrador a partir de ${post.code}.`);
    }catch(e){setError(errorText(e))}finally{setBusy(false)}
  }

  async function confirmPostStatusChange(){
    if(!pendingStatusChange)return;
    const post=pendingStatusChange;const nextStatus=post.status==='ACTIVE'?'INACTIVE':'ACTIVE';
    setBusy(true);setError('');
    try{
      const updated=await api.saveCoordinationPost(post.id,{name:post.name,format:post.format,shiftStartTime:post.shiftStartTime,dayMask:post.dayMask,status:nextStatus}) as CoordinationPost;
      await loadCompany(companyId);setSelectedId(updated.id);setPendingStatusChange(null);setNotice(`${updated.code} ahora está ${statusLabel(updated.status).toLowerCase()}.`);
    }catch(e){setError(errorText(e))}finally{setBusy(false)}
  }

  function openPostActions(event:MouseEvent<HTMLButtonElement>,postId:string){
    if(actionMenu?.postId===postId){setActionMenu(null);return}
    const rect=event.currentTarget.getBoundingClientRect();const menuHeight=96;const menuWidth=196;
    const top=rect.bottom+menuHeight+10>window.innerHeight?Math.max(8,rect.top-menuHeight-8):rect.bottom+8;
    const left=Math.max(8,Math.min(rect.right-menuWidth,window.innerWidth-menuWidth-8));
    setActionMenu({postId,top,left});
  }

  async function createRoute(){
    if(!form.id)return;
    setBusy(true);setError('');
    try{
      const r=await api.createCoordinationRoute(form.id,{name:`Ruta ${form.name}`,pointIds:[]}) as SupervisionRoute;
      setRoute(r);setRouteName(r.name);setRoutePointIds([]);setNotice(`${r.code} creada en Borrador.`);
      await loadCompany(companyId);
    }catch(e){setError(errorText(e))}finally{setBusy(false)}
  }

  async function editPublishedRoute(){
    if(!route)return;
    setBusy(true);setError('');
    try{const r=await api.forkCoordinationRoute(route.id) as SupervisionRoute;setRoute(r);setRouteName(r.name);setRoutePointIds(r.points.map(x=>x.pointId));setPreview(null);setNotice(`Se creó ${r.code} v${r.versionNo} como Borrador.`)}catch(e){setError(errorText(e))}finally{setBusy(false)}
  }

  async function saveRoute(){
    if(!route)return;
    setBusy(true);setError('');
    try{const r=await api.saveCoordinationRoute(route.id,{name:routeName,pointIds:routePointIds}) as SupervisionRoute;setRoute(r);setRoutePointIds(r.points.map(x=>x.pointId));setNotice('Ruta guardada.');await loadCompany(companyId);void loadPreview(r.id)}catch(e){setError(errorText(e))}finally{setBusy(false)}
  }

  async function publishRoute(){
    if(!route)return;
    setBusy(true);setError('');
    try{await api.saveCoordinationRoute(route.id,{name:routeName,pointIds:routePointIds});const r=await api.publishCoordinationRoute(route.id) as SupervisionRoute;setRoute(r);setRoutePointIds(r.points.map(x=>x.pointId));setNotice(`${r.code} v${r.versionNo} publicada como Ruta Activa.`);await loadCompany(companyId);void loadPreview(r.id)}catch(e){setError(errorText(e))}finally{setBusy(false)}
  }

  async function loadPreview(routeId:string){try{const p=await api.coordinationRoutePreview(routeId) as Preview;setPreview(p);setPreviewShift(0)}catch(e){setError(errorText(e))}}
  async function showHistory(){if(!route)return;try{setRouteHistory(await api.coordinationRouteHistory(route.id) as SupervisionRoute[])}catch(e){setError(errorText(e))}}

  function setFormat(format:FormState['format']){setForm(f=>({...f,format,dayMask:format==='12/5'?(IntegerBitCount(f.dayMask)===5?f.dayMask:31):127}))}
  function toggleDay(bit:number){if(form.format!=='12/5')return;setForm(f=>({...f,dayMask:(f.dayMask&bit)?f.dayMask&~bit:f.dayMask|bit}))}
  function toggleRoutePoint(id:string){if(mode==='VIEW'||route?.status!=='DRAFT')return;setRoutePointIds(ids=>ids.includes(id)?ids.filter(x=>x!==id):[...ids,id])}
  function moveRoutePoint(overId:string){if(mode==='VIEW'||route?.status!=='DRAFT'||!dragId||dragId===overId)return;setRoutePointIds(ids=>{const a=ids.indexOf(dragId),b=ids.indexOf(overId);if(a<0||b<0)return ids;const next=[...ids];next.splice(a,1);next.splice(b,0,dragId);return next});setDragId(null)}

  const availablePoints=useMemo(()=>points.filter(p=>!pointQuery.trim()||`${p.code} ${p.name} ${p.clientName}`.toLowerCase().includes(pointQuery.trim().toLowerCase())),[points,pointQuery]);
  const routePoints=routePointIds.map(id=>points.find(p=>p.id===id)).filter(Boolean) as Point[];
  const previewCurrent=preview?.shifts[Math.min(previewShift,(preview?.shifts.length??1)-1)]??null;
  const actionMenuPost=actionMenu?posts.find(post=>post.id===actionMenu.postId)??null:null;

  return <div className="coord-page">
    {mode==='LIST'?<>
      <div className="coord-titlebar"><div><div className="ser-title-row"><h2>Coordinación</h2><span>Puestos internos de Monitoreo y Supervisión por Compañía</span></div><small>Administre Puestos de Coordinación, horarios y Rutas de Supervisión.</small></div><button className="coord-primary" onClick={newPost}><Plus size={16}/>Crear Puesto de Coordinación</button></div>

      <div className="coord-context panel"><div><span>Compañía</span><select value={companyId} onChange={e=>setCompanyId(e.target.value)}>{companies.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></div><div><span>Tipo</span><strong>{company?.kaibil?'Kaibil · Coordinación':'Compañía operativa'}</strong></div><div><span>Puestos de Coordinación</span><strong>{metrics.total}</strong></div><div><span>Rutas activas</span><strong>{metrics.routes}</strong></div></div>

      <div className="coord-kpis"><article><span className="blue"><ClipboardList/></span><div><small>Puestos de Coordinación</small><strong>{metrics.total}</strong><em>configurados</em></div></article><article><span className="blue"><Monitor/></span><div><small>Monitoreo</small><strong>{metrics.monitoring}</strong><em>puestos</em></div></article><article><span className="green"><Users/></span><div><small>Supervisión</small><strong>{metrics.supervision}</strong><em>puestos</em></div></article><article><span className="blue"><Route/></span><div><small>Rutas activas</small><strong>{metrics.routes}</strong><em>vigentes</em></div></article><article><span className="amber"><AlertTriangle/></span><div><small>Alertas</small><strong>{coverage?.uncoveredPoints??0}</strong><em>sin cobertura</em></div></article></div>

      {(coverage?.uncoveredPoints??0)>0&&<div className="coord-alert"><AlertTriangle size={18}/><div><strong>{coverage!.uncoveredPoints} Punto{coverage!.uncoveredPoints===1?'':'s'} activo{coverage!.uncoveredPoints===1?'':'s'} sin cobertura de Ruta de Supervisión</strong><span>{coverage!.uncovered.slice(0,3).map(p=>p.name).join(' · ')}</span></div></div>}
      {error&&<Message tone="error" text={error} onClose={()=>setError('')}/>} {notice&&<Message tone="notice" text={notice} onClose={()=>setNotice('')}/>} 

      <div className="coord-list-card">
        <div className="coord-filters"><label className="coord-search"><Search size={16}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar por código o nombre…"/></label><label><span>Tipo</span><select value={typeFilter} onChange={e=>setTypeFilter(e.target.value)}><option value="ALL">Todos</option><option value="MONITORING">Monitoreo</option><option value="SUPERVISION">Supervisión</option></select></label><label><span>Estado</span><select value={statusFilter} onChange={e=>setStatusFilter(e.target.value)}><option value="ALL">Todos</option><option value="DRAFT">Borrador</option><option value="ACTIVE">Activo</option><option value="INACTIVE">Inactivo</option></select></label><label><span>Formato</span><select value={formatFilter} onChange={e=>setFormatFilter(e.target.value)}><option value="ALL">Todos</option><option>24/7</option><option>12/7</option><option>12/5</option></select></label><button onClick={()=>{setQuery('');setTypeFilter('ALL');setStatusFilter('ALL');setFormatFilter('ALL')}}><RefreshCw size={14}/>Limpiar filtros</button></div>
        <div className="coord-table-wrap"><table className="coord-table"><thead><tr><th>Código</th><th>Nombre</th><th>Tipo</th><th>Formato</th><th>Rotación</th><th>Horario</th><th>Ruta</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{filteredPosts.map(p=><tr key={p.id}><td><strong>{p.code}</strong></td><td>{p.name}</td><td><span className="coord-type">{p.postType==='MONITORING'?<Monitor size={15}/>:<Users size={15}/>} {typeLabel(p.postType)}</span></td><td>{p.format}</td><td>{p.rotation}</td><td>{p.shifts.map((s,i)=><span key={s.code} className="coord-shift-line">{s.start}–{s.end}{i<p.shifts.length-1?<br/>:null}</span>)}</td><td>{p.route?<button className="linklike" onClick={()=>void viewPost(p)}>{p.route.name} v{p.route.versionNo}</button>:'—'}</td><td><span className={`coord-status ${stateClass(p.status)}`}>{statusLabel(p.status)}</span></td><td><div className="coord-row-actions"><button title="Ver" aria-label={`Ver ${p.code}`} onClick={()=>void viewPost(p)}><Eye size={15}/></button><button title="Editar" aria-label={`Editar ${p.code}`} onClick={()=>void editPost(p)}><Save size={15}/></button><button title="Más acciones" aria-label={`Más acciones para ${p.code}`} aria-haspopup="menu" aria-expanded={actionMenu?.postId===p.id} onClick={event=>openPostActions(event,p.id)}><MoreVertical size={15}/></button></div></td></tr>)}{!filteredPosts.length&&<tr><td colSpan={9} className="coord-empty">No hay Puestos con estos filtros.</td></tr>}</tbody></table></div>
      </div>
      {actionMenu&&actionMenuPost&&createPortal(<div className="coord-action-menu-veil" onMouseDown={()=>setActionMenu(null)} onKeyDown={event=>{if(event.key==='Escape')setActionMenu(null)}}><div className="coord-action-menu" role="menu" aria-label={`Acciones para ${actionMenuPost.code}`} style={{top:actionMenu.top,left:actionMenu.left}} onMouseDown={event=>event.stopPropagation()}><button type="button" role="menuitem" autoFocus onClick={()=>{setPendingStatusChange(actionMenuPost);setActionMenu(null)}}><Power size={15}/>{actionMenuPost.status==='ACTIVE'?'Inactivar puesto':'Activar puesto'}</button><button type="button" role="menuitem" onClick={()=>void duplicatePost(actionMenuPost)}><Copy size={15}/>Duplicar como borrador</button></div></div>,document.body)}
      {pendingStatusChange&&<div className="coord-confirm-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)setPendingStatusChange(null)}} onKeyDown={event=>{if(event.key==='Escape'&&!busy)setPendingStatusChange(null)}}><section className="coord-confirm" role="alertdialog" aria-modal="true" aria-labelledby="coord-status-confirm-title"><span className={`coord-status ${stateClass(pendingStatusChange.status==='ACTIVE'?'INACTIVE':'ACTIVE')}`}>{pendingStatusChange.status==='ACTIVE'?'Inactivar':'Activar'}</span><h3 id="coord-status-confirm-title">¿{pendingStatusChange.status==='ACTIVE'?'Inactivar':'Activar'} {pendingStatusChange.code}?</h3><p>{pendingStatusChange.status==='ACTIVE'?'El puesto dejará de contar como activo en la cobertura operativa.':'El puesto quedará disponible como activo. En Supervisión, la cobertura requiere además una Ruta activa.'}</p><div><button type="button" autoFocus onClick={()=>setPendingStatusChange(null)} disabled={busy}>Cancelar</button><button type="button" className="coord-primary" onClick={()=>void confirmPostStatusChange()} disabled={busy}>{busy?'Actualizando…':'Confirmar cambio'}</button></div></section></div>}
    </>:<>
      <div className="coord-titlebar"><div><button className="coord-back" onClick={()=>{setMode('LIST');setError('');setNotice('')}}><ArrowLeft size={15}/>Volver al listado</button><div className="ser-title-row"><h2>Puesto de Coordinación</h2><span>{mode==='VIEW'?'Ver puesto':form.id?'Editar puesto':'Crear nuevo puesto'}</span></div><small>{mode==='VIEW'?'Consulte la información del Puesto y su Ruta.':'Configure la información, jornada y —para Supervisión— su Ruta.'}</small></div>{mode==='EDIT'&&<div className="coord-edit-actions"><button onClick={()=>setMode('LIST')}>Cancelar</button><button className="coord-primary" onClick={()=>void savePost()} disabled={busy}><Save size={15}/>{busy?'Guardando…':'Guardar Puesto'}</button></div>}</div>
      {error&&<Message tone="error" text={error} onClose={()=>setError('')}/>} {notice&&<Message tone="notice" text={notice} onClose={()=>setNotice('')}/>} 

      <div className="coord-editor-layout"><div className="coord-editor-main">
        <section className="coord-section"><header>1. Tipo de Puesto</header><div className="coord-type-picker"><button className={form.postType==='MONITORING'?'selected':''} disabled={mode==='VIEW'||!!form.id} onClick={()=>setForm(f=>({...f,postType:'MONITORING'}))}><span><Monitor/></span><div><strong>Monitoreo</strong><small>Puesto interno de monitoreo y control operativo.</small></div><i/></button><button className={form.postType==='SUPERVISION'?'selected':''} disabled={mode==='VIEW'||!!form.id} onClick={()=>setForm(f=>({...f,postType:'SUPERVISION'}))}><span><Users/></span><div><strong>Supervisión</strong><small>Puesto de supervisión en campo con Ruta de Puntos.</small></div><i/></button></div></section>

        <section className="coord-section"><header>2. Información general</header><div className="coord-form-grid"><label><span>Código</span><input value={form.code??'Automático al guardar'} disabled/></label><label className="wide"><span>Nombre *</span><input value={form.name} disabled={mode==='VIEW'} onChange={e=>setForm(f=>({...f,name:e.target.value}))} placeholder={form.postType==='MONITORING'?'Ej. Monitoreo Principal':'Ej. Supervisión Norte'}/></label><label><span>Compañía</span><input value={company?.name??''} disabled/></label><label><span>Estado</span><select value={form.status} disabled={!form.id||mode==='VIEW'} onChange={e=>setForm(f=>({...f,status:e.target.value as FormState['status']}))}><option value="DRAFT">Borrador</option><option value="INACTIVE">Inactivo</option><option value="ACTIVE">Activo</option></select></label></div></section>

        <section className="coord-section"><header>3. Jornada operacional</header><div className="coord-format-grid"><div className="coord-format-options"><button className={form.format==='24/7'?'selected':''} disabled={mode==='VIEW'} onClick={()=>setFormat('24/7')}><i/> <strong>24/7</strong><small>Operación continua</small></button><button className={form.format==='12/7'?'selected':''} disabled={mode==='VIEW'} onClick={()=>setFormat('12/7')}><i/> <strong>12/7</strong><small>12 horas, 7 días</small></button><button className={form.format==='12/5'?'selected':''} disabled={mode==='VIEW'} onClick={()=>setFormat('12/5')}><i/> <strong>12/5</strong><small>12 horas, 5 días</small></button></div><label><span>Rotación</span><input value={form.format==='12/5'?'5-2':'6-2'} disabled/><small>Definida automáticamente según el formato.</small></label></div>{form.format==='12/5'&&<div className="coord-days"><div><strong>Días de operación</strong><span>Seleccione exactamente cinco días.</span></div><div>{DAYS.map(([d,bit])=><button key={d} disabled={mode==='VIEW'} className={(form.dayMask&bit)?'selected':''} onClick={()=>toggleDay(bit)}>{d}</button>)}</div><em>{IntegerBitCount(form.dayMask)}/5 seleccionados</em></div>}</section>

        <section className="coord-section"><header>4. Horarios</header><div className="coord-schedule-box"><label><span>{form.format==='24/7'?'Hora de inicio del primer turno':'Hora de inicio'} *</span><input type="time" value={form.shiftStartTime} disabled={mode==='VIEW'} onChange={e=>setForm(f=>({...f,shiftStartTime:e.target.value}))}/></label><div className="coord-resulting-shifts">{form.format==='24/7'?<><article><span>Turno 1 (Día)</span><strong>{form.shiftStartTime} – {end12(form.shiftStartTime)}</strong><small>12 horas</small></article><article><span>Turno 2 (Noche)</span><strong>{end12(form.shiftStartTime)} – {form.shiftStartTime}</strong><small>12 horas</small></article></>:<article><span>Turno operativo</span><strong>{form.shiftStartTime} – {end12(form.shiftStartTime)}</strong><small>12 horas</small></article>}</div></div></section>

        {form.postType==='SUPERVISION'&&<section className="coord-section"><header>5. Ruta de Supervisión</header>{!form.id?<div className="coord-empty-route"><Route size={30}/><strong>Primero guarde el Puesto</strong><span>La Ruta puede construirse mientras el Puesto permanezca en Borrador.</span></div>:!route?<div className="coord-empty-route"><Route size={30}/><strong>Sin Ruta configurada</strong><span>Cree la Ruta para definir la secuencia de Puntos a visitar.</span>{mode==='EDIT'&&<button className="coord-primary" onClick={()=>void createRoute()}><Plus size={14}/>Crear Ruta</button>}</div>:<>
          <div className="coord-route-head"><div><Route size={19}/><div><strong>{route.code}</strong><input value={routeName} onChange={e=>setRouteName(e.target.value)} disabled={mode==='VIEW'||route.status!=='DRAFT'}/><small>v{route.versionNo} · {statusLabel(route.status)} · {routePointIds.length} Puntos</small></div></div><div><button onClick={()=>void showHistory()}><History size={14}/>Historial</button>{mode==='EDIT'&&(route.status==='DRAFT'?<><button onClick={()=>void saveRoute()} disabled={busy}><Save size={14}/>Guardar Ruta</button><button className="coord-primary" onClick={()=>void publishRoute()} disabled={busy||!routePointIds.length}><ShieldCheck size={14}/>Publicar Ruta</button></>:<button className="coord-primary" onClick={()=>void editPublishedRoute()}><Plus size={14}/>Crear nueva versión</button>)}</div></div>
          <div className="coord-route-editor"><div className="coord-available"><h4>Puntos disponibles</h4><label className="coord-search"><Search size={15}/><input value={pointQuery} onChange={e=>setPointQuery(e.target.value)} placeholder="Buscar Punto…"/></label><div>{availablePoints.map(p=><button key={p.id} disabled={mode==='VIEW'||route.status!=='DRAFT'} className={routePointIds.includes(p.id)?'included':''} onClick={()=>toggleRoutePoint(p.id)}><MapPin size={16}/><div><strong>{p.name}</strong><span>{p.posts.length} Puesto{p.posts.length===1?'':'s'} · {p.posts.map(x=>x.format).filter((v,i,a)=>a.indexOf(v)===i).join(' / ')}</span>{p.newOrUncovered&&<em>Nuevo / Sin cobertura</em>}</div><Plus size={14}/></button>)}</div></div><div className="coord-sequence"><h4>Secuencia de la Ruta</h4><span>Arrastre para reordenar los Puntos.</span><div className="coord-route-sequence">{routePoints.map((p,i)=><div key={p.id} draggable={mode==='EDIT'&&route.status==='DRAFT'} onDragStart={()=>setDragId(p.id)} onDragOver={e=>e.preventDefault()} onDrop={()=>moveRoutePoint(p.id)}><b>{i+1}</b><MapPin size={15}/><span>{p.name}</span><GripVertical size={15}/><button disabled={mode==='VIEW'||route.status!=='DRAFT'} onClick={()=>toggleRoutePoint(p.id)}><X size={13}/></button></div>)}{!routePoints.length&&<p>Agregue uno o más Puntos.</p>}</div></div></div>
        </>}</section>}
      </div>

      <aside className="coord-summary"><section><div className="coord-summary-title"><h3>Resumen del Puesto</h3><span className={`coord-status ${stateClass(form.status)}`}>{statusLabel(form.status)}</span></div><div className="coord-summary-identity"><span>{form.postType==='MONITORING'?<Monitor/>:<Users/>}</span><div><strong>{form.code??(form.postType==='MONITORING'?'MON----':'SUP----')}</strong><h4>{form.name||'Nuevo Puesto'}</h4><small>{typeLabel(form.postType)}</small></div></div><dl><dt><Building2 size={15}/>Compañía</dt><dd>{company?.name??'—'}</dd><dt><ClipboardList size={15}/>Formato</dt><dd>{form.format}</dd><dt><RefreshCw size={15}/>Rotación</dt><dd>{form.format==='12/5'?'5-2':'6-2'}</dd><dt><Clock3 size={15}/>Turnos</dt><dd>{form.format==='24/7'?<>{form.shiftStartTime}–{end12(form.shiftStartTime)}<br/>{end12(form.shiftStartTime)}–{form.shiftStartTime}</>:<>{form.shiftStartTime}–{end12(form.shiftStartTime)}</>}</dd>{route&&<><dt><Route size={15}/>Ruta</dt><dd>{route.name} v{route.versionNo}</dd></>}</dl></section>
        {form.postType==='SUPERVISION'&&route&&<section><h3>Vista previa de ejecución</h3>{preview?.shifts.length?<><div className="coord-preview-tabs">{preview.shifts.map((s,i)=><button key={s.code} className={i===previewShift?'selected':''} onClick={()=>setPreviewShift(i)}>{s.label}</button>)}</div><div className="coord-preview-list">{previewCurrent?.points.map((p,i)=><article key={p.pointId} className={p.included?'':'omitted'}><b>{i+1}</b><div><strong>{p.name}</strong><span>{p.included?`${p.activePostCount}/${p.totalPostCount} Puestos activos`:'Omitido en este turno'}</span>{p.activePosts.map(x=><small key={x.postId}>{x.code} · {x.name}</small>)}</div></article>)}</div></>:<div className="coord-empty-preview">Guarde la Ruta para calcular la ejecución.</div>}{(coverage?.uncoveredPoints??0)>0&&<div className="coord-mini-alert"><AlertTriangle size={17}/><div><strong>Cobertura de Puntos</strong><span>{coverage!.uncoveredPoints} Punto{coverage!.uncoveredPoints===1?'':'s'} activo{coverage!.uncoveredPoints===1?'':'s'} sin cobertura</span></div></div>}</section>}
      </aside></div>
    </>}

    {routeHistory&&<div className="coord-modal-backdrop" onClick={()=>setRouteHistory(null)}><section className="coord-modal" onClick={e=>e.stopPropagation()}><header><div><h3>Historial de Ruta</h3><span>{route?.code}</span></div><button onClick={()=>setRouteHistory(null)}><X size={18}/></button></header><div>{routeHistory.map(r=><article key={r.id}><div><strong>v{r.versionNo}</strong><span className={`coord-status ${stateClass(r.status)}`}>{statusLabel(r.status)}</span></div><div><b>{r.name}</b><small>{r.points.length} Puntos · {r.updatedBy}</small></div><small>{r.publishedAt?new Date(r.publishedAt).toLocaleString('es-EC'):'Borrador no publicado'}</small></article>)}</div></section></div>}
  </div>
}

function IntegerBitCount(n:number){let x=n>>>0,c=0;while(x){x&=x-1;c++}return c}
function Message({tone,text,onClose}:{tone:'error'|'notice';text:string;onClose:()=>void}){return <div className={`coord-message ${tone}`}>{tone==='error'?<AlertTriangle size={16}/>:<Info size={16}/>}<span>{text}</span><button onClick={onClose}><X size={14}/></button></div>}
