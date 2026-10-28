import {useEffect, useState} from 'react';
import {ArrowRight, Ban, Camera as CameraIcon, Check, CircleCheck, CircleHelp, FileText, ImagePlus, MessageCircle, Paperclip, X} from 'lucide-react';
import {errorText, obsintApi, OBSINT_JUSTIFICATION_MAX, type ObsintCase, type ObsintDecision, type ObsintEvidence, type ObsintTask} from './obsintApi';
import {clearDraft, formatClock, readDraft, writeDraft, type AnswerDraft} from './obsint';

/** Lo que la pantalla entrega al capturar: de qué cámara, en qué segundo del video y la imagen. */
export type CapturedFrame={cameraId:string;videoTimeSeconds:number;image:Blob};

type Props={
  task:ObsintTask;
  running:boolean;
  captureFrame:()=>Promise<CapturedFrame>;
  onAnswered:(next:ObsintCase)=>void;
  onNotice:(text:string)=>void;
};

const DECISIONS:{value:ObsintDecision;label:string;className:string;Icon:typeof Check}[]=[
  {value:'YES',label:'Sí',className:'yes',Icon:Check},
  {value:'NO',label:'No',className:'no',Icon:Ban},
  {value:'UNCERTAIN',label:'Incierto',className:'uncertain',Icon:CircleHelp},
];

/** Panel derecho: consigna, decisión, justificación, evidencia y continuar. */
export function ObsintTaskPanel({task,running,captureFrame,onAnswered,onNotice}:Props){
  const [draft,setDraft]=useState<AnswerDraft>(()=>readDraft(task.id));
  const [saving,setSaving]=useState(false);
  const isLast=task.position===task.totalTasks;
  useEffect(()=>setDraft(readDraft(task.id)),[task.id]);

  function update(next:AnswerDraft){setDraft(next);writeDraft(task.id,next);}

  async function submit(){
    if(!draft.decision)return onNotice('Selecciona Sí, No o Incierto.');
    if(!draft.justification.trim())return onNotice('Ingresa una justificación antes de continuar.');
    setSaving(true);
    try{
      const next=await obsintApi.answerTask(task.id,draft.decision,draft.justification.trim());
      clearDraft(task.id);
      onAnswered(next);
    }catch(error){onNotice(errorText(error))}
    finally{setSaving(false)}
  }

  return <aside className="obs-card obs-task">
    <div className="obs-progress"><span>Tarea {task.position} de {task.totalTasks}</span><Dots total={task.totalTasks} current={task.position}/></div>

    <h3 className="obs-section"><FileText size={22} className="obs-orange"/>Consigna <small>{task.consignaCode}</small></h3>
    <div className="obs-consigna">{task.instruction}</div>

    <h4 className="obs-sub"><CircleHelp size={17}/>¿Se cumple la consigna?</h4>
    <div className="obs-choices" role="radiogroup" aria-label="¿Se cumple la consigna?">
      {DECISIONS.map(({value,label,className,Icon})=><button key={value} type="button" role="radio" aria-checked={draft.decision===value} disabled={!running}
        className={`obs-choice ${className}${draft.decision===value?' active':''}`} onClick={()=>update({...draft,decision:value})}><Icon size={17}/>{label}</button>)}
    </div>

    <h4 className="obs-sub"><MessageCircle size={17}/>Justificación</h4>
    <textarea className="obs-justification" value={draft.justification} maxLength={OBSINT_JUSTIFICATION_MAX} disabled={!running}
      onChange={e=>update({...draft,justification:e.target.value})} placeholder="Explica brevemente tu decisión (por qué seleccionaste SÍ, NO o INCIERTO)..."/>
    <div className="obs-counter">{draft.justification.length}/{OBSINT_JUSTIFICATION_MAX}</div>

    <EvidencePanel task={task} disabled={!running} captureFrame={captureFrame} onNotice={onNotice}/>

    <button type="button" className="obs-continue" onClick={submit} disabled={!running||saving}>
      {saving?'Guardando…':isLast?<>FINALIZAR <CircleCheck size={20}/></>:<>CONTINUAR <ArrowRight size={20}/></>}
    </button>
  </aside>;
}

export function Dots({total,current}:{total:number;current:number}){
  return <span className="obs-dots" aria-hidden="true">{Array.from({length:total},(_,i)=><i key={i} className={i+1===current?'active':i+1<current?'done':''}/>)}</span>;
}

function EvidencePanel({task,disabled,captureFrame,onNotice}:{task:ObsintTask;disabled:boolean;captureFrame:()=>Promise<CapturedFrame>;onNotice:(text:string)=>void}){
  const [items,setItems]=useState<ObsintEvidence[]>([]);
  const [busy,setBusy]=useState(false);
  const cameraName=(id:string)=>task.cameras.find(c=>c.id===id)?.name??'Cámara';

  useEffect(()=>{
    let current=true;
    setItems([]);
    void obsintApi.evidence(task.id).then(list=>{if(current)setItems(list)}).catch(()=>{if(current)onNotice('No se pudo cargar la evidencia.')});
    return()=>{current=false};
  },[task.id]);

  async function capture(){
    setBusy(true);
    try{
      const frame=await captureFrame();
      const saved=await obsintApi.addEvidence(task.id,frame.cameraId,frame.videoTimeSeconds,frame.image);
      setItems(prev=>[...prev,saved]);
      onNotice(`Evidencia capturada desde ${cameraName(frame.cameraId)}.`);
    }catch(error){onNotice(errorText(error))}
    finally{setBusy(false)}
  }
  async function remove(id:string){
    setBusy(true);
    try{await obsintApi.deleteEvidence(task.id,id);setItems(prev=>prev.filter(e=>e.id!==id));}
    catch(error){onNotice(errorText(error))}
    finally{setBusy(false)}
  }

  return <>
    <div className="obs-evidence-head"><h4 className="obs-sub"><Paperclip size={17}/>Evidencia</h4><span>{items.length} {items.length===1?'elemento':'elementos'}</span></div>
    <div className={`obs-evidence${items.length?' filled':''}`}>
      {items.length===0
        ?<div className="obs-evidence-empty"><ImagePlus size={26}/><div>No has agregado evidencia.<br/>Puedes capturar imágenes de las cámaras como respaldo.</div></div>
        :<div className="obs-evidence-grid">{items.map(e=><figure key={e.id}>
          <EvidenceImage taskId={task.id} evidenceId={e.id} alt={`Captura ${cameraName(e.cameraId)}`}/>
          {!disabled&&<button type="button" aria-label="Quitar evidencia" disabled={busy} onClick={()=>remove(e.id)}><X size={12}/></button>}
          <figcaption>{cameraName(e.cameraId)} · {formatClock(e.capturedAt,true)}</figcaption>
        </figure>)}</div>}
      <button type="button" className="obs-capture" onClick={capture} disabled={disabled||busy}><CameraIcon size={16}/>{busy?'Capturando…':'Capturar evidencia'}</button>
    </div>
  </>;
}

/** La imagen se pide con el token de la sesión, por eso no basta un <img src>. */
function EvidenceImage({taskId,evidenceId,alt}:{taskId:string;evidenceId:string;alt:string}){
  const [url,setUrl]=useState<string|null>(null);
  useEffect(()=>{
    let objectUrl:string|null=null,current=true;
    void obsintApi.evidenceImage(taskId,evidenceId).then(blob=>{if(!current)return;objectUrl=URL.createObjectURL(blob);setUrl(objectUrl)}).catch(()=>{});
    return()=>{current=false;if(objectUrl)URL.revokeObjectURL(objectUrl)};
  },[taskId,evidenceId]);
  return url?<img src={url} alt={alt}/>:<div className="obs-evidence-loading"/>;
}
