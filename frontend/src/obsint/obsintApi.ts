import {ApiError, binaryRequest, multipartRequest, request} from '../api';
import type {ObsintCase, ObsintDecision, ObsintEvidence, ObsintMessage, ObsintTask} from './obsint';

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

/** Texto para mostrar de un error de la API: el backend de OBSINT devuelve sus reglas como texto plano. */
export const errorText=(error:unknown)=>error instanceof ApiError?(error.body||`Error ${error.status}`):error instanceof Error?error.message:String(error);
