import {useEffect,useState} from 'react';
import {api,ApiError} from '../api';

/** Contexto del relevo que entrega /operator/runtime?assignmentId (relief). */
export type ReliefContext={assignmentId:string;shiftOccurrenceId:string;postId:string;postName:string;incomingEmployeeId:string;incomingEmployeeName?:string;
 configurationVersion:string;reliefAlreadyRegistered:boolean;reliefId?:string;reliefReceivedAt?:string;stationPhotos:string[];
 consignments:{consignmentId:string;version:string;code?:string;title?:string;instruction?:string}[];stationVisint:{enabled:boolean;standardImageIds:string[]}};
type StationResult={eventId:string;station:string|null;outcome:'NOT_REQUIRED'|'PENDING'|'VALIDATED'|'NOT_VALIDATED'|'TECHNICAL_ERROR';message:string};
type Shot={file:File;url:string};
const STATIONS=['station_0','station_1','station_2'];
const ICON:Record<StationResult['outcome'],string>={PENDING:'',VALIDATED:'✓',NOT_REQUIRED:'✓',NOT_VALIDATED:'!',TECHNICAL_ERROR:'!'};
const errorText=(e:unknown)=>e instanceof ApiError?(e.body||`Error ${e.status}`):String(e);
const hhmm=(iso:string)=>new Date(iso).toLocaleTimeString('es-EC',{hour:'2-digit',minute:'2-digit'});

/** TEMPORAL (demo UAT): relevo unilateral como en SGI: Operador — foto del agente entrante, 3 fotos del puesto, lectura de consignas y envío.
 * Si el Puesto tiene "VISINT en el relevo", cada foto del puesto se valida contra sus fotos estándar; nunca bloquea el relevo. */
export default function AgentReliefSim({ctx,employeeId,instanceCountryId,deviceId,onError,onReceived}:{ctx:ReliefContext;employeeId:string;instanceCountryId:string;deviceId:string;
 onError:(text:string)=>void;onReceived:()=>void}){
 const [guides,setGuides]=useState<string[]>([]);const [shots,setShots]=useState<Record<string,Shot>>({});const [read,setRead]=useState<Set<string>>(new Set());
 const [sending,setSending]=useState<string|null>(null);const [reliefId,setReliefId]=useState<string|null>(ctx.reliefAlreadyRegistered?ctx.reliefId??null:null);
 const [results,setResults]=useState<StationResult[]>([]);const [problem,setProblem]=useState('');

 useEffect(()=>{
  let urls:string[]=[];
  void Promise.all(ctx.stationVisint.standardImageIds.map(id=>api.operatorStandardImage(id,ctx.assignmentId).then(b=>URL.createObjectURL(b)).catch(()=>'')))
   .then(list=>{urls=list;setGuides(list)});
  return()=>urls.forEach(u=>u&&URL.revokeObjectURL(u));
 },[ctx.assignmentId,ctx.stationVisint.standardImageIds.join()]);
 useEffect(()=>()=>Object.values(shots).forEach(s=>URL.revokeObjectURL(s.url)),[]);// eslint-disable-line react-hooks/exhaustive-deps
 // Resultado de VISINT por foto del puesto: se consulta cada 3 s mientras alguna siga validándose.
 useEffect(()=>{
  if(!reliefId)return;let stop=false;let t:number|undefined;
  const load=()=>api.operatorExecutionsByGroup(reliefId).then((rows:StationResult[])=>{if(stop)return;setResults(rows);if(rows.some(r=>r.outcome==='PENDING'))t=window.setTimeout(load,3000)}).catch(e=>onError(errorText(e)));
  void load();return()=>{stop=true;clearTimeout(t)};
 },[reliefId]);// eslint-disable-line react-hooks/exhaustive-deps

 const pick=(purpose:string,files:FileList|null)=>{
  const f=files?.[0];if(!f)return;setProblem('');
  if(f.type!=='image/jpeg'){setProblem('El relevo solo admite fotos JPG.');return}
  if(f.size>5*1024*1024){setProblem('La foto no puede superar 5 MB.');return}
  setShots(old=>{if(old[purpose])URL.revokeObjectURL(old[purpose].url);return {...old,[purpose]:{file:f,url:URL.createObjectURL(f)}}});
 };
 const toggleRead=(id:string)=>setRead(old=>{const n=new Set(old);if(n.has(id))n.delete(id);else n.add(id);return n});
 const complete=!!shots.entrant&&STATIONS.every(s=>shots[s])&&ctx.consignments.every(c=>read.has(c.consignmentId));
 const send=async()=>{
  if(!complete)return;const event=crypto.randomUUID();
  try{
   const evidence:{purpose:string;evidenceId:string}[]=[];
   // La foto del agente entrante cubre rostro y cuerpo completo; luego las 3 del puesto.
   const uploads:[string,File][]=[['entrant_face',shots.entrant.file],['entrant_full',shots.entrant.file],...STATIONS.map(s=>[s,shots[s].file] as [string,File])];
   for(let i=0;i<uploads.length;i++){
    setSending(`Subiendo fotos ${i+1} de ${uploads.length}…`);
    const r=await api.uploadReliefEvidence(event,uploads[i][0],ctx.assignmentId,uploads[i][1]);evidence.push({purpose:uploads[i][0],evidenceId:r.evidenceId});
   }
   setSending('Enviando relevo…');const at=new Date().toISOString();
   await api.submitExecution({batchId:crypto.randomUUID(),correlationId:crypto.randomUUID(),employeeId,instanceCountryId,deviceId,capturedAt:at,events:[{
    eventId:event,type:'RELIEF_SUBMITTED',assignmentId:ctx.assignmentId,postId:ctx.postId,shiftOccurrenceId:ctx.shiftOccurrenceId,incomingEmployeeId:ctx.incomingEmployeeId,
    inventoryStatus:'PENDING_SOURCE',unilateral:true,unilateralReason:'Saliente no presente (simulador UAT)',executedAt:at,configurationVersion:ctx.configurationVersion,evidence,
    consignmentReadings:ctx.consignments.map(c=>({consignmentId:c.consignmentId,version:c.version,confirmedAt:at}))}]});
   setReliefId(event);onReceived();
  }catch(e){onError(errorText(e))}finally{setSending(null)}
 };

 const received=!!reliefId;
 const pendingCount=results.filter(r=>r.outcome==='PENDING').length;
 const stepIndex=received?(pendingCount||!results.length&&ctx.stationVisint.enabled?2:3):complete?2:shots.entrant?1:0;
 const action=received?{label:pendingCount?'Esperando a VISINT…':'Relevo recibido'}:sending?{label:sending}:{label:complete?'Confirmar relevo':'Completa las fotos y consignas',run:complete?()=>void send():undefined,go:true};
 const slot=(purpose:string,title:string,hint:string,guide?:string)=><div className="agent-sim-relief-slot" key={purpose}>
  <div className="agent-sim-relief-title"><strong>{title}</strong><small>{hint}</small></div>
  <div className="agent-sim-relief-pair">
   {guide!==undefined&&(guide?<img src={guide} alt={`Foto estándar: ${title}`}/>:<span className="agent-sim-relief-noguide">Sin foto estándar</span>)}
   <label className={`agent-sim-relief-shot${shots[purpose]?' ok':''}`}>
    {shots[purpose]?<img src={shots[purpose].url} alt={`Tu foto: ${title}`}/>:<span><i/>Tomar foto</span>}
    {!received&&<input type="file" accept="image/jpeg" capture="environment" hidden aria-label={`Foto: ${title}`} onChange={e=>{pick(purpose,e.target.files);e.currentTarget.value=''}}/>}
   </label>
  </div>
 </div>;

 return <>
  <div className="agent-sim-steps">{['Fotos','Consignas','Validación'].map((label,i)=><div key={label} className={i<stepIndex?'done':i===stepIndex?'active':''}><i/><span>{label}</span></div>)}</div>
  <div className="agent-sim-body">
   <div className="agent-sim-task">
    <small>Relevo · {ctx.postName}</small>
    <h3>Tomar el puesto</h3>
    <div className="agent-sim-chips"><span className={`agent-sim-chip${ctx.stationVisint.enabled?' on':''}`}>{ctx.stationVisint.enabled?'VISINT activo':'Sin VISINT'}</span><span className="agent-sim-chip">Unilateral</span></div>
    <p>{ctx.stationVisint.enabled?'Las fotos del puesto se comparan con sus fotos estándar. El relevo se recibe aunque alguna no cumpla.':'Las fotos del puesto se guardan; este puesto no las valida con VISINT.'}</p>
   </div>
   {/* El relevo siempre queda recibido: el estado de cada foto del puesto se ve en su fila. */}
   {received&&<div className={`agent-sim-result ${pendingCount?'pending':'validated'}`} role="status">
    <div>{pendingCount?<span className="spin"/>:<span className="ico"><span>✓</span></span>}
     <div><strong>Relevo recibido{ctx.reliefAlreadyRegistered&&ctx.reliefReceivedAt&&reliefId===ctx.reliefId?` a las ${hhmm(ctx.reliefReceivedAt)}`:''}</strong>
      <small>{!results.length?'Las fotos del puesto no se validan con VISINT.':pendingCount?`VISINT está validando ${pendingCount} de ${results.length} fotos del puesto…`:'Resultado de VISINT por foto del puesto'}</small></div></div>
    {results.length>0&&<ul className="agent-sim-relief-results">{results.map(r=>{const i=STATIONS.indexOf(r.station??'');return <li key={r.eventId} className={r.outcome.toLowerCase()}>
     {r.outcome==='PENDING'?<span className="spin"/>:<span className="ico"><span>{ICON[r.outcome]}</span></span>}<div><b>F{i+1} · {ctx.stationPhotos[i]??'Foto del puesto'}</b><small>{r.message}</small></div></li>})}</ul>}
   </div>}
   {problem&&<p className="agent-sim-note bad">{problem}</p>}
   {!received&&<>
    <div className="agent-sim-section"><span className="agent-sim-label">Agente entrante <small>{shots.entrant?1:0} de 1</small></span>
     {slot('entrant','Tú al ingresar','Rostro y uniforme completo')}</div>
    <div className="agent-sim-section"><span className="agent-sim-label">Fotos del puesto <small>{STATIONS.filter(s=>shots[s]).length} de 3</small></span>
     {STATIONS.map((s,i)=>slot(s,`F${i+1} · ${ctx.stationPhotos[i]}`,ctx.stationVisint.enabled?'Izquierda: así debe verse · derecha: tu foto':'Tu foto',ctx.stationVisint.enabled?guides[i]??'':undefined))}</div>
    <div className="agent-sim-section"><span className="agent-sim-label">Consignas vigentes <small>{read.size} de {ctx.consignments.length}</small></span>
     {ctx.consignments.length?ctx.consignments.map(c=><label key={c.consignmentId} className="agent-sim-relief-read"><input type="checkbox" checked={read.has(c.consignmentId)} onChange={()=>toggleRead(c.consignmentId)}/>
      <span><b>{c.code??'Consigna'}{c.title?` · ${c.title}`:''}</b>{c.instruction&&<small>{c.instruction}</small>}<em>Leí y confirmo</em></span></label>):<p className="agent-sim-empty">No hay consignas vigentes para este puesto.</p>}</div>
   </>}
  </div>
  <div className="agent-sim-actions"><button type="button" className={action.go&&action.run?'go':''} onClick={action.run} disabled={!action.run}>{action.label}</button></div>
 </>;
}
