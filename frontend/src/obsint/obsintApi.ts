import {ApiError, binaryRequest, multipartRequest, request} from '../api';

/*
 * Contrato con el backend de OBSINT: tipos (espejo de los records de ObsintResource.java y ObsintConfigResource.java)
 * y llamadas a la API. Si cambia un endpoint o un campo en el backend, se actualiza aquí.
 */

// Consola
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
/** deliveryStatus: hoy siempre 'TEST' (modo prueba: registrado, NO enviado al agente). 'SENT'/'FAILED' quedan para cuando exista el envío real. */
export type ObsintMessage={id:string;operator:string;text:string;sentAt:string;deliveryStatus:'TEST'|'SENT'|'FAILED'};
/** Máximo de caracteres de la justificación (ObsintCaseTask.JUSTIFICATION_MAX). */
export const OBSINT_JUSTIFICATION_MAX=500;

// Configuración del Punto (protocolo versionado y consignas)
export type ObsintProtocolStatus='BORRADOR'|'INACTIVO'|'ACTIVO';
export type ObsintCategory='SSO'|'PRODUCTION'|'LOGISTICS'|'CLEANING'|'MOBILITY'|'CUSTOMER_SERVICE';
export type ObsintSlot={dayOfWeek:number;start:string;end:string};
export type ObsintFrequency='EVERY_SHIFT'|'EVERY_2_HOURS'|'EVERY_4_HOURS'|'DAILY';
/** consignmentCode: consigna de Consignas (marcada OBSINT) de la que sale la tarea; null en tareas anteriores al vínculo. */
export type ObsintConsignaConfig={id:string;code:string;consignmentCode:string|null;title:string;description:string;instruction:string;category:ObsintCategory;frequency:ObsintFrequency;timeLimitSeconds:number;position:number;active:boolean;cameraIds:string[];schedule:ObsintSlot[]};
export type ObsintConsignaSave=Omit<ObsintConsignaConfig,'id'|'code'|'position'|'frequency'>;
export type ObsintProtocol={id:string;pointId:string;code:string;versionNo:number;status:ObsintProtocolStatus;publishedAt:string|null;activatedAt:string|null;updatedBy:string;updatedAt:string;consignas:ObsintConsignaConfig[]};
/** Consigna del protocolo de Consignas activo del Punto marcada con el "puesto" OBSINT. */
export type ObsintConsignmentOption={code:string;title:string;instruction:string};
export type ObsintCameraOption={id:string;code:string;name:string;location:string|null;status:'ACTIVE'|'OBSERVED'};
export type ObsintSummary={enabled:boolean;status:'complete'|'warning'|'blocked';meta:string;activeVersion:number|null;activeConsignas:number;hasDraft:boolean;warnings:string[]};
export type ObsintScheduleSuggestion={available:boolean;message:string;slots:ObsintSlot[]};

/**
 * API de la Consola OBSINT (/api/obsint). Del cliente común de SGI solo usa el transporte (token, renovación y errores),
 * así los cambios en el resto de api.ts no afectan a este módulo.
 */
const task=(taskId:string)=>`/api/obsint/tasks/${encodeURIComponent(taskId)}`;

export const obsintApi={
  access:()=>request<{enabled:boolean}>('/api/obsint/access'),
  currentCase:()=>request<ObsintCase|undefined>('/api/obsint/cases/current'),
  openCase:()=>request<ObsintCase>('/api/obsint/cases',{method:'POST'}),
  task:(taskId:string)=>request<ObsintTask>(task(taskId)),
  startTask:(taskId:string)=>request<ObsintTask>(`${task(taskId)}/start`,{method:'POST'}),
  answerTask:(taskId:string,decision:ObsintDecision,justification:string)=>request<ObsintCase>(`${task(taskId)}/answer`,{method:'PUT',body:JSON.stringify({decision,justification})}),
  evidence:(taskId:string)=>request<ObsintEvidence[]>(`${task(taskId)}/evidence`),
  addEvidence:(taskId:string,cameraId:string,videoTimeSeconds:number,image:Blob)=>{
    const form=new FormData();
    form.append('file',image,'captura.jpg');form.append('cameraId',cameraId);form.append('videoTimeSeconds',String(videoTimeSeconds));
    return multipartRequest<ObsintEvidence>(`${task(taskId)}/evidence`,form);
  },
  deleteEvidence:(taskId:string,evidenceId:string)=>request<void>(`${task(taskId)}/evidence/${encodeURIComponent(evidenceId)}`,{method:'DELETE'}),
  evidenceImage:(taskId:string,evidenceId:string)=>binaryRequest(`${task(taskId)}/evidence/${encodeURIComponent(evidenceId)}/content`),
  messages:(taskId:string)=>request<ObsintMessage[]>(`${task(taskId)}/messages`),
  sendMessage:(taskId:string,text:string)=>request<ObsintMessage>(`${task(taskId)}/messages`,{method:'POST',body:JSON.stringify({text})}),
};

/** Configuración OBSINT del Punto (/api/obsint/config): protocolo versionado, consignas con cámaras y horario, resumen. */
const config='/api/obsint/config';
const point=(pointId:string)=>`${config}/points/${encodeURIComponent(pointId)}`;
const protocol=(protocolId:string)=>`${config}/protocols/${encodeURIComponent(protocolId)}`;
const monitoringUrl=(pointId:string)=>`/api/obsint/monitoring/points/${encodeURIComponent(pointId)}`;

export const obsintConfigApi={
  protocols:(pointId:string)=>request<ObsintProtocol[]>(`${point(pointId)}/protocols`),
  cameras:(pointId:string)=>request<ObsintCameraOption[]>(`${point(pointId)}/cameras`),
  consignments:(pointId:string)=>request<ObsintConsignmentOption[]>(`${point(pointId)}/consignments`),
  scheduleSuggestion:(pointId:string,consignmentCode:string)=>request<ObsintScheduleSuggestion>(`${point(pointId)}/schedule-suggestion?consignmentCode=${encodeURIComponent(consignmentCode)}`),
  /** Para la fila "OBSINT" de Configuración del Punto: status complete | warning | blocked y el texto a mostrar. */
  summary:(pointId:string)=>request<ObsintSummary>(`${point(pointId)}/summary`),
  draft:(pointId:string)=>request<ObsintProtocol>(`${point(pointId)}/protocols/draft`,{method:'POST'}),
  discard:(protocolId:string)=>request<void>(protocol(protocolId),{method:'DELETE'}),
  publish:(protocolId:string)=>request<ObsintProtocol>(`${protocol(protocolId)}/publish`,{method:'POST'}),
  activate:(protocolId:string)=>request<ObsintProtocol>(`${protocol(protocolId)}/activate`,{method:'POST'}),
  deactivate:(protocolId:string)=>request<ObsintProtocol>(`${protocol(protocolId)}/deactivate`,{method:'POST'}),
  /** Guardar = activar: cada cambio de una tarea deja activa una versión nueva del protocolo (la tarea se identifica por su código). */
  addTask:(pointId:string,body:ObsintConsignaSave)=>request<ObsintTaskSaved>(`${point(pointId)}/tasks`,{method:'POST',body:JSON.stringify(body)}),
  updateTask:(pointId:string,code:string,body:ObsintConsignaSave)=>request<ObsintTaskSaved>(`${point(pointId)}/tasks/${encodeURIComponent(code)}`,{method:'PUT',body:JSON.stringify(body)}),
  deleteTask:(pointId:string,code:string)=>request<ObsintProtocol>(`${point(pointId)}/tasks/${encodeURIComponent(code)}`,{method:'DELETE'}),
  createConsigna:(protocolId:string,body:ObsintConsignaSave)=>request<ObsintConsignaConfig>(`${protocol(protocolId)}/consignas`,{method:'POST',body:JSON.stringify(body)}),
  updateConsigna:(consignaId:string,body:ObsintConsignaSave)=>request<ObsintConsignaConfig>(`${config}/consignas/${encodeURIComponent(consignaId)}`,{method:'PUT',body:JSON.stringify(body)}),
  deleteConsigna:(consignaId:string)=>request<void>(`${config}/consignas/${encodeURIComponent(consignaId)}`,{method:'DELETE'}),
  /** Monitoristas del Punto: uno o más, de la Compañía del Punto (la asignada en Servicios). */
  monitoring:(pointId:string)=>request<ObsintMonitoring>(monitoringUrl(pointId)),
  monitors:(pointId:string)=>request<ObsintMonitorOption[]>(`${monitoringUrl(pointId)}/monitors`),
  saveMonitoring:(pointId:string,body:ObsintMonitoringSave)=>request<ObsintMonitoring>(monitoringUrl(pointId),{method:'PUT',body:JSON.stringify(body)}),
};

export type ObsintTaskSaved={protocol:ObsintProtocol;code:string};

export type ObsintMonitorOption={employeeId:string;name:string;cargo:string|null;companyId:string|null;companyName:string|null;confidential:boolean};
/** monitors vacío: lo atiende cualquier monitor de turno de la Compañía del Punto (la asignada en Servicios). */
export type ObsintMonitoring={companyId:string|null;companyName:string|null;monitors:ObsintMonitorOption[];updatedBy:string|null;updatedAt:string|null};
export type ObsintMonitoringSave={employeeIds:string[]};

/** Texto para mostrar de un error de la API: el backend devuelve {"message": …}; si no, el cuerpo tal cual. */
export const errorText=(error:unknown)=>{
  if(error instanceof ApiError){
    try{const data=JSON.parse(error.body);if(data?.message)return String(data.message)}catch{/* texto plano */}
    return error.body||`Error ${error.status}`;
  }
  return error instanceof Error?error.message:String(error);
};
export const isObsintNoOrder=(error:unknown)=>{
  if(!(error instanceof ApiError)||error.status!==403)return false;
  try{return JSON.parse(error.body)?.code==='OBSINT_SIN_ORDEN'}catch{return false}
};
