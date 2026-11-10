import {ApiError, request} from '../api';

// Espejo de los records de SecElecResource.java. Mantener sincronizado.
export type Family='PERIMETER'|'ACCESS_CONTROL'|'VIDEO'|'INTRUSION'|'LIGHTING'|'FIRE';
export type ComponentStatus='ACTIVE'|'OBSERVED'|'INACTIVE';
/** COPY: el servidor de video reenvía el flujo tal cual. TRANSCODE: lo recodifica (p. ej. H.265 → H.264). */
export type StreamingMode='COPY'|'TRANSCODE';
/** Marca de la cámara: define la ruta RTSP del video. OTHER ("Otra") lleva la ruta escrita a mano (rtspPath). */
export type CameraBrand='HIKVISION'|'DAHUA'|'TPLINK'|'OTHER';
/** Calidad del video: MAIN = stream principal (alta calidad), SUB = secundario (liviano, recomendado para la consola). */
export type StreamQuality='MAIN'|'SUB';
/** Opción de la lista "Marca" (GET /api/secelec/camera-brands). requiresPath: hay que escribir la ruta (solo "Otra"). */
export type BrandOption={code:CameraBrand;label:string;defaultPort:number;pathTemplate:string|null;subPathTemplate:string|null;requiresPath:boolean};
/** channel, latitude, longitude y streaming* solo vienen en cámaras (VIDEO); en otras familias son null. */
export type SecurityComponent={id:string;pointId:string;family:Family;componentType:string;code:string;name:string;location:string|null;coverage:string|null;
  brandModel:string|null;status:ComponentStatus;observations:string|null;ip:string|null;port:number|null;channel:number|null;
  cameraBrand:CameraBrand|null;streamQuality:StreamQuality|null;rtspPath:string|null;streamPath:string|null;detectedCodec:string|null;latitude:number|null;
  longitude:number|null;streamingEnabled:boolean|null;streamingMode:StreamingMode|null;username:string|null;hasPassword:boolean;
  streamUrl:string|null;electronicDeviceId:string|null;updatedBy:string;updatedAt:string};
/** password: obligatoria al crear una cámara; al editar, vacía conserva la actual. latitude y longitude van juntas o ambas null. */
export type ComponentSave={family:Family;componentType:string;name:string;location:string;coverage:string;brandModel:string;status:ComponentStatus;
  observations:string;ip:string;port:number|null;channel:number|null;cameraBrand:CameraBrand;streamQuality:StreamQuality;rtspPath:string;latitude:number|null;longitude:number|null;streamingEnabled:boolean;
  streamingMode:StreamingMode;username:string;password:string;streamUrl:string;electronicDeviceId:string|null};
/** Dispositivo OBSINT que SIC:COM envió para el Punto (la línea contratada) con su horario y cuántas cámaras tiene asignadas. */
export type ElectronicDevice={id:string;code:string;name:string;product:string|null;commercialStatus:string;schedule:string;cameras:number};
/** "Probar conexión": datos del formulario. password vacía + componentId usa la contraseña guardada de esa cámara. */
export type CameraTestRequest={componentId?:string;ip:string;port:number|null;channel:number|null;cameraBrand:CameraBrand;streamQuality:StreamQuality;rtspPath:string;username:string;password:string};
export type CameraTestOutcome='OK'|'AUTH_FAILED'|'NOT_FOUND'|'UNREACHABLE'|'TIMEOUT'|'PROTOCOL_ERROR';
/** Siempre llega con 200: ok=false trae el motivo en message. codec: 'H.264', 'H.265'… o null. */
/** previewUrl: video en vivo para el formulario (null sin servidor de video); vive unos minutos. */
export type CameraTestResult={ok:boolean;outcome:CameraTestOutcome;message:string;codec:string|null;streamPath:string;elapsedMs:number;previewUrl:string|null};
export type SecElecSummary={total:number;active:number;observed:number;cameras:number;status:'complete'|'warning'|'blocked';meta:string};
/** Lo que entrega Servicios al abrir el módulo. */
export type PointContext={pointId:string;pointName:string;clientName?:string;serviceName?:string;companyName?:string};

const base='/api/secelec';
export const secelecApi={
  /** Lista "Marca" del formulario de cámara. */
  brands:()=>request<BrandOption[]>(`${base}/camera-brands`),
  /** Dispositivos OBSINT del Punto enviados por SIC:COM, para asignarlos a cada cámara. */
  devices:(pointId:string)=>request<ElectronicDevice[]>(`${base}/points/${encodeURIComponent(pointId)}/devices`),
  components:(pointId:string)=>request<SecurityComponent[]>(`${base}/points/${encodeURIComponent(pointId)}/components`),
  create:(pointId:string,body:ComponentSave)=>request<SecurityComponent>(`${base}/points/${encodeURIComponent(pointId)}/components`,{method:'POST',body:JSON.stringify(body)}),
  update:(id:string,body:ComponentSave)=>request<SecurityComponent>(`${base}/components/${encodeURIComponent(id)}`,{method:'PUT',body:JSON.stringify(body)}),
  remove:(id:string)=>request<void>(`${base}/components/${encodeURIComponent(id)}`,{method:'DELETE'}),
  /** Prueba la conexión RTSP de una cámara con los datos del formulario, sin guardarlos. */
  testCamera:(pointId:string,body:CameraTestRequest)=>request<CameraTestResult>(`${base}/points/${encodeURIComponent(pointId)}/cameras/test`,{method:'POST',body:JSON.stringify(body)}),
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
export const streamingModeLabel=(m:StreamingMode)=>({COPY:'Copy (sin recodificar)',TRANSCODE:'Transcodificar'}[m]);
