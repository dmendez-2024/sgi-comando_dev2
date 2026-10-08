import {useEffect, useMemo, useState, type ChangeEvent, type ReactNode} from 'react';
import {
  AlertTriangle, CalendarClock, CheckCircle2, CircleAlert, ClipboardCheck, Clock3,
  FileText, ImagePlus, Info, MapPin, OctagonAlert, Save, Send, ShieldAlert,
  Trash2, UserCheck, Users, X
} from 'lucide-react';
import {INCIDENT_TAXONOMY} from '../data/incidentTaxonomy';
import {api} from '../api';

export type IncidentCategory='SERVICE'|'SECURITY'|'ADMINISTRATIVE';
export type IncidentSeverity='INFORMATIVE'|'MINOR'|'MODERATE'|'MAJOR'|'CRITICAL';
export type IncidentLifecycle='DRAFT'|'FINALIZED';
export type AttachmentMeta={id:string;name:string;size:number;type:string};
export type LocationOption={clientId:string;client:string;pointId:string;point:string;postId:string;post:string;companyId:string|null;company:string;companyCode:string;city:string};

export type IncidentRecord={
  id:string;
  code:string;
  title:string;
  category:IncidentCategory|'';
  subcategory:string;
  incidentType:string;
  severity:IncidentSeverity|'';
  client:string;
  clientId?:string;
  point:string;
  pointId?:string;
  post:string;
  postId?:string;
  company:string;
  companyId?:string;
  companyCode:string;
  city:string;
  collaboratorIds:string[];
  collaboratorNames:string[];
  description:string;
  descriptionImages:AttachmentMeta[];
  resolution:string;
  resolutionImages:AttachmentMeta[];
  sanction:boolean;
  sanctionDescription:string;
  absenceMode:''|'PROGRAMMED'|'EFFECTIVE';
  targetShiftId:string;
  targetShiftLabel:string;
  replacementEmployeeId:string;
  replacementEmployeeName:string;
  status:IncidentLifecycle;
  createdAt:string;
  updatedAt:string;
};

type Props={
  initial?:IncidentRecord|null;
  nextCode:string;
  locations:LocationOption[];
  locationsLoading:boolean;
  locationsError:string;
  onRetryLocations:()=>void;
  onCancel:()=>void;
  onSave:(record:IncidentRecord)=>void;
};

type DemoPerson={
  id:string;name:string;role:string;companyCode:string;lastPoint:string;lastPost:string;lastWorkedAt:string;
  phone:string;francosWorked6m:number;
  lat:number;lng:number;busy:Set<string>;
};

type CollaboratorRow={employeeId:string;fullName:string;roleCode:string;postName:string;lastAt:string;source:'EXECUTION'|'PUBLISHED_SHIFT'|'CURRENT_SHIFT'|'UPCOMING_SHIFT'};

const CATEGORY_META:Record<IncidentCategory,{label:string;icon:ReactNode;description:string}>={
  SERVICE:{label:'Servicio',icon:<ClipboardCheck size={24}/>,description:'Cumplimiento del servicio y desempeño operativo.'},
  SECURITY:{label:'Seguridad',icon:<ShieldAlert size={24}/>,description:'Eventos que afectan personas, bienes o instalaciones.'},
  ADMINISTRATIVE:{label:'Administrativo',icon:<FileText size={24}/>,description:'Asuntos administrativos, logísticos o tecnológicos.'}
};

const SEVERITIES:{value:IncidentSeverity;label:string;tone:string;icon:ReactNode}[]=[
  {value:'INFORMATIVE',label:'Informativo',tone:'informative',icon:<Info size={26}/>},
  {value:'MINOR',label:'Menor',tone:'minor',icon:<CheckCircle2 size={26}/>},
  {value:'MODERATE',label:'Moderado',tone:'moderate',icon:<AlertTriangle size={26}/>},
  {value:'MAJOR',label:'Mayor',tone:'major',icon:<CircleAlert size={26}/>},
  {value:'CRITICAL',label:'Crítico',tone:'critical',icon:<OctagonAlert size={26}/>},
];

const POINT_COORDS:Record<string,{lat:number;lng:number}>={
  'Portería Principal':{lat:-2.1701,lng:-79.9224},
  'Mall del Sol':{lat:-2.1567,lng:-79.8927},
  'Bodega 4':{lat:-2.1814,lng:-79.8458},
  'Local Centro':{lat:-2.1903,lng:-79.8890},
  'Planta Norte':{lat:-2.1355,lng:-79.8715},
  'Sucursal Centro':{lat:-2.1918,lng:-79.8875},
  'Patio 3':{lat:-2.2052,lng:-79.9074},
  'Acceso Sur':{lat:-2.2339,lng:-79.8974},
  'Muelle Sur':{lat:-3.2675,lng:-79.9588},
  'Torre A':{lat:-2.1725,lng:-79.9011},
};

const PEOPLE:DemoPerson[]=[
  {id:'e01',name:'Carlos Rojas',role:'Agente',companyCode:'GAL',lastPoint:'Muelle Sur',lastPost:'Cuarto Técnico',lastWorkedAt:'2026-09-26T19:00:00-05:00',phone:'099 410 2381',francosWorked6m:2,lat:-3.2675,lng:-79.9588,busy:new Set(['next2'])},
  {id:'e02',name:'Pedro Vera',role:'Agente',companyCode:'GAL',lastPoint:'Muelle Sur',lastPost:'Acceso Muelle Sur',lastWorkedAt:'2026-09-25T07:00:00-05:00',phone:'098 527 6140',francosWorked6m:5,lat:-3.2677,lng:-79.9591,busy:new Set(['current'])},
  {id:'e03',name:'María González',role:'Agente',companyCode:'GAL',lastPoint:'Muelle Sur',lastPost:'Cuarto Técnico',lastWorkedAt:'2026-09-24T19:00:00-05:00',phone:'096 882 3415',francosWorked6m:3,lat:-3.2674,lng:-79.9585,busy:new Set()},
  {id:'e04',name:'Luis Pérez',role:'Agente',companyCode:'GAL',lastPoint:'Local Centro',lastPost:'Puesto Principal',lastWorkedAt:'2026-09-26T07:00:00-05:00',phone:'099 735 2048',francosWorked6m:6,lat:-2.1903,lng:-79.8890,busy:new Set(['next1'])},
  {id:'e05',name:'Diego Torres',role:'Agente',companyCode:'GAL',lastPoint:'Sucursal Centro',lastPost:'Puesto 1',lastWorkedAt:'2026-09-25T19:00:00-05:00',phone:'098 311 7642',francosWorked6m:4,lat:-2.1918,lng:-79.8875,busy:new Set()},
  {id:'e06',name:'José Mendoza',role:'Agente',companyCode:'GAL',lastPoint:'Patio 3',lastPost:'Control Patio 3',lastWorkedAt:'2026-09-23T07:00:00-05:00',phone:'096 447 1820',francosWorked6m:1,lat:-2.2052,lng:-79.9074,busy:new Set(['previous'])},
  {id:'e07',name:'Andrés Vega',role:'Agente',companyCode:'GAL',lastPoint:'Bodega 4',lastPost:'Bodega 4',lastWorkedAt:'2026-09-22T19:00:00-05:00',phone:'099 256 7301',francosWorked6m:2,lat:-2.1814,lng:-79.8458,busy:new Set(['next2'])},
  {id:'e08',name:'Natalia León',role:'Supervisora',companyCode:'GAL',lastPoint:'Planta Norte',lastPost:'Control Principal',lastWorkedAt:'2026-09-21T07:00:00-05:00',phone:'098 602 1945',francosWorked6m:0,lat:-2.1355,lng:-79.8715,busy:new Set(['current','next1'])},
  {id:'e09',name:'Luis García',role:'Agente',companyCode:'GAL',lastPoint:'Portería Principal',lastPost:'Control de Acceso Principal',lastWorkedAt:'2026-09-20T19:00:00-05:00',phone:'096 915 3270',francosWorked6m:7,lat:-2.1701,lng:-79.9224,busy:new Set()},
  {id:'e10',name:'Ana Torres',role:'Agente',companyCode:'SISA',lastPoint:'Mall del Sol',lastPost:'Patrulla Perimetral',lastWorkedAt:'2026-09-26T07:00:00-05:00',phone:'099 803 4516',francosWorked6m:3,lat:-2.1567,lng:-79.8927,busy:new Set()},
  {id:'e11',name:'Jorge Ruiz',role:'Agente',companyCode:'SISA',lastPoint:'Acceso Sur',lastPost:'Cerco Perimetral',lastWorkedAt:'2026-09-24T07:00:00-05:00',phone:'098 744 2603',francosWorked6m:2,lat:-2.2339,lng:-79.8974,busy:new Set(['next1'])},
  {id:'e12',name:'Carla Méndez',role:'Agente',companyCode:'SISA',lastPoint:'Torre A',lastPost:'Sala de Control',lastWorkedAt:'2026-09-25T07:00:00-05:00',phone:'096 530 8174',francosWorked6m:4,lat:-2.1725,lng:-79.9011,busy:new Set()},
  {id:'e13',name:'Roberto Silva',role:'Agente',companyCode:'GAL',lastPoint:'Muelle Sur',lastPost:'Acceso Muelle Sur',lastWorkedAt:'2026-09-18T07:00:00-05:00',phone:'099 672 1438',francosWorked6m:5,lat:-3.2678,lng:-79.9590,busy:new Set()},
  {id:'e14',name:'Mónica Paz',role:'Escolta',companyCode:'GAL',lastPoint:'Sucursal Centro',lastPost:'Puesto 1',lastWorkedAt:'2026-09-17T19:00:00-05:00',phone:'098 219 6057',francosWorked6m:1,lat:-2.1918,lng:-79.8875,busy:new Set()},
];

const SHIFT_OPTIONS=[
  {id:'NEXT_1',label:'Siguiente turno · 27/09/2026 19:00–28/09/2026 07:00',slot:'next1',previous:'current'},
  {id:'NEXT_2',label:'Segundo turno · 28/09/2026 07:00–19:00',slot:'next2',previous:'next1'},
];
const CURRENT_SHIFT={id:'CURRENT',label:'Turno en curso · 27/09/2026 07:00–19:00',slot:'current',previous:'previous'};

const emptyRecord=(nextCode:string):IncidentRecord=>({
  id:'',code:nextCode,title:'',category:'',subcategory:'',incidentType:'',severity:'',client:'',clientId:'',point:'',pointId:'',post:'',postId:'',company:'',companyId:'',companyCode:'',city:'',
  collaboratorIds:[],collaboratorNames:[],description:'',descriptionImages:[],resolution:'',resolutionImages:[],sanction:false,
  sanctionDescription:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:'',
  status:'DRAFT',createdAt:'',updatedAt:''
});

const absenceModeFromIncidentType=(incidentType:string)=>incidentType==='Inasistencia programada'?'PROGRAMMED':incidentType==='Inasistencia efectiva'?'EFFECTIVE':'';
const uniq=<T,>(values:T[])=>Array.from(new Set(values));
const fmtDate=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(value));

function distanceKm(a:{lat:number;lng:number},b:{lat:number;lng:number}){
  const r=6371; const rad=(x:number)=>x*Math.PI/180; const dLat=rad(b.lat-a.lat); const dLng=rad(b.lng-a.lng);
  const h=Math.sin(dLat/2)**2+Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLng/2)**2;
  return 2*r*Math.asin(Math.sqrt(h));
}

export default function IncidentNotificationPanel({initial,nextCode,locations,locationsLoading,locationsError,onRetryLocations,onCancel,onSave}:Props){
  const [form,setForm]=useState<IncidentRecord>(initial?{...emptyRecord(nextCode),...structuredClone(initial)}:emptyRecord(nextCode));
  const [error,setError]=useState('');
  const [fileError,setFileError]=useState('');
  const [collaborators,setCollaborators]=useState<CollaboratorRow[]>([]);
  const [collaboratorsLoading,setCollaboratorsLoading]=useState(false);
  const [collaboratorsError,setCollaboratorsError]=useState('');
  const [collaboratorsRefresh,setCollaboratorsRefresh]=useState(0);

  useEffect(()=>{
    if(!form.pointId){setCollaborators([]);setCollaboratorsError('');setCollaboratorsLoading(false);return;}
    let current=true;
    setCollaborators([]);
    setCollaboratorsLoading(true);
    setCollaboratorsError('');
    void api.operationCollaborators(form.pointId).then(rows=>{if(current)setCollaborators(rows as CollaboratorRow[])})
      .catch(()=>{if(current)setCollaboratorsError('No se pudo cargar el personal de este Punto.')})
      .finally(()=>{if(current)setCollaboratorsLoading(false)});
    return ()=>{current=false};
  },[form.pointId,collaboratorsRefresh]);

  const clients=useMemo(()=>Array.from(new Map(locations.map(x=>[x.clientId,{id:x.clientId,name:x.client}])).values()),[locations]);
  const points=useMemo(()=>Array.from(new Map(locations.filter(x=>x.clientId===form.clientId).map(x=>[x.pointId,{id:x.pointId,name:x.point}])).values()),[locations,form.clientId]);
  const posts=useMemo(()=>locations.filter(x=>x.pointId===form.pointId).map(x=>({id:x.postId,name:x.post})),[locations,form.pointId]);
  const subcategories=form.category?Object.keys(INCIDENT_TAXONOMY[form.category]):[];
  const incidentTypes=form.category&&form.subcategory?(INCIDENT_TAXONOMY[form.category][form.subcategory]??[]):[];
  const absenceMode=absenceModeFromIncidentType(form.incidentType);

  const selectedLocation=useMemo(()=>locations.find(x=>x.clientId===form.clientId&&x.pointId===form.pointId&&(form.postId?x.postId===form.postId:true)),[locations,form.clientId,form.pointId,form.postId]);
  const targetShift=absenceMode==='EFFECTIVE'?CURRENT_SHIFT:SHIFT_OPTIONS.find(x=>x.id===form.targetShiftId);

  const replacementCandidates=useMemo(()=>{
    if(!absenceMode||!form.point||!selectedLocation||!targetShift)return [];
    const target=POINT_COORDS[form.point]??{lat:0,lng:0};
    return PEOPLE.filter(p=>p.companyCode===selectedLocation.companyCode)
      .filter(p=>!form.collaboratorIds.includes(p.id))
      .filter(p=>!p.busy.has(targetShift.slot)&&!p.busy.has(targetShift.previous))
      .map(p=>{
        const samePost=!!form.post&&p.lastPoint===form.point&&p.lastPost===form.post;
        const samePoint=p.lastPoint===form.point;
        const group=samePost?1:samePoint?2:3;
        const km=group===3?distanceKm(target,{lat:p.lat,lng:p.lng}):0;
        return {person:p,group,km};
      })
      .sort((a,b)=>a.group-b.group||(a.group===3?a.km-b.km:a.person.name.localeCompare(b.person.name,'es')));
  },[absenceMode,form.point,form.post,form.collaboratorIds,selectedLocation,targetShift]);

  function patch<K extends keyof IncidentRecord>(key:K,value:IncidentRecord[K]){setForm(prev=>({...prev,[key]:value}));}
  function chooseCategory(value:IncidentCategory){setForm(prev=>({...prev,category:value,subcategory:'',incidentType:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));}
  function chooseClient(value:string){const loc=locations.find(x=>x.clientId===value);setForm(prev=>({...prev,clientId:value,client:loc?.client??'',pointId:'',point:'',postId:'',post:'',collaboratorIds:[],collaboratorNames:[],companyId:'',company:'',companyCode:'',city:'',replacementEmployeeId:'',replacementEmployeeName:''}));}
  function choosePoint(value:string){
    const loc=locations.find(x=>x.clientId===form.clientId&&x.pointId===value);
    setForm(prev=>({...prev,pointId:value,point:loc?.point??'',postId:'',post:'',collaboratorIds:[],collaboratorNames:[],companyId:loc?.companyId??'',company:loc?.company??'',companyCode:loc?.companyCode??'',city:loc?.city??'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function choosePost(value:string){const loc=locations.find(x=>x.pointId===form.pointId&&x.postId===value);setForm(prev=>({...prev,postId:value,post:loc?.post??''}));}
  function toggleCollaborator(person:CollaboratorRow){
    setForm(prev=>{
      const selected=prev.collaboratorIds.includes(person.employeeId);
      return {...prev,collaboratorIds:selected?prev.collaboratorIds.filter(x=>x!==person.employeeId):[...prev.collaboratorIds,person.employeeId],collaboratorNames:selected?prev.collaboratorNames.filter(x=>x!==person.fullName):[...prev.collaboratorNames,person.fullName]};
    });
  }
  function chooseSubcategory(value:string){
    setForm(prev=>({...prev,subcategory:value,incidentType:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseIncidentType(value:string){
    const mode=absenceModeFromIncidentType(value);
    setForm(prev=>({...prev,incidentType:value,absenceMode:mode,targetShiftId:mode==='EFFECTIVE'?CURRENT_SHIFT.id:'',targetShiftLabel:mode==='EFFECTIVE'?CURRENT_SHIFT.label:'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseShift(value:string){
    const shift=SHIFT_OPTIONS.find(x=>x.id===value);
    setForm(prev=>({...prev,targetShiftId:value,targetShiftLabel:shift?.label??'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseReplacement(person:DemoPerson){setForm(prev=>({...prev,replacementEmployeeId:person.id,replacementEmployeeName:person.name}));}
  function addImages(key:'descriptionImages'|'resolutionImages',event:ChangeEvent<HTMLInputElement>){
    const files=Array.from(event.target.files??[]); event.target.value=''; setFileError('');
    const invalid=files.find(file=>!file.type.startsWith('image/')); if(invalid){setFileError('Solo se pueden adjuntar imágenes.');return;}
    const current=form[key]; const remaining=5-current.length;
    if(remaining<=0){setFileError('El máximo es 5 imágenes por sección.');return;}
    if(files.length>remaining)setFileError(`Solo se agregaron ${remaining} imagen(es): el máximo es 5 por sección.`);
    const metas=files.slice(0,remaining).map(file=>({id:crypto.randomUUID(),name:file.name,size:file.size,type:file.type}));
    patch(key,[...current,...metas] as IncidentRecord[typeof key]);
  }
  function removeImage(key:'descriptionImages'|'resolutionImages',id:string){patch(key,form[key].filter(x=>x.id!==id) as IncidentRecord[typeof key]);}
  function validateFinal(){
    if(!form.title.trim())return 'Ingrese un título.';
    if(!form.category)return 'Seleccione una categoría.';
    if(!form.subcategory)return 'Seleccione una subcategoría.';
    if(!form.incidentType)return 'Seleccione el tipo de incidente.';
    if(!form.severity)return 'Seleccione una criticidad.';
    if(!form.client||!form.point)return 'Cliente y Punto son obligatorios.';
    if(!form.collaboratorIds.length)return 'Seleccione uno o más colaboradores involucrados.';
    if(!form.description.trim())return 'Ingrese la descripción del incidente.';
    if(absenceMode==='PROGRAMMED'&&!form.targetShiftId)return 'Seleccione uno de los próximos dos turnos.';
    if(absenceMode&&!form.replacementEmployeeId)return 'Seleccione un Agente disponible para la reasignación.';
    if(!form.resolution.trim())return 'Ingrese la resolución antes de finalizar.';
    if(form.sanction&&!form.sanctionDescription.trim())return 'Describa la sanción cuando marca que sí genera sanción.';
    return '';
  }
  function submit(status:IncidentLifecycle){
    setError('');
    if(status==='FINALIZED'){const validation=validateFinal();if(validation){setError(validation);return;}}
    const now=new Date().toISOString();
    const location=selectedLocation;
    onSave({...form,id:form.id||crypto.randomUUID(),code:form.code||nextCode,status,absenceMode,companyId:location?.companyId??form.companyId,company:location?.company??form.company,companyCode:location?.companyCode??form.companyCode,city:location?.city??form.city,createdAt:form.createdAt||now,updatedAt:now});
  }

  return <aside className="csl-detail-card csl-incident-panel" aria-label="Notificación de Incidente">
    <div className="csl-incident-head">
      <div className="csl-incident-brand"><span className="csl-incident-brand-icon" aria-hidden="true"><AlertTriangle size={24}/></span><div><span>Incidentes</span><h3>Notificación de Incidente</h3><small>{initial?`${initial.code} · Editar`:'Nuevo caso operativo'}</small></div></div>
      <button type="button" className="csl-icon-button" aria-label="Cerrar" onClick={onCancel}><X size={18}/></button>
    </div>

    <div className="csl-incident-scroll">
      <Field label="Título *"><input value={form.title} maxLength={140} onChange={e=>patch('title',e.target.value)} placeholder="Título del incidente"/></Field>

      <section className="csl-incident-section"><SectionTitle step="1" title="Categoría"/><div className="csl-incident-categories">{(Object.keys(CATEGORY_META) as IncidentCategory[]).map(value=>{const meta=CATEGORY_META[value];return <button type="button" key={value} className={form.category===value?'selected':''} onClick={()=>chooseCategory(value)}><span className="csl-category-symbol" aria-hidden="true">{meta.icon}</span><strong>{meta.label}</strong><small>{meta.description}</small><i aria-hidden="true"/></button>})}</div></section>

      <section className="csl-incident-section"><SectionTitle step="2" title="Subcategoría"/><Field label=""><select value={form.subcategory} disabled={!form.category} onChange={e=>chooseSubcategory(e.target.value)}><option value="">{form.category?'Seleccione una subcategoría':'Seleccione primero una categoría'}</option>{subcategories.map(x=><option key={x} value={x}>{x}</option>)}</select></Field></section>

      <section className="csl-incident-section"><SectionTitle step="3" title="Incidente"/><Field label=""><select value={form.incidentType} disabled={!form.subcategory} onChange={e=>chooseIncidentType(e.target.value)}><option value="">{form.subcategory?'Seleccione el incidente':'Seleccione primero una subcategoría'}</option>{incidentTypes.map(x=><option key={x} value={x}>{x}</option>)}</select></Field>{form.category==='SERVICE'&&form.subcategory==='Asistencia y Puntualidad'&&<p className="csl-incident-hint">Según el Excel vigente, Inasistencia programada e Inasistencia efectiva son tipos de incidente dentro de Asistencia y Puntualidad; ambos activan el flujo especial de cobertura/reasignación.</p>}</section>

      <section className="csl-incident-section"><SectionTitle step="4" title="Criticidad"/><div className="csl-severity-grid">{SEVERITIES.map(item=><button key={item.value} type="button" className={`${item.tone} ${form.severity===item.value?'selected':''}`} aria-pressed={form.severity===item.value} onClick={()=>patch('severity',item.value)}><span className="csl-severity-icon" aria-hidden="true">{item.icon}</span><strong>{item.label}</strong></button>)}</div></section>

      <section className="csl-incident-section"><SectionTitle step="5" title="Ubicación"/>{locationsError&&<div className="csl-incident-error"><AlertTriangle size={16}/>{locationsError}<button type="button" onClick={onRetryLocations}>Reintentar</button></div>}<div className="csl-incident-grid two"><Field label="Cliente *"><select value={form.clientId??''} disabled={locationsLoading||!!locationsError} onChange={e=>chooseClient(e.target.value)}><option value="">{locationsLoading?'Cargando clientes…':locationsError?'Clientes no disponibles':clients.length?'Seleccione cliente':'No hay clientes en su alcance'}</option>{clients.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field><Field label="Punto *"><select value={form.pointId??''} disabled={!form.clientId||locationsLoading||!!locationsError} onChange={e=>choosePoint(e.target.value)}><option value="">{form.clientId?'Seleccione punto':'Seleccione cliente primero'}</option>{points.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field></div><Field label="Puesto (opcional)"><select value={form.postId??''} disabled={!form.pointId||locationsLoading||!!locationsError} onChange={e=>choosePost(e.target.value)}><option value="">Todos / No aplica</option>{posts.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field></section>

      <section className="csl-incident-section"><SectionTitle step="6" title="Colaboradores involucrados"/><p className="csl-incident-hint">Personas con actividad confirmada en los últimos 14 días o con turnos publicados en este Punto hasta los próximos 14 días. Cada persona aparece una sola vez.</p><div className="csl-collaborator-list">{form.pointId&&collaborators.map(person=><label key={person.employeeId} className={form.collaboratorIds.includes(person.employeeId)?'selected':''}><input type="checkbox" checked={form.collaboratorIds.includes(person.employeeId)} onChange={()=>toggleCollaborator(person)}/><span><strong>{person.fullName}</strong><small>{person.roleCode||'Personal'} · {person.source==='EXECUTION'?'Actividad confirmada':person.source==='CURRENT_SHIFT'?'Turno en curso':person.source==='UPCOMING_SHIFT'?'Turno próximo':'Turno publicado'}: {fmtDate(person.lastAt)} · {person.postName}</small></span></label>)}{form.pointId&&collaboratorsLoading&&<div className="csl-inline-empty"><Users size={17}/>Cargando colaboradores del Punto…</div>}{form.pointId&&collaboratorsError&&<div className="csl-inline-empty"><AlertTriangle size={17}/>{collaboratorsError}<button type="button" onClick={()=>setCollaboratorsRefresh(value=>value+1)}>Reintentar</button></div>}{form.pointId&&!collaboratorsLoading&&!collaboratorsError&&!collaborators.length&&<div className="csl-inline-empty"><Users size={17}/>No hay actividad confirmada ni turnos publicados para este Punto en el período consultado.</div>}{!form.pointId&&<div className="csl-inline-empty"><MapPin size={17}/>Seleccione Cliente y Punto para obtener la lista.</div>}</div></section>

      <section className="csl-incident-section"><SectionTitle step="7" title="Descripción"/><Field label="Descripción *"><textarea value={form.description} onChange={e=>patch('description',e.target.value)} placeholder="Describa lo ocurrido con el mayor detalle posible…"/></Field><ImagePicker label="Evidencia de descripción" items={form.descriptionImages} onAdd={e=>addImages('descriptionImages',e)} onRemove={id=>removeImage('descriptionImages',id)}/></section>

      {absenceMode&&<section className="csl-incident-section csl-reassignment-section"><SectionTitle step="8" title="Cobertura / Reasignación"/><div className="csl-special-flow"><CalendarClock size={18}/><div><strong>{absenceMode==='PROGRAMMED'?'Inasistencia Programada':'Inasistencia Efectiva'}</strong><span>{absenceMode==='PROGRAMMED'?'Debe seleccionarse uno de los siguientes dos turnos.':'Se utiliza automáticamente el turno actualmente en curso.'}</span></div></div>{absenceMode==='PROGRAMMED'?<Field label="Turno a cubrir *"><select value={form.targetShiftId} onChange={e=>chooseShift(e.target.value)}><option value="">Seleccione turno</option>{SHIFT_OPTIONS.map(x=><option key={x.id} value={x.id}>{x.label}</option>)}</select></Field>:<div className="csl-current-shift"><Clock3 size={16}/>{CURRENT_SHIFT.label}</div>}<div className="csl-reassignment-title"><UserCheck size={17}/><div><strong>Agentes Disponibles para Reasignación</strong><small>Prelación: mismo Puesto → mismo Punto → misma Compañía por cercanía geográfica.</small></div></div><div className="csl-candidate-list">{targetShift&&replacementCandidates.map(({person,group,km})=><button type="button" key={person.id} className={form.replacementEmployeeId===person.id?'selected':''} onClick={()=>chooseReplacement(person)}><span className={`rank rank-${group}`}>{group}</span><div><strong>{person.name}</strong><small className="candidate-contact">Tel. {person.phone} · <b>Francos Trabajados: {person.francosWorked6m}</b> (últ. 6 meses)</small><small>{group===1?`Libre · mismo Puesto (${person.lastPost})`:group===2?`Libre · mismo Punto (${person.lastPost})`:`Libre · misma Compañía · ${km.toFixed(1)} km desde último Punto`}</small></div><i/></button>)}{!targetShift&&<div className="csl-inline-empty"><CalendarClock size={17}/>Seleccione el turno para calcular disponibilidad.</div>}{targetShift&&!replacementCandidates.length&&<div className="csl-inline-empty"><Users size={17}/>No hay Agentes disponibles con la regla de turno + turno previo.</div>}</div><p className="csl-incident-hint">Programada: libre en el turno a cubrir y en el turno previo. Efectiva: libre ahora y en el turno previo.</p></section>}

      <section className="csl-incident-section"><SectionTitle step={absenceMode?'9':'8'} title="Resolución"/><Field label="Resolución"><textarea value={form.resolution} onChange={e=>patch('resolution',e.target.value)} placeholder="Detalle cómo se resolvió o gestionó el incidente…"/></Field><ImagePicker label="Evidencia de resolución" items={form.resolutionImages} onAdd={e=>addImages('resolutionImages',e)} onRemove={id=>removeImage('resolutionImages',id)}/></section>

      <section className="csl-incident-section"><SectionTitle step={absenceMode?'10':'9'} title="Sanción"/><div className="csl-sanction-choice"><span>¿Genera sanción?</span><label><input type="radio" name="sanction" checked={!form.sanction} onChange={()=>patch('sanction',false)}/>No</label><label><input type="radio" name="sanction" checked={form.sanction} onChange={()=>patch('sanction',true)}/>Sí</label></div>{form.sanction&&<Field label="Descripción de la sanción *"><textarea value={form.sanctionDescription} onChange={e=>patch('sanctionDescription',e.target.value)} placeholder="Describa la sanción propuesta…"/></Field>}</section>

      {fileError&&<div className="csl-incident-warning"><AlertTriangle size={16}/>{fileError}</div>}
      {error&&<div className="csl-incident-error"><AlertTriangle size={16}/>{error}</div>}
    </div>

    <div className="csl-incident-footer"><button type="button" className="draft" onClick={()=>submit('DRAFT')}><Save size={16}/>Guardar borrador</button><button type="button" className="finalize" onClick={()=>submit('FINALIZED')}><Send size={16}/>{initial?.status==='FINALIZED'?'Guardar finalizado':'Finalizar'}</button></div>
  </aside>
}

function SectionTitle({step,title}:{step:string;title:string}){return <div className="csl-section-title"><span>{step}</span><strong>{title}</strong></div>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="csl-incident-field">{label&&<span>{label}</span>}{children}</label>}
function ImagePicker({label,items,onAdd,onRemove}:{label:string;items:AttachmentMeta[];onAdd:(event:ChangeEvent<HTMLInputElement>)=>void;onRemove:(id:string)=>void}){return <div className="csl-image-picker"><div><strong>{label}</strong><span>{items.length}/5 imágenes</span></div><label className={items.length>=5?'disabled':''}><ImagePlus size={16}/>Adjuntar imágenes<input type="file" accept="image/*" multiple disabled={items.length>=5} onChange={onAdd}/></label>{items.length>0&&<div className="csl-image-list">{items.map(item=><div key={item.id}><span><CheckCircle2 size={14}/>{item.name}<small>{Math.max(1,Math.round(item.size/1024))} KB</small></span><button type="button" aria-label={`Quitar ${item.name}`} onClick={()=>onRemove(item.id)}><Trash2 size={14}/></button></div>)}</div>}</div>}
