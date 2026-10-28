import {useCallback, useEffect, useRef, useState, type ReactNode} from 'react';
import {ArrowLeft, CalendarDays, CircleCheck, Clock3, FileText, FolderOpen, ListChecks, Play, RefreshCw, UserRound, Users, Video} from 'lucide-react';
import {errorText, obsintApi, type ObsintCase, type ObsintTask} from './obsintApi';
import {ObsintCameraGrid} from './ObsintCameraGrid';
import {ObsintCommPanel} from './ObsintCommPanel';
import {Dots, ObsintTaskPanel, type CapturedFrame} from './ObsintTaskPanel';
import {captureFrame, decisionLabel, formatDateTime, formatSeconds, useCountdown, useNow} from './obsint';
import ObsintReports, {canViewObsintReports} from './ObsintReports';
import './obsint.css';

/** Botón de entrada a la Consola OBSINT; se muestra en la cabecera de Consola. */
export function ObsintButton({onClick}:{onClick:()=>void}){
  return <button type="button" className="obs-entry-button" onClick={onClick}><Video size={16}/>OBSINT</button>;
}

/**
 * Consola OBSINT (Consola / OBSINT): el monitorista recorre las consignas en orden, mira sus cámaras,
 * responde Sí / No / Incierto con justificación, captura evidencia y puede escribir al agente en sitio.
 */
export default function ObsintConsole({onBack}:{onBack:()=>void}){
  const [obsCase,setObsCase]=useState<ObsintCase|null>(null);
  const [task,setTask]=useState<ObsintTask|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [opening,setOpening]=useState(false);
  const [selectedCameraId,setSelectedCameraId]=useState<string|null>(null);
  const [notice,setNotice]=useState('');
  const [answerFeedback,setAnswerFeedback]=useState<{title:string;detail:string}|null>(null);
  const [reportsOpen,setReportsOpen]=useState(false);
  const videos=useRef(new Map<string,HTMLVideoElement>());
  const now=useNow();
  const countdown=useCountdown(task?.deadline??null,task?.serverNow??null,task?.timeLimitSeconds??0);

  const showNotice=useCallback((text:string)=>setNotice(text),[]);
  useEffect(()=>{if(!notice)return;const id=setTimeout(()=>setNotice(''),3200);return()=>clearTimeout(id);},[notice]);
  useEffect(()=>{if(!answerFeedback)return;const id=setTimeout(()=>setAnswerFeedback(null),8000);return()=>clearTimeout(id);},[answerFeedback]);

  function handleAnswered(next:ObsintCase){
    setObsCase(next);
    setAnswerFeedback(next.status==='COMPLETED'?null:{title:'Respuesta registrada',detail:'La siguiente tarea está lista para revisar.'});
    window.scrollTo({top:0,behavior:'smooth'});
  }

  useEffect(()=>{
    let current=true;
    void obsintApi.currentCase().then(c=>{if(current)setObsCase(c??null)})
      .catch(e=>{if(current)setError(errorText(e))}).finally(()=>{if(current)setLoading(false)});
    return()=>{current=false};
  },[]);

  // La tarea actual del caso; al cambiar de tarea, la primera cámara queda elegida para capturar evidencia.
  const currentTaskId=obsCase?.status==='IN_PROGRESS'?obsCase.currentTaskId:null;
  useEffect(()=>{
    setTask(null);
    if(!currentTaskId)return;
    let current=true;
    void obsintApi.task(currentTaskId).then(t=>{if(!current)return;setTask(t);setSelectedCameraId(t.cameras[0]?.id??null)})
      .catch(e=>{if(current)setError(errorText(e))});
    return()=>{current=false};
  },[currentTaskId]);

  const registerVideo=useCallback((cameraId:string,el:HTMLVideoElement|null)=>{if(el)videos.current.set(cameraId,el);else videos.current.delete(cameraId);},[]);

  // El tiempo arranca cuando las cámaras ya cargaron, no antes. En el servidor es idempotente.
  const startWhenReady=useCallback(()=>{
    if(task?.status!=='PENDING')return;
    void obsintApi.startTask(task.id).then(setTask).catch(e=>showNotice(errorText(e)));
  },[task?.id,task?.status,showNotice]);

  const captureSelected=useCallback(async():Promise<CapturedFrame>=>{
    if(!selectedCameraId)throw new Error('No hay una cámara seleccionada para capturar.');
    const video=videos.current.get(selectedCameraId);
    if(!video)throw new Error('La cámara seleccionada no tiene video.');
    return {cameraId:selectedCameraId,videoTimeSeconds:video.currentTime,image:await captureFrame(video)};
  },[selectedCameraId]);

  async function openCase(){
    setOpening(true);setError('');setAnswerFeedback(null);
    try{setObsCase(await obsintApi.openCase())}catch(e){setError(errorText(e))}finally{setOpening(false)}
  }

  const running=task?.status==='IN_PROGRESS';
  const timerTone=!task?.deadline?'idle':countdown.level;
  const [today,clock]=formatDateTime(now).split(', ');

  if(reportsOpen)return <ObsintReports onBack={()=>setReportsOpen(false)}/>;

  return <div className="obs-page">
    <nav className="obs-breadcrumbs"><button type="button" onClick={onBack}><ArrowLeft size={15}/>Volver a Consola</button><span>/</span><small>OBSINT</small></nav>
    <div className="obs-console-titlebar"><div className="obs-hero"><h2>Consola OBSINT</h2><p>Consola operativa para monitoristas de Observational Intelligence.</p></div>
      {canViewObsintReports()&&<button type="button" className="obs-reports-entry" onClick={()=>setReportsOpen(true)}><FileText size={16}/>Reportes</button>}
    </div>

    {answerFeedback&&<div className="obs-answer-feedback" role="status"><span><CircleCheck size={22}/></span><div><strong>{answerFeedback.title}</strong><p>{answerFeedback.detail}</p></div></div>}

    {error&&<div className="obs-error">{error}</div>}
    {loading?<div className="obs-card obs-message">Cargando consola OBSINT…</div>
    :!obsCase?<StartCard onStart={openCase} opening={opening}/>
    :obsCase.status==='COMPLETED'?<Summary obsCase={obsCase} onNew={openCase} opening={opening}/>
    :!task?<div className="obs-card obs-message">Cargando tarea…</div>
    :<>
      <div className="obs-kpis">
        <Kpi icon={<FolderOpen size={20}/>} tone="blue" label="Caso" value={task.caseCode}/>
        <Kpi icon={<Users size={20}/>} tone="purple" label="Grupo" value={<span className="obs-kpi-stack"><span>Grupo {task.position}</span><span>{task.cameras.length} {task.cameras.length===1?'cámara':'cámaras'}</span></span>}/>
        <Kpi icon={<ListChecks size={20}/>} tone="blue" label="Tarea" value={<>{task.position} de {task.totalTasks} <Dots total={task.totalTasks} current={task.position}/></>}/>
        <Kpi icon={<Clock3 size={20}/>} tone="green" label="Tiempo restante" value={<span className={`obs-timer ${timerTone}`}>{formatSeconds(countdown.seconds)}</span>}/>
        <Kpi icon={<CalendarDays size={20}/>} tone="blue" label="Fecha y hora" value={<span className="obs-kpi-stack obs-kpi-date"><span>{today}</span><span>{clock}</span></span>}/>
        <Kpi icon={<UserRound size={20}/>} tone="blue" label="Operador" value={task.operator}/>
      </div>
      {running&&countdown.expired&&<div className="obs-timeout-alert" role="alert">
        <span className="obs-timeout-icon"><Clock3 size={22}/></span>
        <div><strong>Tiempo agotado para esta tarea</strong><p>Completa la evaluación y continúa. La respuesta se registrará fuera de tiempo.</p></div>
      </div>}
      <div className="obs-workspace">
        <div className="obs-left">
          <section className="obs-card obs-video">
            <h3><Video size={19}/>Video en vivo <small>Clic en una cámara para usarla como evidencia</small></h3>
            <ObsintCameraGrid cameras={task.cameras} selectedId={selectedCameraId} onSelect={setSelectedCameraId} now={now}
              registerVideo={registerVideo} onAllSettled={startWhenReady}/>
          </section>
          <ObsintCommPanel taskId={task.id} disabled={!running} onNotice={showNotice}/>
        </div>
        <ObsintTaskPanel key={task.id} task={task} running={running} captureFrame={captureSelected} onAnswered={handleAnswered} onNotice={showNotice}/>
      </div>
    </>}
    {notice&&<div className="obs-toast" role="status">{notice}</div>}
  </div>;
}

function Kpi({icon,tone,label,value}:{icon:ReactNode;tone:'blue'|'green'|'purple';label:string;value:ReactNode}){
  return <article><span className={tone}>{icon}</span><div><small>{label}</small><strong>{value}</strong></div></article>;
}

function StartCard({onStart,opening}:{onStart:()=>void;opening:boolean}){
  return <section className="obs-card obs-start">
    <Video size={34}/>
    <h3>No tienes un caso OBSINT en curso</h3>
    <p>Al iniciar se crea un caso con las consignas OBSINT activas. Cada consigna tiene su tiempo máximo, que empieza a correr cuando cargan sus cámaras.</p>
    <button type="button" onClick={onStart} disabled={opening}><Play size={16}/>{opening?'Iniciando…':'Iniciar caso'}</button>
  </section>;
}

function Summary({obsCase,onNew,opening}:{obsCase:ObsintCase;onNew:()=>void;opening:boolean}){
  const yes=obsCase.tasks.filter(t=>t.decision==='YES').length;
  const no=obsCase.tasks.filter(t=>t.decision==='NO').length;
  const uncertain=obsCase.tasks.filter(t=>t.decision==='UNCERTAIN').length;
  const late=obsCase.tasks.filter(t=>t.timedOut).length;
  return <section className="obs-card obs-summary-card">
    <div className="obs-summary-header"><span><CircleCheck size={30}/></span><div><h3>Caso {obsCase.code} completado</h3><p>Se registraron las {obsCase.tasks.length} respuestas de este caso OBSINT.</p></div></div>
    <div className="obs-summary-body">
      <div className="obs-summary-stats" aria-label="Resumen de respuestas">
        <div><strong>{yes}</strong><span>Sí</span></div><div><strong>{no}</strong><span>No</span></div>
        <div><strong>{uncertain}</strong><span>Incierto</span></div><div><strong>{late}</strong><span>Fuera de tiempo</span></div>
      </div>
      <h4>Resultados por tarea</h4>
      <ul className="obs-summary">{obsCase.tasks.map(t=><li key={t.id}><span>Tarea {t.position}</span>
        <b className={`obs-decision ${t.decision?.toLowerCase()??''}`}>{t.decision?decisionLabel(t.decision):'—'}</b>
        {t.timedOut&&<small>Fuera de tiempo</small>}</li>)}</ul>
      <button type="button" className="obs-summary-new" onClick={onNew} disabled={opening}><RefreshCw size={16}/>{opening?'Iniciando…':'Iniciar nuevo caso'}</button>
    </div>
  </section>;
}
