import {useEffect,useState} from 'react';
import {AlertTriangle,ArrowLeft,CheckCircle2,Clock3,RefreshCw,ShieldCheck,X,XCircle} from 'lucide-react';
import {api,ApiError} from '../api';

type Review={id:string;status:string;result:string|null;simulated:boolean};
type Row={id:string;executedAt:string;postCode:string;postName:string;protocolCode:string;protocolVersion:number;module:string;groupCode:string|null;groupName:string|null;taskCode:string|null;taskName:string|null;employeeName:string;captureNo:number;evidenceIds:string[];flags:string[];review:Review|null};
type Evidence={id:string;capturedAt:string;latitude:number|null;longitude:number|null;source:string;flags:string[];distanceM:number|null;radiusM:number|null};
type ReviewDetail=Review&{matchThreshold:number|null;matchScore:number|null;findings:string|null;matchedStandardImageId:string|null;reasonCode:string|null;modelVersion:string|null;standardImageVersion:number;attempts:number;lastError:string|null;createdAt:string;requestedAt:string|null;reviewedAt:string|null};
type Standard={id:string;position:number};
type Detail={row:Row;observation:string|null;standardNotes:string|null;evidences:Evidence[];standards:Standard[];review:ReviewDetail|null};
type Point={pointId:string;pointName:string;clientName:string;companyName:string};

/** Módulo de la tarea con foto. */
const MODULES:Record<string,string>={PATRULLA:'Patrulla',CONSIGNA:'Consigna',BITACORA:'Bitácora',RELEVO:'Relevo'};
const taskLabel=(r:Row)=>[r.taskCode,r.taskName].filter(Boolean).join(' · ');
const FLAGS:Record<string,string>={OUT_OF_RANGE:'Fuera del radio GPS'};
/** Distancia de la foto al punto configurado (solo aviso: nunca bloquea). */
const distance=(e?:Evidence)=>e?.distanceM==null?null:{out:e.radiusM!=null&&e.distanceM>e.radiusM,text:`A ${e.distanceM>=1000?`${(e.distanceM/1000).toFixed(1)} km`:`${e.distanceM} m`} del punto · radio ${e.radiusM} m`};
/** Alertas que se registran pero no se muestran: GALLERY (foto elegida de la galería) y SUSPECTED_REUSE (el mismo archivo ya se usó en otra ejecución). */
const shown=(flags:string[]|undefined)=>(flags??[]).filter(f=>f in FLAGS);
const PENDING=new Set(['QUEUED_FOR_VISINT','ERROR_RETRYABLE']);
const errorText=(e:unknown)=>e instanceof ApiError?(e.body||`Error ${e.status}`):String(e);
const when=(iso:string)=>new Date(iso).toLocaleString('es-EC');

/** Veredicto de VISINT: lo decide VISINT (PASS/FAIL/ERROR); Comando solo lo muestra. */
function verdict(review:Review|null):{label:string;tone:string}{
 if(!review)return {label:'Sin VISINT',tone:'none'};
 switch(review.status){
  case 'PASSED':return {label:'Cumple',tone:'good'};
  case 'FAILED':return {label:'No cumple',tone:'bad'};
  case 'QUEUED_FOR_VISINT':return {label:'En revisión',tone:'info'};
  case 'ERROR_RETRYABLE':return {label:'Reintentando',tone:'warn'};
  default:return review.result==='ERROR'?{label:'VISINT no pudo evaluar',tone:'warn'}:{label:'VISINT no disponible',tone:'warn'};
 }
}
function Verdict({review}:{review:Review|null}){
 const v=verdict(review);
 return <span className={`opr-pill ${v.tone}`}>{v.tone==='good'?<CheckCircle2 size={13}/>:v.tone==='bad'?<XCircle size={13}/>:<Clock3 size={13}/>}{v.label}</span>;
}
function Img({load,alt,className}:{load:()=>Promise<Blob>;alt:string;className?:string}){
 const [url,setUrl]=useState('');
 useEffect(()=>{let u='';let live=true;load().then(b=>{if(live){u=URL.createObjectURL(b);setUrl(u)}}).catch(()=>{});return()=>{live=false;if(u)URL.revokeObjectURL(u)}},[]);
 return url?<img src={url} alt={alt} className={className}/>:<div className={`opr-img-loading ${className??''}`}/>;
}

export default function OperationPage({point,onBack}:{point:Point;onBack:()=>void}){
 const [rows,setRows]=useState<Row[]>([]);const [error,setError]=useState('');const [loading,setLoading]=useState(true);
 const [openId,setOpenId]=useState('');const [detail,setDetail]=useState<Detail|null>(null);const [selected,setSelected]=useState('');
 const load=()=>api.operationExecutions(point.pointId).then((r:Row[])=>{setRows(r);setError('')}).catch(e=>setError(errorText(e))).finally(()=>setLoading(false));
 const loadDetail=(id:string)=>api.operationExecution(id).then((d:Detail)=>{setDetail(d);setSelected(s=>s&&d.standards.some(x=>x.id===s)?s:d.review?.matchedStandardImageId??d.standards[0]?.id??'')}).catch(e=>setError(errorText(e)));
 useEffect(()=>{void load()},[]);
 useEffect(()=>{
  const pending=rows.some(r=>r.review&&PENDING.has(r.review.status))||(!!detail?.review&&PENDING.has(detail.review.status));
  if(!pending)return;
  const t=setInterval(()=>{void load();if(openId)void loadDetail(openId)},4000);
  return()=>clearInterval(t);
 },[rows,detail,openId]);
 const open=(id:string)=>{setOpenId(id);setDetail(null);void loadDetail(id)};
 const close=()=>{setOpenId('');setDetail(null)};
 const retry=async()=>{if(!detail?.review)return;try{await api.retryVisualReview(detail.review.id);await loadDetail(detail.row.id);await load()}catch(e){setError(errorText(e))}};
 const reviewed=rows.filter(r=>r.review);
 const count=(s:string)=>reviewed.filter(r=>r.review!.status===s).length;
 const pending=reviewed.filter(r=>PENDING.has(r.review!.status)).length;

 return <div className="services-v01 opr-page">
  <div className="ser-breadcrumbs"><button onClick={onBack}><ArrowLeft size={15}/>Volver a Servicios</button><span>/</span><small>Operación</small></div>
  <div className="opr-title"><div><h2>Operación · {point.pointName}</h2><span>{point.clientName} · {point.companyName} · Tareas con foto (Patrullas, Consignas, Bitácora, Relevo) y validación visual (VISINT)</span></div><button onClick={()=>void load()}><RefreshCw size={14}/>Actualizar</button></div>
  <div className="opr-kpis">
   <article><small>Ejecuciones</small><strong>{rows.length}</strong></article>
   <article className="good"><small>Cumplen</small><strong>{count('PASSED')}</strong></article>
   <article className="bad"><small>No cumplen</small><strong>{count('FAILED')}</strong></article>
   <article className="info"><small>En revisión</small><strong>{pending}</strong></article>
   <article className="warn"><small>Error VISINT</small><strong>{count('ERROR_FINAL')}</strong></article>
   <article><small>Sin VISINT</small><strong>{rows.length-reviewed.length}</strong></article>
  </div>
  {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>setError('')}>Cerrar</button></div>}
  <section className="ser-table-card"><table className="opr-table"><thead><tr><th>Fecha</th><th>Tarea</th><th>Protocolo</th><th>Agente</th><th>Fotos</th><th>VISINT</th><th>Alertas</th></tr></thead>
   <tbody>{loading?<tr><td colSpan={7}>Cargando…</td></tr>:rows.length?rows.map(r=><tr key={r.id} className={r.id===openId?'selected':''} onClick={()=>open(r.id)}>
    <td>{when(r.executedAt)}</td>
    <td><em className={`opr-module ${r.module.toLowerCase()}`}>{MODULES[r.module]??r.module}</em><strong>{taskLabel(r)}</strong><small>{r.postCode}{r.captureNo>1&&` · Captura ${r.captureNo}`}</small></td>
    <td>{r.protocolCode}{r.protocolVersion>0&&` v${r.protocolVersion}`}<small>{[r.groupCode,r.groupName].filter(Boolean).join(' ')}</small></td>
    <td>{r.employeeName}</td>
    <td><div className="opr-thumbs">{r.evidenceIds.slice(0,3).map(id=><Img key={id} load={()=>api.operationEvidenceImage(id)} alt="" className="opr-thumb"/>)}{r.evidenceIds.length>3&&<span>+{r.evidenceIds.length-3}</span>}</div></td>
    <td><Verdict review={r.review}/></td>
    <td>{shown(r.flags).map(f=><em key={f} className="opr-flag">{FLAGS[f]??f}</em>)}</td></tr>)
    :<tr><td colSpan={7} className="bit-empty-cell">Todavía no hay Hitos ejecutados en este Punto.</td></tr>}</tbody></table></section>

  {openId&&<div className="opr-drawer-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)close()}}><aside className="opr-drawer">
   <header><div><h3>{detail?`${MODULES[detail.row.module]??detail.row.module} · ${taskLabel(detail.row)}`:'Cargando…'}</h3>{detail&&<span>{detail.row.employeeName} · {detail.row.postCode} · {when(detail.row.executedAt)}</span>}</div><button onClick={close} aria-label="Cerrar"><X size={18}/></button></header>
   {detail&&<div className="opr-drawer-body">
    <div className="opr-result"><Verdict review={detail.review}/>
     {detail.review?.matchThreshold!=null&&<span className={`opr-threshold${detail.review.matchScore!=null&&detail.review.matchScore<detail.review.matchThreshold?' below':''}`}>Coincidencia <b>{detail.review.matchScore!=null?detail.review.matchScore.toFixed(2):'—'}</b> de umbral <b>{detail.review.matchThreshold.toFixed(2)}</b>{detail.review.matchThreshold<=0.4&&<em>umbral bajo</em>}</span>}
     {detail.review&&<span>Foto estándar v{detail.review.standardImageVersion}{detail.review.simulated&&" · VISINT simulado"}{!detail.review.simulated&&detail.review.modelVersion&&` · modelo ${detail.review.modelVersion}`}</span>}
     {detail.review&&(detail.review.status==='ERROR_FINAL'||detail.review.status==='ERROR_RETRYABLE')&&<button onClick={()=>void retry()}><RefreshCw size={13}/>Reintentar VISINT</button>}
    </div>
    {detail.review?.findings&&<p className="opr-findings">{detail.review.findings}</p>}
    {detail.review?.lastError&&<p className="opr-error-note"><AlertTriangle size={13}/>{detail.review.lastError} (intentos: {detail.review.attempts})</p>}
    <div className="opr-compare">
     <figure><figcaption>Foto del agente{shown(detail.evidences[0]?.flags).map(f=><em key={f} className="opr-flag">{FLAGS[f]??f}</em>)}{(()=>{const d=distance(detail.evidences[0]);return d&&<em className={`opr-distance${d.out?' out':''}`}>{d.text}</em>})()}</figcaption>{detail.evidences[0]?<Img key={detail.evidences[0].id} load={()=>api.operationEvidenceImage(detail.evidences[0].id)} alt="Foto del agente" className="opr-big"/>:<div className="opr-img-loading opr-big"/>}</figure>
     <figure><figcaption>Foto estándar {detail.standards.find(x=>x.id===selected)?.position??""}{selected&&selected===detail.review?.matchedStandardImageId&&<em className="opr-match">Coincide</em>}</figcaption>{selected?<Img key={selected} load={()=>api.operationStandardImage(detail.row.id,selected)} alt="Foto estándar" className="opr-big"/>:<div className="opr-img-loading opr-big"/>}{detail.standardNotes&&<small>{detail.standardNotes}</small>}</figure>
    </div>
    <div className="opr-gallery">{detail.standards.map(x=><button key={x.id} className={x.id===selected?'selected':''} onClick={()=>setSelected(x.id)}>
     <Img load={()=>api.operationStandardImage(detail.row.id,x.id)} alt="" className="opr-gallery-img"/><span>Estándar {x.position}</span>{x.id===detail.review?.matchedStandardImageId&&<em className="opr-match">Coincide</em>}</button>)}</div>
    {detail.review&&<ol className="opr-timeline">
     <li><ShieldCheck size={13}/>Recibida {when(detail.review.createdAt)}</li>
     {detail.review.requestedAt&&<li><Clock3 size={13}/>Enviada a VISINT {when(detail.review.requestedAt)}</li>}
     {detail.review.reviewedAt&&<li><CheckCircle2 size={13}/>Veredicto {when(detail.review.reviewedAt)}</li>}
    </ol>}
   </div>}
  </aside></div>}
 </div>;
}
