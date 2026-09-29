import {useEffect,useMemo,useState} from 'react';
import {AlertTriangle,Camera,CheckCircle2,Info,Send,Trash2,Upload} from 'lucide-react';
import {api,ApiError} from '../api';
import {buildEvidenceForm,pickPhotos,type PickedPhoto} from '../lib/evidenceUpload';

type Cp={checkpointId:string;code:string;name:string;description:string;requiresEvidence:boolean;evidenceMinCount:number;evidenceMaxCount:number;hasStandardImage:boolean;standardImageNotes:string;latitude:number|null;longitude:number|null};
type Patrol={protocolCode:string;patrolId:string;code:string;name:string;checkpoints:Cp[]};
type Result={clientEvidenceId:string;evidenceId?:string;status:string;reason?:string|null;flags:string[]};
const REASONS:Record<string,string>={FILE_TOO_LARGE:'Supera 5 MB',UNSUPPORTED_FORMAT:'Formato no permitido',CHECKSUM_MISMATCH:'El archivo se dañó en el envío',TOO_MANY_PHOTOS:'Se superó el máximo de fotos'};
const FLAGS:Record<string,string>={GALLERY:'De galería',OUT_OF_RANGE:'Fuera del radio GPS',SUSPECTED_REUSE:'Posible foto repetida'};
const INSTANCE='11111111-1111-1111-1111-111111111111';
const errorText=(e:unknown)=>e instanceof ApiError?(e.body||`Error ${e.status}`):String(e);

/** TEMPORAL (demo UAT) — quitar junto con SIMULATOR_USER de api.ts.
 * Reproduce lo que hará SGI: Operador — elegir un Hito, adjuntar varias fotos, subirlas por FormData y confirmar. */
export default function AgentSimulator(){
 const [employeeId,setEmployeeId]=useState('');const [assignments,setAssignments]=useState<any[]>([]);const [assignmentId,setAssignmentId]=useState('');
 const [patrols,setPatrols]=useState<Patrol[]>([]);const [patrolId,setPatrolId]=useState('');const [checkpointId,setCheckpointId]=useState('');
 const [guideUrl,setGuideUrl]=useState('');const [photos,setPhotos]=useState<PickedPhoto[]>([]);const [results,setResults]=useState<Result[]>([]);
 const [progress,setProgress]=useState<number|null>(null);const [error,setError]=useState('');const [done,setDone]=useState('');
 const [eventId,setEventId]=useState(()=>crypto.randomUUID());const [runId]=useState(()=>crypto.randomUUID());
 const patrol=patrols.find(p=>p.patrolId===patrolId);const cp=patrol?.checkpoints.find(c=>c.checkpointId===checkpointId);
 const stored=results.filter(r=>r.status==='STORED'||r.status==='ALREADY_STORED');
 const valid=photos.filter(p=>!p.problem);
 const gps=useMemo(()=>cp?.latitude!=null&&cp?.longitude!=null?{latitude:cp.latitude,longitude:cp.longitude,accuracyM:8}:undefined,[cp]);

 useEffect(()=>{
  api.operatorRuntime().then(r=>{
   const list=[...(r.assignments??[])].sort((a:any,b:any)=>a.startsAt.localeCompare(b.startsAt));
   const now=new Date().toISOString();const current=[...list].reverse().find((a:any)=>a.startsAt<=now)??list[0];
   setEmployeeId(r.employeeId);setAssignments(list);setAssignmentId(current?.assignmentId??'');
  }).catch(e=>setError(errorText(e)));
 },[]);
 useEffect(()=>{
  if(!assignmentId)return;
  api.operatorRuntime(assignmentId).then(r=>{const list:Patrol[]=(r.patrols??[]).filter((p:Patrol)=>p.checkpoints.length);setPatrols(list);setPatrolId(list[0]?.patrolId??'');setCheckpointId(list[0]?.checkpoints[0]?.checkpointId??'')}).catch(e=>setError(errorText(e)));
 },[assignmentId]);
 useEffect(()=>{
  photos.forEach(p=>URL.revokeObjectURL(p.previewUrl));setPhotos([]);setResults([]);setDone('');setEventId(crypto.randomUUID());
  setGuideUrl(old=>{if(old)URL.revokeObjectURL(old);return ''});
  if(cp?.hasStandardImage)api.operatorCheckpointImage(cp.checkpointId,assignmentId).then(b=>setGuideUrl(URL.createObjectURL(b))).catch(()=>{});
 // eslint-disable-next-line react-hooks/exhaustive-deps
 },[checkpointId]);

 const add=async(files:FileList|null)=>{if(!files||!cp)return;const picked=await pickPhotos(files,valid.length+stored.length,cp.evidenceMaxCount);setPhotos(prev=>[...prev,...picked])};
 const remove=(id:string)=>{const p=photos.find(x=>x.clientEvidenceId===id);if(p)URL.revokeObjectURL(p.previewUrl);setPhotos(photos.filter(x=>x.clientEvidenceId!==id));setResults(results.filter(r=>r.clientEvidenceId!==id||r.status!=='REJECTED'))};
 const upload=async()=>{
  if(!cp||!valid.length)return;setError('');setProgress(0);
  try{
   const batch=crypto.randomUUID();
   const r=await api.uploadEvidences(buildEvidenceForm({uploadBatchId:batch,eventId,assignmentId,targetId:cp.checkpointId},valid,gps),{onProgress:setProgress,idempotencyKey:batch});
   const incoming:Result[]=r.results;
   setResults(prev=>[...prev.filter(x=>!incoming.some(y=>y.clientEvidenceId===x.clientEvidenceId)),...incoming]);
   setPhotos(prev=>prev.filter(p=>!incoming.some(y=>y.clientEvidenceId===p.clientEvidenceId&&y.status!=='REJECTED')));
  }catch(e){setError(errorText(e))}finally{setProgress(null)}
 };
 const confirm=async()=>{
  if(!cp||!patrol)return;setError('');
  try{
   const now=new Date().toISOString();
   const r=await api.submitExecution({batchId:crypto.randomUUID(),correlationId:crypto.randomUUID(),employeeId,instanceCountryId:INSTANCE,deviceId:'simulador-web',capturedAt:now,
    events:[{type:'PATROL_CHECKPOINT_COMPLETED',eventId,assignmentId,patrolRunId:runId,patrolId:patrol.patrolId,checkpointId:cp.checkpointId,executedAt:now,...(gps??{}),evidenceIds:stored.map(s=>s.evidenceId)}]});
   setDone(`Hito ${cp.code} registrado con ${r.results[0].evidenceCount} foto(s). Estado: recibido.`);
  }catch(e){setError(errorText(e))}
 };
 const min=cp?(cp.requiresEvidence?cp.evidenceMinCount:0):0;
 const canConfirm=!!cp&&stored.length>=min&&stored.length<=cp.evidenceMaxCount&&!done;
 const rejectedFor=(id:string)=>results.find(r=>r.clientEvidenceId===id&&r.status==='REJECTED');

 return <div className="agent-sim">
  <div className="agent-sim-head"><h2>Simulador de Agente <span>UAT</span></h2><p>Reproduce lo que hará SGI: Operador: elegir un Hito, adjuntar varias fotos y enviarlas por FormData. Actúa como el agente UAT vinculado, sin cambiar el usuario de esta sesión.</p></div>
  {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>setError('')}>Cerrar</button></div>}
  {done&&<div className="posts-notice"><CheckCircle2 size={15}/><span>{done}</span></div>}
  <section className="agent-sim-card"><h3>1 · Tarea</h3>
   <label>Asignación<select value={assignmentId} onChange={e=>setAssignmentId(e.target.value)}>{assignments.map(a=><option key={a.assignmentId} value={a.assignmentId}>{a.postName} · {new Date(a.startsAt).toLocaleString('es-EC')}</option>)}</select></label>
   <label>Patrulla<select value={patrolId} onChange={e=>{setPatrolId(e.target.value);setCheckpointId(patrols.find(p=>p.patrolId===e.target.value)?.checkpoints[0]?.checkpointId??'')}}>{patrols.map(p=><option key={p.patrolId} value={p.patrolId}>{p.protocolCode} · {p.code} {p.name}</option>)}</select></label>
   <label>Hito<select value={checkpointId} onChange={e=>setCheckpointId(e.target.value)}>{patrol?.checkpoints.map(c=><option key={c.checkpointId} value={c.checkpointId}>{c.code} · {c.name}</option>)}</select></label>
   {!patrols.length&&assignmentId&&<p className="agent-sim-empty"><Info size={14}/>No hay patrullas activas con Hitos para este puesto. Publique un protocolo de Patrullas.</p>}
  </section>
  {cp&&<section className="agent-sim-card"><h3>2 · Foto guía</h3>
   {guideUrl?<img className="agent-sim-guide" src={guideUrl} alt="Foto estándar del Hito"/>:<p className="agent-sim-empty">Este Hito no tiene foto estándar.</p>}
   {cp.standardImageNotes&&<p>{cp.standardImageNotes}</p>}
  </section>}
  {cp&&<section className="agent-sim-card"><h3>3 · Fotos del agente <small>({stored.length+valid.length} de {min}–{cp.evidenceMaxCount})</small></h3>
   <label className="agent-sim-pick"><Camera size={20}/>Tomar o elegir fotos<input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" multiple hidden onChange={e=>{void add(e.target.files);e.currentTarget.value=''}}/></label>
   <div className="agent-sim-grid">
    {stored.map(r=><article key={r.clientEvidenceId} className="ok"><CheckCircle2 size={22}/><b>{r.status==='STORED'?'Guardada':'Ya estaba'}</b>{r.flags.map(f=><em key={f}>{FLAGS[f]??f}</em>)}</article>)}
    {photos.map(p=>{const rej=rejectedFor(p.clientEvidenceId);return <article key={p.clientEvidenceId} className={p.problem||rej?'bad':''}><img src={p.previewUrl} alt=""/>{p.problem&&<em>{p.problem}</em>}{rej&&<em>{REASONS[rej.reason??'']??rej.reason}</em>}<button onClick={()=>remove(p.clientEvidenceId)} aria-label="Quitar foto"><Trash2 size={14}/></button></article>})}
   </div>
   {progress!==null&&<div className="agent-sim-progress"><i style={{width:`${progress}%`}}/><span>{progress}%</span></div>}
   <div className="agent-sim-actions">
    <button onClick={()=>void upload()} disabled={!valid.length||progress!==null}><Upload size={15}/>Subir {valid.length} foto(s)</button>
    <button className="primary" onClick={()=>void confirm()} disabled={!canConfirm}><Send size={15}/>Confirmar Hito</button>
   </div>
  </section>}
 </div>;
}
