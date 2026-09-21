import {useEffect,useMemo,useRef,useState} from 'react';
import type {CSSProperties,MouseEvent as ReactMouseEvent} from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Download,
  Eye,
  FileText,
  History,
  Info,
  Link2,
  LockKeyhole,
  Layers3,
  Map as MapIcon,
  MapPin,
  Minus,
  Plus,
  RefreshCcw,
  Save,
  Search,
  Settings2,
  ShieldCheck,
  Upload,
  Users,
  Warehouse,
  Wrench,
} from 'lucide-react';
import {api} from '../api';
import BitacoraConfig from './BitacoraConfig';
import PatrolConfig from './PatrolConfig';
import ConsignasConfig from './ConsignasConfig';

type State='ACTIVE'|'INACTIVE'|'TO_CONFIGURE';
type Row={
  serviceId:string;pointId:string;postId:string;companyId:string|null;
  companyName:string;companyLogoDataUrl?:string|null;
  serviceCode:string;serviceName:string;clientName:string;
  pointCode:string;pointName:string;postCode:string;postName:string;tier:string;
  idAverage:number|null;icAverage:number|null;pendingNews:number;state:State;
};
type Overview={
  windowFrom:string;windowTo:string;executionBasis:string;
  summary:{totalPosts:number;activePosts:number;toConfigurePosts:number;inactivePosts:number;pendingNews:number;icAverage:number|null};
  rows:Row[];
};
type PointRow={
  serviceId:string;pointId:string;companyId:string|null;companyName:string;companyLogoDataUrl?:string|null;
  serviceCode:string;serviceName:string;clientName:string;pointCode:string;pointName:string;
  idAverage:number|null;icAverage:number|null;pendingNews:number;state:State;posts:Row[];
};
type ModuleStatus='complete'|'warning'|'blocked';
type ModuleCard={
  key:'ats'|'puestos'|'bitacora'|'patrullas'|'consignas'|'rrhh'|'rrmm'|'historial';
  title:string;summary:string;meta:string;status:ModuleStatus;blocking:boolean;
  icon:'ats'|'posts'|'bitacora'|'patrullas'|'consignas'|'rrhh'|'rrmm'|'historial';
};
type Snapshot={version:string;progress:number;blockers:number;warnings:number;updatedAt:string;modules:ModuleCard[];nextActions:string[]};
type AtsLinkedPost={location:string;code:string;status:'VINCULADO'|'PENDIENTE'};
type AtsLinkedPatrol={route:string;patrol:string;status:'VINCULADA'|'PENDIENTE'};
type AtsLinkedCheckpoint={code:string;kind:string;linkedTo:string;status:'VINCULADO'|'SIN_VINCULAR'};
type AtsChecklistItem={label:string;status:ModuleStatus};
type AtsHistoryItem={version:string;file:string;date:string;user:string;state:'Vigente'|'Reemplazada'};
type AtsSnapshot={
  fileName:string;version:string;importedAt:string;importedAtLong:string;updatedAt:string;importedBy:string;identifier:string;
  coverage:number;conflicts:number;riskIndex:number;state:'Vigente';linkedPosts:AtsLinkedPost[];linkedPatrols:AtsLinkedPatrol[];
  checkpoints:AtsLinkedCheckpoint[];checklist:AtsChecklistItem[];changes:{adds:number;modifies:number;removes:number};
  observations:string[];history:AtsHistoryItem[];
};

type AtsPackageDto={
  id:string;pointId:string;revisionNo:number;current:boolean;originalFilename:string;atsSchemaVersion?:string|null;packageType?:string|null;
  sourcePtoId?:string|null;sourcePtoCode?:string|null;sourcePtoName?:string|null;modelRevision?:number|null;modelHash?:string|null;
  publicationId?:string|null;publicationVersion?:string|null;publicationStatus?:string|null;publishedAt?:string|null;packageHash?:string|null;archiveSha256:string;riskIndex?:number|null;
  planLevelId?:string|null;planLevelCode?:string|null;planLevelName?:string|null;planOriginalFilename?:string|null;planContentType:string;planWidthPx?:number|null;planHeightPx?:number|null;planSha256?:string|null;
  importedByUsername:string;importedAt:string;
};

type PostSkillSet={attendance:number;accessControl:number;patrol:number;judgement:number;tactical:number;bearing:number;leadership:number;customerService:number};
type PostOperationalConfig={postId:string;postType:'CAA'|'PAT'|'VIG'|'MIX';description:string;atsLocationKey:string;atsLocationLabel:string;atsPackageId?:string|null;atsLocationX?:number|null;atsLocationY?:number|null;skills:PostSkillSet;adjustmentJustification?:string|null;configStatus:'DRAFT'|'CONFIGURED';updatedBy?:string|null};
type CommercialPost={id:string;pointId:string;code:string;name:string;format:string;fhe:number;tier:string;rotationCode?:string|null;cycleLengthDays?:number|null};
type CommercialShift={id:string;postId:string;shiftName:string;startsAt:string;endsAt:string};
type CommercialWeek={posts:CommercialPost[];shifts:CommercialShift[]};
type SkillKey=keyof PostSkillSet;

type View='list'|'config-landing'|'config-ats'|'config-posts'|'config-bitacora'|'config-patrols'|'config-consignas';

const PAGE_SIZE=10;
const TIER_MIN:Record<string,number>={I:6.5,II:7.5,III:8.5,IV:9.5};
const SKILLS:{key:SkillKey;label:string}[]=[
  {key:'attendance',label:'Asistencia'},
  {key:'accessControl',label:'Control de Acceso'},
  {key:'patrol',label:'Patrulla Operativa'},
  {key:'judgement',label:'Criterio Operativo'},
  {key:'tactical',label:'Condición Táctica'},
  {key:'bearing',label:'Porte Cajamarca'},
  {key:'leadership',label:'Liderazgo'},
  {key:'customerService',label:'Atención al Cliente'},
];
const POST_TEMPLATES:Record<'CAA'|'PAT'|'VIG'|'MIX',PostSkillSet>={
  CAA:{attendance:3,accessControl:3,patrol:1,judgement:3,tactical:1,bearing:3,leadership:1,customerService:3},
  PAT:{attendance:2,accessControl:1,patrol:3,judgement:2,tactical:2,bearing:1,leadership:1,customerService:1},
  VIG:{attendance:2,accessControl:1,patrol:1,judgement:2,tactical:2,bearing:2,leadership:1,customerService:1},
  MIX:{attendance:3,accessControl:3,patrol:3,judgement:3,tactical:2,bearing:3,leadership:1,customerService:3},
};

function stateLabel(s:State){return s==='ACTIVE'?'Activo':s==='INACTIVE'?'Inactivo':'Por Configurar'}
function statusLabel(s:ModuleStatus){return s==='complete'?'Completo':s==='warning'?'Observación':'Bloqueante'}
function metric(value:number|null,suffix=''){return value==null?'—':`${value.toFixed(1)}${suffix}`}
function errorMessage(e:unknown){return e instanceof Error?e.message:String(e)}
function csvCell(v:unknown){const s=String(v??'');return `"${s.replaceAll('"','""')}"`}
function average(values:(number|null)[]){const nums=values.filter((v):v is number=>v!=null);return nums.length?nums.reduce((a,b)=>a+b,0)/nums.length:null}
function pointFileName(pointName:string){return `${pointName.replace(/\s+/g,'-')}_v3.ats`}
function pointIdentifier(pointId:string){return `ATS-${pointId.toUpperCase().replace(/[^A-Z0-9]/g,'').slice(0,4)}-${pointId.toUpperCase().replace(/[^A-Z0-9]/g,'').slice(-4)}`}
function pointRiskIndex(point:PointRow){
  const base=6.2 + Math.min(point.posts.length,4)*0.45 + Math.min(point.pendingNews,4)*0.12;
  return Math.min(9.9, Number(base.toFixed(1)));
}
function currentMondayIso(){
  const now=new Date();
  const local=new Date(now.toLocaleString('en-US',{timeZone:'America/Guayaquil'}));
  const day=local.getDay();
  const diff=day===0?-6:1-day;
  local.setDate(local.getDate()+diff);
  const y=local.getFullYear(),m=String(local.getMonth()+1).padStart(2,'0'),d=String(local.getDate()).padStart(2,'0');
  return `${y}-${m}-${d}`;
}
function skillTotal(skills:PostSkillSet){return SKILLS.reduce((sum:number,item)=>sum+skills[item.key],0)}
function skillRuleError(skills:PostSkillSet){
  const values=SKILLS.map(item=>skills[item.key]);
  if(values.some(value=>value<1||value>5))return 'Cada habilidad debe tener entre 1 y 5 puntos.';
  if(values.filter(value=>value===5).length>1)return 'Solo una habilidad puede tener 5 puntos.';
  if(values.filter(value=>value===4).length>2)return 'Solo dos habilidades pueden tener 4 puntos.';
  if(values.reduce((sum,value)=>sum+value,0)>22)return 'La suma total no puede superar 22 puntos.';
  return '';
}
function skillsEqual(a:PostSkillSet,b:PostSkillSet){return SKILLS.every(item=>a[item.key]===b[item.key])}
function hoursBetween(start:string,end:string){return Math.max(0,(new Date(end).getTime()-new Date(start).getTime())/3600000)}

function idTone(value:number|null,tier:string){
  if(value==null)return 'neutral';
  const min=TIER_MIN[tier]??0;
  if(value>=min)return 'good';
  if(value>=min-.5)return 'warn';
  return 'bad';
}
function icTone(value:number|null){
  if(value==null)return 'neutral';
  if(value>=100)return 'good';
  if(value>=90)return 'warn';
  return 'bad';
}
function aggregatePoints(rows:Row[]):PointRow[]{
  const map=new Map<string,PointRow>();
  rows.forEach((row:Row)=>{
    const current=map.get(row.pointId);
    if(current){
      current.posts.push(row);
      return;
    }
    map.set(row.pointId,{serviceId:row.serviceId,pointId:row.pointId,companyId:row.companyId,companyName:row.companyName,companyLogoDataUrl:row.companyLogoDataUrl,serviceCode:row.serviceCode,serviceName:row.serviceName,clientName:row.clientName,pointCode:row.pointCode,pointName:row.pointName,idAverage:null,icAverage:null,pendingNews:0,state:'ACTIVE',posts:[row]});
  });
  return [...map.values()].map((point:PointRow)=>{
    point.idAverage=average(point.posts.map((post:Row)=>post.idAverage));
    point.icAverage=average(point.posts.map((post:Row)=>post.icAverage));
    point.pendingNews=point.posts.reduce((sum:number,post:Row)=>sum+post.pendingNews,0);
    const allInactive=point.posts.every((post:Row)=>post.state==='INACTIVE');
    const anyToConfigure=point.posts.some((post:Row)=>post.state==='TO_CONFIGURE');
    point.state=allInactive?'INACTIVE':anyToConfigure?'TO_CONFIGURE':'ACTIVE';
    point.posts.sort((a:Row,b:Row)=>a.postCode.localeCompare(b.postCode));
    return point;
  }).sort((a:PointRow,b:PointRow)=>a.clientName.localeCompare(b.clientName)||a.pointName.localeCompare(b.pointName));
}
function highestTier(posts:Row[]){
  const order=['I','II','III','IV'];
  return [...posts].sort((a:Row,b:Row)=>order.indexOf(b.tier)-order.indexOf(a.tier))[0]?.tier ?? 'I';
}
function snapshotFor(point:PointRow):Snapshot{
  const totalPosts=point.posts.length;
  const configuredPosts=point.posts.filter((post:Row)=>post.state!=='TO_CONFIGURE').length;
  const highest=highestTier(point.posts);
  const atsStatus:ModuleStatus=point.state==='TO_CONFIGURE'?'warning':'complete';
  const postStatus:ModuleStatus=configuredPosts===totalPosts?'complete':'blocked';
  const bitacoraStatus:ModuleStatus=configuredPosts===totalPosts?'complete':'warning';
  const patrullaStatus:ModuleStatus=point.pendingNews>2?'warning':'complete';
  const consignaStatus:ModuleStatus='complete';
  const rrhhStatus:ModuleStatus=(point.idAverage!=null&&(point.idAverage>=(TIER_MIN[highest]??0)))?'complete':'warning';
  const rrmmStatus:ModuleStatus=point.pendingNews>0?'warning':'complete';
  const historialStatus:ModuleStatus='complete';
  const modules:ModuleCard[]=[
    {key:'ats',title:'ATS',summary:'Plano del punto, importación y vinculación operacional',meta:`${pointFileName(point.pointName)} · vigente`,status:atsStatus,blocking:true,icon:'ats'},
    {key:'puestos',title:'Puestos',summary:'Estructura contractual recibida de SIC: COM',meta:`${configuredPosts}/${totalPosts} puestos con configuración base`,status:postStatus,blocking:true,icon:'posts'},
    {key:'bitacora',title:'Bitácora',summary:'Procedimiento operativo y protocolos por puesto',meta:`${configuredPosts}/${totalPosts} puestos con bitácora definida`,status:bitacoraStatus,blocking:true,icon:'bitacora'},
    {key:'patrullas',title:'Patrullas',summary:'Rutas, hitos y frecuencia de patrullaje',meta:`${Math.max(1,totalPosts-1)}/${Math.max(1,totalPosts)} patrullas configuradas`,status:patrullaStatus,blocking:false,icon:'patrullas'},
    {key:'consignas',title:'Consignas',summary:'Consignas vigentes y alcance por punto o puesto',meta:`${Math.max(3,totalPosts+2)} consignas vigentes`,status:consignaStatus,blocking:false,icon:'consignas'},
    {key:'rrhh',title:'Recursos Humanos',summary:'Cobertura y enlace a asignaciones',meta:`ID 14 días: ${metric(point.idAverage)} · ${totalPosts} puestos`,status:rrhhStatus,blocking:false,icon:'rrhh'},
    {key:'rrmm',title:'Recursos Materiales',summary:'Activos esperados y estado observado en el punto',meta:`${Math.max(totalPosts*2,4)-Math.min(point.pendingNews,2)}/${Math.max(totalPosts*2,4)} activos confirmados`,status:rrmmStatus,blocking:false,icon:'rrmm'},
    {key:'historial',title:'Historial',summary:'Versiones del REGESEP y trazabilidad documental',meta:'Última publicación hace 9 días',status:historialStatus,blocking:false,icon:'historial'},
  ];
  const blockers=modules.filter((module:ModuleCard)=>module.blocking&&module.status==='blocked').length;
  const warnings=modules.filter((module:ModuleCard)=>module.status==='warning').length;
  const progress=Math.round((modules.filter((module:ModuleCard)=>module.status==='complete').length/modules.length)*100);
  const nextActions:string[]=[];
  if(postStatus!=='complete')nextActions.push('Completar la configuración base de todos los puestos del punto.');
  if(bitacoraStatus!=='complete')nextActions.push('Definir o revisar el procedimiento de bitácora en los puestos pendientes.');
  if(atsStatus!=='complete')nextActions.push('Validar que el archivo ATS vigente corresponda a la última versión del punto.');
  if(!nextActions.length)nextActions.push('La configuración base está completa; continuar con refinamientos y control documental.');
  if(rrmmStatus==='warning')nextActions.push('Revisar los activos observados o no confirmados en el punto.');
  return {version:`REGESEP v1.${Math.max(1,totalPosts)}`,progress,blockers,warnings,updatedAt:'09-sept, 18:40',modules,nextActions:nextActions.slice(0,4)};
}
function atsSnapshotFor(point:PointRow):AtsSnapshot{
  const totalPosts=point.posts.length;
  const linkedPosts:AtsLinkedPost[]=point.posts.slice(0,3).map((post:Row,index:number)=>({
    location:index===0?'Acceso Principal':index===1?'Perímetro Norte':'Lobby Principal',
    code:post.postCode,
    status:index===Math.min(2,totalPosts-1)&&point.state==='TO_CONFIGURE'?'PENDIENTE':'VINCULADO',
  }));
  if(!linkedPosts.length){
    linkedPosts.push({location:'Acceso Principal',code:'GGTT01',status:'PENDIENTE'});
  }
  const linkedPatrols:AtsLinkedPatrol[]=[
    {route:'Ruta R-01',patrol:'P01 Perímetro Norte',status:'VINCULADA'},
    {route:'Ruta R-02',patrol:'P02 Interna Nocturna',status:point.pendingNews>0?'PENDIENTE':'VINCULADA'},
  ];
  const checkpoints:AtsLinkedCheckpoint[]=[
    {code:'NFC-001',kind:'Control',linkedTo:'P01',status:'VINCULADO'},
    {code:'NFC-002',kind:'Control',linkedTo:'P01',status:'VINCULADO'},
    {code:'QR-004',kind:'Punto de control',linkedTo:'—',status:point.state==='TO_CONFIGURE'?'SIN_VINCULAR':'VINCULADO'},
  ];
  const pendingPost=linkedPosts.some((row:AtsLinkedPost)=>row.status==='PENDIENTE');
  const pendingPatrol=linkedPatrols.some((row:AtsLinkedPatrol)=>row.status==='PENDIENTE');
  const pendingCheckpoint=checkpoints.some((row:AtsLinkedCheckpoint)=>row.status==='SIN_VINCULAR');
  const checklist:AtsChecklistItem[]=[
    {label:'Archivo .ats cargado',status:'complete'},
    {label:'Versión vigente definida',status:'complete'},
    {label:'Puestos vinculados',status:pendingPost?'warning':'complete'},
    {label:'Patrullas base vinculadas',status:pendingPatrol?'warning':'complete'},
    {label:'Hitos validados',status:pendingCheckpoint?'blocked':'complete'},
    {label:'Sin conflictos pendientes',status:(point.pendingNews>0||pendingCheckpoint)?'warning':'complete'},
  ];
  const completeWeight=checklist.filter((item:AtsChecklistItem)=>item.status==='complete').length;
  const coverage=Math.round((completeWeight/checklist.length)*100);
  const observations:string[]=[];
  if(pendingPatrol)observations.push('Ruta R-02 requiere revisión porque presenta cambios respecto a la última importación.');
  if(pendingPost)observations.push(`${linkedPosts.find((row:AtsLinkedPost)=>row.status==='PENDIENTE')?.code ?? 'Un puesto'} no tiene ubicación vinculada.`);
  if(pendingCheckpoint)observations.push('Existe al menos un hito o punto de control sin vinculación operativa.');
  if(!observations.length)observations.push('No existen observaciones pendientes en la configuración ATS.');
  return {
    fileName:pointFileName(point.pointName),
    version:'v3',
    importedAt:'09-sept · 18:40',
    importedAtLong:'09-sept-2026 18:40',
    updatedAt:'09 sept 2026',
    importedBy:'coord.gye',
    identifier:pointIdentifier(point.pointId),
    riskIndex:pointRiskIndex(point),
    coverage,
    conflicts:observations.length,
    state:'Vigente',
    linkedPosts,
    linkedPatrols,
    checkpoints,
    checklist,
    changes:{adds:2,modifies:1,removes:1},
    observations,
    history:[
      {version:'v3',file:pointFileName(point.pointName),date:'09-sept',user:'coord.gye',state:'Vigente'},
      {version:'v2',file:pointFileName(point.pointName).replace('_v3','_v2'),date:'01-sept',user:'coord.gye',state:'Reemplazada'},
      {version:'v1',file:pointFileName(point.pointName).replace('_v3','_v1'),date:'20-ago',user:'coord.gye',state:'Reemplazada'},
    ],
  };
}
function moduleIcon(icon:ModuleCard['icon']){
  switch(icon){
    case 'ats': return <MapIcon size={18}/>;
    case 'posts': return <MapPin size={18}/>;
    case 'bitacora': return <FileText size={18}/>;
    case 'patrullas': return <ShieldCheck size={18}/>;
    case 'consignas': return <ClipboardList size={18}/>;
    case 'rrhh': return <Users size={18}/>;
    case 'rrmm': return <Warehouse size={18}/>;
    default: return <History size={18}/>;
  }
}
function statusPillClass(status:ModuleStatus){return `ser-status-tag ${status}`}
function atsRowStatusClass(status:'VINCULADO'|'PENDIENTE'|'VINCULADA'|'SIN_VINCULAR'){return status==='VINCULADO'||status==='VINCULADA'?'good':status==='PENDIENTE'?'warn':'bad'}

function AtsViewerMap(){
  return <div className="ats-map-board">
    <div className="ats-map-compass"><span>N</span></div>
    <div className="ats-map-road vertical"/>
    <div className="ats-map-road top"/>
    <div className="ats-map-park"/>
    <div className="ats-map-building main"><span>Telco-City</span></div>
    <div className="ats-map-building lobby"><span>Lobby</span></div>
    <div className="ats-map-building side"/>
    <div className="ats-map-marker access">Acceso Principal</div>
    <div className="ats-map-marker north">Perímetro Norte</div>
    <div className="ats-map-marker south">Perímetro Sur</div>
    <div className="ats-map-marker parking">Parqueo</div>
    <div className="ats-map-perimeter"/>
    <span className="ats-camera c1"/><span className="ats-camera c2"/><span className="ats-camera c3"/><span className="ats-camera c4"/><span className="ats-camera c5"/>
    {Array.from({length:20}).map((_item: unknown,index:number)=><span key={index} className={`ats-tree t${index+1}`}/>)}
  </div>
}

function AtsPage({point,onBack}:{point:PointRow;onBack:()=>void}){
  const synthetic=useMemo(()=>atsSnapshotFor(point),[point]);
  const [current,setCurrent]=useState<AtsPackageDto|null>(null);
  const [history,setHistory]=useState<AtsPackageDto[]>([]);
  const [planUrl,setPlanUrl]=useState('');
  const [loadingAts,setLoadingAts]=useState(true);
  const [uploading,setUploading]=useState(false);
  const [atsError,setAtsError]=useState('');
  const [atsNotice,setAtsNotice]=useState('');
  const [zoom,setZoom]=useState(1);
  const inputRef=useRef<HTMLInputElement|null>(null);
  const planObjectUrlRef=useRef('');

  const replacePlanUrl=(next:string)=>{
    if(planObjectUrlRef.current) URL.revokeObjectURL(planObjectUrlRef.current);
    planObjectUrlRef.current=next;
    setPlanUrl(next);
  };
  const loadPlan=async()=>{
    const blob=await api.atsPlan(point.pointId) as Blob;
    replacePlanUrl(URL.createObjectURL(blob));
  };
  const loadAts=async()=>{
    setLoadingAts(true);setAtsError('');
    try{
      const [pkg,rows]=await Promise.all([api.atsCurrent(point.pointId),api.atsHistory(point.pointId)]) as [AtsPackageDto|null,AtsPackageDto[]];
      setCurrent(pkg);setHistory(rows);
      if(pkg) await loadPlan(); else replacePlanUrl('');
    }catch(error){setAtsError(errorMessage(error))}finally{setLoadingAts(false)}
  };
  useEffect(()=>{void loadAts();return()=>{if(planObjectUrlRef.current)URL.revokeObjectURL(planObjectUrlRef.current)}},[point.pointId]);

  const onFileSelected=async(file?:File)=>{
    if(!file)return;
    if(!file.name.toLowerCase().endsWith('.ats')){setAtsError('Debe seleccionar un archivo con extensión .ats.');return}
    setUploading(true);setAtsError('');setAtsNotice('');
    try{
      const pkg=await api.atsUpload(point.pointId,file) as AtsPackageDto;
      setCurrent(pkg);
      setHistory(await api.atsHistory(point.pointId) as AtsPackageDto[]);
      await loadPlan();
      setZoom(1);
      setAtsNotice(`Archivo ${file.name} importado correctamente. El plano ya está disponible en SGI.`);
    }catch(error){setAtsError(errorMessage(error))}finally{setUploading(false);if(inputRef.current)inputRef.current.value=''}
  };
  const downloadCurrent=async()=>{
    if(!current)return;
    try{
      const blob=await api.atsDownload(point.pointId) as Blob;
      const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download=current.originalFilename;anchor.click();URL.revokeObjectURL(url);
    }catch(error){setAtsError(errorMessage(error))}
  };
  const localDateTime=(value?:string|null)=>{
    if(!value)return '—';
    try{return new Intl.DateTimeFormat('es-EC',{dateStyle:'medium',timeStyle:'short',timeZone:'America/Guayaquil'}).format(new Date(value))}catch{return value}
  };
  const importedShort=current?localDateTime(current.importedAt):'—';
  const version=current?.publicationVersion??(current?`r${current.revisionNo}`:'—');
  const pendingLinks=synthetic.linkedPosts.filter((row:AtsLinkedPost)=>row.status==='PENDIENTE').length + synthetic.linkedPatrols.filter((row:AtsLinkedPatrol)=>row.status==='PENDIENTE').length + synthetic.checkpoints.filter((row:AtsLinkedCheckpoint)=>row.status==='SIN_VINCULAR').length;

  return <div className="services-v01 services-v02 ser-config-landing ats-page">
    <input ref={inputRef} type="file" accept=".ats,application/octet-stream" hidden onChange={event=>void onFileSelected(event.target.files?.[0])}/>
    <div className="ser-breadcrumbs"><button onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración</button><span>/</span><small>ATS</small></div>

    <div className="ats-header">
      <div>
        <div className="ser-title-row"><h2>ATS</h2><span>Plano maestro del punto</span></div>
        <small>Importación y vinculación operacional · el archivo .ats se conserva íntegro y SGI materializa su plano para la configuración del Punto.</small>
      </div>
      <div className="ats-header-actions">
        <button className="ser-action ghost" onClick={()=>inputRef.current?.click()} disabled={uploading}><Upload size={15}/>{uploading?'Importando…':current?'Reemplazar archivo':'Importar archivo'}</button>
        <button className="ser-action ghost" disabled={!history.length}><History size={15}/>Ver historial</button>
        <button className="ser-action primary" disabled={!current}><ShieldCheck size={15}/>Validar vínculos</button>
      </div>
    </div>

    <div className="ats-context-bar panel">
      <div><span>Compañía</span><strong>{point.companyName}</strong></div>
      <div><span>Servicio</span><strong>{point.serviceName}</strong></div>
      <div><span>Cliente</span><strong>{point.clientName}</strong></div>
      <div><span>Estado</span><strong>{stateLabel(point.state)}</strong></div>
      <div><span>Última actualización ATS</span><strong>{current?localDateTime(current.importedAt):'Sin archivo importado'}</strong></div>
    </div>

    {atsError&&<div className="ser-error"><AlertTriangle size={16}/><span>{atsError}</span></div>}
    {atsNotice&&<div className="posts-notice"><Info size={15}/><span>{atsNotice}</span></div>}

    {!current&&!loadingAts?<section className="ser-table-card ats-upload-empty">
      <div className="ats-upload-empty-icon"><MapIcon size={28}/></div>
      <h3>Este Punto todavía no tiene un archivo .ats</h3>
      <p>Importa la publicación ATS del Punto. SGI validará el paquete, leerá su manifiesto y cargará automáticamente el plano incluido.</p>
      <button className="ser-action primary" onClick={()=>inputRef.current?.click()} disabled={uploading}><Upload size={15}/>{uploading?'Importando…':'Seleccionar archivo .ats'}</button>
      <small>Formatos de plano admitidos dentro del paquete: PNG, JPEG y WebP · tamaño máximo del archivo .ats: 20 MB.</small>
    </section>:current&&<>
      <div className="ser-kpis ats-kpis">
        <article><span className="ser-kpi-icon blue"><FileText/></span><div><small>Archivo vigente</small><strong className="ats-kpi-value ats-kpi-file">{current.originalFilename}</strong><em>paquete .ats importado</em></div></article>
        <article><span className="ser-kpi-icon blue"><MapIcon/></span><div><small>Versión publicada</small><strong className="ats-kpi-value">{version}</strong><em>revisión SGI {current.revisionNo}</em></div></article>
        <article><span className="ser-kpi-icon blue"><History/></span><div><small>Última importación</small><strong className="ats-kpi-value ats-kpi-time">{importedShort}</strong><em>por {current.importedByUsername}</em></div></article>
        <article><span className="ser-kpi-icon green"><Link2/></span><div><small>Cobertura de vinculación</small><strong className="ats-kpi-value">{synthetic.coverage}%</strong><em>{pendingLinks} vínculos pendientes</em></div></article>
        <article><span className="ser-kpi-icon red"><AlertTriangle/></span><div><small>Conflictos</small><strong className="ats-kpi-value">{synthetic.conflicts}</strong><em>requieren revisión</em></div></article>
        <article><span className="ser-kpi-icon green"><ShieldCheck/></span><div><small>Estado</small><strong className="ats-kpi-value">Vigente</strong><em>publicación ATS: {current.publicationStatus??'—'}</em></div></article>
        <article><span className="ser-kpi-icon blue"><BarChart3/></span><div><small>Índice de Riesgo de Punto</small><strong className="ats-kpi-value">{current.riskIndex==null?'—':current.riskIndex.toFixed(1)}</strong><em>{current.riskIndex==null?'no informado por este archivo .ats':'importado del archivo .ats'}</em></div></article>
      </div>

      <section className="ser-table-card ats-file-card">
        <header><div><h3>Archivo vigente</h3><span>El archivo se importa y versiona; no se edita dentro de SGI.</span></div><div className="ats-file-actions"><button onClick={()=>document.querySelector('.ats-real-plan')?.scrollIntoView({behavior:'smooth',block:'center'})}><Eye size={15}/>Visualizar</button><button onClick={()=>void downloadCurrent()}><Download size={15}/>Descargar</button><button onClick={()=>inputRef.current?.click()}><RefreshCcw size={15}/>Reemplazar versión</button></div></header>
        <div className="ats-file-meta">
          <div className="ats-meta-item"><span>Archivo</span><strong>{current.originalFilename}</strong></div>
          <div className="ats-meta-item"><span>Versión</span><strong>{version}</strong></div>
          <div className="ats-meta-item"><span>Importado por</span><strong>{current.importedByUsername}</strong></div>
          <div className="ats-meta-item"><span>Fecha de importación</span><strong>{localDateTime(current.importedAt)}</strong></div>
          <div className="ats-meta-item"><span>Punto ATS de origen</span><strong>{current.sourcePtoName??current.sourcePtoCode??'—'}</strong></div>
          <div className="ats-meta-item"><span>Esquema ATS</span><strong>{current.atsSchemaVersion??'—'}</strong></div>
        </div>
      </section>

      <div className="ats-main-grid">
        <section className="ser-table-card ats-viewer-card">
          <header><div><h3>Visor del plano</h3><span>{current.planLevelName??'Plano principal'} · {current.planOriginalFilename??'imagen incluida en .ats'}</span></div><div className="ats-map-tools"><button onClick={()=>setZoom(value=>Math.min(3,Number((value+.2).toFixed(1))))}><Plus size={14}/></button><button onClick={()=>setZoom(value=>Math.max(.4,Number((value-.2).toFixed(1))))}><Minus size={14}/></button><button onClick={()=>setZoom(1)}><MapPin size={14}/>Ajustar</button><button disabled><Layers3 size={14}/>Capas</button></div></header>
          <div className="ats-viewer-wrap ats-real-viewer">{planUrl?<div className="ats-real-plan-scroll"><img className="ats-real-plan" src={planUrl} alt={`Plano ATS de ${point.pointName}`} style={{width:`${zoom*100}%`}}/></div>:<div className="ser-loading">Cargando plano…</div>}</div>
        </section>

        <div className="ats-right-column">
          <section className="ser-table-card ats-link-card">
            <header><div><h3>Vinculación operacional</h3><span>Relación entre el plano y la configuración del punto</span></div></header>
            <div className="ats-link-sections">
              <div><h4>Puestos vinculados</h4><table className="ats-mini-table"><thead><tr><th>Ubicación</th><th>Código</th><th>Estado</th></tr></thead><tbody>{synthetic.linkedPosts.map((row:AtsLinkedPost)=><tr key={`${row.location}-${row.code}`}><td>{row.location}</td><td>{row.code}</td><td><span className={`ats-inline-state ${atsRowStatusClass(row.status)}`}>{row.status==='VINCULADO'?'Vinculado':'Pendiente'}</span></td></tr>)}</tbody></table></div>
              <div><h4>Patrullas vinculadas</h4><table className="ats-mini-table"><thead><tr><th>Ruta</th><th>Patrulla</th><th>Estado</th></tr></thead><tbody>{synthetic.linkedPatrols.map((row:AtsLinkedPatrol)=><tr key={row.route}><td>{row.route}</td><td>{row.patrol}</td><td><span className={`ats-inline-state ${atsRowStatusClass(row.status)}`}>{row.status==='VINCULADA'?'Vinculada':'Pendiente'}</span></td></tr>)}</tbody></table></div>
              <div><h4>Hitos / puntos de control</h4><table className="ats-mini-table"><thead><tr><th>Código</th><th>Tipo</th><th>Vinculado a</th></tr></thead><tbody>{synthetic.checkpoints.map((row:AtsLinkedCheckpoint)=><tr key={row.code}><td>{row.code}</td><td>{row.kind}</td><td><span className={`ats-inline-state ${atsRowStatusClass(row.status)}`}>{row.status==='SIN_VINCULAR'?'Sin vincular':row.linkedTo}</span></td></tr>)}</tbody></table></div>
            </div>
          </section>
          <section className="ser-table-card ats-checklist-card"><header><div><h3>Lista de verificación ATS</h3><span>Control mínimo para dar el módulo por completo</span></div></header><div className="ats-checklist">{synthetic.checklist.map((item:AtsChecklistItem)=><div key={item.label} className="ats-check-row"><span>{item.label}</span><span className={statusPillClass(item.status)}>{statusLabel(item.status)}</span></div>)}</div></section>
        </div>
      </div>

      <section className="ser-table-card ats-history-card">
        <header><div><h3>Historial ATS</h3><span>Cada reemplazo conserva la versión anterior del archivo importado.</span></div></header>
        <div className="ser-table-wrap"><table className="ser-table ats-history-table"><thead><tr><th>Revisión SGI</th><th>Archivo</th><th>Versión ATS</th><th>Importado</th><th>Usuario</th><th>Estado</th></tr></thead><tbody>{history.map((row:AtsPackageDto)=><tr key={row.id}><td>r{row.revisionNo}</td><td>{row.originalFilename}</td><td>{row.publicationVersion??'—'}</td><td>{localDateTime(row.importedAt)}</td><td>{row.importedByUsername}</td><td><span className={`ats-inline-state ${row.current?'good':'neutral'}`}>{row.current?'Vigente':'Reemplazada'}</span></td></tr>)}</tbody></table></div>
      </section>
    </>}
    {loadingAts&&<div className="ser-loading">Cargando configuración ATS…</div>}
  </div>
}

function PostLocationMap({planUrl,x,y,onSelect}:{planUrl:string;x?:number|null;y?:number|null;onSelect:(x:number,y:number)=>void}){
  const hasSelection=x!=null&&y!=null;
  if(!planUrl){
    return <div className="post-map-picker post-map-picker-empty"><div className="post-map-empty"><MapIcon size={26}/><strong>Plano ATS no disponible</strong><span>Importe primero un archivo .ats desde Configuración → ATS para seleccionar la ubicación del Puesto.</span></div></div>;
  }
  const choose=(event:ReactMouseEvent<HTMLDivElement>)=>{
    const image=event.currentTarget.querySelector('img');
    if(!image)return;
    const rect=image.getBoundingClientRect();
    if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)return;
    const nx=Math.max(0,Math.min(1,(event.clientX-rect.left)/rect.width));
    const ny=Math.max(0,Math.min(1,(event.clientY-rect.top)/rect.height));
    onSelect(Number(nx.toFixed(6)),Number(ny.toFixed(6)));
  };
  return <div className="post-map-picker">
    <div className="post-map-real-surface" onClick={choose} role="button" tabIndex={0} aria-label="Seleccione la ubicación del Puesto sobre el plano ATS">
      <img src={planUrl} alt="Plano ATS del Punto" draggable={false}/>
      {hasSelection&&<span className="post-map-real-pin" style={{left:`${x!*100}%`,top:`${y!*100}%`}}><MapPin size={18}/></span>}
      <span className="post-map-click-hint">Haz clic sobre el plano</span>
    </div>
    <div className={`post-map-selection ${hasSelection?'selected':''}`}><MapPin size={14}/><span>{hasSelection?`Ubicación seleccionada · X ${(x!*100).toFixed(1)}% · Y ${(y!*100).toFixed(1)}%`:'Seleccione en el plano la ubicación del puesto'}</span></div>
  </div>;
}

function PostsPage({point,onBack}:{point:PointRow;onBack:()=>void}){
  const [configs,setConfigs]=useState<PostOperationalConfig[]>([]);
  const [commercialWeek,setCommercialWeek]=useState<CommercialWeek|null>(null);
  const [atsPackage,setAtsPackage]=useState<AtsPackageDto|null>(null);
  const [atsPlanUrl,setAtsPlanUrl]=useState('');
  const atsPlanObjectUrlRef=useRef('');
  const [selectedPostId,setSelectedPostId]=useState(point.posts[0]?.postId??'');
  const [draft,setDraft]=useState<PostOperationalConfig|null>(null);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [notice,setNotice]=useState('');
  const [pageError,setPageError]=useState('');
  const [searchPost,setSearchPost]=useState('');

  const setPlanObjectUrl=(url:string)=>{
    if(atsPlanObjectUrlRef.current)URL.revokeObjectURL(atsPlanObjectUrlRef.current);
    atsPlanObjectUrlRef.current=url;
    setAtsPlanUrl(url);
  };
  const load=async()=>{
    setLoading(true);setPageError('');
    try{
      const [configRows,pkg]=await Promise.all([api.postConfigurations(point.pointId),api.atsCurrent(point.pointId)]) as [PostOperationalConfig[],AtsPackageDto|null];
      setConfigs(configRows);
      setAtsPackage(pkg);
      if(pkg){const blob=await api.atsPlan(point.pointId) as Blob;setPlanObjectUrl(URL.createObjectURL(blob))}else setPlanObjectUrl('');
      if(point.companyId){
        const week=await api.assignmentWeek(point.companyId,currentMondayIso(),point.pointId) as CommercialWeek;
        setCommercialWeek(week);
      }
    }catch(error){setPageError(errorMessage(error))}finally{setLoading(false)}
  };
  useEffect(()=>{void load();return()=>{if(atsPlanObjectUrlRef.current)URL.revokeObjectURL(atsPlanObjectUrlRef.current)}},[point.pointId]);

  useEffect(()=>{
    const config=configs.find((item:PostOperationalConfig)=>item.postId===selectedPostId);
    if(config){setDraft({...config,skills:{...config.skills}});return}
    const row=point.posts.find((item:Row)=>item.postId===selectedPostId);
    if(row){
      const type:PostOperationalConfig['postType']=row.postName.toLowerCase().includes('acceso')?'CAA':row.postName.toLowerCase().includes('perímetro')?'PAT':'VIG';
      setDraft({postId:row.postId,postType:type,description:'',atsLocationKey:'',atsLocationLabel:'',atsPackageId:null,atsLocationX:null,atsLocationY:null,skills:{...POST_TEMPLATES[type]},adjustmentJustification:'',configStatus:'DRAFT'});
    }
  },[configs,selectedPostId,point.posts]);

  const filteredPosts=useMemo(()=>{
    const q=searchPost.trim().toLowerCase();
    return point.posts.filter((post:Row)=>!q||post.postCode.toLowerCase().includes(q)||post.postName.toLowerCase().includes(q));
  },[point.posts,searchPost]);
  const selectedRow=point.posts.find((post:Row)=>post.postId===selectedPostId)??point.posts[0];
  const commercialPost=commercialWeek?.posts?.find((post:CommercialPost)=>post.id===selectedPostId);
  const postShifts=(commercialWeek?.shifts??[]).filter((shift:CommercialShift)=>shift.postId===selectedPostId);
  const shiftNames=Array.from(new Set(postShifts.map((shift:CommercialShift)=>shift.shiftName))).join(' / ')||'—';
  const weeklyHours=Math.round(postShifts.reduce((sum:number,shift:CommercialShift)=>sum+hoursBetween(shift.startsAt,shift.endsAt),0));
  const isConfigCurrent=(config:PostOperationalConfig)=>config.configStatus==='CONFIGURED'&&(!atsPackage||(config.atsLocationX!=null&&config.atsLocationY!=null&&config.atsPackageId===atsPackage.id));
  const configuredCount=configs.filter(isConfigCurrent).length;
  const tierMax=highestTier(point.posts);
  const currentTotal=draft?skillTotal(draft.skills):0;
  const currentRuleError=draft?skillRuleError(draft.skills):'';
  const template=draft?POST_TEMPLATES[draft.postType]:POST_TEMPLATES.VIG;
  const adjusted=draft?!skillsEqual(draft.skills,template):false;
  const hasPlanLocation=!!draft&&(atsPackage?(draft.atsLocationX!=null&&draft.atsLocationY!=null&&draft.atsPackageId===atsPackage.id):!!draft.atsLocationKey);
  const canComplete=!!draft&&!currentRuleError&&draft.description.trim().length>0&&hasPlanLocation&&(!adjusted||!!draft.adjustmentJustification?.trim());

  const selectType=(type:PostOperationalConfig['postType'])=>{
    if(!draft)return;
    setDraft({...draft,postType:type,skills:{...POST_TEMPLATES[type]},adjustmentJustification:''});
    setNotice(`Se aplicó la plantilla sugerida para ${type}. Puedes ajustarla respetando la economía de puntos.`);
  };
  const changeSkill=(key:SkillKey,value:number)=>{
    if(!draft)return;
    const next={...draft.skills,[key]:value};
    const error=skillRuleError(next);
    if(error){setNotice(error);return}
    setDraft({...draft,skills:next});
    setNotice('');
  };
  const save=async(status:'DRAFT'|'CONFIGURED')=>{
    if(!draft)return;
    if(status==='CONFIGURED'&&!canComplete){
      setPageError(currentRuleError||(!draft.description.trim()?'La descripción es obligatoria.':!hasPlanLocation?'Debes seleccionar la ubicación del puesto directamente en el plano ATS.':'Los ajustes de habilidades requieren una justificación.'));
      return;
    }
    setSaving(true);setPageError('');setNotice('');
    try{
      const saved=await api.savePostConfiguration(draft.postId,{...draft,configStatus:status}) as PostOperationalConfig;
      setConfigs(previous=>{
        const exists=previous.some((item:PostOperationalConfig)=>item.postId===saved.postId);
        return exists?previous.map((item:PostOperationalConfig)=>item.postId===saved.postId?saved:item):[...previous,saved];
      });
      setDraft({...saved,skills:{...saved.skills}});
      setNotice(status==='CONFIGURED'?'Configuración del puesto guardada correctamente.':'Borrador guardado correctamente.');
    }catch(error){setPageError(errorMessage(error))}finally{setSaving(false)}
  };

  if(!selectedRow){return <div className="services-v01"><div className="ser-empty">Este punto no tiene puestos recibidos desde SIC: COM.</div></div>}

  return <div className="services-v01 services-v02 posts-page">
    <div className="ser-breadcrumbs"><button onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración</button><span>/</span><small>Puestos</small></div>
    <div className="posts-header">
      <div><div className="ser-title-row"><h2>Puestos</h2><span>Configuración operacional del punto</span></div><small>Los datos contractuales se reciben desde SIC: COM; SGI agrega únicamente la configuración operacional.</small></div>
      <button className="ser-refresh" onClick={()=>void load()} disabled={loading}><RefreshCcw size={15}/>{loading?'Actualizando…':'Actualizar'}</button>
    </div>

    <div className="posts-context panel">
      <div><span>Compañía</span><strong>{point.companyName}</strong></div>
      <div><span>Servicio</span><strong>{point.serviceName}</strong></div>
      <div><span>Cliente</span><strong>{point.clientName}</strong></div>
      <div><span>Punto</span><strong>{point.pointName}</strong></div>
      <div><span>Última actualización</span><strong>10 sept 2026</strong></div>
    </div>

    <div className="ser-kpis posts-kpis">
      <article><span className="ser-kpi-icon blue"><ShieldCheck/></span><div><small>Puestos SIC: COM</small><strong>{point.posts.length}</strong><em>total de puestos recibidos</em></div></article>
      <article><span className="ser-kpi-icon green"><ShieldCheck/></span><div><small>Configurados</small><strong>{configuredCount}</strong><em>{point.posts.length?Math.round(configuredCount/point.posts.length*100):0}% del total</em></div></article>
      <article><span className="ser-kpi-icon red"><AlertTriangle/></span><div><small>Pendientes</small><strong>{Math.max(0,point.posts.length-configuredCount)}</strong><em>requieren configuración</em></div></article>
      <article><span className="ser-kpi-icon blue"><BarChart3/></span><div><small>TIER máximo</small><strong>{tierMax}</strong><em>en este punto</em></div></article>
      <article><span className="ser-kpi-icon green"><Link2/></span><div><small>Sincronización SIC: COM</small><strong>Vigente</strong><em>datos contractuales actualizados</em></div></article>
    </div>

    {pageError&&<div className="ser-error"><AlertTriangle size={16}/><span>{pageError}</span></div>}
    {notice&&<div className="posts-notice"><Info size={15}/><span>{notice}</span></div>}

    <div className="posts-workspace">
      <aside className="ser-table-card posts-list-panel">
        <header><div><h3>Puestos del punto</h3><span>Listado recibido desde SIC: COM</span></div></header>
        <div className="posts-list-search"><Search size={15}/><input value={searchPost} onChange={event=>setSearchPost(event.target.value)} placeholder="Buscar puesto por código o nombre…"/></div>
        <div className="posts-list">{filteredPosts.map((post:Row)=>{
          const config=configs.find((item:PostOperationalConfig)=>item.postId===post.postId);
          const complete=!!config&&isConfigCurrent(config);
          return <button key={post.postId} className={selectedPostId===post.postId?'selected':''} onClick={()=>{setSelectedPostId(post.postId);setNotice('');setPageError('')}}>
            <span className="posts-list-icon"><ShieldCheck size={17}/></span>
            <span className="posts-list-copy"><strong>{post.postCode}</strong><small>{post.postName}</small></span>
            <span className="posts-list-tier">TIER {post.tier}</span>
            <span className={`posts-config-state ${complete?'complete':'pending'}`}>{complete?'Completo':'Pendiente'}</span>
          </button>;
        })}</div>
      </aside>

      <div className="posts-detail-card">
        <div className="posts-detail-head"><div><span className="posts-detail-icon"><ShieldCheck size={19}/></span><div><h3>{selectedRow.postCode}</h3><span>{selectedRow.postName}</span></div></div><div><span className={`posts-config-state ${draft&&isConfigCurrent(draft)?'complete':'pending'}`}>{draft&&isConfigCurrent(draft)?'Completo':'Pendiente'}</span><small>TIER {selectedRow.tier}</small></div></div>

        <div className="posts-top-grid">
          <section className="posts-section posts-commercial">
            <header><div><h4>A. Datos recibidos desde SIC: COM</h4><Info size={14}/></div><span>Solo lectura · administrado por SIC: COM</span></header>
            <div className="posts-readonly-grid">
              <div><span>Código</span><strong>{selectedRow.postCode}</strong></div>
              <div><span>Nombre</span><strong>{selectedRow.postName}</strong></div>
              <div><span>TIER</span><strong>{commercialPost?.tier??selectedRow.tier}</strong></div>
              <div><span>Formato</span><strong>{commercialPost?.format??'—'}</strong></div>
              <div><span>Rotación</span><strong>{commercialPost?.rotationCode??'—'}</strong></div>
              <div><span>Turnos</span><strong>{shiftNames}</strong></div>
              <div><span>Horas requeridas</span><strong>{weeklyHours?`${weeklyHours} h/semana`:'—'}</strong></div>
              <div><span>Estado SIC</span><strong className="post-commercial-active">Activo</strong></div>
            </div>
          </section>

          <section className="posts-section posts-sgi-config">
            <header><h4>B. Configuración SGI</h4></header>
            {draft&&<div className="posts-config-form">
              <label><span>Tipo de Puesto</span><select value={draft.postType} onChange={event=>selectType(event.target.value as PostOperationalConfig['postType'])}><option value="CAA">CAA</option><option value="PAT">PAT</option><option value="VIG">VIG</option><option value="MIX">MIX</option></select></label>
              <label className="posts-description"><span>Descripción</span><textarea value={draft.description} maxLength={600} onChange={event=>setDraft({...draft,description:event.target.value})} placeholder="Describe brevemente la función operacional del puesto…"/></label>
              <div className="posts-config-status"><span>Estado de configuración</span><b className={isConfigCurrent(draft)?'complete':'pending'}>{isConfigCurrent(draft)?'Completo':'Pendiente'}</b></div>
              <div className="posts-map-field"><div className="posts-map-title"><div><strong>Ubicación en el plano</strong><span>{atsPackage?'Seleccione directamente en el plano importado desde el archivo .ats.':'No existe un archivo .ats vigente para este Punto.'}</span></div><Info size={14}/></div><PostLocationMap planUrl={atsPlanUrl} x={draft.atsLocationX} y={draft.atsLocationY} onSelect={(x:number,y:number)=>setDraft({...draft,atsPackageId:atsPackage?.id??null,atsLocationX:x,atsLocationY:y,atsLocationKey:'XY',atsLocationLabel:`Ubicación en plano (${(x*100).toFixed(1)}%, ${(y*100).toFixed(1)}%)`})}/></div>
            </div>}
          </section>
        </div>

        {draft&&<div className="posts-lower-grid">
          <section className="posts-section posts-skills-section">
            <header><div><h4>C. Habilidades requeridas</h4><span>Define el nivel mínimo requerido para ocupar este puesto.</span></div></header>
            <div className="posts-skills-layout">
              <div className="posts-skill-list">
                <div className="posts-skill-head"><span>#</span><span>Habilidad</span><span>Nivel requerido</span></div>
                {SKILLS.map((skill,index:number)=>{
                  const value=draft.skills[skill.key];
                  return <div className="posts-skill-row" key={skill.key}>
                    <span>{index+1}</span><strong>{skill.label}</strong>
                    <div className="gamified-slider"><div className="gamified-slider-labels">{[1,2,3,4,5].map(level=><span key={level}>{level}</span>)}</div><input aria-label={`${skill.label}: ${value}`} type="range" min="1" max="5" step="1" value={value} style={{'--slider-progress':`${((value-1)/4)*100}%`} as CSSProperties} onChange={event=>changeSkill(skill.key,Number(event.target.value))}/><b style={{left:`calc(${((value-1)/4)*100}% - ${((value-1)/4)*18}px)`}}>{value}</b></div>
                  </div>;
                })}
              </div>
              <aside className="posts-skill-rules">
                <div className={`posts-total ${currentTotal>22?'bad':''}`}><span>Σ</span><div><strong>Total actual: {currentTotal} / 22</strong><small>Puntos asignados en habilidades</small></div></div>
                <div className="posts-rules-box"><strong>Reglas institucionales</strong><ul><li>Mínimo 1 punto por habilidad</li><li>Máximo 1 habilidad con 5 puntos</li><li>Máximo 2 habilidades con 4 puntos</li><li>Suma máxima total: 22 puntos</li></ul></div>
                <div className="posts-template-note"><Info size={15}/><span>Plantilla sugerida por tipo de puesto, ajustable con justificación.</span></div>
                {adjusted&&<label className="posts-justification"><span>Justificación del ajuste</span><textarea value={draft.adjustmentJustification??''} onChange={event=>setDraft({...draft,adjustmentJustification:event.target.value})} placeholder="Explique por qué este puesto requiere niveles distintos a la plantilla sugerida…" maxLength={600}/></label>}
              </aside>
            </div>
          </section>

          <section className="posts-section posts-permissions">
            <header><div><h4>D. Permisos requeridos</h4><span>Permisos o acreditaciones especiales</span></div></header>
            <div className="posts-coming-soon"><LockKeyhole size={30}/><strong>Próximamente</strong><span>Aquí se configurarán permisos o acreditaciones especiales, por ejemplo del Ministerio del Interior.</span></div>
          </section>
        </div>}

        <div className="posts-actions"><button className="ser-action ghost" onClick={()=>{const original=configs.find((item:PostOperationalConfig)=>item.postId===selectedPostId);if(original)setDraft({...original,skills:{...original.skills}})}}>Cancelar cambios</button><button className="ser-action ghost" onClick={()=>void save('DRAFT')} disabled={saving}><Save size={15}/>{saving?'Guardando…':'Guardar borrador'}</button><button className="ser-action primary" onClick={()=>void save('CONFIGURED')} disabled={saving||!canComplete}><Save size={15}/>{saving?'Guardando…':'Guardar configuración'}</button></div>
      </div>
    </div>
  </div>
}

function ConfigurationLanding({point,onBack,onOpenModule}:{point:PointRow;onBack:()=>void;onOpenModule:(module:ModuleCard['key'])=>void}){
  const snapshot=useMemo(()=>snapshotFor(point),[point]);
  const stateClass=point.state.toLowerCase();
  return <div className="services-v01 services-v02 ser-config-landing">
    <div className="ser-breadcrumbs"><button onClick={onBack}><ArrowLeft size={15}/>Volver a Servicios</button><span>/</span><small>Configuración del Punto</small></div>

    <div className="ser-config-hero">
      <div>
        <div className="ser-title-row"><h2>{point.pointName}</h2><span>Configuración del Punto</span></div>
        <small>{point.clientName} · {point.serviceName} · {point.companyName}</small>
      </div>
      <div className="ser-hero-actions">
        <span className={`ser-state ${stateClass}`}><i/>{stateLabel(point.state)}</span>
        <button className="ser-refresh"><RefreshCcw size={15}/>Actualizar</button>
      </div>
    </div>

    <div className="ser-config-context panel">
      <div><span>Compañía</span><strong>{point.companyName}</strong></div>
      <div><span>Servicio</span><strong>{point.serviceName}</strong><small>{point.serviceCode}</small></div>
      <div><span>Cliente</span><strong>{point.clientName}</strong></div>
      <div><span>Punto</span><strong>{point.pointName}</strong><small>{point.pointCode}</small></div>
      <div><span>Puestos</span><strong>{point.posts.length}</strong></div>
      <div><span>Versión</span><strong>{snapshot.version}</strong><small>Actualizado {snapshot.updatedAt}</small></div>
    </div>

    <div className="ser-kpis ser-config-kpis">
      <article><span className="ser-kpi-icon green"><Settings2/></span><div><small>Configuración</small><strong>{snapshot.progress}%</strong><em>avance total</em></div></article>
      <article><span className="ser-kpi-icon red"><AlertTriangle/></span><div><small>Bloqueantes</small><strong>{snapshot.blockers}</strong><em>impiden activar completamente</em></div></article>
      <article><span className="ser-kpi-icon amber"><Wrench/></span><div><small>Observaciones</small><strong>{snapshot.warnings}</strong><em>requieren revisión</em></div></article>
      <article><span className="ser-kpi-icon blue"><BarChart3/></span><div><small>Módulos</small><strong>{snapshot.modules.length}</strong><em>configurables desde esta landing</em></div></article>
    </div>

    <div className="ser-config-layout">
      <section className="ser-table-card ser-checklist-card">
        <header><div><h3>Checklist de configuración</h3><span>Solo configuración · no muestra actividad operativa</span></div></header>
        <div className="ser-checklist">
          {snapshot.modules.map((module:ModuleCard)=>{
            const openable=module.key==='ats'||module.key==='puestos'||module.key==='bitacora'||module.key==='patrullas'||module.key==='consignas';
            return <button key={module.key} className={`ser-check-row ${module.status} ${openable?'':'disabled'}`} onClick={()=>openable&&onOpenModule(module.key)}>
              <span className="ser-check-main">{moduleIcon(module.icon)}<b>{module.title}</b></span>
              <span className="ser-check-meta">{module.meta}</span>
              <span className={statusPillClass(module.status)}>{statusLabel(module.status)}</span>
            </button>;
          })}
        </div>
      </section>

      <aside className="ser-config-side">
        <section className="ser-table-card ser-next-card">
          <header><div><h3>Próximas acciones</h3><span>Resumen ejecutivo</span></div></header>
          <div className="ser-next-list">{snapshot.nextActions.map((item:string,idx:number)=><div key={idx}><b>{idx+1}</b><span>{item}</span></div>)}</div>
        </section>
        <section className="ser-table-card ser-post-summary-card">
          <header><div><h3>Puestos del punto</h3><span>Vista rápida</span></div></header>
          <div className="ser-post-summary-list">{point.posts.map((post:Row)=><div key={post.postId} className="ser-post-summary-row"><div><strong>{post.postCode}</strong><span>{post.postName}</span></div><small>TIER {post.tier}</small><span className={`ser-state ${post.state.toLowerCase()}`}><i/>{stateLabel(post.state)}</span></div>)}</div>
        </section>
      </aside>
    </div>

    <section className="ser-modules-grid">
      {snapshot.modules.map((module:ModuleCard)=>{
        const openable=module.key==='ats'||module.key==='puestos'||module.key==='bitacora'||module.key==='patrullas'||module.key==='consignas';
        return <article key={module.key} className={`ser-module-card ${module.status}`}>
          <div className="ser-module-top"><span className={`ser-module-icon ${module.status}`}>{moduleIcon(module.icon)}</span><div><h4>{module.title}</h4><p>{module.summary}</p></div></div>
          <div className="ser-module-bottom"><strong>{module.meta}</strong><div><span className={statusPillClass(module.status)}>{statusLabel(module.status)}</span><button onClick={()=>openable&&onOpenModule(module.key)} disabled={!openable}>{openable?'Abrir':'Próximamente'}</button></div></div>
        </article>;
      })}
    </section>
  </div>
}

export default function Services(){
  const [data,setData]=useState<Overview|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [company,setCompany]=useState('');
  const [client,setClient]=useState('');
  const [state,setState]=useState('');
  const [query,setQuery]=useState('');
  const [page,setPage]=useState(0);
  const [selectedPointId,setSelectedPointId]=useState('');
  const [view,setView]=useState<View>('list');

  const reload=async()=>{
    setLoading(true);
    setError('');
    try{setData(await api.serviceOverview())}catch(e){setError(errorMessage(e))}finally{setLoading(false)}
  };
  useEffect(()=>{void reload()},[]);

  const points=useMemo(()=>aggregatePoints(data?.rows??[]),[data]);
  const companies=useMemo(()=>Array.from(new Set(points.map((row:PointRow)=>row.companyName))).sort(),[points]);
  const clients=useMemo(()=>Array.from(new Set(points.map((row:PointRow)=>row.clientName))).sort(),[points]);
  const filtered=useMemo(()=>{
    const q=query.trim().toLowerCase();
    return points.filter((row:PointRow)=>(!company||row.companyName===company)&&(!client||row.clientName===client)&&(!state||row.state===state)&&(!q||[row.serviceName,row.serviceCode,row.clientName,row.pointName,row.pointCode,row.companyName].some((value:string)=>value.toLowerCase().includes(q))));
  },[points,company,client,state,query]);
  const pages=Math.max(1,Math.ceil(filtered.length/PAGE_SIZE));
  useEffect(()=>{setPage(0)},[company,client,state,query]);
  useEffect(()=>{if(page>=pages)setPage(pages-1)},[page,pages]);
  const visible=filtered.slice(page*PAGE_SIZE,(page+1)*PAGE_SIZE);
  const selectedPoint=points.find((point:PointRow)=>point.pointId===selectedPointId) ?? null;

  const clearFilters=()=>{setCompany('');setClient('');setState('');setQuery('')};
  const openConfiguration=(pointId:string)=>{setSelectedPointId(pointId);setView('config-landing')};
  const backToList=()=>{setSelectedPointId('');setView('list')};
  const exportCsv=()=>{
    const head=['Compañía','Servicio','Cliente','Punto','Puestos','ID Promedio','IC Promedio','Novedades','Estado'];
    const body=filtered.map((row:PointRow)=>[row.companyName,row.serviceName,row.clientName,row.pointName,row.posts.length,row.idAverage??'',row.icAverage==null?'':`${row.icAverage}%`,row.pendingNews,stateLabel(row.state)]);
    const csv='\ufeff'+[head,...body].map((line:unknown[])=>line.map(csvCell).join(',')).join('\r\n');
    const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'});
    const url=URL.createObjectURL(blob);
    const link=document.createElement('a');
    link.href=url;
    link.download='SGI_Servicios_Puntos.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  if(selectedPoint&&view==='config-ats'){
    return <AtsPage point={selectedPoint} onBack={()=>setView('config-landing')}/>;
  }
  if(selectedPoint&&view==='config-posts'){
    return <PostsPage point={selectedPoint} onBack={()=>setView('config-landing')}/>;
  }
  if(selectedPoint&&view==='config-bitacora'){
    return <BitacoraConfig point={selectedPoint} onBack={()=>setView('config-landing')}/>;
  }
  if(selectedPoint&&view==='config-patrols'){
    return <PatrolConfig point={selectedPoint} onBack={()=>setView('config-landing')}/>;
  }
  if(selectedPoint&&view==='config-consignas'){
    return <ConsignasConfig point={selectedPoint} onBack={()=>setView('config-landing')}/>;
  }
  if(selectedPoint&&view==='config-landing'){
    return <ConfigurationLanding point={selectedPoint} onBack={backToList} onOpenModule={(module:ModuleCard['key'])=>{if(module==='ats')setView('config-ats');if(module==='puestos')setView('config-posts');if(module==='bitacora')setView('config-bitacora');if(module==='patrullas')setView('config-patrols');if(module==='consignas')setView('config-consignas')}}/>;
  }

  return <div className="services-v01 services-v02">
    <div className="ser-titlebar">
      <div><div className="ser-title-row"><h2>Servicios</h2><span>Listado maestro operacional</span></div><small>Vista a nivel de Cliente · Punto · acciones de Operación y Configuración</small></div>
      <button className="ser-refresh" onClick={()=>void reload()} disabled={loading}><RefreshCcw size={15}/>{loading?'Actualizando…':'Actualizar'}</button>
    </div>

    <div className="ser-kpis">
      <article><span className="ser-kpi-icon blue"><MapPin/></span><div><small>Puntos activos</small><strong>{points.filter((point:PointRow)=>point.state==='ACTIVE').length}</strong><em>de {points.length} puntos</em></div></article>
      <article><span className="ser-kpi-icon amber"><Settings2/></span><div><small>Por Configurar</small><strong>{points.filter((point:PointRow)=>point.state==='TO_CONFIGURE').length}</strong><em>requieren completar configuración</em></div></article>
      <article><span className="ser-kpi-icon red"><AlertTriangle/></span><div><small>Novedades pendientes</small><strong>{points.reduce((sum:number,point:PointRow)=>sum+point.pendingNews,0)}</strong><em>pendientes de revisión</em></div></article>
      <article><span className="ser-kpi-icon green"><BarChart3/></span><div><small>IC promedio</small><strong>{metric(average(points.map((point:PointRow)=>point.icAverage)),'%')}</strong><em>últimas 2 semanas</em></div></article>
    </div>

    <div className="ser-filterbar">
      <label><span>Compañía</span><select value={company} onChange={e=>setCompany(e.target.value)}><option value="">Todas las compañías</option>{companies.map((item:string)=><option key={item}>{item}</option>)}</select></label>
      <label><span>Cliente</span><select value={client} onChange={e=>setClient(e.target.value)}><option value="">Todos los clientes</option>{clients.map((item:string)=><option key={item}>{item}</option>)}</select></label>
      <label><span>Estado</span><select value={state} onChange={e=>setState(e.target.value)}><option value="">Todos los estados</option><option value="ACTIVE">Activo</option><option value="TO_CONFIGURE">Por Configurar</option><option value="INACTIVE">Inactivo</option></select></label>
      <label className="ser-search"><span className="sr-only">Buscar</span><Search size={16}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar cliente o punto…"/></label>
      <button className="ser-clear" onClick={clearFilters}><RefreshCcw size={15}/>Limpiar filtros</button>
    </div>

    {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>void reload()}>Reintentar</button></div>}

    <section className="ser-table-card">
      <header><div><h3>Servicios <b>({filtered.length})</b></h3><span>Vista a nivel de Cliente · Punto</span></div><button onClick={exportCsv} disabled={!filtered.length}><Download size={15}/>Exportar</button></header>
      <div className="ser-table-wrap">
        <table className="ser-table ser-point-table">
          <thead><tr><th>Compañía</th><th>Servicio</th><th>Cliente</th><th>Punto</th><th>Puestos</th><th>ID Promedio</th><th>IC Promedio</th><th>Novedades</th><th>Estado</th><th>Operación</th><th>Configuración</th></tr></thead>
          <tbody>
            {loading&&!data?<tr><td colSpan={11}><div className="ser-loading">Cargando Servicios…</div></td></tr>:visible.length?visible.map((row:PointRow)=>{
              const tier=highestTier(row.posts);
              return <tr key={row.pointId}>
                <td><div className="ser-company">{row.companyLogoDataUrl?<img src={row.companyLogoDataUrl} alt=""/>:<span>{row.companyName.slice(0,1)}</span>}<strong>{row.companyName}</strong></div></td>
                <td><div className="ser-primary"><strong>{row.serviceName}</strong><small>{row.serviceCode}</small></div></td>
                <td>{row.clientName}</td>
                <td><div className="ser-primary"><strong>{row.pointName}</strong><small>{row.pointCode}</small></div></td>
                <td><div className="ser-primary"><strong>{row.posts.length} puesto{row.posts.length===1?'':'s'}</strong><small>TIER máx. {tier}</small></div></td>
                <td><span className={`ser-metric ${idTone(row.idAverage,tier)}`}><i/>{metric(row.idAverage)}</span></td>
                <td><span className={`ser-metric ${icTone(row.icAverage)}`}><i/>{metric(row.icAverage,'%')}</span></td>
                <td><span className={`ser-news ${row.pendingNews?'pending':'zero'}`}>{row.pendingNews}</span></td>
                <td><span className={`ser-state ${row.state.toLowerCase()}`}><i/>{stateLabel(row.state)}</span></td>
                <td><button className="ser-action ghost" disabled title="Operación queda temporalmente en stand by"><Eye size={15}/>Operación</button></td>
                <td><button className="ser-action primary" onClick={()=>openConfiguration(row.pointId)}><Settings2 size={15}/>Configuración</button></td>
              </tr>;
            }):<tr><td colSpan={11}><div className="ser-empty">No existen puntos que coincidan con los filtros.</div></td></tr>}
          </tbody>
        </table>
      </div>
      <footer><span>Mostrando {visible.length} de {filtered.length} puntos</span><div className="ser-pagination"><button onClick={()=>setPage((current:number)=>Math.max(0,current-1))} disabled={page===0}><ChevronLeft size={16}/></button><b>{page+1}</b><span>de {pages}</span><button onClick={()=>setPage((current:number)=>Math.min(pages-1,current+1))} disabled={page>=pages-1}><ChevronRight size={16}/></button></div></footer>
    </section>
  </div>;
}
