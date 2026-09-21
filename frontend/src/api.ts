const API=import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
export type UatUser='presidente'|'dlatam'|'don'|'dnacional'|'dzonal'|'jregional'|'coord'|'asistente'|'supervisor'|'agente'|'cliente';
let currentUser:UatUser=(localStorage.getItem('sgi-uat-user') as UatUser)||'coord';
const PASSWORD='CajamarcaUAT!2026';
export const setUser=(u:UatUser)=>{currentUser=u;localStorage.setItem('sgi-uat-user',u)};
export const getUser=()=>currentUser;
export class ApiError extends Error{status:number;body:string;constructor(status:number,body:string){super(`${status} ${body}`);this.status=status;this.body=body}}
async function request<T>(path:string,init?:RequestInit):Promise<T>{
 const headers=new Headers(init?.headers); headers.set('Content-Type','application/json'); headers.set('Authorization','Basic '+btoa(`${currentUser}:${PASSWORD}`));
 const res=await fetch(`${API}${path}`,{...init,headers}); if(!res.ok){const body=await res.text();throw new ApiError(res.status,body)} return res.status===204?undefined as T:res.json();
}

async function binaryRequest(path:string,init?:RequestInit):Promise<Blob>{
 const headers=new Headers(init?.headers); headers.set('Authorization','Basic '+btoa(`${currentUser}:${PASSWORD}`));
 const res=await fetch(`${API}${path}`,{...init,headers}); if(!res.ok){const body=await res.text();throw new ApiError(res.status,body)} return res.blob();
}
async function atsCurrentOrNull(pointId:string):Promise<any|null>{
 try{return await request<any>(`/api/points/${encodeURIComponent(pointId)}/ats`)}catch(error){if(error instanceof ApiError&&error.status===404)return null;throw error}
}

async function bitacoraImageUpload(fieldId:string,file:File):Promise<any>{
 const headers=new Headers(); headers.set('Authorization','Basic '+btoa(`${currentUser}:${PASSWORD}`)); headers.set('Content-Type','application/octet-stream');
 const res=await fetch(`${API}/api/bitacora/fields/${encodeURIComponent(fieldId)}/standard-image?filename=${encodeURIComponent(file.name)}&contentType=${encodeURIComponent(file.type||'application/octet-stream')}`,{method:'POST',headers,body:file});
 if(!res.ok){const body=await res.text();throw new ApiError(res.status,body)} return res.json();
}
async function patrolImageUpload(checkpointId:string,file:File):Promise<any>{
 const headers=new Headers(); headers.set('Authorization','Basic '+btoa(`${currentUser}:${PASSWORD}`)); headers.set('Content-Type','application/octet-stream');
 const res=await fetch(`${API}/api/patrols/checkpoints/${encodeURIComponent(checkpointId)}/standard-image?filename=${encodeURIComponent(file.name)}&contentType=${encodeURIComponent(file.type||'application/octet-stream')}`,{method:'POST',headers,body:file});
 if(!res.ok){const body=await res.text();throw new ApiError(res.status,body)} return res.json();
}

async function consignmentImageUpload(evidenceId:string,file:File):Promise<any>{
 const headers=new Headers(); headers.set('Authorization','Basic '+btoa(`${currentUser}:${PASSWORD}`)); headers.set('Content-Type','application/octet-stream');
 const res=await fetch(`${API}/api/consignments/evidences/${encodeURIComponent(evidenceId)}/standard-image?filename=${encodeURIComponent(file.name)}&contentType=${encodeURIComponent(file.type||'application/octet-stream')}`,{method:'POST',headers,body:file});
 if(!res.ok){const body=await res.text();throw new ApiError(res.status,body)} return res.json();
}
async function atsUpload(pointId:string,file:File):Promise<any>{
 const headers=new Headers(); headers.set('Authorization','Basic '+btoa(`${currentUser}:${PASSWORD}`)); headers.set('Content-Type','application/octet-stream');
 const res=await fetch(`${API}/api/points/${encodeURIComponent(pointId)}/ats/upload?filename=${encodeURIComponent(file.name)}`,{method:'POST',headers,body:file});
 if(!res.ok){const body=await res.text();throw new ApiError(res.status,body)} return res.json();
}
export const api={
 context:()=>request<any>('/api/context'),
 companies:(page=0,size=50)=>request<any>(`/api/companies?page=${page}&size=${size}`),
 companyCoreCatalog:()=>request<any[]>('/api/companies/core-catalog'),
 activateCompany:(body:any)=>request<any>('/api/companies/activate',{method:'POST',body:JSON.stringify(body)}),
 company:(id:string)=>request<any>(`/api/companies/${encodeURIComponent(id)}`),
 updateCompany:(id:string,body:any)=>request<any>(`/api/companies/${encodeURIComponent(id)}`,{method:'PUT',body:JSON.stringify(body)}),
 companyHistory:(id:string)=>request<any[]>(`/api/companies/${encodeURIComponent(id)}/history`),
 services:()=>request<any[]>('/api/services'),
 serviceOverview:()=>request<any>('/api/services/overview'),
 serviceAssignmentDestinations:(pointId:string)=>request<any[]>(`/api/services/overview/assignment-destinations?pointId=${encodeURIComponent(pointId)}`),
 assignServiceCompany:(pointId:string,body:any)=>request<any>(`/api/services/overview/points/${encodeURIComponent(pointId)}/assign-company`,{method:'POST',body:JSON.stringify(body)}),
 returnServiceToCoordination:(pointId:string,body:any)=>request<any>(`/api/services/overview/points/${encodeURIComponent(pointId)}/return-to-coordination`,{method:'POST',body:JSON.stringify(body)}),
 atsCurrent:(pointId:string)=>atsCurrentOrNull(pointId),
 atsHistory:(pointId:string)=>request<any[]>(`/api/points/${encodeURIComponent(pointId)}/ats/history`),
 atsUpload:(pointId:string,file:File)=>atsUpload(pointId,file),
 atsPlan:(pointId:string)=>binaryRequest(`/api/points/${encodeURIComponent(pointId)}/ats/plan`),
 atsDownload:(pointId:string)=>binaryRequest(`/api/points/${encodeURIComponent(pointId)}/ats/download`),

 bitacoraProtocols:(pointId:string)=>request<any[]>(`/api/bitacora/protocols?pointId=${encodeURIComponent(pointId)}`),
 createBitacoraProtocol:(body:any)=>request<any>('/api/bitacora/protocols',{method:'POST',body:JSON.stringify(body)}),
 saveBitacoraProtocol:(protocolId:string,body:any)=>request<any>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}`,{method:'PUT',body:JSON.stringify(body)}),
 bitacoraProtocolScope:(protocolId:string,applicablePostIds:string[])=>request<any>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}/scope`,{method:'PUT',body:JSON.stringify({applicablePostIds})}),
 publishBitacoraProtocol:(protocolId:string)=>request<any>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}/publish`,{method:'POST'}),
 activateBitacoraProtocol:(protocolId:string)=>request<any>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}/activate`,{method:'POST'}),
 deactivateBitacoraProtocol:(protocolId:string)=>request<any>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}/deactivate`,{method:'POST'}),
 createBitacoraAccreditation:(protocolId:string,body:any)=>request<any>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}/accreditations`,{method:'POST',body:JSON.stringify(body)}),
 saveBitacoraAccreditation:(accreditationId:string,body:any)=>request<any>(`/api/bitacora/accreditations/${encodeURIComponent(accreditationId)}`,{method:'PUT',body:JSON.stringify(body)}),
 deleteBitacoraAccreditation:(accreditationId:string)=>request<void>(`/api/bitacora/accreditations/${encodeURIComponent(accreditationId)}`,{method:'DELETE'}),
 createBitacoraField:(accreditationId:string,body:any)=>request<any>(`/api/bitacora/accreditations/${encodeURIComponent(accreditationId)}/fields`,{method:'POST',body:JSON.stringify(body)}),
 saveBitacoraField:(fieldId:string,body:any)=>request<any>(`/api/bitacora/fields/${encodeURIComponent(fieldId)}`,{method:'PUT',body:JSON.stringify(body)}),
 deleteBitacoraField:(fieldId:string)=>request<void>(`/api/bitacora/fields/${encodeURIComponent(fieldId)}`,{method:'DELETE'}),
 bitacoraStandardImage:(fieldId:string)=>binaryRequest(`/api/bitacora/fields/${encodeURIComponent(fieldId)}/standard-image`),
 uploadBitacoraStandardImage:(fieldId:string,file:File)=>bitacoraImageUpload(fieldId,file),
 deleteBitacoraStandardImage:(fieldId:string)=>request<any>(`/api/bitacora/fields/${encodeURIComponent(fieldId)}/standard-image`,{method:'DELETE'}),
 bitacoraProtocolHistory:(protocolId:string)=>request<any[]>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}/history`),
 forkBitacoraProtocol:(protocolId:string)=>request<any>(`/api/bitacora/protocols/${encodeURIComponent(protocolId)}/fork`,{method:'POST'}),

 patrolProtocols:(pointId:string)=>request<any[]>(`/api/patrols/protocols?pointId=${encodeURIComponent(pointId)}`),
 createPatrolProtocol:(body:any)=>request<any>('/api/patrols/protocols',{method:'POST',body:JSON.stringify(body)}),
 savePatrolProtocol:(protocolId:string,body:any)=>request<any>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}`,{method:'PUT',body:JSON.stringify(body)}),
 patrolProtocolScope:(protocolId:string,applicablePostIds:string[])=>request<any>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}/scope`,{method:'PUT',body:JSON.stringify({applicablePostIds})}),
 forkPatrolProtocol:(protocolId:string)=>request<any>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}/fork`,{method:'POST'}),
 publishPatrolProtocol:(protocolId:string)=>request<any>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}/publish`,{method:'POST'}),
 activatePatrolProtocol:(protocolId:string)=>request<any>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}/activate`,{method:'POST'}),
 deactivatePatrolProtocol:(protocolId:string)=>request<any>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}/deactivate`,{method:'POST'}),
 patrolProtocolHistory:(protocolId:string)=>request<any[]>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}/history`),
 createPatrol:(protocolId:string,body:any)=>request<any>(`/api/patrols/protocols/${encodeURIComponent(protocolId)}/patrols`,{method:'POST',body:JSON.stringify(body)}),
 savePatrol:(patrolId:string,body:any)=>request<any>(`/api/patrols/patrols/${encodeURIComponent(patrolId)}`,{method:'PUT',body:JSON.stringify(body)}),
 deletePatrol:(patrolId:string)=>request<void>(`/api/patrols/patrols/${encodeURIComponent(patrolId)}`,{method:'DELETE'}),
 createPatrolCheckpoint:(patrolId:string,body:any)=>request<any>(`/api/patrols/patrols/${encodeURIComponent(patrolId)}/checkpoints`,{method:'POST',body:JSON.stringify(body)}),
 savePatrolCheckpoint:(checkpointId:string,body:any)=>request<any>(`/api/patrols/checkpoints/${encodeURIComponent(checkpointId)}`,{method:'PUT',body:JSON.stringify(body)}),
 savePatrolCheckpointRules:(checkpointId:string,body:any)=>request<any>(`/api/patrols/checkpoints/${encodeURIComponent(checkpointId)}/rules`,{method:'PUT',body:JSON.stringify(body)}),
 deletePatrolCheckpoint:(checkpointId:string)=>request<void>(`/api/patrols/checkpoints/${encodeURIComponent(checkpointId)}`,{method:'DELETE'}),
 patrolStandardImage:(checkpointId:string)=>binaryRequest(`/api/patrols/checkpoints/${encodeURIComponent(checkpointId)}/standard-image`),
 uploadPatrolStandardImage:(checkpointId:string,file:File)=>patrolImageUpload(checkpointId,file),
 deletePatrolStandardImage:(checkpointId:string)=>request<any>(`/api/patrols/checkpoints/${encodeURIComponent(checkpointId)}/standard-image`,{method:'DELETE'}),

 postConfigurations:(pointId:string)=>request<any[]>(`/api/post-configurations?pointId=${encodeURIComponent(pointId)}`),
 savePostConfiguration:(postId:string,body:any)=>request<any>(`/api/post-configurations/${encodeURIComponent(postId)}`,{method:'PUT',body:JSON.stringify(body)}),
 points:(serviceId:string)=>request<any[]>(`/api/services/${serviceId}/points`),
 posts:(pointId:string)=>request<any[]>(`/api/posts?pointId=${encodeURIComponent(pointId)}`),
 consignmentProtocols:(pointId:string)=>request<any[]>(`/api/consignments/protocols?pointId=${encodeURIComponent(pointId)}`),
 currentConsignmentProtocol:(pointId:string)=>request<any|null>(`/api/consignments/protocols/current?pointId=${encodeURIComponent(pointId)}`),
 createConsignmentProtocol:(body:any)=>request<any>('/api/consignments/protocols',{method:'POST',body:JSON.stringify(body)}),
 saveConsignmentProtocol:(protocolId:string,body:any)=>request<any>(`/api/consignments/protocols/${encodeURIComponent(protocolId)}`,{method:'PUT',body:JSON.stringify(body)}),
 forkConsignmentProtocol:(protocolId:string)=>request<any>(`/api/consignments/protocols/${encodeURIComponent(protocolId)}/fork`,{method:'POST'}),
 publishConsignmentProtocol:(protocolId:string)=>request<any>(`/api/consignments/protocols/${encodeURIComponent(protocolId)}/publish`,{method:'POST'}),
 activateConsignmentProtocol:(protocolId:string)=>request<any>(`/api/consignments/protocols/${encodeURIComponent(protocolId)}/activate`,{method:'POST'}),
 deactivateConsignmentProtocol:(protocolId:string)=>request<any>(`/api/consignments/protocols/${encodeURIComponent(protocolId)}/deactivate`,{method:'POST'}),
 consignmentProtocolHistory:(protocolId:string)=>request<any[]>(`/api/consignments/protocols/${encodeURIComponent(protocolId)}/history`),
 createConsignment:(protocolId:string,body:any)=>request<any>(`/api/consignments/protocols/${encodeURIComponent(protocolId)}/items`,{method:'POST',body:JSON.stringify(body)}),
 saveConsignment:(itemId:string,body:any)=>request<any>(`/api/consignments/items/${encodeURIComponent(itemId)}`,{method:'PUT',body:JSON.stringify(body)}),
 deleteConsignment:(itemId:string)=>request<void>(`/api/consignments/items/${encodeURIComponent(itemId)}`,{method:'DELETE'}),
 createConsignmentEvidence:(itemId:string,body:any)=>request<any>(`/api/consignments/items/${encodeURIComponent(itemId)}/evidences`,{method:'POST',body:JSON.stringify(body)}),
 saveConsignmentEvidence:(evidenceId:string,body:any)=>request<any>(`/api/consignments/evidences/${encodeURIComponent(evidenceId)}`,{method:'PUT',body:JSON.stringify(body)}),
 deleteConsignmentEvidence:(evidenceId:string)=>request<void>(`/api/consignments/evidences/${encodeURIComponent(evidenceId)}`,{method:'DELETE'}),
 consignmentStandardImage:(evidenceId:string)=>binaryRequest(`/api/consignments/evidences/${encodeURIComponent(evidenceId)}/standard-image`),
 uploadConsignmentStandardImage:(evidenceId:string,file:File)=>consignmentImageUpload(evidenceId,file),
 deleteConsignmentStandardImage:(evidenceId:string)=>request<any>(`/api/consignments/evidences/${encodeURIComponent(evidenceId)}/standard-image`,{method:'DELETE'}),
 consignments:(pointId:string)=>request<any[]>(`/api/consignments?pointId=${encodeURIComponent(pointId)}`),
 currentRegesep:(pointId:string)=>request<any>(`/api/points/${pointId}/regesep/current`),
 assignmentWeek:(companyId:string,weekStart:string,pointId?:string)=>request<any>(`/api/assignments/week?companyId=${encodeURIComponent(companyId)}&weekStart=${weekStart}${pointId?`&pointId=${encodeURIComponent(pointId)}`:''}`),
 assignmentPersonnel:(companyId:string,weekStart:string,q='',role='',availability='',page=0,size=50)=>request<any>(`/api/assignments/personnel?companyId=${encodeURIComponent(companyId)}&weekStart=${weekStart}&q=${encodeURIComponent(q)}&role=${encodeURIComponent(role)}&availability=${encodeURIComponent(availability)}&page=${page}&size=${size}`),
 assignmentEmployee:(employeeId:string,weekStart:string,companyId:string)=>request<any>(`/api/assignments/employees/${encodeURIComponent(employeeId)}?weekStart=${weekStart}&companyId=${encodeURIComponent(companyId)}`),
 transferReasons:()=>request<any[]>('/api/assignments/transfer-reasons'),
 transferDestinations:(originCompanyId:string)=>request<any[]>(`/api/assignments/transfer-destinations?originCompanyId=${encodeURIComponent(originCompanyId)}`),
 createCompanyTransfer:(body:any)=>request<any>('/api/assignments/transfers',{method:'POST',body:JSON.stringify(body)}),
 acceptCompanyTransfer:(transferId:string,note='')=>request<any>(`/api/assignments/transfers/${encodeURIComponent(transferId)}/accept`,{method:'POST',body:JSON.stringify({note})}),
 rejectCompanyTransfer:(transferId:string,note='')=>request<any>(`/api/assignments/transfers/${encodeURIComponent(transferId)}/reject`,{method:'POST',body:JSON.stringify({note})}),
 cancelCompanyTransfer:(transferId:string,note='')=>request<any>(`/api/assignments/transfers/${encodeURIComponent(transferId)}/cancel`,{method:'POST',body:JSON.stringify({note})}),
 companyTransferHistory:(employeeId:string)=>request<any[]>(`/api/assignments/transfers/employee/${encodeURIComponent(employeeId)}`),
 assignmentEvaluate:(planId:string,employeeId:string)=>request<any[]>(`/api/assignments/evaluate?planId=${encodeURIComponent(planId)}&employeeId=${encodeURIComponent(employeeId)}`),
 assignmentAssign:(body:any)=>request<any>('/api/assignments/assign',{method:'POST',body:JSON.stringify(body)}),
 assignmentRemove:(assignmentId:string)=>request<void>(`/api/assignments/${encodeURIComponent(assignmentId)}`,{method:'DELETE'}),
 assignmentSaveDraft:(planId:string)=>request<any>(`/api/assignments/plans/${encodeURIComponent(planId)}/save-draft`,{method:'POST'}),
 assignmentPublish:(planId:string)=>request<any>(`/api/assignments/plans/${encodeURIComponent(planId)}/publish`,{method:'POST'}),
 assignmentCompatibility:(assignmentId:string)=>request<any>(`/api/assignments/compatibility/${encodeURIComponent(assignmentId)}`),
 assignmentPostDetails:(postId:string)=>request<any>(`/api/assignments/posts/${encodeURIComponent(postId)}/details`),
 assignmentCoverage:(weekStart:string)=>request<any>(`/api/assignments/coverage?weekStart=${weekStart}`),

 coordinationCompanies:()=>request<any[]>('/api/coordination/companies'),
 coordinationPosts:(companyId:string)=>request<any[]>(`/api/coordination/posts?companyId=${encodeURIComponent(companyId)}`),
 createCoordinationPost:(body:any)=>request<any>('/api/coordination/posts',{method:'POST',body:JSON.stringify(body)}),
 saveCoordinationPost:(postId:string,body:any)=>request<any>(`/api/coordination/posts/${encodeURIComponent(postId)}`,{method:'PUT',body:JSON.stringify(body)}),
 coordinationPoints:(companyId:string)=>request<any[]>(`/api/coordination/points?companyId=${encodeURIComponent(companyId)}`),
 coordinationCoverage:(companyId:string)=>request<any>(`/api/coordination/coverage?companyId=${encodeURIComponent(companyId)}`),
 coordinationRouteForPost:(postId:string)=>request<any|null>(`/api/coordination/posts/${encodeURIComponent(postId)}/route`),
 createCoordinationRoute:(postId:string,body:any)=>request<any>(`/api/coordination/posts/${encodeURIComponent(postId)}/route`,{method:'POST',body:JSON.stringify(body)}),
 saveCoordinationRoute:(routeId:string,body:any)=>request<any>(`/api/coordination/routes/${encodeURIComponent(routeId)}`,{method:'PUT',body:JSON.stringify(body)}),
 forkCoordinationRoute:(routeId:string)=>request<any>(`/api/coordination/routes/${encodeURIComponent(routeId)}/fork`,{method:'POST'}),
 publishCoordinationRoute:(routeId:string)=>request<any>(`/api/coordination/routes/${encodeURIComponent(routeId)}/publish`,{method:'POST'}),
 coordinationRouteHistory:(routeId:string)=>request<any[]>(`/api/coordination/routes/${encodeURIComponent(routeId)}/history`),
 coordinationRoutePreview:(routeId:string)=>request<any>(`/api/coordination/routes/${encodeURIComponent(routeId)}/preview`),
 territory:()=>request<any>('/api/territory'),
 territoryAdmin:()=>request<any>('/api/territory/admin'),
 territoryResponsibles:()=>request<any[]>('/api/territory/responsibles'),
 territoryAudit:(entityType:string,entityId:string)=>request<any[]>(`/api/territory/audit?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`),
 createZone:(body:any)=>request<any>('/api/territory/zones',{method:'POST',body:JSON.stringify(body)}),
 updateZone:(id:string,body:any)=>request<any>(`/api/territory/zones/${encodeURIComponent(id)}`,{method:'PUT',body:JSON.stringify(body)}),
 deleteZone:(id:string)=>request<void>(`/api/territory/zones/${encodeURIComponent(id)}`,{method:'DELETE'}),
 createRegion:(body:any)=>request<any>('/api/territory/regions',{method:'POST',body:JSON.stringify(body)}),
 updateRegion:(id:string,body:any)=>request<any>(`/api/territory/regions/${encodeURIComponent(id)}`,{method:'PUT',body:JSON.stringify(body)}),
 deleteRegion:(id:string)=>request<void>(`/api/territory/regions/${encodeURIComponent(id)}`,{method:'DELETE'}),
 assignCompanyRegion:(companyId:string,regionId:string)=>request<any>(`/api/territory/companies/${encodeURIComponent(companyId)}/region/${encodeURIComponent(regionId)}`,{method:'PUT'}),
};
