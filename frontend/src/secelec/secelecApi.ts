import {ApiError, request} from '../api';

// Espejo de los records de SecElecResource.java. Mantener sincronizado.
export type Family='PERIMETER'|'ACCESS_CONTROL'|'VIDEO'|'INTRUSION'|'LIGHTING'|'FIRE';
export type ComponentStatus='ACTIVE'|'OBSERVED'|'INACTIVE';
export type SecurityComponent={id:string;pointId:string;family:Family;componentType:string;code:string;name:string;location:string|null;coverage:string|null;
  brandModel:string|null;status:ComponentStatus;observations:string|null;ip:string|null;port:number|null;username:string|null;hasPassword:boolean;
  streamUrl:string|null;updatedBy:string;updatedAt:string};
/** password: obligatoria al crear una cámara; al editar, vacía conserva la actual. */
export type ComponentSave={family:Family;componentType:string;name:string;location:string;coverage:string;brandModel:string;status:ComponentStatus;
  observations:string;ip:string;port:number|null;username:string;password:string;streamUrl:string};
export type SecElecSummary={total:number;active:number;observed:number;cameras:number;status:'complete'|'warning'|'blocked';meta:string};
/** Lo que entrega Servicios al abrir el módulo. */
export type PointContext={pointId:string;pointName:string;clientName?:string;serviceName?:string;companyName?:string};

const base='/api/secelec';
export const secelecApi={
  components:(pointId:string)=>request<SecurityComponent[]>(`${base}/points/${encodeURIComponent(pointId)}/components`),
  create:(pointId:string,body:ComponentSave)=>request<SecurityComponent>(`${base}/points/${encodeURIComponent(pointId)}/components`,{method:'POST',body:JSON.stringify(body)}),
  update:(id:string,body:ComponentSave)=>request<SecurityComponent>(`${base}/components/${encodeURIComponent(id)}`,{method:'PUT',body:JSON.stringify(body)}),
  remove:(id:string)=>request<void>(`${base}/components/${encodeURIComponent(id)}`,{method:'DELETE'}),
  /** Para la fila "Componentes de Seguridad" de Configuración del Punto. */
  summary:(pointId:string)=>request<SecElecSummary>(`${base}/points/${encodeURIComponent(pointId)}/summary`),
};

export const errorText=(error:unknown)=>{
  if(error instanceof ApiError){
    try{const data=JSON.parse(error.body);if(data?.message)return String(data.message)}catch{/* texto plano */}
    return error.body||`Error ${error.status}`;
  }
  return error instanceof Error?error.message:String(error);
};

export const FAMILIES:{value:Family;label:string;hint:string;types:string[]}[]=[
  {value:'PERIMETER',label:'Seguridad Perimetral',hint:'Cercas, barreras y sensores',types:['Cerca eléctrica','Barrera vehicular','Sensor perimetral']},
  {value:'ACCESS_CONTROL',label:'Control de Accesos',hint:'Lectores, tarjetas y torniquetes',types:['Lector biométrico','Lector de tarjetas','Torniquete']},
  {value:'VIDEO',label:'Videovigilancia',hint:'Cámaras y grabadores',types:['Cámara Bala','Cámara Domo','Cámara PTZ','NVR / DVR']},
  {value:'INTRUSION',label:'Detección de Intrusiones',hint:'Sensores y alarmas',types:['Sensor de movimiento','Contacto magnético','Panel de alarma']},
  {value:'LIGHTING',label:'Iluminación de Seguridad',hint:'Luminarias y emergencia',types:['Reflector','Luminaria perimetral','Luz de emergencia']},
  {value:'FIRE',label:'Protección Contra Incendios',hint:'Detectores y paneles',types:['Detector de humo','Panel de incendios','Sirena']},
];
export const familyLabel=(f:Family)=>FAMILIES.find(x=>x.value===f)?.label??f;
export const statusLabel=(s:ComponentStatus)=>({ACTIVE:'Activo',OBSERVED:'Con observación',INACTIVE:'Inactivo'}[s]);
