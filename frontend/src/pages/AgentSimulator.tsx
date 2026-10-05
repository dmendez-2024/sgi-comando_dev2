import {useEffect,useMemo,useState} from 'react';
import {AlertTriangle,CheckCircle2,Info} from 'lucide-react';
import {api,ApiError} from '../api';
import {buildEvidenceForm,pickPhotos,type PickedPhoto} from '../lib/evidenceUpload';
import AgentReliefSim,{type ReliefContext} from './AgentReliefSim';

type Std={id:string;position:number};
type Cp={checkpointId:string;code:string;name:string;description:string;requiresEvidence:boolean;visintEnabled:boolean;hasStandardImage:boolean;standardImages:Std[];standardImageNotes:string;latitude:number|null;longitude:number|null};
type Patrol={protocolCode:string;patrolId:string;code:string;name:string;checkpoints:Cp[]};
type ConsignmentTask={consignmentId:string;code:string;title:string;instruction:string;latitude?:number|null;longitude?:number|null;evidences:{evidenceId:string;name:string;description:string;visintEnabled:boolean;standardImageNotes:string;standardImages:Std[]}[]};
type LogbookTask={protocolId:string;code:string;name:string;fields:{fieldId:string;accreditationCode:string|null;section:string;name:string;fieldType:string;visintEnabled:boolean;standardImageNotes:string|null;standardImages:Std[]}[]};
type Kind='PATRULLA'|'CONSIGNA'|'BITACORA'|'RELEVO';
/** La tarea seleccionada, igual para los tres módulos. */
type Task={kind:Kind;targetType:string;targetId:string;label:string;visint:boolean;standardImages:Std[];notes:string|null;latitude:number|null;longitude:number|null;requiresEvidence:boolean};
type Outcome={eventId:string;captureNo:number;outcome:'NOT_REQUIRED'|'PENDING'|'VALIDATED'|'NOT_VALIDATED'|'TECHNICAL_ERROR';message:string;canRetake:boolean};
type Result={clientEvidenceId:string;evidenceId?:string;status:string;reason?:string|null;flags:string[]};
/** Etapa de la captura en el teléfono: decide la barra de pasos y el botón de abajo. */
type Phase='idle'|'picked'|'uploading'|'stored'|'pending'|'done';
const REASONS:Record<string,string>={FILE_TOO_LARGE:'Supera 5 MB',UNSUPPORTED_FORMAT:'Formato no permitido',CHECKSUM_MISMATCH:'El archivo se dañó en el envío',TOO_MANY_PHOTOS:'Solo se permite 1 foto por tarea'};
const FLAGS:Record<string,string>={OUT_OF_RANGE:'Fuera del radio GPS'};
const KINDS:{key:Kind;label:string}[]=[{key:'PATRULLA',label:'Patrulla'},{key:'CONSIGNA',label:'Consigna'},{key:'BITACORA',label:'Bitácora'},{key:'RELEVO',label:'Relevo'}];
const INSTANCE='11111111-1111-1111-1111-111111111111';
const DEVICE='simulador-web';
const STEPS=['Foto','Subida','Validación'];
const PHASE_STEP:Record<Phase,number>={idle:0,picked:0,uploading:1,stored:2,pending:2,done:3};
const OUTCOME_ICON:Record<Outcome['outcome'],string>={PENDING:'',VALIDATED:'✓',NOT_REQUIRED:'✓',NOT_VALIDATED:'!',TECHNICAL_ERROR:'!'};
const errorText=(e:unknown)=>e instanceof ApiError?(e.body||`Error ${e.status}`):String(e);
/** 409 de una tarea ya registrada (Hito en la ronda, evidencia en el turno o campo del visitante): se avisa en un modal, no como error. */
const duplicateText=(e:unknown)=>{
 if(!(e instanceof ApiError)||e.status!==409)return null;
 let text=e.body;try{const j=JSON.parse(e.body);text=j.message??j.title??text}catch{/* texto plano */}
 return /ya fue registrad/i.test(text)?text:null;
};
const hhmm=(d:Date)=>d.toLocaleTimeString('es-EC',{hour:'2-digit',minute:'2-digit'});

/** Estilos del simulador: a la izquierda la tarea a probar; a la derecha, la pantalla del agente dentro de un teléfono. */
const STYLES=`
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600;700&display=swap');
.agent-sim{--ink:#0c120e;--green:#4ade80;--green-d:#16a34a;--green-dd:#15803d;--muted:#6b7a70;--text2:#4b5a50;--text3:#33413a;--line:#e1e6df;--line2:#d3dad1;--soft:#eef1ec;--soft2:#f6f8f5;
 font-family:"IBM Plex Sans",system-ui,sans-serif;color:var(--ink);-webkit-font-smoothing:antialiased;background:#f3f5f2;border-radius:20px;padding:30px 28px 44px;display:grid;gap:26px;max-width:1240px}
.agent-sim .mono{font-family:"IBM Plex Mono",ui-monospace,monospace}
.agent-sim select:focus-visible,.agent-sim button:focus-visible,.agent-sim label:focus-within{outline:3px solid #86efac;outline-offset:2px}
.agent-sim-head{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:flex-end;gap:16px;padding-bottom:22px;border-bottom:1px solid var(--line)}
.agent-sim-head>div:first-child{display:grid;gap:8px;max-width:640px}
.agent-sim-head h2{margin:0;display:flex;align-items:center;gap:10px;font-size:26px;font-weight:700;letter-spacing:-.02em}
.agent-sim-head h2 span{font-family:"IBM Plex Mono",monospace;font-size:11px;font-weight:500;letter-spacing:.06em;background:var(--ink);color:var(--green);padding:3px 8px;border-radius:6px}
.agent-sim-head p{margin:0;color:var(--text2);font-size:14.5px;line-height:1.55}
.agent-sim-session{flex:none;white-space:nowrap;display:flex;align-items:center;gap:8px;font-size:12.5px;color:var(--text2);background:#fff;border:1px solid var(--line);border-radius:999px;padding:6px 12px 6px 10px}
.agent-sim-session i{display:inline-block;flex:none;width:8px;height:8px;border-radius:50%;background:var(--green-d)}
.agent-sim-layout{display:flex;flex-wrap:wrap;gap:32px;align-items:flex-start}
.agent-sim-left{margin:0;padding:0;flex:1 1 380px;min-width:0;display:grid;gap:14px;position:sticky;top:20px}
.agent-sim-card.agent-sim-bench{background:#fff;border:1px solid var(--line);border-radius:16px;overflow:hidden}
.agent-sim-step{padding:20px 22px;display:grid;gap:10px;border-bottom:1px solid var(--soft)}
.agent-sim-step:last-child{border-bottom:0;gap:14px}
.agent-sim-step-title{display:flex;align-items:center;gap:10px;font-size:13px;font-weight:600}
.agent-sim-step-title b{width:22px;height:22px;border-radius:50%;background:var(--ink);color:var(--green);font-size:12px;font-weight:700;display:grid;place-items:center}
.agent-sim-select{position:relative}
.agent-sim-select::after{content:"▼";position:absolute;right:14px;top:50%;transform:translateY(-50%);pointer-events:none;color:var(--muted);font-size:11px}
.agent-sim-select select{width:100%;appearance:none;padding:11px 36px 11px 12px;border:1px solid var(--line2);border-radius:10px;background:#fff;font:inherit;font-size:14px;color:var(--ink);cursor:pointer}
.agent-sim-bench label{display:grid;gap:6px;font-size:12px;color:var(--muted)}
.agent-sim-kinds{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;padding:4px;background:var(--soft);border-radius:12px}
.agent-sim-kinds button{padding:9px 8px;border:0;border-radius:9px;font:inherit;font-size:13.5px;font-weight:600;cursor:pointer;background:transparent;color:var(--text2)}
.agent-sim-kinds button.selected{background:var(--ink);color:var(--green)}
.agent-sim-entry{display:flex;align-items:center;justify-content:space-between;gap:10px;background:var(--soft2);border:1px solid var(--line);border-radius:10px;padding:9px 10px 9px 12px;font-size:13px;color:var(--text3)}
.agent-sim-entry code{font-family:"IBM Plex Mono",monospace;color:var(--ink)}
.agent-sim-entry button{padding:6px 11px;border:1px solid var(--line2);border-radius:8px;background:#fff;font:inherit;font-size:12.5px;font-weight:600;cursor:pointer;color:var(--ink)}
.agent-sim-entry button:hover{background:var(--soft)}
.agent-sim-empty{display:flex;gap:6px;align-items:center;margin:0;color:var(--muted);font-size:13px}
.agent-sim-facts{background:var(--ink);border-radius:16px;padding:18px 22px;display:grid;gap:12px}
.agent-sim-facts>span{font-family:"IBM Plex Mono",monospace;font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--green)}
.agent-sim-facts dl{margin:0;display:grid;gap:10px}
.agent-sim-facts dl div{display:grid;grid-template-columns:96px minmax(0,1fr);gap:12px;font-size:13px;line-height:1.45}
.agent-sim-facts dt{color:#9aa89f}
.agent-sim-facts dd{margin:0;color:#e7ece5}
.agent-sim-facts dd.on{color:var(--green);font-weight:600}
.agent-sim-phone{flex:0 1 404px;max-width:100%;margin:0 auto;padding:0}
.agent-sim-bezel{background:#0a0d0b;border-radius:56px;padding:12px;box-shadow:0 40px 80px -40px rgba(5,20,10,.6),inset 0 0 0 1.5px #2a332d}
.agent-sim-card.agent-sim-screen{position:relative;background:var(--soft2);border-radius:44px;height:800px;display:flex;flex-direction:column;overflow:hidden}
.agent-sim-statusbar{background:var(--ink);color:#fff;padding:14px 26px 0}
.agent-sim-clock{display:flex;justify-content:space-between;align-items:center;font-size:13px;font-weight:600;height:24px}
.agent-sim-clock i{width:96px;height:26px;background:#000;border-radius:20px}
.agent-sim-clock .mono{font-size:11px;opacity:.8}
.agent-sim-app{display:flex;justify-content:space-between;align-items:flex-end;gap:10px;padding:16px 0}
.agent-sim-app div{display:grid;gap:2px}
.agent-sim-app small{font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--green)}
.agent-sim-app strong{font-size:16px;font-weight:600}
.agent-sim-app>span{font-size:12px;color:#d1fae5;background:rgba(74,222,128,.14);padding:4px 9px;border-radius:999px;white-space:nowrap}
.agent-sim-steps{display:flex;gap:6px;padding:14px 20px 0}
.agent-sim-steps div{flex:1;display:grid;gap:6px}
.agent-sim-steps i{height:4px;border-radius:4px;background:#dfe5dc}
.agent-sim-steps span{font-size:11px;font-weight:600;color:#94a39a}
.agent-sim-steps .done i{background:var(--green-d)}.agent-sim-steps .done span{color:var(--green-dd)}
.agent-sim-steps .active i{background:var(--ink)}.agent-sim-steps .active span{color:var(--ink)}
.agent-sim-steps .bad i{background:#dc2626}.agent-sim-steps .bad span{color:#b91c1c}
.agent-sim-body{flex:1;overflow-y:auto;display:grid;align-content:start;gap:18px;padding:16px 0 20px}
.agent-sim-task{padding:0 20px;display:grid;gap:6px}
.agent-sim-task small{font-size:12px;color:var(--muted)}
.agent-sim-task h3{margin:0;font-size:22px;line-height:1.2;font-weight:700;letter-spacing:-.015em}
.agent-sim-task p{margin:4px 0 0;color:var(--text2);font-size:13.5px;line-height:1.5}
.agent-sim-chips{display:flex;gap:6px;flex-wrap:wrap;margin-top:2px}
.agent-sim-chip{font-size:11px;font-weight:600;padding:3px 8px;border-radius:6px;background:#e7ece5;color:var(--text3)}
.agent-sim-chip.on{background:var(--ink);color:var(--green)}
.agent-sim-result{margin:0 20px;display:grid;gap:12px;padding:14px;border-radius:14px;border:1px solid}
.agent-sim-result>div{display:grid;grid-template-columns:auto minmax(0,1fr);gap:12px;align-items:start}
.agent-sim-result strong{font-size:14px;line-height:1.4}
.agent-sim-result small{display:block;font-size:11.5px;opacity:.8;margin-top:3px}
.agent-sim-result .ico{width:22px;height:22px;border-radius:50%;background:currentColor;display:grid;place-items:center}
.agent-sim-result .ico span{color:#fff;font-size:13px;font-weight:700;line-height:1}
.agent-sim-result .spin{width:18px;height:18px;margin-top:1px;border-radius:50%;border:2.5px solid currentColor;border-right-color:transparent;animation:agent-sim-spin .9s linear infinite}
.agent-sim-result button{padding:10px;border:0;border-radius:10px;font:inherit;font-size:13.5px;font-weight:700;background:var(--ink);color:#fff;cursor:pointer}
.agent-sim-result.pending{background:var(--ink);border-color:var(--ink);color:var(--green)}
.agent-sim-result.validated,.agent-sim-result.not_required{background:#f0fdf4;border-color:#86efac;color:#166534}
.agent-sim-result.not_validated{background:#fef2f2;border-color:#fca5a5;color:#991b1b}
.agent-sim-result.technical_error{background:#fffbeb;border-color:#fcd34d;color:#92400e}
@keyframes agent-sim-spin{to{transform:rotate(360deg)}}
.agent-sim-note{margin:0 20px;padding:12px 14px;background:#dcfce7;color:#14532d;font-size:13px;line-height:1.5;border-radius:12px}
.agent-sim-section{display:grid;gap:10px}
.agent-sim-label{padding:0 20px;display:flex;justify-content:space-between;align-items:baseline;font-size:12px;font-weight:700;color:var(--text3)}
.agent-sim-label small{font-weight:400;color:var(--muted)}
.agent-sim-guides{display:flex;gap:8px;overflow-x:auto;padding:0 20px 2px;scroll-snap-type:x mandatory;scroll-padding-inline:20px}
.agent-sim-guides img{flex:0 0 auto;width:140px;height:104px;object-fit:cover;border-radius:12px;border:1px solid #dfe5dc;scroll-snap-align:start;background:repeating-linear-gradient(135deg,#ebefe9 0 8px,#e2e8df 8px 16px)}
.agent-sim-section>p{margin:0 20px;font-size:12.5px;color:var(--text2);line-height:1.5}
.agent-sim-section>p.agent-sim-empty{font-size:13px;color:var(--muted)}
.agent-sim-pick{margin:0 20px;display:grid;justify-items:center;gap:10px;padding:30px 16px;border:1.5px dashed var(--green);border-radius:16px;background:#fff;cursor:pointer;color:var(--green-dd)}
.agent-sim-pick:hover{background:#f0fdf4}
.agent-sim-pick .cam{width:48px;height:48px;border-radius:50%;background:var(--ink);display:grid;place-items:center}
.agent-sim-pick .cam i{width:20px;height:15px;border:2.5px solid var(--green);border-radius:4px;display:grid;place-items:center}
.agent-sim-pick .cam i::after{content:"";width:6px;height:6px;border-radius:50%;border:2px solid var(--green)}
.agent-sim-pick strong{font-size:14.5px;color:var(--ink)}
.agent-sim-pick small{font-size:12px;color:var(--muted)}
.agent-sim-grid{margin:0 20px}
.agent-sim-grid article{position:relative;border-radius:16px;overflow:hidden;border:2px solid #dfe5dc;background:#fff}
.agent-sim-grid article.ok{border-color:var(--green-d)}
.agent-sim-grid article.bad{border-color:#fca5a5}
.agent-sim-grid img{display:block;width:100%;height:210px;object-fit:cover}
.agent-sim-overlay{position:absolute;left:10px;right:10px;bottom:10px;display:flex;justify-content:space-between;align-items:center;gap:8px}
.agent-sim-photo-chip{font-size:11.5px;font-weight:600;padding:4px 9px;border-radius:999px;background:rgba(12,18,14,.85);color:#fff}
.agent-sim-photo-chip.up{background:var(--ink);color:var(--green)}
.agent-sim-photo-chip.ok{background:var(--green-d);color:#fff}
.agent-sim-photo-chip.bad{background:#fef2f2;color:#991b1b}
.agent-sim-overlay div{display:flex;gap:6px}
.agent-sim-overlay label,.agent-sim-overlay button{font:inherit;font-size:12px;font-weight:600;padding:5px 10px;border:0;border-radius:8px;background:rgba(255,255,255,.95);color:var(--ink);cursor:pointer}
.agent-sim-overlay button{color:#b91c1c}
.agent-sim-flags{margin:8px 0 0;display:flex;gap:6px;flex-wrap:wrap}
.agent-sim-flags em{font-size:11px;font-style:normal;background:#fff7ed;color:#9a3412;padding:2px 8px;border-radius:99px}
.agent-sim-progress{margin:2px 20px 0;height:6px;background:var(--line);border-radius:6px;overflow:hidden}
.agent-sim-progress i{display:block;height:100%;background:var(--green-d);border-radius:6px;transition:width .2s}
.agent-sim-idle{margin:auto 20px;display:grid;justify-items:center;gap:10px;text-align:center;color:var(--muted);font-size:13.5px;line-height:1.5}
.agent-sim-actions{padding:14px 20px 26px;border-top:1px solid var(--line);background:#fff}
.agent-sim-actions button{width:100%;padding:15px 12px;border:0;border-radius:14px;font:inherit;font-size:15px;font-weight:700;background:var(--ink);color:#fff;cursor:pointer}
.agent-sim-actions button.go{background:var(--green-d)}
.agent-sim-actions button:disabled{background:var(--line);color:var(--muted);cursor:not-allowed}
.agent-sim-modal{position:absolute;inset:0;z-index:5;display:flex;align-items:flex-end;background:rgba(12,18,14,.55);animation:agent-sim-fade .18s ease-out}
.agent-sim-sheet{width:100%;background:#fff;border-radius:24px 24px 0 0;padding:22px 22px 26px;display:grid;gap:10px;box-shadow:0 -12px 32px rgba(12,18,14,.18);animation:agent-sim-rise .22s ease-out}
.agent-sim-sheet-ico{width:36px;height:36px;border-radius:50%;background:#fef3c7;color:#92400e;display:grid;place-items:center;font-weight:700;font-size:18px}
.agent-sim-sheet h4{margin:4px 0 0;font-size:17px;line-height:1.3;color:var(--ink)}
.agent-sim-sheet p{margin:0;font-size:13.5px;line-height:1.5;color:var(--text2)}
.agent-sim-sheet>div{display:grid;gap:8px;margin-top:8px}
.agent-sim-sheet button{padding:13px 12px;border:1px solid var(--line2);border-radius:12px;font:inherit;font-size:14.5px;font-weight:700;background:#fff;color:var(--ink);cursor:pointer}
.agent-sim-sheet button.go{border-color:var(--green-d);background:var(--green-d);color:#fff}
.agent-sim-sheet button:focus-visible{outline:3px solid var(--green);outline-offset:2px}
@keyframes agent-sim-fade{from{opacity:0}}
@keyframes agent-sim-rise{from{transform:translateY(24px);opacity:0}}
.agent-sim-relief-slot{margin:0 20px;display:grid;gap:7px}
.agent-sim-relief-title{display:flex;justify-content:space-between;align-items:baseline;gap:8px}
.agent-sim-relief-title strong{font-size:13.5px}
.agent-sim-relief-title small{font-size:11px;color:var(--muted);text-align:right}
.agent-sim-relief-pair{display:flex;gap:8px}
.agent-sim-relief-pair>*{flex:1;min-width:0;height:118px;border-radius:12px}
.agent-sim-relief-pair>img{object-fit:cover;border:1px solid #dfe5dc}
.agent-sim-relief-noguide{display:grid;place-items:center;font-size:12px;color:var(--muted);background:repeating-linear-gradient(135deg,#ebefe9 0 8px,#e2e8df 8px 16px)}
.agent-sim-relief-shot{position:relative;display:grid;place-items:center;overflow:hidden;border:1.5px dashed var(--green);background:#fff;cursor:pointer;color:var(--green-dd);font-size:13px;font-weight:600}
.agent-sim-relief-shot:hover{background:#f0fdf4}
.agent-sim-relief-shot span{display:grid;justify-items:center;gap:6px}
.agent-sim-relief-shot span i{width:20px;height:15px;border:2.5px solid var(--green-d);border-radius:4px}
.agent-sim-relief-shot.ok{border:2px solid var(--green-d)}
.agent-sim-relief-shot img{width:100%;height:100%;object-fit:cover}
.agent-sim-relief-read{margin:0 20px;display:flex!important;gap:10px!important;align-items:flex-start;padding:12px;border:1px solid var(--line);border-radius:12px;background:#fff;cursor:pointer;color:var(--ink)!important}
.agent-sim-relief-read input{margin-top:3px;accent-color:var(--green-d);width:18px;height:18px}
.agent-sim-relief-read span{display:grid;gap:3px;font-size:13px}
.agent-sim-relief-read small{font-size:12px;color:var(--text2);line-height:1.45}
.agent-sim-relief-read em{font-style:normal;font-size:11.5px;font-weight:700;color:var(--green-dd)}
.agent-sim-relief-results{list-style:none;margin:0;padding:0;display:grid;gap:8px}
.agent-sim-relief-results li{display:grid;grid-template-columns:auto minmax(0,1fr);gap:10px;align-items:start;padding:10px;border-radius:10px;background:#fff;color:var(--ink)}
.agent-sim-relief-results li b{font-size:13px}
.agent-sim-relief-results li small{opacity:1;color:var(--text2)}
.agent-sim-relief-results li.validated .ico{background:var(--green-d)}
.agent-sim-relief-results li.not_validated .ico{background:#dc2626}
.agent-sim-relief-results li.technical_error .ico{background:#d97706}
.agent-sim-relief-results li .spin{color:var(--ink)}
.agent-sim-note.bad{background:#fef2f2;color:#991b1b}
@media (prefers-reduced-motion:reduce){.agent-sim-modal,.agent-sim-sheet{animation:none}.agent-sim-result .spin{animation-duration:3s}.agent-sim-progress i{transition:none}}
@media (max-width:900px){.agent-sim-left{position:static}}
@media (max-width:480px){.agent-sim{padding:18px 14px 28px}.agent-sim-bezel{padding:0;background:none;box-shadow:none}.agent-sim-card.agent-sim-screen{border:1px solid var(--line);border-radius:18px;height:auto;min-height:640px}}
`;

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
 const [progress,setProgress]=useState<number|null>(null);const [error,setError]=useState('');const [done,setDone]=useState('');const [confirming,setConfirming]=useState(false);
 const [eventId,setEventId]=useState(()=>crypto.randomUUID());const [outcome,setOutcome]=useState<Outcome|null>(null);const [runId,setRunId]=useState(()=>crypto.randomUUID());
 const [relief,setRelief]=useState<ReliefContext|null>(null);const [instanceId,setInstanceId]=useState(INSTANCE);
 const [far,setFar]=useState(false);
 const [captureNo,setCaptureNo]=useState(1);const [duplicate,setDuplicate]=useState<string|null>(null);const [now,setNow]=useState(()=>new Date());

 const patrol=patrols.find(p=>p.patrolId===patrolId);
 const consignment=consignments.find(c=>c.consignmentId===consignmentId);
 const logbook=logbooks.find(l=>l.protocolId===logbookId);
 const checkpoint=patrol?.checkpoints.find(c=>c.checkpointId===checkpointId);
 const evidence=consignment?.evidences.find(e=>e.evidenceId===consignmentEvidenceId);
 const field=logbook?.fields.find(f=>f.fieldId===fieldId);
 const task=useMemo<Task|null>(()=>{
  if(kind==='PATRULLA')return checkpoint?{kind,targetType:'PATROL_CHECKPOINT',targetId:checkpoint.checkpointId,label:`Hito ${checkpoint.code}`,visint:checkpoint.visintEnabled,standardImages:checkpoint.standardImages,notes:checkpoint.standardImageNotes,latitude:checkpoint.latitude,longitude:checkpoint.longitude,requiresEvidence:checkpoint.requiresEvidence}:null;
  if(kind==='CONSIGNA')return evidence&&consignment?{kind,targetType:'CONSIGNMENT_EVIDENCE',targetId:evidence.evidenceId,label:`Consigna ${consignment.code} · ${evidence.name}`,visint:evidence.visintEnabled,standardImages:evidence.standardImages,notes:evidence.standardImageNotes,latitude:consignment.latitude??null,longitude:consignment.longitude??null,requiresEvidence:true}:null;
  return field?{kind,targetType:'LOGBOOK_FIELD',targetId:field.fieldId,label:`Bitácora · ${field.name}`,visint:field.visintEnabled,standardImages:field.standardImages,notes:field.standardImageNotes,latitude:null,longitude:null,requiresEvidence:true}:null;
 },[kind,checkpoint,consignment,evidence,field]);
 // Una sola foto por captura: la que se ve en el teléfono, antes y después de subirla.
 const photo=photos[0];
 const photoResult=photo?results.find(r=>r.clientEvidenceId===photo.clientEvidenceId):undefined;
 const rejected=photo?.problem??(photoResult?.status==='REJECTED'?REASONS[photoResult.reason??'']??photoResult.reason??'Foto rechazada':null);
 const stored=results.filter(r=>r.status==='STORED'||r.status==='ALREADY_STORED');
 // GPS que manda el teléfono: el del Hito, o ~1 km al norte para probar el aviso "Fuera del radio GPS" en Operación (no bloquea).
 const gps=useMemo(()=>task?.latitude!=null&&task?.longitude!=null?{latitude:task.latitude+(far?0.009:0),longitude:task.longitude,accuracyM:8}:undefined,[task,far]);

 useEffect(()=>{const t=setInterval(()=>setNow(new Date()),30000);return()=>clearInterval(t)},[]);
 useEffect(()=>{
  api.operatorRuntime().then(r=>{
   const list=[...(r.assignments??[])].sort((a:any,b:any)=>a.startsAt.localeCompare(b.startsAt));
   const iso=new Date().toISOString();const current=[...list].reverse().find((a:any)=>a.startsAt<=iso)??list[0];
   setEmployeeId(r.employeeId);setAssignments(list);setAssignmentId(current?.assignmentId??'');
  }).catch(e=>setError(errorText(e)));
 },[]);
 useEffect(()=>{
  if(!assignmentId)return;
  api.operatorRuntime(assignmentId).then(r=>{
   setRelief(r.relief??null);if(r.instanceCountryId)setInstanceId(r.instanceCountryId);
   const list:Patrol[]=(r.patrols??[]).filter((p:Patrol)=>p.checkpoints.length);setPatrols(list);setPatrolId(list[0]?.patrolId??'');setCheckpointId(list[0]?.checkpoints[0]?.checkpointId??'');
   const cons:ConsignmentTask[]=r.consignmentTasks??[];setConsignments(cons);setConsignmentId(cons[0]?.consignmentId??'');setConsignmentEvidenceId(cons[0]?.evidences[0]?.evidenceId??'');
   const logs:LogbookTask[]=(r.logbookTasks??[]).filter((l:LogbookTask)=>l.fields.length);setLogbooks(logs);setLogbookId(logs[0]?.protocolId??'');setFieldId(logs[0]?.fields[0]?.fieldId??'');
  }).catch(e=>setError(errorText(e)));
 },[assignmentId]);
 const reset=()=>{photos.forEach(p=>URL.revokeObjectURL(p.previewUrl));setPhotos([]);setResults([]);setDone('');setOutcome(null);setEventId(crypto.randomUUID())};
 useEffect(()=>{
  reset();setCaptureNo(1);
  setGuideUrls(old=>{old.forEach(u=>URL.revokeObjectURL(u));return []});
  if(task)void Promise.all(task.standardImages.map(i=>api.operatorStandardImage(i.id,assignmentId).then(b=>URL.createObjectURL(b)).catch(()=>'')))
   .then(urls=>setGuideUrls(urls.filter(Boolean)));
 // eslint-disable-next-line react-hooks/exhaustive-deps
 },[task?.targetId]);

 /** Elegir o cambiar la foto: siempre queda una sola, la nueva. */
 const pick=async(files:FileList|null)=>{
  if(!files?.length||!task)return;
  photos.forEach(p=>URL.revokeObjectURL(p.previewUrl));setResults([]);
  const picked=await pickPhotos(files,0,1);setPhotos(picked.slice(0,1));
 };
 const removePhoto=()=>{photos.forEach(p=>URL.revokeObjectURL(p.previewUrl));setPhotos([]);setResults([])};
 const upload=async()=>{
  if(!task||!photo||rejected)return;setError('');setProgress(0);
  try{
   const batch=crypto.randomUUID();
   const r=await api.uploadEvidences(buildEvidenceForm({uploadBatchId:batch,eventId,assignmentId,targetId:task.targetId,targetType:task.targetType},[photo],gps),{onProgress:setProgress,idempotencyKey:batch});
   setResults(r.results as Result[]);
  }catch(e){setError(errorText(e))}finally{setProgress(null)}
 };
 const confirm=async()=>{
  if(!task)return;setError('');setConfirming(true);
  try{
   const at=new Date().toISOString();
   const evidenceIds=stored.map(s=>s.evidenceId);
   const event=task.kind==='PATRULLA'
    ?{type:'PATROL_CHECKPOINT_COMPLETED',eventId,assignmentId,patrolRunId:runId,patrolId:patrol!.patrolId,checkpointId:task.targetId,executedAt:at,...(gps??{}),evidenceIds}
    :{type:'TASK_EVIDENCE_SUBMITTED',eventId,assignmentId,targetType:task.targetType,targetId:task.targetId,...(task.kind==='BITACORA'?{groupId:entryId}:{}),executedAt:at,evidenceIds};
   const r=await api.submitExecution({batchId:crypto.randomUUID(),correlationId:crypto.randomUUID(),employeeId,instanceCountryId:instanceId,deviceId:DEVICE,capturedAt:at,events:[event]});
   setDone(`${task.label} registrado con ${r.results[0].evidenceCount} foto(s). Estado: recibido.`);
   const o:Outcome=await api.operatorExecution(eventId);setOutcome(o);setCaptureNo(o.captureNo);
  }catch(e){const dup=duplicateText(e);if(dup)setDuplicate(dup);else setError(errorText(e))}finally{setConfirming(false)}
 };
 const canConfirm=!!task&&stored.length===1&&!done&&!confirming;
 // Fase 3: mientras VISINT valida, se consulta el resultado cada 3 s (como hará la app del agente).
 useEffect(()=>{
  if(outcome?.outcome!=='PENDING')return;
  const t=setTimeout(()=>{api.operatorExecution(outcome.eventId).then(setOutcome).catch(e=>setError(errorText(e)))},3000);
  return()=>clearTimeout(t);
 },[outcome]);
 /** "No cumple": nueva captura de la misma tarea (nuevo eventId; misma ronda, turno o registro del visitante). */
 const retake=()=>{reset();setCaptureNo(n=>n+1)};
 /** Bitácora: cada visitante es un registro nuevo. */
 const newVisitor=()=>{setEntryId(crypto.randomUUID());reset();setCaptureNo(1)};
 /** Salida del aviso de tarea repetida según el tipo: la foto ya subida se conserva para confirmar de nuevo. */
 const duplicateHelp=kind==='PATRULLA'
  ?{text:'Cada Hito se registra una sola vez por ronda. Para volver a probarlo, empieza una ronda nueva o elige otro Hito.',action:'Empezar ronda nueva',run:()=>setRunId(crypto.randomUUID())}
  :kind==='CONSIGNA'
  ?{text:'Cada evidencia de la consigna se envía una sola vez por turno. Para volver a probarla, elige otro turno en el paso 1.',action:null,run:null}
  :{text:'Cada campo se registra una sola vez por visitante. Para registrar a otra persona, empieza un visitante nuevo.',action:'Nuevo visitante',run:()=>setEntryId(crypto.randomUUID())};
 useEffect(()=>{
  if(!duplicate)return;
  const onKey=(e:KeyboardEvent)=>{if(e.key==='Escape')setDuplicate(null)};
  window.addEventListener('keydown',onKey);return()=>window.removeEventListener('keydown',onKey);
 },[duplicate]);
 const empty=kind==='PATRULLA'?!patrols.length:kind==='CONSIGNA'?!consignments.length:kind==='RELEVO'?!relief:!logbooks.length;

 const assignment=assignments.find(a=>a.assignmentId===assignmentId);
 const shiftLabel=(iso:string)=>new Date(iso).toLocaleString('es-EC',{weekday:'short',day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'});
 const phase:Phase=progress!==null?'uploading':outcome?(outcome.outcome==='PENDING'?'pending':'done'):stored.length?'stored':photo?'picked':'idle';
 const failed=phase==='done'&&(outcome?.outcome==='NOT_VALIDATED'||outcome?.outcome==='TECHNICAL_ERROR');
 const step=PHASE_STEP[phase];
 const screen=kind==='PATRULLA'?{title:checkpoint?`${checkpoint.code} · ${checkpoint.name}`:'',context:patrol?`Patrulla ${patrol.code} · ${patrol.name}`:'',detail:checkpoint?.description}
  :kind==='CONSIGNA'?{title:evidence?.name??'',context:consignment?`Consigna ${consignment.code} · ${consignment.title}`:'',detail:evidence?.description}
  :{title:field?.name??'',context:logbook?`${logbook.name} · visitante ${entryId.slice(0,8)}`:'',detail:null};
 const confirmLabel=kind==='PATRULLA'?'Confirmar Hito':'Confirmar tarea';
 /** El botón de abajo del teléfono cambia con la etapa, como en la app del agente. */
 const action:{label:string;run?:()=>void;go?:boolean}={
  idle:{label:'Toma una foto para continuar'},
  picked:{label:'Subir foto',run:rejected?undefined:()=>void upload()},
  uploading:{label:`Subiendo… ${progress??0}%`},
  stored:{label:confirmLabel,run:canConfirm?()=>void confirm():undefined,go:true},
  pending:{label:'Esperando a VISINT…'},
  done:failed?{label:'Registro enviado'}:{label:'Siguiente tarea',run:()=>{reset();setCaptureNo(1)},go:true},
 }[phase];
 const chip=rejected?{cls:'bad',label:rejected}:phase==='uploading'?{cls:'up',label:'Subiendo…'}:stored.length?{cls:'ok',label:'✓ Foto subida'}:{cls:'',label:'Lista para subir'};
 const facts:{k:string;v:string;on?:boolean}[]=kind==='RELEVO'?(relief?[{k:'Validación',v:relief.stationVisint.enabled?'VISINT compara las fotos del puesto con sus fotos estándar':'Sin VISINT: las fotos del puesto solo se guardan',on:relief.stationVisint.enabled},{k:'Modalidad',v:'Unilateral: el saliente no está presente'},{k:'Bloquea',v:'No: el relevo se recibe aunque una foto no cumpla'}]:[]):task?[{k:'Validación',v:task.visint?'VISINT compara con las fotos estándar':'Sin VISINT: la foto solo se guarda',on:task.visint}]:[];
 if(task&&kind==='PATRULLA'){facts.push({k:'Ronda',v:`${runId.slice(0,8)} · ronda en curso`});facts.push({k:'GPS',v:gps?`${gps.latitude.toFixed(5)}, ${gps.longitude.toFixed(5)} · ±8 m${far?' · lejos: Operación mostrará el aviso':''}`:'El Hito no tiene ubicación'})}
 if(task&&kind==='CONSIGNA'){facts.push({k:'Agrupa por',v:'Turno: una foto por evidencia'});facts.push({k:'GPS',v:gps?`${gps.latitude.toFixed(5)}, ${gps.longitude.toFixed(5)} · ±8 m${far?' · lejos: Operación mostrará el aviso':''}`:'La consigna no tiene ubicación GPS esperada'})}
 if(task&&kind==='BITACORA')facts.push({k:'Agrupa por',v:'Visitante: una foto por campo'});

 return <div className="agent-sim">
  <style>{STYLES}</style>
  <div className="agent-sim-head">
   <div>
    <h2>Simulador de Agente <span>UAT</span></h2>
    <p>Prueba lo que hará la app del agente: elige el turno y la tarea (o el relevo), toma la foto y mira el resultado de VISINT. Se registra como el usuario agente, sin cambiar tu sesión.</p>
   </div>
   <div className="agent-sim-session"><i/>Sesión simulada · <span className="mono">{DEVICE}</span></div>
  </div>
  {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>setError('')}>Cerrar</button></div>}
  {done&&<div className="posts-notice"><CheckCircle2 size={15}/><span>{done}</span></div>}

  <div className="agent-sim-layout">
   <section className="agent-sim-left">
    <div className="agent-sim-card agent-sim-bench">
     <div className="agent-sim-step">
      <div className="agent-sim-step-title"><b>1</b>Turno</div>
      <div className="agent-sim-select"><select aria-label="Turno" value={assignmentId} onChange={e=>setAssignmentId(e.target.value)}>{assignments.map(a=><option key={a.assignmentId} value={a.assignmentId}>{a.postName} · {shiftLabel(a.startsAt)}</option>)}</select></div>
     </div>
     <div className="agent-sim-step">
      <div className="agent-sim-step-title"><b>2</b>Tipo de tarea</div>
      <div className="agent-sim-kinds" role="group" aria-label="Tipo de tarea">{KINDS.map(k=><button key={k.key} type="button" aria-pressed={kind===k.key} className={kind===k.key?'selected':''} onClick={()=>setKind(k.key)}>{k.label}</button>)}</div>
     </div>
     <div className="agent-sim-step">
      <div className="agent-sim-step-title"><b>3</b>Tarea</div>
      {kind==='PATRULLA'&&<>
       <label>Patrulla<div className="agent-sim-select"><select value={patrolId} onChange={e=>{setPatrolId(e.target.value);setCheckpointId(patrols.find(p=>p.patrolId===e.target.value)?.checkpoints[0]?.checkpointId??'')}}>{patrols.map(p=><option key={p.patrolId} value={p.patrolId}>{p.protocolCode} · {p.code} {p.name}</option>)}</select></div></label>
       <label>Hito<div className="agent-sim-select"><select value={checkpointId} onChange={e=>setCheckpointId(e.target.value)}>{patrol?.checkpoints.map(c=><option key={c.checkpointId} value={c.checkpointId}>{c.code} · {c.name}</option>)}</select></div></label>
      </>}
      {kind==='CONSIGNA'&&<>
       <label>Consigna vigente<div className="agent-sim-select"><select value={consignmentId} onChange={e=>{setConsignmentId(e.target.value);setConsignmentEvidenceId(consignments.find(c=>c.consignmentId===e.target.value)?.evidences[0]?.evidenceId??'')}}>{consignments.map(c=><option key={c.consignmentId} value={c.consignmentId}>{c.code} · {c.title}</option>)}</select></div></label>
       <label>Evidencia (una foto por turno)<div className="agent-sim-select"><select value={consignmentEvidenceId} onChange={e=>setConsignmentEvidenceId(e.target.value)}>{consignment?.evidences.map(ev=><option key={ev.evidenceId} value={ev.evidenceId}>{ev.name}</option>)}</select></div></label>
      </>}
      {kind==='RELEVO'&&relief&&<div className="agent-sim-entry"><span>{relief.reliefAlreadyRegistered?'Este turno ya tiene un relevo recibido':`Relevo de ${relief.postName}`}</span><code>{relief.stationPhotos.length} fotos del puesto</code></div>}
      {kind==='BITACORA'&&<>
       <label>Bitácora<div className="agent-sim-select"><select value={logbookId} onChange={e=>{setLogbookId(e.target.value);setFieldId(logbooks.find(l=>l.protocolId===e.target.value)?.fields[0]?.fieldId??'')}}>{logbooks.map(l=><option key={l.protocolId} value={l.protocolId}>{l.code} · {l.name}</option>)}</select></div></label>
       <label>Campo con foto<div className="agent-sim-select"><select value={fieldId} onChange={e=>setFieldId(e.target.value)}>{logbook?.fields.map(f=><option key={f.fieldId} value={f.fieldId}>{f.name}{f.visintEnabled?' · VISINT':' · sin VISINT'}</option>)}</select></div></label>
       <div className="agent-sim-entry"><span>Visitante <code>{entryId.slice(0,8)}</code></span><button type="button" onClick={newVisitor}>↻ Nuevo visitante</button></div>
      </>}
      {task?.latitude!=null&&<label>Ubicación del agente<div className="agent-sim-select"><select aria-label="Ubicación del agente" value={far?'FAR':'AT'} onChange={e=>setFar(e.target.value==='FAR')}><option value="AT">{kind==='PATRULLA'?'En el Hito':'En el punto de la consigna'}</option><option value="FAR">{kind==='PATRULLA'?'Lejos del Hito (~1 km)':'Lejos del punto (~1 km)'}</option></select></div></label>}
      {empty&&assignmentId&&<p className="agent-sim-empty"><Info size={14}/>{kind==='PATRULLA'?'No hay patrullas activas con Hitos para este puesto.':kind==='CONSIGNA'?'No hay consignas vigentes con evidencia tipo Foto para este puesto.':kind==='RELEVO'?'El relevo no está disponible para este turno.':'No hay bitácoras activas con campos de foto para este puesto.'}</p>}
     </div>
    </div>
    {facts.length>0&&<div className="agent-sim-facts">
     <span>Cómo se registra</span>
     <dl>{facts.map(f=><div key={f.k}><dt>{f.k}</dt><dd className={f.on?'on':''}>{f.v}</dd></div>)}</dl>
    </div>}
   </section>

   <section className="agent-sim-phone" aria-label="Pantalla del agente en SGI: Operador">
    <div className="agent-sim-bezel">
     <div className="agent-sim-card agent-sim-screen">
      <div className="agent-sim-statusbar">
       <div className="agent-sim-clock"><span>{hhmm(now)}</span><i/><span className="mono">LTE</span></div>
       <div className="agent-sim-app">
        <div><small>SGI · Operador</small><strong>{assignment?.postName??'Sin turno asignado'}</strong></div>
        {assignment&&<span>desde {hhmm(new Date(assignment.startsAt))}</span>}
       </div>
      </div>
      {kind==='RELEVO'?(relief?<AgentReliefSim key={`${relief.assignmentId}:${relief.reliefId??''}`} ctx={relief} employeeId={employeeId} instanceCountryId={instanceId} deviceId={DEVICE}
        onError={setError} onReceived={()=>{setDone(`Relevo de ${relief.postName} recibido.`);api.operatorRuntime(assignmentId).then(r=>setRelief(r.relief??null)).catch(e=>setError(errorText(e)))}}/>:<div className="agent-sim-idle"><p>Elige un turno para hacer el relevo.</p></div>)
      :!task?<div className="agent-sim-idle"><p>Elige una tarea a la izquierda para ver lo que verá el agente.</p></div>:<>
       <div className="agent-sim-steps">{STEPS.map((label,i)=><div key={label} className={failed&&i===2?'bad':i<step?'done':i===step?'active':''}><i/><span>{label}</span></div>)}</div>
       <div className="agent-sim-body">
        <div className="agent-sim-task">
         <small>{screen.context}</small>
         <h3>{screen.title}</h3>
         <div className="agent-sim-chips"><span className={`agent-sim-chip${task.visint?' on':''}`}>{task.visint?'VISINT activo':'Sin VISINT'}</span><span className="agent-sim-chip">Captura {outcome?.captureNo??captureNo}</span></div>
         {screen.detail&&<p>{screen.detail}</p>}
        </div>
        {outcome&&<div className={`agent-sim-result ${outcome.outcome.toLowerCase()}`} role="status">
         <div>
          {outcome.outcome==='PENDING'?<span className="spin"/>:<span className="ico"><span>{OUTCOME_ICON[outcome.outcome]}</span></span>}
          <div><strong>{outcome.message}</strong><small>Mensaje que recibe el agente</small></div>
         </div>
         {outcome.canRetake&&<button type="button" onClick={retake}>Tomar nueva foto</button>}
        </div>}
        {kind==='CONSIGNA'&&consignment?.instruction&&<p className="agent-sim-note">{consignment.instruction}</p>}
        <div className="agent-sim-section">
         <span className="agent-sim-label">Así debe verse</span>
         {guideUrls.length?<div className="agent-sim-guides">{guideUrls.map((u,i)=><img key={u} className="agent-sim-guide" src={u} alt={`Foto estándar ${i+1}`}/>)}</div>:<p className="agent-sim-empty">Esta tarea no tiene fotos estándar.</p>}
         {task.notes&&<p>{task.notes}</p>}
        </div>
        <div className="agent-sim-section">
         <span className="agent-sim-label">Tu foto <small>{photo&&!rejected?1:0} de 1</small></span>
         {!photo&&<label className="agent-sim-pick">
          <span className="cam"><i/></span><strong>Tomar o elegir foto</strong><small>JPG, PNG o WebP · máx. 5 MB</small>
          <input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" hidden onChange={e=>{void pick(e.target.files);e.currentTarget.value=''}}/>
         </label>}
         {photo&&<div className="agent-sim-grid">
          <article className={rejected?'bad':stored.length?'ok':''}>
           <img src={photo.previewUrl} alt="Foto tomada por el agente"/>
           <div className="agent-sim-overlay">
            <span className={`agent-sim-photo-chip ${chip.cls}`}>{chip.label}</span>
            {phase==='picked'&&<div>
             <label>Cambiar<input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" hidden onChange={e=>{void pick(e.target.files);e.currentTarget.value=''}}/></label>
             <button type="button" onClick={removePhoto} aria-label="Quitar foto">Quitar</button>
            </div>}
           </div>
          </article>
          {photoResult&&photoResult.flags.some(f=>f in FLAGS)&&<div className="agent-sim-flags">{photoResult.flags.filter(f=>f in FLAGS).map(f=><em key={f}>{FLAGS[f]}</em>)}</div>}
         </div>}
         {phase==='uploading'&&<div className="agent-sim-progress" role="progressbar" aria-valuenow={progress??0} aria-valuemin={0} aria-valuemax={100}><i style={{width:`${progress??0}%`}}/></div>}
        </div>
       </div>
       <div className="agent-sim-actions"><button type="button" className={action.go&&action.run?'go':''} onClick={action.run} disabled={!action.run}>{action.label}</button></div>
      </>}
      {duplicate&&<div className="agent-sim-modal" onClick={e=>{if(e.target===e.currentTarget)setDuplicate(null)}}>
       <div className="agent-sim-sheet" role="alertdialog" aria-modal="true" aria-labelledby="agent-sim-dup-title" aria-describedby="agent-sim-dup-text">
        <span className="agent-sim-sheet-ico" aria-hidden="true">!</span>
        <h4 id="agent-sim-dup-title">{duplicate}</h4>
        <p id="agent-sim-dup-text">{duplicateHelp.text}</p>
        <div>
         {duplicateHelp.run&&<button type="button" className="go" autoFocus onClick={()=>{duplicateHelp.run!();setDuplicate(null)}}>{duplicateHelp.action}</button>}
         <button type="button" autoFocus={!duplicateHelp.run} onClick={()=>setDuplicate(null)}>{duplicateHelp.run?'Cancelar':'Entendido'}</button>
        </div>
       </div>
      </div>}
     </div>
    </div>
   </section>
  </div>
 </div>;
}
