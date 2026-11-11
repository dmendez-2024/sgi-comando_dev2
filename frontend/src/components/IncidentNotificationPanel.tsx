import {useEffect, useMemo, useState, type ChangeEvent, type ReactNode} from 'react';
import {
  AlertTriangle, CalendarClock, CheckCircle2, CircleAlert, ClipboardCheck, Clock3,
  FileText, ImagePlus, Info, MapPin, OctagonAlert, Save, Send, ShieldAlert,
  Trash2, UserCheck, Users, X
} from 'lucide-react';
import {api,ApiError} from '../api';
import SearchableSelect from './SearchableSelect';

export type IncidentCategory='SERVICE'|'SECURITY'|'ADMINISTRATIVE';
export type IncidentSeverity='INFORMATIVE'|'MINOR'|'MODERATE'|'MAJOR'|'CRITICAL';
export type IncidentLifecycle='DRAFT'|'FINALIZED';
export type AttachmentMeta={id:string;name:string;size:number;type:string;stored?:boolean;file?:File};
export type LocationOption={clientId:string;client:string;pointId:string;point:string;postId:string;post:string;companyId:string|null;company:string;companyCode:string;city:string};
type IncidentCatalogType={id:string;name:string};
type IncidentCatalogSubcategory={id:string;name:string;incidentTypes:IncidentCatalogType[]};
type IncidentCatalogCategory={id:string;systemKey:string;name:string;subcategories:IncidentCatalogSubcategory[]};
type IncidentCatalogSeverity={id:string;systemKey:string;name:string};
type IncidentCatalog={categories:IncidentCatalogCategory[];severities:IncidentCatalogSeverity[]};

export type IncidentRecord={
  id:string;
  operationalReportId:string;
  code:string;
  title:string;
  categoryCatalogId:string;
  category:IncidentCategory|'';
  categoryName:string;
  subcategoryCatalogId:string;
  subcategory:string;
  incidentTypeCatalogId:string;
  incidentType:string;
  severityCatalogId:string;
  severity:IncidentSeverity|'';
  severityName:string;
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
  sanctionDeliveryStatus?:'NOT_REQUIRED'|'PENDING'|'SENT'|'FAILED';
  sanctionEventId?:string;
  sanctionDeliveryAttempts?:number;
  sanctionSentAt?:string;
  absenceMode:''|'PROGRAMMED'|'EFFECTIVE';
  targetShiftId:string;
  targetShiftLabel:string;
  replacementEmployeeId:string;
  replacementEmployeeName:string;
  coverageAssignmentId?:string;
  coverageAppliedAt?:string;
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
  onSave:(record:IncidentRecord,keepEditing?:boolean)=>Promise<IncidentRecord>;
};

type ReplacementCandidate={employeeId:string;fullName:string;roleCode:string;group:number;lastPoint:string;lastPost:string};
type CoverageShift={id:string;startsAt:string;endsAt:string;candidates:ReplacementCandidate[]};
type CoverageOptions={currentShift:CoverageShift|null;nextShifts:CoverageShift[];currentShiftAllowed:boolean;futureShiftAllowed:boolean};

type CollaboratorRow={employeeId:string;fullName:string;roleCode:string;postName:string;lastAt:string;source:'EXECUTION'|'PUBLISHED_SHIFT'|'CURRENT_SHIFT'|'UPCOMING_SHIFT'};

const CATEGORY_META:Record<IncidentCategory,{icon:ReactNode;description:string}>={
  SERVICE:{icon:<ClipboardCheck size={24}/>,description:'Cumplimiento del servicio y desempeño operativo.'},
  SECURITY:{icon:<ShieldAlert size={24}/>,description:'Eventos que afectan personas, bienes o instalaciones.'},
  ADMINISTRATIVE:{icon:<FileText size={24}/>,description:'Asuntos administrativos, logísticos o tecnológicos.'}
};

const SEVERITY_META:Record<IncidentSeverity,{tone:string;icon:ReactNode}>={
  INFORMATIVE:{tone:'informative',icon:<Info size={26}/>},
  MINOR:{tone:'minor',icon:<CheckCircle2 size={26}/>},
  MODERATE:{tone:'moderate',icon:<AlertTriangle size={26}/>},
  MAJOR:{tone:'major',icon:<CircleAlert size={26}/>},
  CRITICAL:{tone:'critical',icon:<OctagonAlert size={26}/>},
};
const categoryMeta=(key:string)=>CATEGORY_META[key as IncidentCategory]??{icon:<ClipboardCheck size={24}/>,description:'Clasificación operativa del incidente.'};
const severityMeta=(key:string)=>SEVERITY_META[key as IncidentSeverity]??{tone:'informative',icon:<Info size={26}/>};

const emptyRecord=(nextCode:string):IncidentRecord=>({
  id:'',operationalReportId:'',code:nextCode,title:'',categoryCatalogId:'',category:'',categoryName:'',subcategoryCatalogId:'',subcategory:'',incidentTypeCatalogId:'',incidentType:'',severityCatalogId:'',severity:'',severityName:'',client:'',clientId:'',point:'',pointId:'',post:'',postId:'',company:'',companyId:'',companyCode:'',city:'',
  collaboratorIds:[],collaboratorNames:[],description:'',descriptionImages:[],resolution:'',resolutionImages:[],sanction:false,
  sanctionDescription:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:'',
  status:'DRAFT',createdAt:'',updatedAt:''
});

const absenceModeFromIncidentType=(incidentType:string)=>incidentType==='Inasistencia programada'?'PROGRAMMED':incidentType==='Inasistencia efectiva'?'EFFECTIVE':'';
const uniq=<T,>(values:T[])=>Array.from(new Set(values));
const fmtDate=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(value));

const saveErrorMessage=(error:unknown)=>{
  if(error instanceof ApiError){
    try{const body=JSON.parse(error.body) as {message?:unknown};if(typeof body.message==='string'&&body.message.trim())return body.message.trim();}catch{/* respuesta no JSON */}
    if(error.status===0)return 'No se pudo contactar al servidor de SGI Comando.';
  }
  return error instanceof Error&&error.message?error.message:'Ocurrió un error inesperado.';
};

const fmtShift=(shift:CoverageShift,prefix:string)=>{
  const date=(value:string)=>new Intl.DateTimeFormat('es-EC',{timeZone:'America/Guayaquil',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value));
  return `${prefix} · ${date(shift.startsAt)}–${date(shift.endsAt)}`;
};

function reconcileCatalog(record:IncidentRecord,catalog:IncidentCatalog):IncidentRecord{
  const category=catalog.categories.find(item=>item.id===record.categoryCatalogId)||catalog.categories.find(item=>item.systemKey===record.category);
  const subcategory=category?.subcategories.find(item=>item.id===record.subcategoryCatalogId)||category?.subcategories.find(item=>item.name===record.subcategory);
  const incidentType=subcategory?.incidentTypes.find(item=>item.id===record.incidentTypeCatalogId)||subcategory?.incidentTypes.find(item=>item.name===record.incidentType);
  const severity=catalog.severities.find(item=>item.id===record.severityCatalogId)||catalog.severities.find(item=>item.systemKey===record.severity);
  return {...record,
    categoryCatalogId:category?.id??'',category:category?(category.systemKey as IncidentCategory):record.category,categoryName:category?.name??record.categoryName,
    subcategoryCatalogId:subcategory?.id??'',subcategory:subcategory?.name??record.subcategory,
    incidentTypeCatalogId:incidentType?.id??'',incidentType:incidentType?.name??record.incidentType,
    severityCatalogId:severity?.id??'',severity:severity?(severity.systemKey as IncidentSeverity):record.severity,severityName:severity?.name??record.severityName,
  };
}

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
  const [catalog,setCatalog]=useState<IncidentCatalog|null>(null);
  const [catalogLoading,setCatalogLoading]=useState(true);
  const [catalogError,setCatalogError]=useState('');
  const [catalogRefresh,setCatalogRefresh]=useState(0);

  useEffect(()=>{
    let current=true;
    setCatalogLoading(true);setCatalogError('');
    void api.incidentCatalog().then(value=>{
      if(!current)return;
      const next=value as IncidentCatalog;
      if(!next.categories?.length||!next.severities?.length)throw new Error('El catálogo de incidentes no tiene registros activos.');
      setCatalog(next);
    }).catch(error=>{if(current){setCatalog(null);setCatalogError(error instanceof Error?error.message:'No se pudo cargar el catálogo de incidentes.')}})
      .finally(()=>{if(current)setCatalogLoading(false)});
    return ()=>{current=false};
  },[catalogRefresh]);

  useEffect(()=>{if(catalog)setForm(previous=>reconcileCatalog(previous,catalog))},[catalog]);

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
    if(!form.pointId||!form.postId||form.coverageAssignmentId){setCoverage(null);setCoverageError('');setCoverageLoading(false);return;}
    let current=true;
    setCoverage(null);setCoverageLoading(true);setCoverageError('');
    void api.operationCoverageOptions(form.pointId,form.postId).then(value=>{if(current)setCoverage(value as CoverageOptions)})
      .catch(()=>{if(current)setCoverageError('No se pudieron consultar los turnos y agentes de este Puesto.')})
      .finally(()=>{if(current)setCoverageLoading(false)});
    return ()=>{current=false};
  },[form.pointId,form.postId,form.coverageAssignmentId,coverageRefresh]);

  const clients=useMemo(()=>Array.from(new Map(locations.map(x=>[x.clientId,{id:x.clientId,name:x.client}])).values()),[locations]);
  const points=useMemo(()=>Array.from(new Map(locations.filter(x=>x.clientId===form.clientId).map(x=>[x.pointId,{id:x.pointId,name:x.point}])).values()),[locations,form.clientId]);
  const posts=useMemo(()=>locations.filter(x=>x.pointId===form.pointId).map(x=>({id:x.postId,name:x.post})),[locations,form.pointId]);
  const categories=catalog?.categories??[];
  const selectedCategory=categories.find(item=>item.id===form.categoryCatalogId);
  const subcategories=selectedCategory?.subcategories??[];
  const selectedSubcategory=subcategories.find(item=>item.id===form.subcategoryCatalogId);
  const incidentTypes=selectedSubcategory?.incidentTypes??[];
  const severities=catalog?.severities??[];
  const absenceMode=absenceModeFromIncidentType(form.incidentType);
  const coverageApplied=!!form.coverageAssignmentId;

  const selectedLocation=useMemo(()=>locations.find(x=>x.clientId===form.clientId&&x.pointId===form.pointId&&(form.postId?x.postId===form.postId:true)),[locations,form.clientId,form.pointId,form.postId]);
  const targetShift=absenceMode==='EFFECTIVE'?coverage?.currentShift:coverage?.nextShifts.find(x=>x.id===form.targetShiftId);
  const replacementCandidates=(targetShift?.candidates??[]).filter(person=>!form.collaboratorIds.includes(person.employeeId));

  function patch<K extends keyof IncidentRecord>(key:K,value:IncidentRecord[K]){setForm(prev=>({...prev,[key]:value}));}
  function chooseCategory(value:string){const selected=categories.find(item=>item.id===value);if(!selected)return;setForm(prev=>({...prev,categoryCatalogId:selected.id,category:selected.systemKey as IncidentCategory,categoryName:selected.name,subcategoryCatalogId:'',subcategory:'',incidentTypeCatalogId:'',incidentType:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));}
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
    const selected=subcategories.find(item=>item.id===value);
    setForm(prev=>({...prev,subcategoryCatalogId:selected?.id??'',subcategory:selected?.name??'',incidentTypeCatalogId:'',incidentType:'',absenceMode:'',targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseIncidentType(value:string){
    const selected=incidentTypes.find(item=>item.id===value);const mode=absenceModeFromIncidentType(selected?.name??'');
    setForm(prev=>({...prev,incidentTypeCatalogId:selected?.id??'',incidentType:selected?.name??'',absenceMode:mode,targetShiftId:'',targetShiftLabel:'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseSeverity(value:string){const selected=severities.find(item=>item.id===value);if(!selected)return;setForm(prev=>({...prev,severityCatalogId:selected.id,severity:selected.systemKey as IncidentSeverity,severityName:selected.name}));}
  function chooseShift(value:string){
    const shift=coverage?.nextShifts.find(x=>x.id===value);
    setForm(prev=>({...prev,targetShiftId:value,targetShiftLabel:shift?fmtShift(shift,'Turno a cubrir'):'',replacementEmployeeId:'',replacementEmployeeName:''}));
  }
  function chooseReplacement(person:ReplacementCandidate){setForm(prev=>({...prev,replacementEmployeeId:person.employeeId,replacementEmployeeName:person.fullName}));}
  function addImages(key:'descriptionImages'|'resolutionImages',event:ChangeEvent<HTMLInputElement>){
    const files=Array.from(event.target.files??[]); event.target.value=''; setFileError('');
    const invalid=files.find(file=>!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size===0||file.size>5*1024*1024);
    if(invalid){setFileError('Solo imágenes JPEG, PNG o WebP de hasta 5 MiB por archivo.');return;}
    const current=form[key]; const remaining=5-current.length;
    if(remaining<=0){setFileError('El máximo es 5 imágenes por sección.');return;}
    if(files.length>remaining)setFileError(`Solo se agregaron ${remaining} imagen(es): el máximo es 5 por sección.`);
    const metas=files.slice(0,remaining).map(file=>({id:crypto.randomUUID(),name:file.name,size:file.size,type:file.type,file,stored:false}));
    patch(key,[...current,...metas] as IncidentRecord[typeof key]);
  }
  function removeImage(key:'descriptionImages'|'resolutionImages',id:string){patch(key,form[key].filter(x=>x.id!==id) as IncidentRecord[typeof key]);}
  function validateFinal(){
    if(catalogLoading)return 'Espere a que termine de cargar el catálogo de incidentes.';
    if(catalogError)return 'No se puede finalizar mientras el catálogo de incidentes no esté disponible.';
    if(!form.title.trim())return 'Ingrese un título.';
    if(!form.categoryCatalogId)return 'Seleccione una categoría.';
    if(!form.subcategoryCatalogId)return 'Seleccione una subcategoría.';
    if(!form.incidentTypeCatalogId)return 'Seleccione el tipo de incidente.';
    if(!form.severityCatalogId)return 'Seleccione una criticidad.';
    if(!form.client||!form.point)return 'Cliente y Punto son obligatorios.';
    if(!form.collaboratorIds.length)return 'Seleccione uno o más colaboradores involucrados.';
    if(!form.description.trim())return 'Ingrese la descripción del incidente.';
    if(!coverageApplied&&absenceMode==='EFFECTIVE'&&coverage?.currentShiftAllowed===false)return 'Su perfil solo puede cubrir turnos futuros.';
    if(!coverageApplied&&absenceMode==='PROGRAMMED'&&coverage?.futureShiftAllowed===false)return 'Su perfil no puede cubrir turnos futuros desde Consola.';
    if(!coverageApplied&&absenceMode==='PROGRAMMED'&&!targetShift)return 'Seleccione uno de los próximos dos turnos.';
    if(!coverageApplied&&absenceMode==='EFFECTIVE'&&!targetShift)return 'No hay un turno en curso para el Puesto seleccionado.';
    if(!coverageApplied&&absenceMode&&form.replacementEmployeeId&&!replacementCandidates.some(person=>person.employeeId===form.replacementEmployeeId))return 'Seleccione un agente que siga disponible para este turno.';
    if(!coverageApplied&&absenceMode&&!form.replacementEmployeeId)return 'Seleccione un Agente disponible para la reasignación.';
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
      let working:IncidentRecord={...form,code:form.code||nextCode,status,absenceMode,targetShiftId:coverageApplied?form.targetShiftId:targetShift?.id??form.targetShiftId,targetShiftLabel:coverageApplied?form.targetShiftLabel:targetShift?fmtShift(targetShift,'Turno a cubrir'):form.targetShiftLabel,companyId:location?.companyId??form.companyId,company:location?.company??form.company,companyCode:location?.companyCode??form.companyCode,city:location?.city??form.city,createdAt:form.createdAt||now,updatedAt:now};
      const pending=working.descriptionImages.some(x=>x.file)||working.resolutionImages.some(x=>x.file);
      if(pending){
        // Persist the draft first; a failed upload must never finalize or lose the selected files.
        const draft=await onSave({...working,status:coverageApplied?'FINALIZED':'DRAFT',descriptionImages:working.descriptionImages.filter(x=>!x.file),resolutionImages:working.resolutionImages.filter(x=>!x.file)},true);
        working={...working,...draft,descriptionImages:working.descriptionImages,resolutionImages:working.resolutionImages};
        setForm(working);
        for(const section of ['description','resolution'] as const){
          const key=section==='description'?'descriptionImages':'resolutionImages';
          for(const item of working[key])if(item.file){
            const stored:AttachmentMeta=await api.uploadIncidentImage(working.id,section,item.id,item.file);
            working={...working,[key]:working[key].map(x=>x.id===item.id?stored:x)};
            setForm(working);
          }
        }
      }
      const saved=await onSave({...working,status});
      setForm(saved);
    }catch(error){setError(`No se pudo ${status==='FINALIZED'?'finalizar':'guardar'} el incidente: ${saveErrorMessage(error)}`);}
    finally{setSaving(false);}
  }

  return <aside className="csl-detail-card csl-incident-panel" aria-label="Notificación de Incidente">
    <div className="csl-incident-head">
      <div className="csl-incident-brand"><span className="csl-incident-brand-icon" aria-hidden="true"><AlertTriangle size={24}/></span><div><span>Incidentes</span><h3>Notificación de Incidente</h3><small>{initial?`${initial.code} · Editar`:'Nuevo caso operativo'}</small></div></div>
      <button type="button" className="csl-icon-button" aria-label="Cerrar" onClick={onCancel}><X size={18}/></button>
    </div>

    <div className="csl-incident-scroll">
      <Field label="Título *"><input value={form.title} maxLength={140} onChange={e=>patch('title',e.target.value)} placeholder="Título del incidente"/></Field>

      <section className="csl-incident-section"><SectionTitle step="1" title="Categoría"/>{catalogLoading&&<div className="csl-inline-empty"><ClipboardCheck size={17}/>Cargando catálogo de incidentes…</div>}{catalogError&&<div className="csl-incident-error"><AlertTriangle size={16}/>{catalogError}<button type="button" onClick={()=>setCatalogRefresh(value=>value+1)}>Reintentar</button></div>}<div className="csl-incident-categories">{categories.map(item=>{const meta=categoryMeta(item.systemKey);return <button type="button" key={item.id} className={form.categoryCatalogId===item.id?'selected':''} disabled={coverageApplied||catalogLoading||!!catalogError} onClick={()=>chooseCategory(item.id)}><span className="csl-category-symbol" aria-hidden="true">{meta.icon}</span><strong>{item.name}</strong><small>{meta.description}</small><i aria-hidden="true"/></button>})}</div></section>

      <section className="csl-incident-section"><SectionTitle step="2" title="Subcategoría"/><Field label=""><select value={form.subcategoryCatalogId} disabled={!form.categoryCatalogId||coverageApplied||catalogLoading||!!catalogError} onChange={e=>chooseSubcategory(e.target.value)}><option value="">{form.categoryCatalogId?'Seleccione una subcategoría':'Seleccione primero una categoría'}</option>{subcategories.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></Field></section>

      <section className="csl-incident-section"><SectionTitle step="3" title="Incidente"/><Field label=""><select value={form.incidentTypeCatalogId} disabled={!form.subcategoryCatalogId||coverageApplied||catalogLoading||!!catalogError} onChange={e=>chooseIncidentType(e.target.value)}><option value="">{form.subcategoryCatalogId?'Seleccione el incidente':'Seleccione primero una subcategoría'}</option>{incidentTypes.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>{form.category==='SERVICE'&&form.subcategory==='Asistencia y Puntualidad'&&<p className="csl-incident-hint">Inasistencia programada e Inasistencia efectiva activan el flujo especial de cobertura/reasignación.</p>}</section>

      <section className="csl-incident-section"><SectionTitle step="4" title="Criticidad"/><div className="csl-severity-grid">{severities.map(item=>{const meta=severityMeta(item.systemKey);return <button key={item.id} type="button" className={`${meta.tone} ${form.severityCatalogId===item.id?'selected':''}`} aria-pressed={form.severityCatalogId===item.id} disabled={catalogLoading||!!catalogError} onClick={()=>chooseSeverity(item.id)}><span className="csl-severity-icon" aria-hidden="true">{meta.icon}</span><strong>{item.name}</strong><i className="csl-severity-dot" aria-hidden="true"/></button>})}</div></section>

      <section className="csl-incident-section"><SectionTitle step="5" title="Ubicación"/>{locationsError&&<div className="csl-incident-error"><AlertTriangle size={16}/>{locationsError}<button type="button" onClick={onRetryLocations}>Reintentar</button></div>}<div className="csl-incident-grid two"><Field label="Cliente *"><SearchableSelect ariaLabel="Cliente" value={form.clientId??''} disabled={coverageApplied||locationsLoading||!!locationsError} onChange={chooseClient} searchPlaceholder="Buscar cliente…" options={[{value:'',label:locationsLoading?'Cargando clientes…':locationsError?'Clientes no disponibles':clients.length?'Seleccione cliente':'No hay clientes en su alcance'},...clients.map(x=>({value:x.id,label:x.name}))]}/></Field><Field label="Punto *"><SearchableSelect ariaLabel="Punto" value={form.pointId??''} disabled={coverageApplied||!form.clientId||locationsLoading||!!locationsError} onChange={choosePoint} searchPlaceholder="Buscar punto…" options={[{value:'',label:form.clientId?'Seleccione punto':'Seleccione cliente primero'},...points.map(x=>({value:x.id,label:x.name}))]}/></Field></div><Field label={absenceMode?'Puesto *':'Puesto (opcional)'}><SearchableSelect ariaLabel="Puesto" value={form.postId??''} disabled={coverageApplied||!form.pointId||locationsLoading||!!locationsError} onChange={choosePost} searchPlaceholder="Buscar puesto…" options={[{value:'',label:'Todos / No aplica'},...posts.map(x=>({value:x.id,label:x.name}))]}/></Field></section>

      <section className="csl-incident-section"><SectionTitle step="6" title="Colaboradores involucrados"/><p className="csl-incident-hint">Personas con actividad confirmada en los últimos 14 días o con turnos publicados en este Punto hasta los próximos 14 días. Cada persona aparece una sola vez.</p><div className="csl-collaborator-list">{form.pointId&&collaborators.map(person=><label key={person.employeeId} className={form.collaboratorIds.includes(person.employeeId)?'selected':''}><input type="checkbox" checked={form.collaboratorIds.includes(person.employeeId)} onChange={()=>toggleCollaborator(person)}/><span><strong>{person.fullName}</strong><small>{person.roleCode||'Personal'} · {person.source==='EXECUTION'?'Actividad confirmada':person.source==='CURRENT_SHIFT'?'Turno en curso':person.source==='UPCOMING_SHIFT'?'Turno próximo':'Turno publicado'}: {fmtDate(person.lastAt)} · {person.postName}</small></span></label>)}{form.pointId&&collaboratorsLoading&&<div className="csl-inline-empty"><Users size={17}/>Cargando colaboradores del Punto…</div>}{form.pointId&&collaboratorsError&&<div className="csl-inline-empty"><AlertTriangle size={17}/>{collaboratorsError}<button type="button" onClick={()=>setCollaboratorsRefresh(value=>value+1)}>Reintentar</button></div>}{form.pointId&&!collaboratorsLoading&&!collaboratorsError&&!collaborators.length&&<div className="csl-inline-empty"><Users size={17}/>No hay actividad confirmada ni turnos publicados para este Punto en el período consultado.</div>}{!form.pointId&&<div className="csl-inline-empty"><MapPin size={17}/>Seleccione Cliente y Punto para obtener la lista.</div>}</div></section>

      <section className="csl-incident-section csl-description-section"><SectionTitle step="7" title="Descripción *"/><Field label=""><textarea aria-label="Descripción requerida" value={form.description} onChange={e=>patch('description',e.target.value)} placeholder="Describa lo ocurrido con el mayor detalle posible…"/></Field><ImagePicker label="Evidencia de descripción" items={form.descriptionImages} onAdd={e=>addImages('descriptionImages',e)} onRemove={id=>removeImage('descriptionImages',id)}/></section>

      {absenceMode&&<section className="csl-incident-section csl-reassignment-section">
        <SectionTitle step="8" title="Cobertura / Reasignación"/>
        <div className="csl-special-flow"><CalendarClock size={18}/><div>
          <strong>{absenceMode==='PROGRAMMED'?'Inasistencia Programada':'Inasistencia Efectiva'}</strong>
          <span>{absenceMode==='PROGRAMMED'?'Seleccione uno de los dos próximos turnos registrados para este Puesto.':'Se utiliza el turno actualmente en curso para este Puesto.'}</span>
        </div></div>
        {coverageApplied&&<div className="csl-current-shift"><CheckCircle2 size={16}/>Cobertura aplicada en Asignaciones: {form.replacementEmployeeName||'agente seleccionado'} · {form.targetShiftLabel||'turno seleccionado'}.</div>}
        {!coverageApplied&&!form.postId&&<div className="csl-inline-empty"><MapPin size={17}/>Seleccione un Puesto en Ubicación para consultar turnos y agentes.</div>}
        {!coverageApplied&&form.postId&&coverageLoading&&<div className="csl-inline-empty"><CalendarClock size={17}/>Cargando turnos y disponibilidad...</div>}
        {!coverageApplied&&form.postId&&coverageError&&<div className="csl-inline-empty"><AlertTriangle size={17}/>{coverageError}<button type="button" onClick={()=>setCoverageRefresh(x=>x+1)}>Reintentar</button></div>}
        {!coverageApplied&&form.postId&&!coverageLoading&&!coverageError&&absenceMode==='PROGRAMMED'&&coverage?.futureShiftAllowed===false&&<div className="csl-inline-empty"><AlertTriangle size={17}/>Su perfil no puede cubrir turnos futuros desde Consola.</div>}
        {!coverageApplied&&form.postId&&!coverageLoading&&!coverageError&&absenceMode==='PROGRAMMED'&&coverage?.futureShiftAllowed!==false&&<Field label="Turno a cubrir *"><select value={form.targetShiftId} onChange={e=>chooseShift(e.target.value)}>
          <option value="">{coverage?.nextShifts.length?'Seleccione turno':'No hay próximos turnos registrados'}</option>
          {coverage?.nextShifts.map((shift,index)=><option key={shift.id} value={shift.id}>{fmtShift(shift,index===0?'Siguiente turno':'Segundo turno')}</option>)}
        </select></Field>}
        {!coverageApplied&&form.postId&&!coverageLoading&&!coverageError&&absenceMode==='EFFECTIVE'&&<div className="csl-current-shift"><Clock3 size={16}/>{coverage?.currentShiftAllowed===false?'Su perfil solo puede cubrir turnos futuros.':coverage?.currentShift?fmtShift(coverage.currentShift,'Turno en curso'):'No hay un turno en curso registrado para este Puesto.'}</div>}
        {!coverageApplied&&<div className="csl-reassignment-title"><UserCheck size={17}/><div><strong>Agentes Disponibles para Reasignación</strong><small>Prelación: mismo Puesto → mismo Punto → misma Compañía.</small></div></div>}
        {!coverageApplied&&<div className="csl-candidate-list">
          {targetShift&&replacementCandidates.map(person=><button type="button" key={person.employeeId} className={form.replacementEmployeeId===person.employeeId?'selected':''} onClick={()=>chooseReplacement(person)}>
            <span className={`rank rank-${person.group}`}>{person.group}</span><div><strong>{person.fullName}</strong>
            <small>{person.roleCode}</small>
            <small>{person.group===1?`Libre · mismo Puesto (${person.lastPost})`:person.group===2?`Libre · mismo Punto (${person.lastPost})`:person.lastPoint?`Libre · misma Compañía · último Punto: ${person.lastPoint}`:'Libre · misma Compañía'}</small></div><i/>
          </button>)}
          {form.postId&&!coverageLoading&&!coverageError&&!targetShift&&<div className="csl-inline-empty"><CalendarClock size={17}/>{absenceMode==='PROGRAMMED'?'Seleccione un turno para consultar disponibilidad.':'No hay turno en curso para este Puesto.'}</div>}
          {targetShift&&!replacementCandidates.length&&<div className="csl-inline-empty"><Users size={17}/>No hay agentes libres según las asignaciones publicadas y las indisponibilidades registradas.</div>}
        </div>}
        {!coverageApplied&&<p className="csl-incident-hint">Disponibilidad calculada para el turno a cubrir y el turno previo, con asignaciones publicadas e indisponibilidades registradas. Al finalizar, la cobertura se aplicará en Asignaciones.</p>}
      </section>}

      <section className="csl-incident-section csl-resolution-section"><SectionTitle step={absenceMode?'9':'8'} title="Resolución *"/><Field label=""><textarea aria-label="Resolución requerida" value={form.resolution} onChange={e=>patch('resolution',e.target.value)} placeholder="Detalle cómo se resolvió o gestionó el incidente…"/></Field><ImagePicker label="Evidencia de resolución" items={form.resolutionImages} onAdd={e=>addImages('resolutionImages',e)} onRemove={id=>removeImage('resolutionImages',id)}/></section>

      <section className="csl-incident-section csl-sanction-section"><SectionTitle step={absenceMode?'10':'9'} title="Sanción"/><div className="csl-sanction-choice"><span>¿Genera sanción?</span><label><input type="radio" name="sanction" checked={!form.sanction} onChange={()=>patch('sanction',false)}/>No</label><label><input type="radio" name="sanction" checked={form.sanction} onChange={()=>patch('sanction',true)}/>Sí</label></div>{form.sanction&&<Field label="Descripción de la sanción *"><textarea value={form.sanctionDescription} onChange={e=>patch('sanctionDescription',e.target.value)} placeholder="Describa la sanción propuesta…"/></Field>}</section>

      {fileError&&<div className="csl-incident-warning"><AlertTriangle size={16}/>{fileError}</div>}
      {error&&<div className="csl-incident-error"><AlertTriangle size={16}/>{error}</div>}
    </div>

    <div className="csl-incident-footer"><button type="button" className="draft" disabled={saving||coverageApplied||catalogLoading||!!catalogError} onClick={()=>void submit('DRAFT')}><Save size={16}/>{saving?'Guardando…':'Guardar borrador'}</button><button type="button" className="finalize" disabled={saving||catalogLoading||!!catalogError} onClick={()=>void submit('FINALIZED')}><Send size={16}/>{initial?.status==='FINALIZED'?'Guardar finalizado':'Finalizar'}</button></div>
  </aside>
}

function SectionTitle({step,title}:{step:string;title:string}){return <div className="csl-section-title"><span>{step}</span><strong>{title}</strong></div>}
function Field({label,children}:{label:string;children:ReactNode}){return <label className="csl-incident-field">{label&&<span>{label}</span>}{children}</label>}
function ImagePicker({label,items,onAdd,onRemove}:{label:string;items:AttachmentMeta[];onAdd:(event:ChangeEvent<HTMLInputElement>)=>void;onRemove:(id:string)=>void}){return <div className="csl-image-picker"><div><strong>{label}</strong><span>{items.length}/5 imágenes</span></div><label className={items.length>=5?'disabled':''}><ImagePlus size={16}/>Adjuntar imágenes<input type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={items.length>=5} onChange={onAdd}/></label>{items.length>0&&<div className="csl-image-list">{items.map(item=><div key={item.id}><span>{item.stored?<CheckCircle2 size={14}/>:<Info size={14}/>} {item.name}<small>{Math.max(1,Math.round(item.size/1024))} KB · {item.file?'Pendiente de guardar':item.stored?'Guardada':'Archivo no disponible; vuelva a adjuntar'}</small></span><button type="button" aria-label={`Quitar ${item.name}`} onClick={()=>onRemove(item.id)}><Trash2 size={14}/></button></div>)}</div>}</div>}
