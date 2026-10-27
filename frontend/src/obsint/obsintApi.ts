import {ApiError, binaryRequest, multipartRequest, request} from '../api';
import type {ObsintCameraOption, ObsintCase, ObsintConsignaConfig, ObsintConsignaSave, ObsintDecision, ObsintEvidence, ObsintMessage, ObsintProtocol, ObsintSummary, ObsintTask} from './obsint';

/**
 * API de la Consola OBSINT (/api/obsint). Del cliente común de SGI solo usa el transporte (token, renovación y errores),
 * así los cambios en el resto de api.ts no afectan a este módulo.
 */
const task=(taskId:string)=>`/api/obsint/tasks/${encodeURIComponent(taskId)}`;

export const obsintApi={
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

export const obsintConfigApi={
  protocols:(pointId:string)=>request<ObsintProtocol[]>(`${point(pointId)}/protocols`),
  cameras:(pointId:string)=>request<ObsintCameraOption[]>(`${point(pointId)}/cameras`),
  /** Para la fila "OBSINT" de Configuración del Punto: status complete | warning | blocked y el texto a mostrar. */
  summary:(pointId:string)=>request<ObsintSummary>(`${point(pointId)}/summary`),
  draft:(pointId:string)=>request<ObsintProtocol>(`${point(pointId)}/protocols/draft`,{method:'POST'}),
  discard:(protocolId:string)=>request<void>(protocol(protocolId),{method:'DELETE'}),
  publish:(protocolId:string)=>request<ObsintProtocol>(`${protocol(protocolId)}/publish`,{method:'POST'}),
  activate:(protocolId:string)=>request<ObsintProtocol>(`${protocol(protocolId)}/activate`,{method:'POST'}),
  deactivate:(protocolId:string)=>request<ObsintProtocol>(`${protocol(protocolId)}/deactivate`,{method:'POST'}),
  createConsigna:(protocolId:string,body:ObsintConsignaSave)=>request<ObsintConsignaConfig>(`${protocol(protocolId)}/consignas`,{method:'POST',body:JSON.stringify(body)}),
  updateConsigna:(consignaId:string,body:ObsintConsignaSave)=>request<ObsintConsignaConfig>(`${config}/consignas/${encodeURIComponent(consignaId)}`,{method:'PUT',body:JSON.stringify(body)}),
  deleteConsigna:(consignaId:string)=>request<void>(`${config}/consignas/${encodeURIComponent(consignaId)}`,{method:'DELETE'}),
};

/** Texto para mostrar de un error de la API: el backend devuelve {"message": …}; si no, el cuerpo tal cual. */
export const errorText=(error:unknown)=>{
  if(error instanceof ApiError){
    try{const data=JSON.parse(error.body);if(data?.message)return String(data.message)}catch{/* texto plano */}
    return error.body||`Error ${error.status}`;
  }
  return error instanceof Error?error.message:String(error);
};
