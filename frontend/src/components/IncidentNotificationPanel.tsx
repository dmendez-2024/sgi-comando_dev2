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
  onSave:(record:IncidentRecord)=>Promise<IncidentRecord>;
};

type ReplacementCandidate={employeeId:string;fullName:string;roleCode:string;group:number;lastPoint:string;lastPost:string};
type CoverageShift={id:string;startsAt:string;endsAt:string;candidates:ReplacementCandidate[]};
type CoverageOptions={currentShift:CoverageShift|null;nextShifts:CoverageShift[]};

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

const emptyRecord=(nextCode:string):IncidentRecord=>({
  id:'',code:nextCode,title:'',category:'',subcategory:'',incidentType:'',severity:'',client:'',clientId:'',point:'',pointId:'',post:'',postId:'',company:'',companyId:'',companyCode:'',city:'',
  collaboratorIds:[],collaboratorNames:[],description:'',descriptionImages:[],resolution:'',resolutionImages:[],sanction:false,
  sanctionDescription:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:'',
  status:'DRAFT',createdAt:'',updatedAt:''
});

const absenceModeFromIncidentType=(incidentType:string)=>incidentType==='Inasistencia programada'?'PROGRAMMED':incidentType==='Inasistencia efectiva'?'EFFECTIVE':'';
const uniq=<T,>(values:T[])=>Array.from(new Set(values));
const fmtDate=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(value));

const fmtShift=(shift:CoverageShift,prefix:string)=>{
  const date=(value:string)=>new Intl.DateTimeFormat('es-EC',{timeZone:'America/Guayaquil',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value));
  return `${prefix} · ${date(shift.startsAt)}–${date(shift.endsAt)}`;
};

export default function IncidentNotificationPanel({initial,nextCode,locations,locationsLoading,locationsError,onRetryLocations,onCancel,onSave}:Props){
  const [form,setForm]=useState<IncidentRecord>(initial?{...emptyRecord(nextCode),...structuredClone(initial)}:emptyRecord(nextCode));
  const [error,setError]=useState('');
  const [fileError,setFileError]=useState('');
  const [saving,setSaving]=useState(false);
  const [collaborators,setCollaborators]=useState<CollaboratorRow[]>([]);
  const [collaboratorsLoading,setCollaboratorsLoading]=useState(false);
  const [collaboratorsError,setCollaboratorsError]=useState('');
  const [collaboratorsRefresh,setCollaboratorsRefresh]=useState(0);
  const [coverage,setCoverage]=useState<CoverageOptions|null>(null);
  const [coverageLoading,setCoverageLoading]=useState(false);
  const [coverageError,setCoverageError]=useState('');
  const [coverageRefresh,setCoverageRefresh]=useState(0);


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

  useEffect(()=>{
    if(!form.pointId||!form.postId){setCoverage(null);setCoverageError('');setCoverageLoading(false);return;}
    let current=true;
    setCoverage(null);setCoverageLoading(true);setCoverageError('');
    void api.operationCoverageOptions(form.pointId,form.postId).then(value=>{if(current)setCoverage(value as CoverageOptions)})
      .catch(()=>{if(current)setCoverageError('No se pudieron consultar los turnos y agentes de este Puesto.')})
      .finally(()=>{if(current)setCoverageLoading(false)});
    return ()=>{current=false};
  },[form.pointId,form.postId,coverageRefresh]);

  const clients=useMemo(()=>Array.from(new Map(locations.map(x=>[x.clientId,{id:x.clientId,name:x.client}])).values()),[locations]);
  const points=useMemo(()=>Array.from(new Map(locations.filter(x=>x.clientId===form.clientId).map(x=>[x.pointId,{id:x.pointId,name:x.point}])).values()),[locations,form.clientId]);
  const posts=useMemo(()=>locations.filter(x=>x.pointId===form.pointId).map(x=>({id:x.postId,name:x.post})),[locations,form.pointId]);
  const subcategories=form.category?Object.keys(INCIDENT_TAXONOMY[form.category]):[];
  const incidentTypes=form.category&&form.subcategory?(INCIDENT_TAXONOMY[form.category][form.subcategory]??[]):[];
  const absenceMode=absenceModeFromIncidentType(form.incidentType);

  const selectedLocation=useMemo(()=>locations.find(x=>x.clientId===form.clientId&&x.pointId===form.pointId&&(form.postId?x.postId===form.postId:true)),[locations,form.clientId,form.pointId,form.postId]);
  const targetShift=absenceMode==='EFFECTIVE'?coverage?.currentShift:coverage?.nextShifts.find(x=>x.id===form.targetShiftId);
  const replacementCandidates=(targetShift?.candidates??[]).filter(person=>!form.collaboratorIds.includes(person.employeeId));

  function patch<K extends keyof IncidentRecord>(key:K,value:IncidentRecord[K]){setForm(prev=>({...prev,[key]:value}));}
  function chooseCategory(value:IncidentCategory){setForm(prev=>({...prev,category:value,subcategory:'',incidentType:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));}
  function chooseClient(value:string){const loc=locations.find(x=>x.clientId===value);setForm(prev=>({...prev,clientId:value,client:loc?.client??'',pointId:'',point:'',postId:'',post:'',collaboratorIds:[],collaboratorNames:[],companyId:'',company:'',companyCode:'',city:'',replacementEmployeeId:'',replacementEmployeeName:'',targetShiftId:'',targetShiftLabel:''}));}
  function choosePoint(value:string){
    const loc=locations.find(x=>x.clientId===form.clientId&&x.pointId===value);
    setForm(prev=>({...prev,pointId:value,point:loc?.point??'',postId:'',post:'',collaboratorIds:[],collaboratorNames:[],companyId:loc?.companyId??'',company:loc?.company??'',companyCode:loc?.companyCode??'',city:loc?.city??'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function choosePost(value:string){const loc=locations.find(x=>x.pointId===form.pointId&&x.postId===value);setForm(prev=>({...prev,postId:value,post:loc?.post??'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));}
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
    setForm(prev=>({...prev,incidentType:value,absenceMode:mode,targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseShift(value:string){
    const shift=coverage?.nextShifts.find(x=>x.id===value);
    setForm(prev=>({...prev,targetShiftId:value,targetShiftLabel:shift?fmtShift(shift,'Turno a cubrir'):'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseReplacement(person:ReplacementCandidate){setForm(prev=>({...prev,replacementEmployeeId:person.employeeId,replacementEmployeeName:person.fullName}));}
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
    if(absenceMode==='PROGRAMMED'&&!targetShift)return 'Seleccione uno de los próximos dos turnos.';
    if(absenceMode==='EFFECTIVE'&&!targetShift)return 'No hay un turno en curso para el Puesto seleccionado.';
    if(absenceMode&&form.replacementEmployeeId&&!replacementCandidates.some(person=>person.employeeId===form.replacementEmployeeId))return 'Seleccione un agente que siga disponible para este turno.';
    if(absenceMode&&!form.replacementEmployeeId)return 'Seleccione un Agente disponible para la reasignación.';
    if(!form.resolution.trim())return 'Ingrese la resolución antes de finalizar.';
    if(form.sanction&&!form.sanctionDescription.trim())return 'Describa la sanción cuando marca que sí genera sanción.';
    return '';
  }
  async function submit(status:IncidentLifecycle){
    if(saving)return;
    setError('');
    if(status==='FINALIZED'){const validation=validateFinal();if(validation){setError(validation);return;}}
    const now=new Date().toISOString();
    const location=selectedLocation;
    setSaving(true);
    try{
      const saved=await onSave({...form,code:form.code||nextCode,status,absenceMode,targetShiftId:targetShift?.id??form.targetShiftId,targetShiftLabel:targetShift?fmtShift(targetShift,'Turno a cubrir'):form.targetShiftLabel,companyId:location?.companyId??form.companyId,company:location?.company??form.company,companyCode:location?.companyCode??form.companyCode,city:location?.city??form.city,createdAt:form.createdAt||now,updatedAt:now});
      setForm(saved);
    }catch(error){setError(error instanceof Error?`No se pudo guardar el incidente: ${error.message}`:'No se pudo guardar el incidente.');}
    finally{setSaving(false);}
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

      <section className="csl-incident-section"><SectionTitle step="4" title="Criticidad"/><div className="csl-severity-grid">{SEVERITIES.map(item=><button key={item.value} type="button" className={`${item.tone} ${form.severity===item.value?'selected':''}`} aria-pressed={form.severity===item.value} onClick={()=>patch('severity',item.value)}><span className="csl-severity-icon" aria-hidden="true">{item.icon}</span><strong>{item.label}</strong><i className="csl-severity-dot" aria-hidden="true"/></button>)}</div></section>

      <section className="csl-incident-section"><SectionTitle step="5" title="Ubicación"/>{locationsError&&<div className="csl-incident-error"><AlertTriangle size={16}/>{locationsError}<button type="button" onClick={onRetryLocations}>Reintentar</button></div>}<div className="csl-incident-grid two"><Field label="Cliente *"><select value={form.clientId??''} disabled={locationsLoading||!!locationsError} onChange={e=>chooseClient(e.target.value)}><option value="">{locationsLoading?'Cargando clientes…':locationsError?'Clientes no disponibles':clients.length?'Seleccione cliente':'No hay clientes en su alcance'}</option>{clients.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field><Field label="Punto *"><select value={form.pointId??''} disabled={!form.clientId||locationsLoading||!!locationsError} onChange={e=>choosePoint(e.target.value)}><option value="">{form.clientId?'Seleccione punto':'Seleccione cliente primero'}</option>{points.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field></div><Field label={absenceMode?'Puesto *':'Puesto (opcional)'}><select value={form.postId??''} disabled={!form.pointId||locationsLoading||!!locationsError} onChange={e=>choosePost(e.target.value)}><option value="">Todos / No aplica</option>{posts.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></Field></section>

      <section className="csl-incident-section"><SectionTitle step="6" title="Colaboradores involucrados"/><p className="csl-incident-hint">Personas con actividad confirmada en los últimos 14 días o con turnos publicados en este Punto hasta los próximos 14 días. Cada persona aparece una sola vez.</p><div className="csl-collaborator-list">{form.pointId&&collaborators.map(person=><label key={person.employeeId} className={form.collaboratorIds.includes(person.employeeId)?'selected':''}><input type="checkbox" checked={form.collaboratorIds.includes(person.employeeId)} onChange={()=>toggleCollaborator(person)}/><span><strong>{person.fullName}</strong><small>{person.roleCode||'Personal'} · {person.source==='EXECUTION'?'Actividad confirmada':person.source==='CURRENT_SHIFT'?'Turno en curso':person.source==='UPCOMING_SHIFT'?'Turno próximo':'Turno publicado'}: {fmtDate(person.lastAt)} · {person.postName}</small></span></label>)}{form.pointId&&collaboratorsLoading&&<div className="csl-inline-empty"><Users size={17}/>Cargando colaboradores del Punto…</div>}{form.pointId&&collaboratorsError&&<div className="csl-inline-empty"><AlertTriangle size={17}/>{collaboratorsError}<button type="button" onClick={()=>setCollaboratorsRefresh(value=>value+1)}>Reintentar</button></div>}{form.pointId&&!collaboratorsLoading&&!collaboratorsError&&!collaborators.length&&<div className="csl-inline-empty"><Users size={17}/>No hay actividad confirmada ni turnos publicados para este Punto en el período consultado.</div>}{!form.pointId&&<div className="csl-inline-empty"><MapPin size={17}/>Seleccione Cliente y Punto para obtener la lista.</div>}</div></section>

      <section className="csl-incident-section csl-description-section"><SectionTitle step="7" title="Descripción *"/><Field label=""><textarea aria-label="Descripción requerida" value={form.description} onChange={e=>patch('description',e.target.value)} placeholder="Describa lo ocurrido con el mayor detalle posible…"/></Field><ImagePicker label="Evidencia de descripción" items={form.descriptionImages} onAdd={e=>addImages('descriptionImages',e)} onRemove={id=>removeImage('descriptionImages',id)}/></section>

      {absenceMode&&<section className="csl-incident-section csl-reassignment-section">
        <SectionTitle step="8" title="Cobertura / Reasignación"/>
        <div className="csl-special-flow"><CalendarClock size={18}/><div>
          <strong>{absenceMode==='PROGRAMMED'?'Inasistencia Programada':'Inasistencia Efectiva'}</strong>
          <span>{absenceMode==='PROGRAMMED'?'Seleccione uno de los dos próximos turnos registrados para este Puesto.':'Se utiliza el turno actualmente en curso para este Puesto.'}</span>
        </div></div>
        {!form.postId&&<div className="csl-inline-empty"><MapPin size={17}/>Seleccione un Puesto en Ubicación para consultar turnos y agentes.</div>}
        {form.postId&&coverageLoading&&<div className="csl-inline-empty"><CalendarClock size={17}/>Cargando turnos y disponibilidad...</div>}
        {form.postId&&coverageError&&<div className="csl-inline-empty"><AlertTriangle size={17}/>{coverageError}<button type="button" onClick={()=>setCoverageRefresh(x=>x+1)}>Reintentar</button></div>}
        {form.postId&&!coverageLoading&&!coverageError&&absenceMode==='PROGRAMMED'&&<Field label="Turno a cubrir *"><select value={form.targetShiftId} onChange={e=>chooseShift(e.target.value)}>
          <option value="">{coverage?.nextShifts.length?'Seleccione turno':'No hay próximos turnos registrados'}</option>
          {coverage?.nextShifts.map((shift,index)=><option key={shift.id} value={shift.id}>{fmtShift(shift,index===0?'Siguiente turno':'Segundo turno')}</option>)}
        </select></Field>}
        {form.postId&&!coverageLoading&&!coverageError&&absenceMode==='EFFECTIVE'&&<div className="csl-current-shift"><Clock3 size={16}/>{coverage?.currentShift?fmtShift(coverage.currentShift,'Turno en curso'):'No hay un turno en curso registrado para este Puesto.'}</div>}
        <div className="csl-reassignment-title"><UserCheck size={17}/><div><strong>Agentes Disponibles para Reasignación</strong><small>Prelación: mismo Puesto → mismo Punto → misma Compañía.</small></div></div>
        <div className="csl-candidate-list">
          {targetShift&&replacementCandidates.map(person=><button type="button" key={person.employeeId} className={form.replacementEmployeeId===person.employeeId?'selected':''} onClick={()=>chooseReplacement(person)}>
            <span className={`rank rank-${person.group}`}>{person.group}</span><div><strong>{person.fullName}</strong>
            <small>{person.roleCode}</small>
            <small>{person.group===1?`Libre · mismo Puesto (${person.lastPost})`:person.group===2?`Libre · mismo Punto (${person.lastPost})`:person.lastPoint?`Libre · misma Compañía · último Punto: ${person.lastPoint}`:'Libre · misma Compañía'}</small></div><i/>
          </button>)}
          {form.postId&&!coverageLoading&&!coverageError&&!targetShift&&<div className="csl-inline-empty"><CalendarClock size={17}/>{absenceMode==='PROGRAMMED'?'Seleccione un turno para consultar disponibilidad.':'No hay turno en curso para este Puesto.'}</div>}
          {targetShift&&!replacementCandidates.length&&<div className="csl-inline-empty"><Users size={17}/>No hay agentes libres según las asignaciones publicadas y las indisponibilidades registradas.</div>}
        </div>
        <p className="csl-incident-hint">Disponibilidad calculada para el turno a cubrir y el turno previo, con asignaciones publicadas e indisponibilidades registradas.</p>
      </section>}

      <section className="csl-incident-section csl-resolution-section"><SectionTitle step={absenceMode?'9':'8'} title="Resolución *"/><Field label=""><textarea aria-label="Resolución requerida" value={form.resolution} onChange={e=>patch('resolution',e.target.value)} placeholder="Detalle cómo se resolvió o gestionó el incidente…"/></Field><ImagePicker label="Evidencia de resolución" items={form.resolutionImages} onAdd={e=>addImages('resolutionImages',e)} onRemove={id=>removeImage('resolutionImages',id)}/></section>

      <section className="csl-incident-section csl-sanction-section"><SectionTitle step={absenceMode?'10':'9'} title="Sanción"/><div className="csl-sanction-choice"><span>¿Genera sanción?</span><label><input type="radio" name="sanction" checked={!form.sanction} onChange={()=>patch('sanction',false)}/>No</label><label><input type="radio" name="sanction" checked={form.sanction} onChange={()=>patch('sanction',true)}/>Sí</label></div>{form.sanction&&<Field label="Descripción de la sanción *"><textarea value={form.sanctionDescription} onChange={e=>patch('sanctionDescription',e.target.value)} placeholder="Describa la sanción propuesta…"/></Field>}</section>

      {fileError&&<div className="csl-incident-warning"><AlertTriangle size={16}/>{fileError}</div>}
      {error&&<div className="csl-incident-error"><AlertTriangle size={16}/>{error}</div>}
    </div>

    <div className="csl-incident-footer"><button type="button" className="draft" disabled={saving} onClick={()=>void submit('DRAFT')}><Save size={16}/>{saving?'Guardando…':'Guardar borrador'}</button><button type="button" className="finalize" disabled={saving} onClick={()=>void submit('FINALIZED')}><Send size={16}/>{initial?.status==='FINALIZED'?'Guardar finalizado':'Finalizar'}</button></div>
  </aside>
}

function SectionTitle({step,title}:{step:string;title:string}){return <div className="csl-section-title"><span>{step}</span><strong>{title}</strong></div>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="csl-incident-field">{label&&<span>{label}</span>}{children}</label>}
function ImagePicker({label,items,onAdd,onRemove}:{label:string;items:AttachmentMeta[];onAdd:(event:ChangeEvent<HTMLInputElement>)=>void;onRemove:(id:string)=>void}){return <div className="csl-image-picker"><div><strong>{label}</strong><span>{items.length}/5 imágenes</span></div><label className={items.length>=5?'disabled':''}><ImagePlus size={16}/>Adjuntar imágenes<input type="file" accept="image/*" multiple disabled={items.length>=5} onChange={onAdd}/></label>{items.length>0&&<div className="csl-image-list">{items.map(item=><div key={item.id}><span><CheckCircle2 size={14}/>{item.name}<small>{Math.max(1,Math.round(item.size/1024))} KB</small></span><button type="button" aria-label={`Quitar ${item.name}`} onClick={()=>onRemove(item.id)}><Trash2 size={14}/></button></div>)}</div>}</div>}
