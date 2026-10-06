import {useEffect,useMemo,useRef,useState} from 'react';
import type {MouseEvent as ReactMouseEvent} from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  CalendarDays,
  Camera,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock3,
  FileImage,
  History,
  Info,
  MapPin,
  Plus,
  Save,
  Search,
  ShieldCheck,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import StandardGallery,{type StandardImageRef,uploadStandardImages,uploadSummary} from '../components/StandardGallery';
import MatchThresholdField from '../components/MatchThresholdField';
import RadiusField from '../components/RadiusField';
import {api} from '../api';

type PointPost={postId:string;postCode:string;postName:string;tier:string;state:string};
type PointContext={pointId:string;pointName:string;pointCode:string;clientName:string;companyName:string;serviceName:string;serviceCode:string;posts:PointPost[]};
type ProtocolStatus='BORRADOR'|'INACTIVO'|'ACTIVO';
type Evidence={id:string;consignmentId:string;sortOrder:number;name:string;description:string;evidenceType:'PHOTO'|'DOCUMENT'|'TEXT'|'CONFIRMATION';required:boolean;hasStandardImage:boolean;standardImages:StandardImageRef[];standardImageVersion:number;standardImageNotes:string;visintEnabled:boolean;matchThreshold?:number|null};
type Item={id:string;protocolId:string;code:string;title:string;instruction:string;priority:'LOW'|'MEDIUM'|'HIGH'|'CRITICAL';status:string;scopeType:'POINT'|'POSTS';postIds:string[];validityType:'PERMANENT'|'TEMPORARY';validityFrom?:string|null;validityUntil?:string|null;applicationType:'ALL_TIME'|'CALENDAR';applicationDaysJson:string;applicationTimeFrom?:string|null;applicationTimeTo?:string|null;acknowledgmentRequired:boolean;confirmationRequired:boolean;evidenceRequired:boolean;gpsRequired:boolean;observationRequired:boolean;expectedLocationMode:'NONE'|'ATS'|'GPS';atsPackageId?:string|null;atsX?:number|null;atsY?:number|null;expectedLatitude?:number|null;expectedLongitude?:number|null;expectedRadiusM?:number|null;publishedAt?:string|null;updatedBy:string;evidences:Evidence[]};
type Protocol={id:string;seriesId:string;basedOnProtocolId?:string|null;pointId:string;code:string;name:string;description:string;status:ProtocolStatus;versionNo:number;publishedAt?:string|null;activatedAt?:string|null;updatedBy:string;consignments:Item[]};
type AtsMeta={id:string;revisionNo:number;planOriginalFilename?:string|null};
type Tab='DEFINICION'|'CONSIGNA'|'ALCANCE'|'APLICACION'|'EVIDENCIAS'|'TRAZABILIDAD';
type ConfirmState={title:string;message:string;confirmLabel:string;tone:'danger'|'warning';run:()=>Promise<void>};

const MAX_CONSIGNMENTS_PER_PROTOCOL=20;
const DAYS=[['MON','Lun'],['TUE','Mar'],['WED','Mié'],['THU','Jue'],['FRI','Vie'],['SAT','Sáb'],['SUN','Dom']] as const;
const TABS:{key:Tab;label:string;short:string}[]=[
  {key:'DEFINICION',label:'Definición',short:'1'},
  {key:'CONSIGNA',label:'Consigna',short:'2'},
  {key:'ALCANCE',label:'Alcance',short:'3'},
  {key:'APLICACION',label:'Aplicación',short:'4'},
  {key:'EVIDENCIAS',label:'Evidencias',short:'5'},
  {key:'TRAZABILIDAD',label:'Trazabilidad',short:'6'},
];

const priorityLabel=(p:string)=>p==='CRITICAL'?'Crítica':p==='HIGH'?'Alta':p==='LOW'?'Baja':'Media';
const statusLabel=(s:ProtocolStatus)=>s==='BORRADOR'?'Borrador':s==='ACTIVO'?'Activo':'Inactivo';
const validityLabel=(v:string)=>v==='TEMPORARY'?'Temporal':'Permanente';
const applicationLabel=(v:string)=>v==='CALENDAR'?'Calendario':'Todo el tiempo';
const locationModeLabel=(v:string)=>v==='ATS'?'Plano ATS':v==='GPS'?'Coordenadas':'Sin ubicación';
const errorText=(e:unknown)=>e instanceof Error?e.message:String(e);
const toInput=(iso?:string|null)=>iso?new Date(iso).toISOString().slice(0,16):'';
const cloneItem=(i:Item):Item=>({...i,postIds:[...i.postIds],evidences:i.evidences.map(e=>({...e}))});
const itemAppliesToPost=(item:Item,postId:string)=>item.scopeType==='POINT'||item.postIds.includes(postId);
const protocolAppliesToPost=(protocol:Protocol,postId:string)=>!postId||!protocol.consignments.length||protocol.consignments.some(item=>itemAppliesToPost(item,postId));
const protocolCoverageCount=(protocol:Protocol,posts:PointPost[])=>posts.filter(post=>protocolAppliesToPost(protocol,post.postId)).length;
const scopeLabel=(item:Item,posts:PointPost[])=>item.scopeType==='POINT'?'Todo el punto':posts.filter(post=>item.postIds.includes(post.postId)).map(post=>post.postCode).join(', ')||'Sin Puestos';

export default function ConsignasConfig({point,onBack}:{point:PointContext;onBack:()=>void}){
  const [protocols,setProtocols]=useState<Protocol[]>([]);
  const [currentProtocol,setCurrentProtocol]=useState<Protocol|null>(null);
  const [selectedPostId,setSelectedPostId]=useState(point.posts[0]?.postId??'');
  const [postQuery,setPostQuery]=useState('');
  const [postsCollapsed,setPostsCollapsed]=useState(false);
  const [protocolQuery,setProtocolQuery]=useState('');
  const [selectedProtocolId,setSelectedProtocolId]=useState('');
  const [selectedItemId,setSelectedItemId]=useState('');
  const [draft,setDraft]=useState<Item|null>(null);
  const [protocolName,setProtocolName]=useState('');
  const [protocolDescription,setProtocolDescription]=useState('');
  const [tab,setTab]=useState<Tab>('DEFINICION');
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [history,setHistory]=useState<Protocol[]|null>(null);
  const [confirmState,setConfirmState]=useState<ConfirmState|null>(null);
  const [confirmBusy,setConfirmBusy]=useState(false);
  const [selectedEvidenceId,setSelectedEvidenceId]=useState('');
  const [standardUrl,setStandardUrl]=useState('');
  const standardRef=useRef('');
  const photoInput=useRef<HTMLInputElement>(null);
  const [ats,setAts]=useState<AtsMeta|null>(null);
  const [planUrl,setPlanUrl]=useState('');
  const planRef=useRef('');
  const stepTabsRef=useRef<HTMLDivElement>(null);

  useEffect(()=>{
    const frame=requestAnimationFrame(()=>{
      const container=stepTabsRef.current;
      const active=container?.querySelector<HTMLButtonElement>('.active');
      if(!container||!active)return;
      const inset=12;
      const containerRect=container.getBoundingClientRect();
      const activeRect=active.getBoundingClientRect();
      const left=activeRect.left-containerRect.left+container.scrollLeft;
      const right=left+activeRect.width;
      const visibleLeft=container.scrollLeft+inset;
      const visibleRight=container.scrollLeft+container.clientWidth-inset;
      if(left<visibleLeft)container.scrollTo({left:Math.max(0,left-inset)});
      else if(right>visibleRight)container.scrollTo({left:right-container.clientWidth+inset});
    });
    return ()=>cancelAnimationFrame(frame);
  },[tab,selectedProtocolId,selectedItemId]);

  const revokeStandard=()=>{if(standardRef.current)URL.revokeObjectURL(standardRef.current);standardRef.current='';setStandardUrl('')};
  const setStandard=(u:string)=>{revokeStandard();standardRef.current=u;setStandardUrl(u)};
  const revokePlan=()=>{if(planRef.current)URL.revokeObjectURL(planRef.current);planRef.current='';setPlanUrl('')};
  const setPlan=(u:string)=>{revokePlan();planRef.current=u;setPlanUrl(u)};

  const selectedProtocol=protocols.find(p=>p.id===selectedProtocolId)??null;
  const isDraft=selectedProtocol?.status==='BORRADOR';
  const selectedEvidence=draft?.evidences.find(e=>e.id===selectedEvidenceId)??null;
  const selectedPost=point.posts.find(post=>post.postId===selectedPostId)??point.posts[0]??null;

  const load=async(preferProtocol?:string,preferItem?:string,preferEvidence?:string)=>{
    setLoading(true);setError('');
    try{
      const [rows,current]=await Promise.all([api.consignmentProtocols(point.pointId),api.currentConsignmentProtocol(point.pointId)]) as [Protocol[],Protocol|null];
      setProtocols(rows);
      setCurrentProtocol(current??null);
      const postId=selectedPostId||point.posts[0]?.postId||'';
      if(!selectedPostId&&postId)setSelectedPostId(postId);
      const visible=rows.filter(protocol=>protocolAppliesToPost(protocol,postId));
      const p=(preferProtocol?rows.find(x=>x.id===preferProtocol):rows.find(x=>x.id===selectedProtocolId))??visible[0]??rows[0]??null;
      const item=(preferItem?p?.consignments.find(x=>x.id===preferItem):p?.consignments.find(x=>x.id===selectedItemId))??p?.consignments[0]??null;
      setSelectedProtocolId(p?.id??'');
      setProtocolName(p?.name??'');
      setProtocolDescription(p?.description??'');
      setSelectedItemId(item?.id??'');
      setDraft(item?cloneItem(item):null);
      const ev=(preferEvidence?item?.evidences.find(x=>x.id===preferEvidence):item?.evidences[0])??null;
      setSelectedEvidenceId(ev?.id??'');
      revokeStandard();
      
    }catch(e){setError(errorText(e))}finally{setLoading(false)}
  };

  const loadAts=async()=>{
    try{
      const meta=await api.atsCurrent(point.pointId) as AtsMeta|null;
      setAts(meta);
      if(meta){
        const blob=await api.atsPlan(point.pointId) as Blob;
        setPlan(URL.createObjectURL(blob));
      }
    }catch{
      setAts(null);
      revokePlan();
    }
  };

  useEffect(()=>{void load();void loadAts();return()=>{revokeStandard();revokePlan()}},[point.pointId]);

  

  const visiblePosts=useMemo(()=>point.posts.filter(post=>!postQuery.trim()||`${post.postCode} ${post.postName}`.toLowerCase().includes(postQuery.trim().toLowerCase())),[point.posts,postQuery]);
  const filteredProtocols=useMemo(()=>{
    const q=protocolQuery.trim().toLowerCase();
    return protocols
      .filter(protocol=>protocolAppliesToPost(protocol,selectedPostId))
      .filter(protocol=>!q||`${protocol.code} ${protocol.name}`.toLowerCase().includes(q))
      .sort((a,b)=>a.code.localeCompare(b.code));
  },[protocols,selectedPostId,protocolQuery]);

  const totalConsignments=protocols.reduce((sum,protocol)=>sum+protocol.consignments.length,0);
  const evidenceCount=protocols.reduce((sum,protocol)=>sum+protocol.consignments.filter(item=>item.evidenceRequired).length,0);
  const gpsCount=protocols.reduce((sum,protocol)=>sum+protocol.consignments.filter(item=>item.gpsRequired).length,0);
  const protocolCount=protocols.length;
  const activeCoverage=selectedProtocol?protocolCoverageCount(selectedProtocol,point.posts):0;

  const selectPost=(postId:string)=>{
    setSelectedPostId(postId);
    const nextProtocol=protocols.find(protocol=>protocolAppliesToPost(protocol,postId))??protocols[0]??null;
    setSelectedProtocolId(nextProtocol?.id??'');
    setProtocolName(nextProtocol?.name??'');
    setProtocolDescription(nextProtocol?.description??'');
    const nextItem=nextProtocol?.consignments[0]??null;
    setSelectedItemId(nextItem?.id??'');
    setDraft(nextItem?cloneItem(nextItem):null);
    setSelectedEvidenceId(nextItem?.evidences[0]?.id??'');
    revokeStandard();
    
    setTab('DEFINICION');
  };

  const chooseProtocol=(p:Protocol)=>{
    setSelectedProtocolId(p.id);
    setProtocolName(p.name);
    setProtocolDescription(p.description);
    const item=p.consignments[0]??null;
    setSelectedItemId(item?.id??'');
    setDraft(item?cloneItem(item):null);
    setSelectedEvidenceId(item?.evidences[0]?.id??'');
    revokeStandard();
    
    setTab('DEFINICION');
  };

  const chooseItem=(i:Item)=>{
    setSelectedItemId(i.id);
    setDraft(cloneItem(i));
    setSelectedEvidenceId(i.evidences[0]?.id??'');
    revokeStandard();
    
    setTab('CONSIGNA');
  };

  const createProtocol=async()=>{try{const p=await api.createConsignmentProtocol({pointId:point.pointId,name:'Nuevo protocolo de consignas'}) as Protocol;await load(p.id);setNotice(`${p.code} creado como borrador.`)}catch(e){setError(errorText(e))}};
  const forkProtocol=async()=>{if(!selectedProtocol)return;try{const p=await api.forkConsignmentProtocol(selectedProtocol.id) as Protocol;await load(p.id,p.consignments[0]?.id);setNotice(`Se creó ${p.code} v${p.versionNo} como borrador editable.`)}catch(e){setError(errorText(e))}};
  const saveProtocol=async()=>{if(!selectedProtocol||!isDraft)return;setSaving(true);try{await api.saveConsignmentProtocol(selectedProtocol.id,{name:protocolName,description:protocolDescription});await load(selectedProtocol.id,selectedItemId,selectedEvidenceId);setNotice('Borrador del protocolo guardado.')}catch(e){setError(errorText(e))}finally{setSaving(false)}};
  const publishProtocol=async()=>{if(!selectedProtocol||!isDraft)return;setSaving(true);try{if(draft)await saveItemInternal(false);await api.saveConsignmentProtocol(selectedProtocol.id,{name:protocolName,description:protocolDescription});const p=await api.publishConsignmentProtocol(selectedProtocol.id) as Protocol;await load(p.id,selectedItemId,selectedEvidenceId);setNotice('Versión publicada en estado Inactivo. Actívela cuando deba entrar en operación.')}catch(e){setError(errorText(e))}finally{setSaving(false)}};
  const activateProtocol=async()=>{if(!selectedProtocol)return;try{const p=await api.activateConsignmentProtocol(selectedProtocol.id) as Protocol;await load(p.id,selectedItemId,selectedEvidenceId);setNotice(`${p.code} quedó como Protocolo Activo de Consignas.`)}catch(e){setError(errorText(e))}};
  const deactivateProtocol=async()=>{if(!selectedProtocol)return;try{const p=await api.deactivateConsignmentProtocol(selectedProtocol.id) as Protocol;await load(p.id,selectedItemId,selectedEvidenceId);setNotice(`${p.code} quedó Inactivo.`)}catch(e){setError(errorText(e))}};
  const showHistory=async()=>{if(!selectedProtocol)return;try{setHistory(await api.consignmentProtocolHistory(selectedProtocol.id) as Protocol[])}catch(e){setError(errorText(e))}};

  const createItem=async()=>{
    if(!selectedProtocol||!isDraft)return;
    if(selectedProtocol.consignments.length>=MAX_CONSIGNMENTS_PER_PROTOCOL){setError(`Este Protocolo ya alcanzó el máximo de ${MAX_CONSIGNMENTS_PER_PROTOCOL} Consignas. Para continuar, cree un nuevo Protocolo.`);return}
    try{const i=await api.createConsignment(selectedProtocol.id,{title:'Nueva consigna'}) as Item;await load(selectedProtocol.id,i.id);setTab('CONSIGNA');setNotice(`${i.code} creada.`)}catch(e){setError(errorText(e))}
  };

  const saveItemInternal=async(reload=true)=>{
    if(!draft||!isDraft)return;
    const body={
      title:draft.title,
      instruction:draft.instruction,
      priority:draft.priority,
      scopeType:draft.scopeType,
      postIds:draft.postIds,
      validityType:draft.validityType,
      validityFrom:draft.validityFrom??null,
      validityUntil:draft.validityUntil??null,
      applicationType:draft.applicationType,
      applicationDaysJson:draft.applicationDaysJson,
      applicationTimeFrom:draft.applicationTimeFrom??null,
      applicationTimeTo:draft.applicationTimeTo??null,
      acknowledgmentRequired:draft.acknowledgmentRequired,
      confirmationRequired:draft.confirmationRequired,
      evidenceRequired:draft.evidenceRequired,
      gpsRequired:draft.gpsRequired,
      observationRequired:draft.observationRequired,
      expectedLocationMode:draft.expectedLocationMode,
      atsPackageId:draft.atsPackageId??null,
      atsX:draft.atsX??null,
      atsY:draft.atsY??null,
      expectedLatitude:draft.expectedLatitude??null,
      expectedLongitude:draft.expectedLongitude??null,
      expectedRadiusM:draft.expectedRadiusM??null,
    };
    const saved=await api.saveConsignment(draft.id,body) as Item;
    if(reload)await load(selectedProtocolId,saved.id,selectedEvidenceId);
  };

  const saveItem=async()=>{setSaving(true);try{await saveItemInternal();setNotice('Consigna guardada en el borrador.')}catch(e){setError(errorText(e))}finally{setSaving(false)}};
  const deleteItem=()=>{if(!draft||!isDraft)return;const itemId=draft.id;const protocolId=selectedProtocolId;setConfirmState({title:'Eliminar consigna',message:`Se eliminará “${draft.title||draft.code}” de este borrador. Esta acción no se puede deshacer.`,confirmLabel:'Eliminar consigna',tone:'danger',run:async()=>{await api.deleteConsignment(itemId);await load(protocolId);setNotice('Consigna eliminada del borrador.')}})};
  const createEvidence=async()=>{if(!draft||!isDraft)return;try{const e=await api.createConsignmentEvidence(draft.id,{name:'Nueva evidencia',evidenceType:'PHOTO',required:true}) as Evidence;await load(selectedProtocolId,draft.id,e.id);setTab('EVIDENCIAS')}catch(e){setError(errorText(e))}};
  const saveEvidence=async()=>{if(!selectedEvidence||!isDraft)return;try{const e=await api.saveConsignmentEvidence(selectedEvidence.id,{name:selectedEvidence.name,description:selectedEvidence.description,evidenceType:selectedEvidence.evidenceType,required:selectedEvidence.required,standardImageNotes:selectedEvidence.standardImageNotes,matchThreshold:selectedEvidence.matchThreshold??null}) as Evidence;await load(selectedProtocolId,draft!.id,e.id);setNotice('Evidencia guardada.')}catch(e){setError(errorText(e))}};
  const deleteEvidence=()=>{if(!selectedEvidence||!isDraft)return;const evidenceId=selectedEvidence.id;const itemId=draft?.id;setConfirmState({title:'Eliminar evidencia',message:`Se eliminará la evidencia “${selectedEvidence.name}”. La foto estándar asociada también se eliminará.`,confirmLabel:'Eliminar evidencia',tone:'danger',run:async()=>{await api.deleteConsignmentEvidence(evidenceId);await load(selectedProtocolId,itemId??undefined);setNotice('Evidencia eliminada.')}})};
  const uploadPhotos=async(files:File[])=>{
    if(!selectedEvidence||!isDraft||!files.length)return;setError('');
    const evidenceId=selectedEvidence.id;
    const r=await uploadStandardImages(files,selectedEvidence.standardImages.length,f=>api.uploadConsignmentStandardImage(evidenceId,f),
      (done,total)=>setNotice(total>1?`Subiendo foto estándar ${done} de ${total}…`:'Subiendo foto estándar…'),errorText);
    if(r.uploaded){await load(selectedProtocolId,draft!.id,evidenceId);setTab('EVIDENCIAS')}
    setNotice(r.uploaded?`${uploadSummary(r.uploaded)} Estándar versionado.`:'');if(r.problems.length)setError(r.problems.join(' · '));
  };
  const removePhoto=async(imageId:string)=>{if(!selectedEvidence||!isDraft)return;try{await api.deleteConsignmentStandardImage(selectedEvidence.id,imageId);await load(selectedProtocolId,draft!.id,selectedEvidence.id)}catch(e){setError(errorText(e))}};

  const updateEvidence=<K extends keyof Evidence>(key:K,value:Evidence[K])=>{
    if(!draft||!selectedEvidence)return;
    setDraft({...draft,evidences:draft.evidences.map(e=>e.id===selectedEvidence.id?{...e,[key]:value}:e)});
  };

  const chooseEvidence=(e:Evidence)=>{setSelectedEvidenceId(e.id);revokeStandard();};
  const onPlanClick=(ev:ReactMouseEvent<HTMLDivElement>)=>{if(!draft||!isDraft||draft.expectedLocationMode!=='ATS'||!planUrl)return;const r=ev.currentTarget.getBoundingClientRect();const x=Math.max(0,Math.min(1,(ev.clientX-r.left)/r.width));const y=Math.max(0,Math.min(1,(ev.clientY-r.top)/r.height));setDraft({...draft,atsPackageId:ats?.id??null,atsX:x,atsY:y})};
  const togglePost=(id:string)=>{if(!draft)return;setDraft({...draft,scopeType:'POSTS',postIds:draft.postIds.includes(id)?draft.postIds.filter(x=>x!==id):[...draft.postIds,id]})};
  const days=useMemo(()=>{try{return JSON.parse(draft?.applicationDaysJson||'[]') as string[]}catch{return []}},[draft?.applicationDaysJson]);
  const toggleDay=(code:string)=>{if(!draft)return;const next=days.includes(code)?days.filter(d=>d!==code):[...days,code];setDraft({...draft,applicationDaysJson:JSON.stringify(next)})};

  const runConfirm=async()=>{if(!confirmState||confirmBusy)return;setConfirmBusy(true);setError('');try{await confirmState.run();setConfirmState(null)}catch(e){setError(errorText(e))}finally{setConfirmBusy(false)}};

  const protocolStateClass=(status:ProtocolStatus)=>status==='ACTIVO'?'vigente':status==='BORRADOR'?'borrador':'suspendido';
  const detailTitle=selectedProtocol?`${selectedProtocol.code} · ${protocolName||selectedProtocol.name}`:'';
  const detailSubtitle=selectedProtocol?`v${selectedProtocol.versionNo} · ${statusLabel(selectedProtocol.status)} · ${protocolCoverageCount(selectedProtocol,point.posts)}/${point.posts.length} Puestos con alcance`:'';

  return <div className="services-v01 consignas-config-page bitacora-config-page">
    <div className="ser-breadcrumbs"><button onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración</button><span>/</span><small>Consignas</small></div>
    <div className="bit-titlebar">
      <div>
        <div className="ser-title-row"><h2>Consignas</h2><span>Configuración de instrucciones operativas por Protocolo</span></div>
        <small>Homologado a la gramática visual de Bitácora y Patrullas. Un solo Protocolo de Consignas puede estar Activo por Punto.</small>
      </div>
      <div className="bit-title-actions"><button disabled><ClipboardList size={15}/>Modelos de Compañía</button><button disabled><ShieldCheck size={15}/>Estándares Cajamarca</button></div>
    </div>

    <div className="bit-context panel"><div><span>Compañía</span><strong>{point.companyName}</strong></div><div><span>Servicio</span><strong>{point.serviceName}</strong></div><div><span>Cliente</span><strong>{point.clientName}</strong></div><div><span>Punto</span><strong>{point.pointName}</strong></div><div><span>Plano ATS</span><strong>{ats?`r${ats.revisionNo} · vigente`:'No disponible'}</strong></div></div>

    <div className="ser-kpis bit-kpis">
      <article><span className="ser-kpi-icon blue"><MapPin/></span><div><small>Puestos</small><strong>{point.posts.length}</strong><em>total en el punto</em></div></article>
      <article><span className="ser-kpi-icon green"><ClipboardList/></span><div><small>Protocolos configurados</small><strong>{protocolCount}</strong><em>series de consignas</em></div></article>
      <article><span className="ser-kpi-icon amber"><CheckCircle2/></span><div><small>Consignas</small><strong>{selectedProtocol?.consignments.length??0}</strong><em>en el protocolo seleccionado</em></div></article>
      <article><span className="ser-kpi-icon blue"><FileImage/></span><div><small>Con evidencia</small><strong>{evidenceCount}</strong><em>requieren evidencia</em></div></article>
      <article><span className="ser-kpi-icon green"><MapPin/></span><div><small>Con GPS</small><strong>{gpsCount}</strong><em>GPS de ejecución</em></div></article>
    </div>

    {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>setError('')}>Cerrar</button></div>}
    {notice&&<div className="posts-notice"><Info size={15}/><span>{notice}</span><button className="icon-only" onClick={()=>setNotice('')}><X size={14}/></button></div>}

    <div className={`bit-main-grid${postsCollapsed?' posts-collapsed':''}`}>
      <section className="ser-table-card bit-posts-card"><header><div><h3>Puestos del Punto</h3><span>{postsCollapsed&&selectedPost?`Seleccionado: ${selectedPost.postCode} · ${selectedPost.postName}`:'Seleccione un Puesto para configurar sus Protocolos'}</span></div><button type="button" className="bit-posts-toggle" aria-expanded={!postsCollapsed} aria-controls="consignas-post-list-content" aria-label={postsCollapsed?'Expandir panel Puestos del Punto':'Contraer panel Puestos del Punto'} title={postsCollapsed?'Expandir panel':'Contraer panel'} onClick={()=>setPostsCollapsed(value=>!value)}>{postsCollapsed?<ChevronRight size={16}/>:<ChevronLeft size={16}/>}</button></header><div id="consignas-post-list-content" hidden={postsCollapsed}><div className="bit-search"><Search size={15}/><input value={postQuery} onChange={e=>setPostQuery(e.target.value)} placeholder="Buscar puesto por código o nombre…"/></div><div className="bit-post-list">{visiblePosts.map(post=>{const protocolRows=protocols.filter(protocol=>protocolAppliesToPost(protocol,post.postId));return <button key={post.postId} className={selectedPostId===post.postId?'selected':''} onClick={()=>selectPost(post.postId)}><span className="bit-post-icon"><ShieldCheck size={17}/></span><div><strong>{post.postCode}</strong><span>{post.postName}</span><small>{protocolRows.length} protocolo{protocolRows.length===1?'':'s'} · {protocolRows.length?'Configurado':'Pendiente'}</small></div><span className={`bit-status-dot ${protocolRows.length?'good':'warn'}`}/><ChevronRight size={15}/></button>})}</div></div></section>

      <section className="ser-table-card bit-protocols-card"><header><div><h3>Protocolos del Punto</h3><span>{selectedPost?`${selectedPost.postCode} · ${selectedPost.postName}`:'Seleccione un Puesto'}</span></div></header><div className="bit-protocol-actions"><button className="primary" onClick={()=>void createProtocol()}><Plus size={14}/>Crear protocolo</button><button disabled><Upload size={14}/>Importar protocolo</button></div><div className="bit-search"><Search size={15}/><input value={protocolQuery} onChange={e=>setProtocolQuery(e.target.value)} placeholder="Buscar protocolo por código o nombre…"/></div><div className="bit-protocol-table-wrap"><table className="bit-protocol-table"><thead><tr><th>Código</th><th>Protocolo</th><th>Consignas</th><th>Estado</th></tr></thead><tbody>{loading?<tr><td colSpan={4}>Cargando…</td></tr>:filteredProtocols.length?filteredProtocols.map(protocol=>{const coverage=protocolCoverageCount(protocol,point.posts);return <tr key={protocol.id} className={protocol.id===selectedProtocolId?'selected':''} onClick={()=>chooseProtocol(protocol)}><td>{protocol.code}</td><td><strong>{protocol.name}</strong><small>{coverage===point.posts.length?'Todo el punto':`${coverage}/${point.posts.length} Puestos`}</small></td><td>{protocol.consignments.length}</td><td><span className={`bit-protocol-state ${protocolStateClass(protocol.status)}`}>{statusLabel(protocol.status)}</span></td></tr>}):<tr><td colSpan={4} className="bit-empty-cell">No hay Protocolos para este criterio.</td></tr>}</tbody></table></div></section>

      <section className="ser-table-card bit-detail-card">{!selectedProtocol?<div className="bit-detail-empty"><ShieldCheck size={30}/><strong>Seleccione o cree un Protocolo</strong><span>La configuración principal aparecerá aquí.</span></div>:<>
        <header className="bit-detail-header"><div><h3>{detailTitle}</h3><small className="bit-contextual-note">{detailSubtitle}</small></div><div>{isDraft?<button onClick={()=>void showHistory()}><History size={14}/>Historial</button>:<button onClick={()=>void showHistory()}><History size={14}/>Historial</button>}{isDraft?<button onClick={()=>void saveProtocol()} disabled={saving}><Save size={14}/>Guardar borrador</button>:<button onClick={()=>void forkProtocol()}><Plus size={14}/>Crear nueva versión</button>}{isDraft?<button className="primary" onClick={()=>void publishProtocol()} disabled={saving}><ShieldCheck size={14}/>Publicar versión</button>:selectedProtocol.status==='INACTIVO'?<button className="primary" onClick={()=>void activateProtocol()}><CheckCircle2 size={14}/>Activar protocolo</button>:<button onClick={()=>void deactivateProtocol()}><ShieldCheck size={14}/>Inactivar protocolo</button>}</div></header>

        <div className="bit-accreditation-strip"><div className="bit-accreditation-label"><ClipboardList size={16}/><div><strong>Consignas ({selectedProtocol.consignments.length}/{MAX_CONSIGNMENTS_PER_PROTOCOL})</strong><span>Máximo {MAX_CONSIGNMENTS_PER_PROTOCOL} por Protocolo. Si necesita más, cree un nuevo Protocolo.</span></div></div><div className="bit-accreditation-options">{selectedProtocol.consignments.map(item=><button key={item.id} className={item.id===selectedItemId?'active':''} onClick={()=>chooseItem(item)}><b>{item.code}</b><span>{item.title||'Sin título'}</span></button>)}<button className="add" onClick={()=>void createItem()} disabled={!isDraft||selectedProtocol.consignments.length>=MAX_CONSIGNMENTS_PER_PROTOCOL}><Plus size={14}/>Nueva consigna</button></div></div>

        <div className="bit-step-tabs-rail"><div className="bit-step-tabs" ref={stepTabsRef}>{TABS.map(item=><button key={item.key} className={tab===item.key?'active':''} onClick={()=>setTab(item.key)}><b>{item.short}</b>{item.label}</button>)}</div></div>
        <div className="bit-version-lock"><ShieldCheck size={14}/><span>{isDraft?'Borrador editable. Al publicar, la versión pasa a Inactivo y queda inmutable.':'Versión publicada e inmutable. Para modificar su contenido, cree una nueva versión del Protocolo.'}</span></div>

        <div className="bit-detail-body"><fieldset className="bit-version-fieldset" disabled={!isDraft}>
          {tab==='DEFINICION'&&<div className="bit-section"><div className="bit-section-title"><div><b>Definición del Protocolo</b><span>Defina la serie, su descripción y el contexto general del Protocolo de Consignas.</span></div></div><div className="bit-definition-grid"><label><span>Código</span><input value={selectedProtocol.code} disabled/></label><label className="wide"><span>Nombre del protocolo</span><input value={protocolName} onChange={e=>setProtocolName(e.target.value)}/></label><label className="full"><span>Descripción</span><textarea value={protocolDescription} onChange={e=>setProtocolDescription(e.target.value)} placeholder="Describa el objetivo operacional del Protocolo"/></label></div><div className="bit-section-title"><div><b>Cobertura por Puesto</b><span>La cobertura del Protocolo depende del alcance configurado en sus Consignas.</span></div></div><div className="bit-list-cards">{point.posts.map(post=>{const covered=protocolAppliesToPost(selectedProtocol,post.postId);return <article key={post.postId}><ShieldCheck size={17}/><div><strong>{post.postCode}</strong><span>{post.postName}</span></div><em>{covered?'Con alcance':'Sin alcance'}</em></article>})}</div><div className="bit-info-box"><Info size={15}/><span>En Consignas, un mismo Protocolo puede contener instrucciones que apliquen a todo el Punto o solo a Puestos específicos.</span></div></div>}

          {tab==='CONSIGNA'&&draft&&<div className="bit-section"><div className="bit-section-title"><div><b>Consigna</b><span>Defina la instrucción operativa principal de la Consigna seleccionada.</span></div>{isDraft&&<button className="bit-accreditation-delete" type="button" onClick={()=>void deleteItem()}><Trash2 size={13}/>Eliminar consigna</button>}</div><div className="bit-definition-grid"><label><span>Código</span><input value={draft.code} disabled/></label><label className="wide"><span>Título</span><input value={draft.title} onChange={e=>setDraft({...draft,title:e.target.value})}/></label><label><span>Prioridad</span><select value={draft.priority} onChange={e=>setDraft({...draft,priority:e.target.value as Item['priority']})}><option value="LOW">Baja</option><option value="MEDIUM">Media</option><option value="HIGH">Alta</option><option value="CRITICAL">Crítica</option></select></label><label className="full"><span>Instrucción / Descripción</span><textarea value={draft.instruction} onChange={e=>setDraft({...draft,instruction:e.target.value})} maxLength={500} placeholder="Redacte la instrucción operativa que deberá seguir el Agente"/></label></div><span className="con-counter">{draft.instruction.length}/500</span><div className="bit-info-box"><Info size={15}/><span>Use la Consigna para definir una instrucción concreta, observable y ejecutable por el Agente en operación.</span></div></div>}

          {tab==='ALCANCE'&&draft&&<div className="bit-section"><div className="bit-section-title"><div><b>Alcance</b><span>Defina si la Consigna aplica a todo el Punto o solamente a Puestos específicos.</span></div></div><div className="con-option-card"><label><input type="radio" checked={draft.scopeType==='POINT'} onChange={()=>setDraft({...draft,scopeType:'POINT',postIds:[]})}/><div><strong>Todo el Punto</strong><span>La Consigna aplica a cualquier Puesto del Punto.</span></div></label><label><input type="radio" checked={draft.scopeType==='POSTS'} onChange={()=>setDraft({...draft,scopeType:'POSTS'})}/><div><strong>Puestos específicos</strong><span>Seleccione todos los Puestos a los que debe aplicar esta Consigna.</span></div></label></div>{draft.scopeType==='POSTS'&&<div className="bit-list-cards">{point.posts.map(post=><label key={post.postId}><input type="checkbox" checked={draft.postIds.includes(post.postId)} onChange={()=>togglePost(post.postId)}/><div><strong>{post.postCode}</strong><span>{post.postName}</span></div><em>{draft.postIds.includes(post.postId)?'Incluido':'No incluido'}</em></label>)}</div>}<div className="bit-info-box"><Info size={15}/><span>Alcance actual: <strong>{scopeLabel(draft,point.posts)}</strong>.</span></div></div>}

          {tab==='APLICACION'&&draft&&<div className="bit-section"><div className="bit-section-title"><div><b>Aplicación</b><span>Configure vigencia, calendario y reglas operativas complementarias para esta Consigna.</span></div></div><div className="con-v109-grid4"><section><span>Vigencia</span><div className="con-pill-toggle"><button type="button" className={draft.validityType==='PERMANENT'?'active':''} onClick={()=>setDraft({...draft,validityType:'PERMANENT',validityFrom:null,validityUntil:null})}>Permanente</button><button type="button" className={draft.validityType==='TEMPORARY'?'active':''} onClick={()=>setDraft({...draft,validityType:'TEMPORARY'})}>Temporal</button></div></section><section><span>Aplicación</span><div className="con-pill-toggle"><button type="button" className={draft.applicationType==='ALL_TIME'?'active':''} onClick={()=>setDraft({...draft,applicationType:'ALL_TIME',applicationDaysJson:'[]',applicationTimeFrom:null,applicationTimeTo:null})}>Todo el tiempo</button><button type="button" className={draft.applicationType==='CALENDAR'?'active':''} onClick={()=>setDraft({...draft,applicationType:'CALENDAR'})}>Calendario</button></div></section><section><span>Ubicación esperada</span><select value={draft.expectedLocationMode} onChange={e=>setDraft({...draft,expectedLocationMode:e.target.value as Item['expectedLocationMode'],atsX:null,atsY:null,expectedLatitude:null,expectedLongitude:null})}><option value="NONE">Sin ubicación</option><option value="ATS">Plano ATS</option><option value="GPS">Coordenadas GPS</option></select></section><section><span>Resumen</span><div className="bit-result-card"><b>Aplicación actual</b><div><span className="good">{applicationLabel(draft.applicationType)}</span><span className="warn">{validityLabel(draft.validityType)}</span></div><small>{locationModeLabel(draft.expectedLocationMode)}</small></div></section></div>{draft.validityType==='TEMPORARY'&&<div className="con-date-row"><label><span>Vigente desde</span><input type="datetime-local" value={toInput(draft.validityFrom)} onChange={e=>setDraft({...draft,validityFrom:e.target.value?new Date(e.target.value).toISOString():null})}/></label><label><span>Vigente hasta</span><input type="datetime-local" value={toInput(draft.validityUntil)} onChange={e=>setDraft({...draft,validityUntil:e.target.value?new Date(e.target.value).toISOString():null})}/></label></div>}{draft.applicationType==='CALENDAR'&&<><div className="con-days">{DAYS.map(([code,label])=><button type="button" key={code} className={days.includes(code)?'active':''} onClick={()=>toggleDay(code)}>{label}</button>)}</div><div className="con-date-row"><label><span>Hora desde</span><input type="time" value={draft.applicationTimeFrom??''} onChange={e=>setDraft({...draft,applicationTimeFrom:e.target.value})}/></label><label><span>Hora hasta</span><input type="time" value={draft.applicationTimeTo??''} onChange={e=>setDraft({...draft,applicationTimeTo:e.target.value})}/></label></div></>}<div className="bit-toggle-list"><label><div><strong>Acuse de lectura</strong><span>El Agente debe confirmar que leyó la Consigna.</span></div><input type="checkbox" checked={draft.acknowledgmentRequired} onChange={e=>setDraft({...draft,acknowledgmentRequired:e.target.checked})}/></label><label><div><strong>Confirmación de cumplimiento</strong><span>Se exige confirmación expresa de ejecución.</span></div><input type="checkbox" checked={draft.confirmationRequired} onChange={e=>setDraft({...draft,confirmationRequired:e.target.checked})}/></label><label><div><strong>GPS requerido</strong><span>Debe registrarse georreferencia de la ejecución.</span></div><input type="checkbox" checked={draft.gpsRequired} onChange={e=>setDraft({...draft,gpsRequired:e.target.checked})}/></label><label><div><strong>Observación obligatoria</strong><span>El Agente debe dejar comentario u observación.</span></div><input type="checkbox" checked={draft.observationRequired} onChange={e=>setDraft({...draft,observationRequired:e.target.checked})}/></label></div>{draft.expectedLocationMode==='ATS'&&<div className="con-mini-map" onClick={onPlanClick}>{planUrl?<><img src={planUrl} alt="Plano ATS"/>{draft.atsX!=null&&draft.atsY!=null&&<span className="con-map-marker" style={{left:`${draft.atsX*100}%`,top:`${draft.atsY*100}%`}}><MapPin size={18}/></span>}<em>Haga clic sobre el plano para ubicar la Consigna.</em></>:<span>Plano ATS no disponible.</span>}</div>}{draft.expectedLocationMode==='GPS'&&<div className="con-date-row"><label><span>Latitud</span><input type="number" step="0.000001" value={draft.expectedLatitude??''} onChange={e=>setDraft({...draft,expectedLatitude:e.target.value===''?null:Number(e.target.value)})}/></label><label><span>Longitud</span><input type="number" step="0.000001" value={draft.expectedLongitude??''} onChange={e=>setDraft({...draft,expectedLongitude:e.target.value===''?null:Number(e.target.value)})}/></label><RadiusField value={draft.expectedRadiusM} disabled={!isDraft} onChange={v=>setDraft({...draft,expectedRadiusM:v})}/></div>}</div>}

          {tab==='EVIDENCIAS'&&draft&&<div className="bit-section"><div className="bit-section-title"><div><b>Evidencias</b><span>Configure las evidencias y sus fotos estándar (hasta 5). VISINT valida las evidencias tipo Foto.</span></div>{isDraft&&<button type="button" className="primary" onClick={()=>void createEvidence()}><Plus size={14}/>Agregar evidencia</button>}</div><div className="bit-evidence-summary"><article><FileImage size={18}/><strong>{draft.evidences.length}</strong><span>Evidencias configuradas</span></article><article><Camera size={18}/><strong>{draft.evidences.filter(e=>e.hasStandardImage).length}</strong><span>con Foto estándar</span></article><article><ShieldCheck size={18}/><strong>{draft.evidences.filter(e=>e.required).length}</strong><span>obligatorias</span></article></div><div className="con-evidence-grid"><div className="con-evidence-list">{draft.evidences.map(e=><button type="button" key={e.id} className={e.id===selectedEvidenceId?'selected':''} onClick={()=>chooseEvidence(e)}><FileImage size={16}/><div><strong>{e.name}</strong><span>{e.required?'Obligatoria':'Opcional'} · {e.hasStandardImage?`Estándar v${e.standardImageVersion}`:'Sin estándar'}</span></div><ChevronRight size={14}/></button>)}{!draft.evidences.length&&<p>No hay evidencias configuradas.</p>}</div>{selectedEvidence?<div className="con-evidence-editor"><label><span>Nombre</span><input value={selectedEvidence.name} onChange={e=>updateEvidence('name',e.target.value)}/></label><label><span>Descripción</span><textarea value={selectedEvidence.description} onChange={e=>updateEvidence('description',e.target.value)}/></label><div className="con-date-row"><label><span>Tipo</span><select value={selectedEvidence.evidenceType} onChange={e=>updateEvidence('evidenceType',e.target.value as Evidence['evidenceType'])}><option value="PHOTO">Foto</option><option value="DOCUMENT">Documento</option><option value="TEXT">Texto</option><option value="CONFIRMATION">Confirmación</option></select></label><label className="con-check"><input type="checkbox" checked={selectedEvidence.required} onChange={e=>updateEvidence('required',e.target.checked)}/><span>Obligatoria</span></label></div><div className="con-standard-photo"><strong>Fotos estándar</strong><small>Hasta 5. JPG / PNG / WebP · máx. 5 MB</small><StandardGallery images={selectedEvidence.standardImages} load={id=>api.consignmentStandardImage(selectedEvidence.id,id) as Promise<Blob>} editable={isDraft} onAdd={()=>photoInput.current?.click()} onRemove={id=>void removePhoto(id)}/><MatchThresholdField value={selectedEvidence.matchThreshold} onChange={v=>updateEvidence('matchThreshold',v)} disabled={!isDraft||selectedEvidence.evidenceType!=='PHOTO'} disabledReason={selectedEvidence.evidenceType!=='PHOTO'?'VISINT solo valida evidencias de tipo Foto.':'Versión publicada: crea una nueva versión para cambiar el umbral.'}/><label><span>Notas del estándar</span><textarea value={selectedEvidence.standardImageNotes} onChange={e=>updateEvidence('standardImageNotes',e.target.value)}/></label><div className="bit-info-box"><ShieldCheck size={16}/><span>{selectedEvidence.evidenceType==='PHOTO'?<><b>VISINT</b> — compara la foto del agente con estas fotos estándar. Se necesita al menos 1 para publicar.</>:<><b>VISINT</b> — solo valida evidencias tipo Foto.</>}</span></div></div><div className="con-evidence-actions">{isDraft&&<button type="button" onClick={()=>void saveEvidence()}><Save size={14}/>Guardar evidencia</button>}{isDraft&&<button type="button" className="danger-ghost" onClick={()=>void deleteEvidence()}><Trash2 size={14}/>Eliminar</button>}</div></div>:<div className="con-empty-editor">Seleccione una evidencia para configurarla.</div>}</div></div>}

          {tab==='TRAZABILIDAD'&&<div className="bit-section"><div className="bit-section-title"><div><b>Trazabilidad</b><span>Resumen de versión, autoría y estructura del Protocolo de Consignas.</span></div></div><div className="bit-trace-grid"><div><CheckCircle2 size={14}/><span>{selectedProtocol.code} · v{selectedProtocol.versionNo}</span></div><div><CheckCircle2 size={14}/><span>{selectedProtocol.consignments.length} Consignas</span></div><div><CheckCircle2 size={14}/><span>{selectedProtocol.updatedBy?`Último usuario: ${selectedProtocol.updatedBy}`:'Usuario no disponible'}</span></div><div><CheckCircle2 size={14}/><span>{selectedProtocol.publishedAt?`Publicado: ${new Date(selectedProtocol.publishedAt).toLocaleString('es-EC')}`:'Borrador no publicado'}</span></div><div><CheckCircle2 size={14}/><span>{currentProtocol?`Protocolo Activo actual: ${currentProtocol.code}`:'Sin protocolo Activo'}</span></div><div><CheckCircle2 size={14}/><span>{draft?`${draft.code} · ${priorityLabel(draft.priority)}`:'Sin consigna seleccionada'}</span></div></div><div className="pat-version-card"><History/><div><strong>Versionado inmutable</strong><p>Todo el Protocolo es un snapshot: Consignas, Alcance, Aplicación, Evidencias y Fotos estándar. Para modificar contenido ya publicado, cree una nueva versión.</p><button type="button" onClick={()=>void showHistory()}>Ver historial de versiones</button></div></div></div>}
        </fieldset></div>

        {draft&&<div className="con-v109-footer-actions">{isDraft&&<button className="primary" onClick={()=>void saveItem()} disabled={saving}><Save size={14}/>Guardar consigna</button>}<span>{draft.code} · {priorityLabel(draft.priority)} · {scopeLabel(draft,point.posts)}</span></div>}
      </>}</section>
    </div>
    <input ref={photoInput} hidden type="file" accept="image/png,image/jpeg,image/webp" multiple onChange={e=>{const files=Array.from(e.currentTarget.files??[]);e.currentTarget.value='';void uploadPhotos(files)}}/>

    {history&&<div className="pat-modal-backdrop" onClick={()=>setHistory(null)}><section className="pat-history-modal" onClick={e=>e.stopPropagation()}><header><div><h3>Historial · {selectedProtocol?.code}</h3><span>Las versiones publicadas conservan su snapshot completo.</span></div><button onClick={()=>setHistory(null)}><X size={18}/></button></header><div>{history.map(v=><article key={v.id}><div><strong>v{v.versionNo}</strong><span className={`con-state ${v.status.toLowerCase()}`}>{statusLabel(v.status)}</span></div><div><b>{v.name}</b><small>{v.consignments.length} Consignas</small></div><small>{v.publishedAt?new Date(v.publishedAt).toLocaleString('es-EC'):'Borrador no publicado'}</small></article>)}</div></section></div>}
    {confirmState&&<ConfirmDialog state={confirmState} busy={confirmBusy} onCancel={()=>!confirmBusy&&setConfirmState(null)} onConfirm={()=>void runConfirm()}/>}  
  </div>
}

function ConfirmDialog({state,busy,onCancel,onConfirm}:{state:ConfirmState;busy:boolean;onCancel:()=>void;onConfirm:()=>void}){
  return <div className="sgi-confirm-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)onCancel()}}><section className={`sgi-confirm-dialog ${state.tone}`} role="dialog" aria-modal="true" aria-labelledby="sgi-confirm-title"><div className="sgi-confirm-icon"><AlertTriangle size={22}/></div><div className="sgi-confirm-copy"><h3 id="sgi-confirm-title">{state.title}</h3><p>{state.message}</p></div><div className="sgi-confirm-actions"><button onClick={onCancel} disabled={busy}>Cancelar</button><button className={state.tone==='danger'?'danger':'warning'} onClick={onConfirm} disabled={busy}>{busy?'Procesando…':state.confirmLabel}</button></div></section></div>;
}
