import {useEffect,useMemo,useState} from 'react';
import {AlertTriangle,Camera,CheckCircle2,Info,Loader2,RefreshCw,Send,Trash2,Upload} from 'lucide-react';
import {api,ApiError} from '../api';
import {buildEvidenceForm,pickPhotos,type PickedPhoto} from '../lib/evidenceUpload';

type Std={id:string;position:number};
type Cp={checkpointId:string;code:string;name:string;description:string;requiresEvidence:boolean;hasStandardImage:boolean;standardImages:Std[];standardImageNotes:string;latitude:number|null;longitude:number|null};
type Patrol={protocolCode:string;patrolId:string;code:string;name:string;checkpoints:Cp[]};
type ConsignmentTask={consignmentId:string;code:string;title:string;instruction:string;evidences:{evidenceId:string;name:string;description:string;visintEnabled:boolean;standardImageNotes:string;standardImages:Std[]}[]};
type LogbookTask={protocolId:string;code:string;name:string;fields:{fieldId:string;accreditationCode:string|null;section:string;name:string;fieldType:string;visintEnabled:boolean;standardImageNotes:string|null;standardImages:Std[]}[]};
type Kind='PATRULLA'|'CONSIGNA'|'BITACORA';
/** La tarea seleccionada, igual para los tres módulos. */
type Task={kind:Kind;targetType:string;targetId:string;label:string;visint:boolean;standardImages:Std[];notes:string|null;latitude:number|null;longitude:number|null;requiresEvidence:boolean};
type Outcome={eventId:string;captureNo:number;outcome:'NOT_REQUIRED'|'PENDING'|'VALIDATED'|'NOT_VALIDATED'|'TECHNICAL_ERROR';message:string;canRetake:boolean};
type Result={clientEvidenceId:string;evidenceId?:string;status:string;reason?:string|null;flags:string[]};
const REASONS:Record<string,string>={FILE_TOO_LARGE:'Supera 5 MB',UNSUPPORTED_FORMAT:'Formato no permitido',CHECKSUM_MISMATCH:'El archivo se dañó en el envío',TOO_MANY_PHOTOS:'Solo se permite 1 foto por tarea'};
const FLAGS:Record<string,string>={OUT_OF_RANGE:'Fuera del radio GPS'};
const KINDS:{key:Kind;label:string}[]=[{key:'PATRULLA',label:'Patrulla'},{key:'CONSIGNA',label:'Consigna'},{key:'BITACORA',label:'Bitácora'}];
const INSTANCE='11111111-1111-1111-1111-111111111111';
const errorText=(e:unknown)=>e instanceof ApiError?(e.body||`Error ${e.status}`):String(e);

/** TEMPORAL (demo UAT) — quitar junto con SIMULATOR_USER de api.ts.
 * Reproduce lo que hará SGI: Operador — elegir la tarea (Hito, consigna o campo de bitácora), tomar la foto, subirla por FormData y confirmar. */
export default function AgentSimulator(){
 const [employeeId,setEmployeeId]=useState('');const [assignments,setAssignments]=useState<any[]>([]);const [assignmentId,setAssignmentId]=useState('');
 const [kind,setKind]=useState<Kind>('PATRULLA');
 const [patrols,setPatrols]=useState<Patrol[]>([]);const [patrolId,setPatrolId]=useState('');const [checkpointId,setCheckpointId]=useState('');
 const [consignments,setConsignments]=useState<ConsignmentTask[]>([]);const [consignmentId,setConsignmentId]=useState('');const [consignmentEvidenceId,setConsignmentEvidenceId]=useState('');
 const [logbooks,setLogbooks]=useState<LogbookTask[]>([]);const [logbookId,setLogbookId]=useState('');const [fieldId,setFieldId]=useState('');
 const [entryId,setEntryId]=useState(()=>crypto.randomUUID());
 const [guideUrls,setGuideUrls]=useState<string[]>([]);const [photos,setPhotos]=useState<PickedPhoto[]>([]);const [results,setResults]=useState<Result[]>([]);
 const [progress,setProgress]=useState<number|null>(null);const [error,setError]=useState('');const [done,setDone]=useState('');
 const [eventId,setEventId]=useState(()=>crypto.randomUUID());const [outcome,setOutcome]=useState<Outcome|null>(null);const [runId]=useState(()=>crypto.randomUUID());

 const patrol=patrols.find(p=>p.patrolId===patrolId);
 const consignment=consignments.find(c=>c.consignmentId===consignmentId);
 const logbook=logbooks.find(l=>l.protocolId===logbookId);
 const task=useMemo<Task|null>(()=>{
  if(kind==='PATRULLA'){const cp=patrol?.checkpoints.find(c=>c.checkpointId===checkpointId);return cp?{kind,targetType:'PATROL_CHECKPOINT',targetId:cp.checkpointId,label:`Hito ${cp.code}`,visint:true,standardImages:cp.standardImages,notes:cp.standardImageNotes,latitude:cp.latitude,longitude:cp.longitude,requiresEvidence:cp.requiresEvidence}:null}
  if(kind==='CONSIGNA'){const ev=consignment?.evidences.find(e=>e.evidenceId===consignmentEvidenceId);return ev&&consignment?{kind,targetType:'CONSIGNMENT_EVIDENCE',targetId:ev.evidenceId,label:`Consigna ${consignment.code} · ${ev.name}`,visint:ev.visintEnabled,standardImages:ev.standardImages,notes:ev.standardImageNotes,latitude:null,longitude:null,requiresEvidence:true}:null}
  const f=logbook?.fields.find(x=>x.fieldId===fieldId);return f?{kind,targetType:'LOGBOOK_FIELD',targetId:f.fieldId,label:`Bitácora · ${f.name}`,visint:f.visintEnabled,standardImages:f.standardImages,notes:f.standardImageNotes,latitude:null,longitude:null,requiresEvidence:true}:null;
 },[kind,patrol,checkpointId,consignment,consignmentEvidenceId,logbook,fieldId]);
 const stored=results.filter(r=>r.status==='STORED'||r.status==='ALREADY_STORED');
 const valid=photos.filter(p=>!p.problem);
 const gps=useMemo(()=>task?.latitude!=null&&task?.longitude!=null?{latitude:task.latitude,longitude:task.longitude,accuracyM:8}:undefined,[task]);

 useEffect(()=>{
  api.operatorRuntime().then(r=>{
   const list=[...(r.assignments??[])].sort((a:any,b:any)=>a.startsAt.localeCompare(b.startsAt));
   const now=new Date().toISOString();const current=[...list].reverse().find((a:any)=>a.startsAt<=now)??list[0];
   setEmployeeId(r.employeeId);setAssignments(list);setAssignmentId(current?.assignmentId??'');
  }).catch(e=>setError(errorText(e)));
 },[]);
 useEffect(()=>{
  if(!assignmentId)return;
  api.operatorRuntime(assignmentId).then(r=>{
   const list:Patrol[]=(r.patrols??[]).filter((p:Patrol)=>p.checkpoints.length);setPatrols(list);setPatrolId(list[0]?.patrolId??'');setCheckpointId(list[0]?.checkpoints[0]?.checkpointId??'');
   const cons:ConsignmentTask[]=r.consignmentTasks??[];setConsignments(cons);setConsignmentId(cons[0]?.consignmentId??'');setConsignmentEvidenceId(cons[0]?.evidences[0]?.evidenceId??'');
   const logs:LogbookTask[]=(r.logbookTasks??[]).filter((l:LogbookTask)=>l.fields.length);setLogbooks(logs);setLogbookId(logs[0]?.protocolId??'');setFieldId(logs[0]?.fields[0]?.fieldId??'');
  }).catch(e=>setError(errorText(e)));
 },[assignmentId]);
 const reset=()=>{photos.forEach(p=>URL.revokeObjectURL(p.previewUrl));setPhotos([]);setResults([]);setDone('');setOutcome(null);setEventId(crypto.randomUUID())};
 useEffect(()=>{
  reset();
  setGuideUrls(old=>{old.forEach(u=>URL.revokeObjectURL(u));return []});
  if(task)void Promise.all(task.standardImages.map(i=>api.operatorStandardImage(i.id,assignmentId).then(b=>URL.createObjectURL(b)).catch(()=>'')))
   .then(urls=>setGuideUrls(urls.filter(Boolean)));
 // eslint-disable-next-line react-hooks/exhaustive-deps
 },[task?.targetId]);

 const add=async(files:FileList|null)=>{if(!files||!task)return;const picked=await pickPhotos(files,valid.length+stored.length,1);setPhotos(prev=>[...prev,...picked])};
 const remove=(id:string)=>{const p=photos.find(x=>x.clientEvidenceId===id);if(p)URL.revokeObjectURL(p.previewUrl);setPhotos(photos.filter(x=>x.clientEvidenceId!==id));setResults(results.filter(r=>r.clientEvidenceId!==id||r.status!=='REJECTED'))};
 const upload=async()=>{
  if(!task||!valid.length)return;setError('');setProgress(0);
  try{
   const batch=crypto.randomUUID();
   const r=await api.uploadEvidences(buildEvidenceForm({uploadBatchId:batch,eventId,assignmentId,targetId:task.targetId,targetType:task.targetType},valid,gps),{onProgress:setProgress,idempotencyKey:batch});
   const incoming:Result[]=r.results;
   setResults(prev=>[...prev.filter(x=>!incoming.some(y=>y.clientEvidenceId===x.clientEvidenceId)),...incoming]);
   setPhotos(prev=>prev.filter(p=>!incoming.some(y=>y.clientEvidenceId===p.clientEvidenceId&&y.status!=='REJECTED')));
  }catch(e){setError(errorText(e))}finally{setProgress(null)}
 };
 const confirm=async()=>{
  if(!task)return;setError('');
  try{
   const now=new Date().toISOString();
   const evidenceIds=stored.map(s=>s.evidenceId);
   const event=task.kind==='PATRULLA'
    ?{type:'PATROL_CHECKPOINT_COMPLETED',eventId,assignmentId,patrolRunId:runId,patrolId:patrol!.patrolId,checkpointId:task.targetId,executedAt:now,...(gps??{}),evidenceIds}
    :{type:'TASK_EVIDENCE_SUBMITTED',eventId,assignmentId,targetType:task.targetType,targetId:task.targetId,...(task.kind==='BITACORA'?{groupId:entryId}:{}),executedAt:now,evidenceIds};
   const r=await api.submitExecution({batchId:crypto.randomUUID(),correlationId:crypto.randomUUID(),employeeId,instanceCountryId:INSTANCE,deviceId:'simulador-web',capturedAt:now,events:[event]});
   setDone(`${task.label} registrado con ${r.results[0].evidenceCount} foto(s). Estado: recibido.`);
   setOutcome(await api.operatorExecution(eventId));
  }catch(e){setError(errorText(e))}
 };
 const min=task?.requiresEvidence?1:0;
 const canConfirm=!!task&&stored.length>=min&&stored.length<=1&&!done;
 // Fase 3: mientras VISINT valida, se consulta el resultado cada 3 s (como hará la app del agente).
 useEffect(()=>{
  if(outcome?.outcome!=='PENDING')return;
  const t=setTimeout(()=>{api.operatorExecution(outcome.eventId).then(setOutcome).catch(e=>setError(errorText(e)))},3000);
  return()=>clearTimeout(t);
 },[outcome]);
 /** "No cumple": nueva captura de la misma tarea (nuevo eventId; misma ronda, turno o registro del visitante). */
 const retake=()=>reset();
 /** Bitácora: cada visitante es un registro nuevo. */
 const newVisitor=()=>{setEntryId(crypto.randomUUID());reset()};
 const rejectedFor=(id:string)=>results.find(r=>r.clientEvidenceId===id&&r.status==='REJECTED');
 const empty=kind==='PATRULLA'?!patrols.length:kind==='CONSIGNA'?!consignments.length:!logbooks.length;

 return <div className="agent-sim">
  <div className="agent-sim-head"><h2>Simulador de Agente <span>UAT</span></h2><p>Reproduce lo que hará SGI: Operador: elegir una tarea (Hito de patrulla, consigna o campo de bitácora), tomar una foto y enviarla por FormData. Actúa como el agente UAT vinculado, sin cambiar el usuario de esta sesión.</p></div>
  {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>setError('')}>Cerrar</button></div>}
  {done&&<div className="posts-notice"><CheckCircle2 size={15}/><span>{done}</span></div>}
  {outcome&&<div className={`agent-sim-result ${outcome.outcome.toLowerCase()}`}>
   {outcome.outcome==='VALIDATED'?<CheckCircle2 size={20}/>:outcome.outcome==='PENDING'?<Loader2 size={20} className="spin"/>:outcome.outcome==='NOT_REQUIRED'?<CheckCircle2 size={20}/>:<AlertTriangle size={20}/>}
   <div><strong>{outcome.captureNo>1?`Captura ${outcome.captureNo} · `:''}{outcome.message}</strong><small>Resultado que verá el agente en SGI: Operador</small></div>
   {outcome.canRetake&&<button className="primary" onClick={retake}><Camera size={15}/>Tomar nueva foto</button>}
  </div>}
  <section className="agent-sim-card"><h3>1 · Tarea</h3>
   <label>Asignación<select value={assignmentId} onChange={e=>setAssignmentId(e.target.value)}>{assignments.map(a=><option key={a.assignmentId} value={a.assignmentId}>{a.postName} · {new Date(a.startsAt).toLocaleString('es-EC')}</option>)}</select></label>
   <div className="agent-sim-kinds">{KINDS.map(k=><button key={k.key} type="button" className={kind===k.key?'selected':''} onClick={()=>setKind(k.key)}>{k.label}</button>)}</div>
   {kind==='PATRULLA'&&<>
    <label>Patrulla<select value={patrolId} onChange={e=>{setPatrolId(e.target.value);setCheckpointId(patrols.find(p=>p.patrolId===e.target.value)?.checkpoints[0]?.checkpointId??'')}}>{patrols.map(p=><option key={p.patrolId} value={p.patrolId}>{p.protocolCode} · {p.code} {p.name}</option>)}</select></label>
    <label>Hito<select value={checkpointId} onChange={e=>setCheckpointId(e.target.value)}>{patrol?.checkpoints.map(c=><option key={c.checkpointId} value={c.checkpointId}>{c.code} · {c.name}</option>)}</select></label>
   </>}
   {kind==='CONSIGNA'&&<>
    <label>Consigna vigente<select value={consignmentId} onChange={e=>{setConsignmentId(e.target.value);setConsignmentEvidenceId(consignments.find(c=>c.consignmentId===e.target.value)?.evidences[0]?.evidenceId??'')}}>{consignments.map(c=><option key={c.consignmentId} value={c.consignmentId}>{c.code} · {c.title}</option>)}</select></label>
    <label>Evidencia (foto, una por turno)<select value={consignmentEvidenceId} onChange={e=>setConsignmentEvidenceId(e.target.value)}>{consignment?.evidences.map(ev=><option key={ev.evidenceId} value={ev.evidenceId}>{ev.name}</option>)}</select></label>
    {consignment&&<p className="agent-sim-note">{consignment.instruction}</p>}
   </>}
   {kind==='BITACORA'&&<>
    <label>Bitácora<select value={logbookId} onChange={e=>{setLogbookId(e.target.value);setFieldId(logbooks.find(l=>l.protocolId===e.target.value)?.fields[0]?.fieldId??'')}}>{logbooks.map(l=><option key={l.protocolId} value={l.protocolId}>{l.code} · {l.name}</option>)}</select></label>
    <label>Campo con foto<select value={fieldId} onChange={e=>setFieldId(e.target.value)}>{logbook?.fields.map(f=><option key={f.fieldId} value={f.fieldId}>{f.name}{f.visintEnabled?' · VISINT':' · sin VISINT'}</option>)}</select></label>
    <div className="agent-sim-entry"><span>Registro del visitante <code>{entryId.slice(0,8)}</code></span><button type="button" onClick={newVisitor}><RefreshCw size={13}/>Nuevo visitante</button></div>
   </>}
   {empty&&assignmentId&&<p className="agent-sim-empty"><Info size={14}/>{kind==='PATRULLA'?'No hay patrullas activas con Hitos para este puesto.':kind==='CONSIGNA'?'No hay consignas vigentes con evidencia tipo Foto para este puesto.':'No hay bitácoras activas con campos de foto para este puesto.'}</p>}
  </section>
  {task&&<section className="agent-sim-card"><h3>2 · Fotos estándar (guía) {!task.visint&&<small>· sin VISINT: la foto solo se guarda</small>}</h3>
   {guideUrls.length?<div className="agent-sim-guides">{guideUrls.map((u,i)=><img key={u} className="agent-sim-guide" src={u} alt={`Foto estándar ${i+1}`}/>)}</div>:<p className="agent-sim-empty">Esta tarea no tiene fotos estándar.</p>}
   {task.notes&&<p>{task.notes}</p>}
  </section>}
  {task&&<section className="agent-sim-card"><h3>3 · Foto del agente <small>({stored.length+valid.length} de 1)</small></h3>
   <label className="agent-sim-pick"><Camera size={20}/>Tomar o elegir foto<input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" hidden onChange={e=>{void add(e.target.files);e.currentTarget.value=''}}/></label>
   <div className="agent-sim-grid">
    {stored.map(r=><article key={r.clientEvidenceId} className="ok"><CheckCircle2 size={22}/><b>{r.status==='STORED'?'Guardada':'Ya estaba'}</b>{r.flags.filter(f=>f in FLAGS).map(f=><em key={f}>{FLAGS[f]}</em>)}</article>)}
    {photos.map(p=>{const rej=rejectedFor(p.clientEvidenceId);return <article key={p.clientEvidenceId} className={p.problem||rej?'bad':''}><img src={p.previewUrl} alt=""/>{p.problem&&<em>{p.problem}</em>}{rej&&<em>{REASONS[rej.reason??'']??rej.reason}</em>}<button onClick={()=>remove(p.clientEvidenceId)} aria-label="Quitar foto"><Trash2 size={14}/></button></article>})}
   </div>
   {progress!==null&&<div className="agent-sim-progress"><i style={{width:`${progress}%`}}/><span>{progress}%</span></div>}
   <div className="agent-sim-actions">
    <button onClick={()=>void upload()} disabled={!valid.length||progress!==null}><Upload size={15}/>Subir foto</button>
    <button className="primary" onClick={()=>void confirm()} disabled={!canConfirm}><Send size={15}/>{task.kind==='PATRULLA'?'Confirmar Hito':'Confirmar tarea'}</button>
   </div>
  </section>}
 </div>;
}
