import {useEffect, useMemo, useState} from 'react';

// Espejo de los records de ObsintResource.java y ObsintConfigResource.java. Mantener sincronizado.
export type ObsintDecision='YES'|'NO'|'UNCERTAIN';
export type ObsintTaskStatus='PENDING'|'IN_PROGRESS'|'ANSWERED';
/** streamUrl null: la cámara aún no tiene video (no existe el servidor de video); se muestra "Sin señal". */
export type ObsintCamera={id:string;code:string;name:string;location:string|null;streamUrl:string|null;live:boolean};
export type ObsintTaskSummary={id:string;position:number;status:ObsintTaskStatus;decision:ObsintDecision|null;timedOut:boolean|null};
export type ObsintCase={id:string;code:string;operator:string;status:'IN_PROGRESS'|'COMPLETED';startedAt:string;completedAt:string|null;currentTaskId:string|null;tasks:ObsintTaskSummary[]};
export type ObsintTask={id:string;caseId:string;caseCode:string;operator:string;position:number;totalTasks:number;consignaCode:string;instruction:string;
  timeLimitSeconds:number;status:ObsintTaskStatus;startedAt:string|null;deadline:string|null;serverNow:string;
  decision:ObsintDecision|null;justification:string|null;timedOut:boolean|null;cameras:ObsintCamera[]};
export type ObsintEvidence={id:string;cameraId:string;videoTimeSeconds:number;capturedAt:string;contentType:string;sizeBytes:number};
export type ObsintMessage={id:string;operator:string;text:string;sentAt:string;deliveryStatus:'SENT'|'FAILED'};

// Configuración del Punto (protocolo versionado y consignas).
export type ObsintProtocolStatus='BORRADOR'|'INACTIVO'|'ACTIVO';
export type ObsintCategory='SSO'|'PRODUCTION'|'LOGISTICS'|'CLEANING'|'MOBILITY'|'CUSTOMER_SERVICE';
export type ObsintSlot={dayOfWeek:number;start:string;end:string};
export type ObsintConsignaConfig={id:string;code:string;title:string;instruction:string;category:ObsintCategory;timeLimitSeconds:number;position:number;active:boolean;cameraIds:string[];schedule:ObsintSlot[]};
export type ObsintConsignaSave=Omit<ObsintConsignaConfig,'id'|'code'|'position'>;
export type ObsintProtocol={id:string;pointId:string;code:string;versionNo:number;status:ObsintProtocolStatus;publishedAt:string|null;activatedAt:string|null;updatedBy:string;updatedAt:string;consignas:ObsintConsignaConfig[]};
export type ObsintCameraOption={id:string;code:string;name:string;location:string|null;status:'ACTIVE'|'OBSERVED'};
export type ObsintSummary={status:'complete'|'warning'|'blocked';meta:string;activeVersion:number|null;activeConsignas:number;hasDraft:boolean;warnings:string[]};

export const CATEGORIES:{value:ObsintCategory;label:string}[]=[
  {value:'SSO',label:'Seguridad y salud ocupacional (SSO)'},
  {value:'PRODUCTION',label:'Producción industrial'},
  {value:'LOGISTICS',label:'Logística y almacenamiento'},
  {value:'CLEANING',label:'Orden y limpieza'},
  {value:'MOBILITY',label:'Movilidad y tránsito interno'},
  {value:'CUSTOMER_SERVICE',label:'Atención al cliente'},
];
export const categoryLabel=(value:ObsintCategory)=>CATEGORIES.find(c=>c.value===value)?.label??value;
export const DAYS=['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo'];
export const protocolStatusLabel=(s:ObsintProtocolStatus)=>({BORRADOR:'Borrador',INACTIVO:'Publicado · inactivo',ACTIVO:'Activo'}[s]);

export const OBSINT_JUSTIFICATION_MAX=500;
export const decisionLabel=(d:ObsintDecision)=>({YES:'Sí',NO:'No',UNCERTAIN:'Incierto'}[d]);

/** Las URL relativas (videos demo en public/obsint/videos) se resuelven contra la base de la web: mismo origen para poder capturar. */
export const streamSrc=(url:string)=>/^https?:\/\//.test(url)||url.startsWith('/')?url:`${import.meta.env.BASE_URL}${url}`;

export const formatClock=(value:string|Date,withSeconds=false)=>new Intl.DateTimeFormat('es-EC',{hour:'2-digit',minute:'2-digit',...(withSeconds?{second:'2-digit'}:{})}).format(new Date(value));
export const formatDateTime=(value:Date)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit'}).format(value);
export const formatSeconds=(total:number)=>{const s=Math.ceil(total);return `${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;};

export type TimerLevel='ok'|'warn'|'alert'|'critical';
/**
 * Tiempo restante hasta deadline con el reloj del servidor (serverNow corrige la diferencia con el reloj del equipo).
 * Al recargar, el tiempo sigue donde iba. Cortes del mockup (30/20/10 s sobre 60 s) como fracción del tiempo máximo.
 */
export function useCountdown(deadline:string|null,serverNow:string|null,limitSeconds:number){
  const offsetMs=useMemo(()=>serverNow?new Date(serverNow).getTime()-Date.now():0,[serverNow]);
  const remainingAt=()=>deadline?(new Date(deadline).getTime()-(Date.now()+offsetMs))/1000:limitSeconds;
  const [remaining,setRemaining]=useState(remainingAt);
  useEffect(()=>{
    setRemaining(remainingAt());
    if(!deadline)return;
    const id=setInterval(()=>setRemaining(remainingAt()),250);
    return()=>clearInterval(id);
  },[deadline,offsetMs,limitSeconds]);
  const seconds=Math.max(0,remaining);
  const fraction=limitSeconds>0?seconds/limitSeconds:0;
  const level:TimerLevel=fraction<=1/6?'critical':fraction<=1/3?'alert':fraction<=1/2?'warn':'ok';
  return {seconds,level,expired:deadline!==null&&remaining<=0};
}

/** Reloj de pared que se actualiza cada segundo (fecha y hora de cabecera y de cada cámara). */
export function useNow(){
  const [now,setNow]=useState(()=>new Date());
  useEffect(()=>{const id=setInterval(()=>setNow(new Date()),1000);return()=>clearInterval(id);},[]);
  return now;
}

/** Borrador de la respuesta por tarea en sessionStorage: recargar la página no pierde lo escrito. */
export type AnswerDraft={decision:ObsintDecision|null;justification:string};
const draftKey=(taskId:string)=>`sgi-obsint-draft:${taskId}`;
export function readDraft(taskId:string):AnswerDraft{
  try{const raw=sessionStorage.getItem(draftKey(taskId));if(raw)return JSON.parse(raw)}catch{/* sin almacenamiento */}
  return {decision:null,justification:''};
}
export function writeDraft(taskId:string,draft:AnswerDraft){try{sessionStorage.setItem(draftKey(taskId),JSON.stringify(draft))}catch{/* sin almacenamiento */}}
export function clearDraft(taskId:string){try{sessionStorage.removeItem(draftKey(taskId))}catch{/* sin almacenamiento */}}

/**
 * Captura el fotograma actual de un video como JPEG. El video debe ser del mismo origen
 * (o servido con CORS); si no, el canvas queda bloqueado y toBlob falla.
 */
export function captureFrame(video:HTMLVideoElement,quality=0.85):Promise<Blob>{
  return new Promise((resolve,reject)=>{
    if(!video.videoWidth||!video.videoHeight)return reject(new Error('El video todavía no tiene imagen.'));
    const canvas=document.createElement('canvas');
    canvas.width=video.videoWidth;canvas.height=video.videoHeight;
    const ctx=canvas.getContext('2d');
    if(!ctx)return reject(new Error('El navegador no permite capturar.'));
    ctx.drawImage(video,0,0,canvas.width,canvas.height);
    try{canvas.toBlob(blob=>blob?resolve(blob):reject(new Error('No se pudo generar la imagen.')),'image/jpeg',quality)}
    catch{reject(new Error('El video no permite capturar imágenes (origen distinto sin CORS).'))}
  });
}
