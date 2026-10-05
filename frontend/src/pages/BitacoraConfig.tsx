import {useEffect,useMemo,useRef,useState} from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  BadgeCheck,
  Camera,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  ClipboardList,
  FileImage,
  FilePlus2,
  FileText,
  History,
  Image as ImageIcon,
  Info,
  KeyRound,
  Plus,
  Save,
  Search,
  ShieldCheck,
  Sparkles,
  Trash2,
  Upload,
  Users,
  X,
} from 'lucide-react';
import StandardGallery,{type StandardImageRef,uploadStandardImages,uploadSummary} from '../components/StandardGallery';
import MatchThresholdField from '../components/MatchThresholdField';
import {api} from '../api';

type PointPost={postId:string;postCode:string;postName:string;tier:string;state:string};
type PointContext={pointId:string;pointName:string;pointCode:string;clientName:string;companyName:string;serviceName:string;serviceCode:string;posts:PointPost[]};

type FieldSection='IDENTIFICACION'|'VERIFICACION';
type FieldType='DOCUMENTO'|'IMAGEN'|'TEXTO'|'SELECCION'|'CODIGO'|'OTRO';
type CaptureMode='MANUAL'|'CAMARA'|'MANUAL_QR'|'QR'|'CODIGO_BARRAS'|'NFC';
type ProtocolStatus='BORRADOR'|'INACTIVO'|'ACTIVO';

type ProtocolField={
  id:string;accreditationId:string;section:FieldSection;sortOrder:number;name:string;description:string;fieldType:FieldType;required:boolean;evidenceRequired:boolean;captureMode:CaptureMode;customField:boolean;
  hasStandardImage:boolean;standardImages:StandardImageRef[];standardImageVersion:number;standardImageNotes?:string|null;visintEnabled:boolean;matchThreshold?:number|null;
};
type Accreditation={
  id:string;protocolId:string;code:string;name:string;description:string;identificationLogic:'ALL'|'ANY';verificationLogic:'ALL'|'ANY';
  authPreapproval:boolean;authClient:boolean;authSupervisor:boolean;whiteListEnabled:boolean;blackListEnabled:boolean;
  captureManual:boolean;captureQr:boolean;captureBarcode:boolean;captureNfc:boolean;captureAutomatic:boolean;fields:ProtocolField[];
};
type Protocol={
  id:string;seriesId:string;basedOnProtocolId?:string|null;postId:string;code:string;name:string;objectType:'PAX'|'VHL'|'CONT';applicationType:'INGRESO'|'EGRESO'|'AMBOS';description:string;status:ProtocolStatus;versionNo:number;
  sourceModelType:string;sourceModelName?:string|null;lastPublishedAt?:string|null;updatedBy?:string|null;applicablePostIds:string[];accreditations:Accreditation[];
};
type DetailTab='DEFINICION'|'ACREDITACION'|'IDENTIFICACION'|'VERIFICACION'|'AUTORIZACION'|'EVIDENCIAS'|'CAPTURA'|'LISTAS'|'TRAZABILIDAD';
type FieldDraft={
  id?:string;section:FieldSection;sortOrder:number;name:string;description:string;fieldType:FieldType;required:boolean;evidenceRequired:boolean;captureMode:CaptureMode;customField:boolean;standardImageNotes:string;standardImages?:StandardImageRef[];matchThreshold?:number|null;
};
type ConfirmState={title:string;message:string;confirmLabel:string;tone:'danger'|'warning';run:()=>Promise<void>};

const TABS:{key:DetailTab;label:string;short:string}[]=[
  {key:'DEFINICION',label:'Definición',short:'1'},
  {key:'ACREDITACION',label:'Acreditación',short:'2'},
  {key:'IDENTIFICACION',label:'Identificación',short:'3'},
  {key:'VERIFICACION',label:'Verificación',short:'4'},
  {key:'AUTORIZACION',label:'Autorización',short:'5'},
  {key:'EVIDENCIAS',label:'Evidencias',short:'6'},
  {key:'CAPTURA',label:'Captura',short:'7'},
  {key:'LISTAS',label:'Listas',short:'8'},
  {key:'TRAZABILIDAD',label:'Trazabilidad',short:'9'},
];

function appLabel(value:Protocol['applicationType']){return value==='INGRESO'?'Ingreso':value==='EGRESO'?'Egreso':'Ambos'}
function statusLabel(value:ProtocolStatus){return value==='BORRADOR'?'Borrador':value==='ACTIVO'?'Activo':'Inactivo'}
function fieldTypeLabel(value:FieldType){return ({DOCUMENTO:'Documento',IMAGEN:'Imagen',TEXTO:'Texto',SELECCION:'Selección',CODIGO:'Código',OTRO:'Otro'})[value]}
function captureLabel(value:CaptureMode){return ({MANUAL:'Manual',CAMARA:'Cámara',MANUAL_QR:'Manual / QR',QR:'QR',CODIGO_BARRAS:'Código de barras',NFC:'NFC'})[value]}
function errorText(error:unknown){return error instanceof Error?error.message:String(error)}
function cloneProtocol(protocol:Protocol):Protocol{return {...protocol,applicablePostIds:[...(protocol.applicablePostIds?.length?protocol.applicablePostIds:[protocol.postId])],accreditations:protocol.accreditations.map(acc=>({...acc,fields:acc.fields.map(field=>({...field}))}))}}
function applicablePosts(protocol:Protocol){return protocol.applicablePostIds?.length?protocol.applicablePostIds:[protocol.postId]}
function appliesToPost(protocol:Protocol,postId:string){return !postId||applicablePosts(protocol).includes(postId)}
function statusForPost(protocol:Protocol,postId:string):ProtocolStatus{return appliesToPost(protocol,postId)?protocol.status:'INACTIVO'}
function scopeSummary(protocol:Protocol,posts:PointPost[]){const labels=posts.filter(post=>applicablePosts(protocol).includes(post.postId)).map(post=>post.postCode);if(!labels.length)return 'Sin puestos';return labels.length===posts.length?'Todo el punto':labels.join(', ')}

export default function BitacoraConfig({point,onBack}:{point:PointContext;onBack:()=>void}){
  const [protocols,setProtocols]=useState<Protocol[]>([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [postQuery,setPostQuery]=useState('');
  const [postsCollapsed,setPostsCollapsed]=useState(false);
  const [selectedPostId,setSelectedPostId]=useState(point.posts[0]?.postId??'');
  const [selectedProtocolId,setSelectedProtocolId]=useState('');
  const [selectedAccreditationId,setSelectedAccreditationId]=useState('');
  const [draft,setDraft]=useState<Protocol|null>(null);
  const [tab,setTab]=useState<DetailTab>('DEFINICION');
  const stepTabsRef=useRef<HTMLDivElement>(null);
  const [fieldDraft,setFieldDraft]=useState<FieldDraft|null>(null);
  const [fieldImageUrl,setFieldImageUrl]=useState('');
  const fieldImageUrlRef=useRef('');
  const [pendingImage,setPendingImage]=useState<File|null>(null);
  const [savingField,setSavingField]=useState(false);
  const [savingProtocol,setSavingProtocol]=useState(false);
  const [versionHistory,setVersionHistory]=useState<Protocol[]|null>(null);
  const [confirmState,setConfirmState]=useState<ConfirmState|null>(null);
  const [confirmBusy,setConfirmBusy]=useState(false);
  const [scopeBusy,setScopeBusy]=useState(false);

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
  },[tab,selectedProtocolId,draft?.code]);

  const setImageUrl=(value:string)=>{
    if(fieldImageUrlRef.current)URL.revokeObjectURL(fieldImageUrlRef.current);
    fieldImageUrlRef.current=value;
    setFieldImageUrl(value);
  };

  const load=async(preferProtocolId?:string,preferAccreditationId?:string)=>{
    setLoading(true);setError('');
    try{
      const rows=(await api.bitacoraProtocols(point.pointId) as Protocol[]).map(row=>({...row,applicablePostIds:row.applicablePostIds?.length?row.applicablePostIds:[row.postId]}));
      setProtocols(rows);
      const postId=selectedPostId||point.posts[0]?.postId||'';
      if(!selectedPostId&&postId)setSelectedPostId(postId);
      const candidates=rows.filter(row=>appliesToPost(row,postId));
      const selected=(preferProtocolId?rows.find(row=>row.id===preferProtocolId):rows.find(row=>row.id===selectedProtocolId))??candidates[0]??rows[0]??null;
      const cloned=selected?cloneProtocol(selected):null;
      const acc=cloned?.accreditations.find(row=>row.id===(preferAccreditationId||selectedAccreditationId))??cloned?.accreditations[0]??null;
      setSelectedProtocolId(cloned?.id??'');
      setSelectedAccreditationId(acc?.id??'');
      setDraft(cloned);
    }catch(e){setError(errorText(e))}finally{setLoading(false)}
  };
  useEffect(()=>{void load();return()=>{if(fieldImageUrlRef.current)URL.revokeObjectURL(fieldImageUrlRef.current)}},[point.pointId]);

  const selectedPost=point.posts.find(post=>post.postId===selectedPostId)??point.posts[0]??null;
  const selectedProtocols=useMemo(()=>[...protocols].sort((a,b)=>{const aApplies=appliesToPost(a,selectedPostId)?0:1;const bApplies=appliesToPost(b,selectedPostId)?0:1;return aApplies-bApplies||a.code.localeCompare(b.code)}),[protocols,selectedPostId]);
  const selectedAccreditation=draft?.accreditations.find(acc=>acc.id===selectedAccreditationId)??draft?.accreditations[0]??null;
  const isDraft=draft?.status==='BORRADOR';
  const postCounts=useMemo(()=>{
    const map=new Map<string,{count:number;activo:number;borrador:number}>();
    point.posts.forEach(post=>map.set(post.postId,{count:0,activo:0,borrador:0}));
    protocols.forEach(protocol=>{for(const postId of applicablePosts(protocol)){const row=map.get(postId)??{count:0,activo:0,borrador:0};row.count++;if(protocol.status==='ACTIVO')row.activo++;if(protocol.status==='BORRADOR')row.borrador++;map.set(postId,row)}});
    return map;
  },[point.posts,protocols]);
  const visiblePosts=point.posts.filter(post=>!postQuery.trim()||`${post.postCode} ${post.postName}`.toLowerCase().includes(postQuery.trim().toLowerCase()));
  const configuredPosts=point.posts.filter(post=>(postCounts.get(post.postId)?.count??0)>0).length;
  const activeProtocols=protocols.filter(protocol=>protocol.status==='ACTIVO').length;
  const draftProtocols=protocols.filter(protocol=>protocol.status==='BORRADOR').length;
  const importedProtocols=protocols.filter(protocol=>protocol.sourceModelType!=='LOCAL').length;
  const accreditationCount=protocols.reduce((sum,protocol)=>sum+protocol.accreditations.length,0);

  const selectPost=(postId:string)=>{
    setSelectedPostId(postId);
    const first=protocols.find(protocol=>appliesToPost(protocol,postId))??protocols[0]??null;
    setSelectedProtocolId(first?.id??'');
    setSelectedAccreditationId(first?.accreditations[0]?.id??'');
    setDraft(first?cloneProtocol(first):null);
    setTab('DEFINICION');closeField();
  };
  const selectProtocol=(protocol:Protocol)=>{
    setSelectedProtocolId(protocol.id);setSelectedAccreditationId(protocol.accreditations[0]?.id??'');setDraft(cloneProtocol(protocol));setTab('DEFINICION');closeField();
  };
  const selectAccreditation=(accreditationId:string)=>{setSelectedAccreditationId(accreditationId);setTab('ACREDITACION');closeField()};

  const createProtocol=async()=>{
    if(!selectedPostId)return;
    setError('');setNotice('');
    try{
      const created=await api.createBitacoraProtocol({postId:selectedPostId,name:'Nuevo protocolo',objectType:'PAX',applicationType:'INGRESO'}) as Protocol;
      await load(created.id,created.accreditations[0]?.id);
      setTab('DEFINICION');setNotice(`Se creó ${created.code} con su acreditación inicial.`);
    }catch(e){setError(errorText(e))}
  };
  const createAccreditation=async()=>{
    if(!draft)return;
    if(draft.accreditations.length>=10){setError('Este Protocolo ya alcanzó el máximo de 10 acreditaciones. Para continuar, crea un nuevo Protocolo.');return}
    setError('');setNotice('');
    try{
      const created=await api.createBitacoraAccreditation(draft.id,{name:'Nueva acreditación',description:''}) as Accreditation;
      await load(draft.id,created.id);setTab('ACREDITACION');setNotice(`Se creó ${created.code}.`);
    }catch(e){setError(errorText(e))}
  };
  const deleteAccreditation=()=>{
    if(!draft||!selectedAccreditation||draft.accreditations.length<=1)return;
    const protocolId=draft.id;const accreditationId=selectedAccreditation.id;const name=selectedAccreditation.name;
    setConfirmState({title:'Eliminar acreditación',message:`Se eliminará “${name}” de este borrador. Esta acción no se puede deshacer.`,confirmLabel:'Eliminar acreditación',tone:'danger',run:async()=>{await api.deleteBitacoraAccreditation(accreditationId);await load(protocolId);setTab('ACREDITACION');setNotice('Acreditación eliminada.')}});
  };

  const protocolBody=()=>draft?{name:draft.name,objectType:draft.objectType,applicationType:draft.applicationType,description:draft.description,status:draft.status,applicablePostIds:draft.applicablePostIds}:null;
  const accreditationBody=()=>selectedAccreditation?{
    name:selectedAccreditation.name,description:selectedAccreditation.description,identificationLogic:selectedAccreditation.identificationLogic,verificationLogic:selectedAccreditation.verificationLogic,
    authPreapproval:selectedAccreditation.authPreapproval,authClient:selectedAccreditation.authClient,authSupervisor:selectedAccreditation.authSupervisor,
    whiteListEnabled:selectedAccreditation.whiteListEnabled,blackListEnabled:selectedAccreditation.blackListEnabled,captureManual:true,captureQr:selectedAccreditation.captureQr,
    captureBarcode:selectedAccreditation.captureBarcode,captureNfc:selectedAccreditation.captureNfc,captureAutomatic:selectedAccreditation.captureAutomatic,
  }:null;
  const saveProtocol=async()=>{
    if(!draft)return;
    setSavingProtocol(true);setError('');setNotice('');
    try{
      await api.saveBitacoraProtocol(draft.id,protocolBody());
      if(selectedAccreditation)await api.saveBitacoraAccreditation(selectedAccreditation.id,accreditationBody());
      await load(draft.id,selectedAccreditation?.id);setNotice('Configuración guardada correctamente.');
    }catch(e){setError(errorText(e))}finally{setSavingProtocol(false)}
  };
  const publishProtocol=async()=>{
    if(!draft)return;
    setSavingProtocol(true);setError('');setNotice('');
    try{
      await api.saveBitacoraProtocol(draft.id,protocolBody());
      if(selectedAccreditation)await api.saveBitacoraAccreditation(selectedAccreditation.id,accreditationBody());
      const published=await api.publishBitacoraProtocol(draft.id) as Protocol;
      await load(published.id,selectedAccreditation?.id);
      const count=published.applicablePostIds?.length??0;
      setNotice(count?`Protocolo ${published.code} publicado y activo en ${count} Puesto${count===1?'':'s'}.`:`Protocolo ${published.code} publicado en estado Inactivo. Active uno o más Puestos desde Definición.`);
    }catch(e){setError(errorText(e))}finally{setSavingProtocol(false)}
  };
  const forkProtocol=async()=>{if(!draft)return;setSavingProtocol(true);setError('');setNotice('');try{const forked=await api.forkBitacoraProtocol(draft.id) as Protocol;await load(forked.id,forked.accreditations[0]?.id);setNotice(`Se creó ${forked.code} v${forked.versionNo} en borrador. La versión publicada permanece inmutable.`)}catch(e){setError(errorText(e))}finally{setSavingProtocol(false)}};
  const applyPublishedScope=async(protocolId:string,nextPostIds:string[],postLabel:string,activating:boolean)=>{
    setScopeBusy(true);setError('');setNotice('');
    try{
      const saved=await api.bitacoraProtocolScope(protocolId,nextPostIds) as Protocol;
      await load(saved.id,selectedAccreditation?.id);
      setNotice(activating?`Protocolo activado para ${postLabel}.`:`Protocolo inactivado para ${postLabel}.`);
    }catch(e){setError(errorText(e))}finally{setScopeBusy(false)}
  };
  const toggleProtocolPost=(postId:string,checked:boolean)=>{
    if(!draft)return;
    const current=new Set(applicablePosts(draft));
    const post=point.posts.find(row=>row.postId===postId);const label=post?`${post.postCode} · ${post.postName}`:'el Puesto';
    if(checked)current.add(postId);else current.delete(postId);
    const next=Array.from(current);
    if(isDraft){updateDraft('applicablePostIds',next);return}
    if(checked){
      setConfirmState({title:'Activar protocolo en este Puesto',message:`¿Desea guardar este cambio? El Protocolo quedará activo en ${label} inmediatamente. La misma versión continuará activa en los demás Puestos ya seleccionados.`,confirmLabel:'Guardar y activar',tone:'warning',run:async()=>{await applyPublishedScope(draft.id,next,label,true)}});
      return;
    }
    setConfirmState({title:'Inactivar protocolo en este Puesto',message:`El Protocolo dejará de estar activo en ${label}. La versión publicada y su historial no se eliminan.`,confirmLabel:'Inactivar en este Puesto',tone:'warning',run:async()=>{await applyPublishedScope(draft.id,next,label,false)}});
  };
  const showHistory=async()=>{if(!draft)return;try{setVersionHistory(await api.bitacoraProtocolHistory(draft.id) as Protocol[])}catch(e){setError(errorText(e))}};

  function newField(section:FieldSection):FieldDraft{
    const next=(selectedAccreditation?.fields.filter(field=>field.section===section).reduce((max,field)=>Math.max(max,field.sortOrder),0)??0)+1;
    return {section,sortOrder:next,name:'',description:'',fieldType:'TEXTO',required:true,evidenceRequired:false,captureMode:'MANUAL',customField:true,standardImageNotes:''};
  }
  const openField=async(field:ProtocolField)=>{
    setPendingImage(null);setImageUrl('');
    setFieldDraft({id:field.id,section:field.section,sortOrder:field.sortOrder,name:field.name,description:field.description,fieldType:field.fieldType,required:field.required,evidenceRequired:field.evidenceRequired,captureMode:field.captureMode,customField:field.customField,standardImageNotes:field.standardImageNotes??'',standardImages:field.standardImages,matchThreshold:field.matchThreshold??null});
  };
  const openNewField=(section:FieldSection)=>{if(!selectedAccreditation)return;setPendingImage(null);setImageUrl('');setFieldDraft(newField(section))};
  function closeField(){setFieldDraft(null);setPendingImage(null);setImageUrl('')}
  const saveField=async()=>{
    if(!draft||!selectedAccreditation||!fieldDraft)return;
    setSavingField(true);setError('');setNotice('');
    try{
      const body={section:fieldDraft.section,sortOrder:fieldDraft.sortOrder,name:fieldDraft.name,description:fieldDraft.description,fieldType:fieldDraft.fieldType,required:fieldDraft.required,evidenceRequired:fieldDraft.evidenceRequired,captureMode:fieldDraft.captureMode,customField:fieldDraft.customField,standardImageNotes:fieldDraft.standardImageNotes,matchThreshold:fieldDraft.matchThreshold??null};
      let saved=fieldDraft.id?await api.saveBitacoraField(fieldDraft.id,body):await api.createBitacoraField(selectedAccreditation.id,body);
      if(pendingImage)saved=await api.uploadBitacoraStandardImage(saved.id,pendingImage);
      await load(draft.id,selectedAccreditation.id);setNotice(`Campo “${saved.name}” guardado.`);closeField();
    }catch(e){setError(errorText(e))}finally{setSavingField(false)}
  };
  const removeField=(field:ProtocolField)=>{
    if(!draft||!selectedAccreditation)return;
    const protocolId=draft.id;const accreditationId=selectedAccreditation.id;
    setConfirmState({title:'Eliminar campo',message:`Se eliminará el campo “${field.name}” de esta acreditación.`,confirmLabel:'Eliminar campo',tone:'danger',run:async()=>{await api.deleteBitacoraField(field.id);await load(protocolId,accreditationId);setNotice('Campo eliminado.')}});
  };
  const removeStandardImage=async(imageId:string)=>{
    if(!fieldDraft?.id||!draft||!selectedAccreditation)return;
    try{const saved=await api.deleteBitacoraStandardImage(fieldDraft.id,imageId);setFieldDraft(d=>d?{...d,standardImages:saved.standardImages}:d);await load(draft.id,selectedAccreditation.id);setNotice('Foto estándar eliminada.')}catch(e){setError(errorText(e))}
  };
  const addStandardImages=async(files:File[])=>{
    if(!fieldDraft?.id||!draft||!selectedAccreditation||!files.length)return;setError('');
    const fieldId=fieldDraft.id;
    const r=await uploadStandardImages(files,fieldDraft.standardImages?.length??0,f=>api.uploadBitacoraStandardImage(fieldId,f),
      (done,total)=>setNotice(total>1?`Subiendo foto estándar ${done} de ${total}…`:'Subiendo foto estándar…'),errorText);
    if(r.last){const saved=r.last;setFieldDraft(d=>d?{...d,standardImages:saved.standardImages}:d);await load(draft.id,selectedAccreditation.id)}
    setNotice(r.uploaded?uploadSummary(r.uploaded):'');if(r.problems.length)setError(r.problems.join(' · '));
  };

  const updateDraft=<K extends keyof Protocol>(key:K,value:Protocol[K])=>setDraft(current=>current?{...current,[key]:value}:current);
  const updateAccreditation=<K extends keyof Accreditation>(key:K,value:Accreditation[K])=>setDraft(current=>current?{...current,accreditations:current.accreditations.map(acc=>acc.id===selectedAccreditationId?{...acc,[key]:value}:acc)}:current);
  const fieldsFor=(section:FieldSection)=>selectedAccreditation?.fields.filter(field=>field.section===section).sort((a,b)=>a.sortOrder-b.sortOrder)??[];
  const runConfirm=async()=>{if(!confirmState||confirmBusy)return;setConfirmBusy(true);setError('');try{await confirmState.run();setConfirmState(null)}catch(e){setError(errorText(e))}finally{setConfirmBusy(false)}};

  return <div className="services-v01 bitacora-config-page">
    <div className="ser-breadcrumbs"><button onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración</button><span>/</span><small>Bitácora</small></div>
    <div className="bit-titlebar"><div><div className="ser-title-row"><h2>Bitácora</h2><span>Configuración de procedimientos, protocolos y acreditaciones del Punto</span></div><small>REGESEP configura la regla; la ejecución real de Bitácora se visualizará posteriormente desde Operación.</small></div><div className="bit-title-actions"><button disabled><ClipboardList size={15}/>Modelos de Compañía</button><button disabled><ShieldCheck size={15}/>Estándares Cajamarca</button></div></div>
    <div className="bit-context panel"><div><span>Compañía</span><strong>{point.companyName}</strong></div><div><span>Servicio</span><strong>{point.serviceName}</strong></div><div><span>Cliente</span><strong>{point.clientName}</strong></div><div><span>Punto</span><strong>{point.pointName}</strong></div><div><span>Última actualización</span><strong>12 sept 2026 · UAT</strong></div></div>
    <div className="ser-kpis bit-kpis"><article><span className="ser-kpi-icon blue"><Users/></span><div><small>Puestos</small><strong>{point.posts.length}</strong><em>total en el punto</em></div></article><article><span className="ser-kpi-icon green"><BadgeCheck/></span><div><small>Puestos configurados</small><strong>{configuredPosts}/{point.posts.length}</strong><em>con protocolos definidos</em></div></article><article><span className="ser-kpi-icon blue"><FileText/></span><div><small>Protocolos activos</small><strong>{activeProtocols}</strong><em>en operación</em></div></article><article><span className="ser-kpi-icon amber"><KeyRound/></span><div><small>Acreditaciones</small><strong>{accreditationCount}</strong><em>perfiles configurados</em></div></article><article><span className="ser-kpi-icon blue"><FilePlus2/></span><div><small>Modelos importados</small><strong>{importedProtocols}</strong><em>Compañía / Cajamarca</em></div></article></div>
    {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>void load()}>Reintentar</button></div>}
    {notice&&<div className="posts-notice"><Info size={15}/><span>{notice}</span></div>}

    <div className={`bit-main-grid${postsCollapsed?' posts-collapsed':''}`}>
      <section className="ser-table-card bit-posts-card"><header><div><h3>Puestos del Punto</h3><span>{postsCollapsed&&selectedPost?`Seleccionado: ${selectedPost.postCode} · ${selectedPost.postName}`:'Seleccione un Puesto para configurar sus protocolos'}</span></div><button type="button" className="bit-posts-toggle" aria-expanded={!postsCollapsed} aria-controls="bit-post-list-content" aria-label={postsCollapsed?'Expandir panel Puestos del Punto':'Contraer panel Puestos del Punto'} title={postsCollapsed?'Expandir panel':'Contraer panel'} onClick={()=>setPostsCollapsed(value=>!value)}>{postsCollapsed?<ChevronRight size={16}/>:<ChevronLeft size={16}/>}</button></header><div id="bit-post-list-content" hidden={postsCollapsed}><div className="bit-search"><Search size={15}/><input value={postQuery} onChange={event=>setPostQuery(event.target.value)} placeholder="Buscar puesto por código o nombre…"/></div><div className="bit-post-list">{visiblePosts.map(post=>{const counts=postCounts.get(post.postId)??{count:0,activo:0,borrador:0};return <button key={post.postId} className={selectedPostId===post.postId?'selected':''} onClick={()=>selectPost(post.postId)}><span className="bit-post-icon"><ShieldCheck size={17}/></span><div><strong>{post.postCode}</strong><span>{post.postName}</span><small>{counts.count} protocolo{counts.count===1?'':'s'} · {counts.count?'Configurado':'Pendiente'}</small></div><span className={`bit-status-dot ${counts.count?'good':'warn'}`}/><ChevronRight size={15}/></button>})}</div></div></section>

      <section className="ser-table-card bit-protocols-card"><header><div><h3>Protocolos del Punto</h3><span>{selectedPost?`${selectedPost.postCode} · ${selectedPost.postName}`:'Seleccione un Puesto'}</span></div></header><div className="bit-protocol-actions"><button className="primary" onClick={()=>void createProtocol()} disabled={!selectedPostId}><Plus size={15}/>Crear protocolo</button><button disabled><Upload size={15}/>Importar protocolo</button></div><div className="bit-protocol-table-wrap"><table className="bit-protocol-table"><thead><tr><th>Código</th><th>Protocolo</th><th>Objeto</th><th>Acredit.</th><th>Estado</th></tr></thead><tbody>{loading?<tr><td colSpan={5}>Cargando protocolos…</td></tr>:selectedProtocols.length?selectedProtocols.map(protocol=>{const contextualStatus=statusForPost(protocol,selectedPostId);const applies=appliesToPost(protocol,selectedPostId);return <tr key={protocol.id} className={selectedProtocolId===protocol.id?'selected':''} onClick={()=>selectProtocol(protocol)}><td>{protocol.code}</td><td><strong>{protocol.name}</strong><small>{appLabel(protocol.applicationType)} · {scopeSummary(protocol,point.posts)}</small></td><td>{protocol.objectType}</td><td>{protocol.accreditations.length}</td><td><span className={`bit-protocol-state ${contextualStatus.toLowerCase()}`}>{statusLabel(contextualStatus)}</span>{!applies&&<small className="bit-contextual-note">No aplica al puesto</small>}</td></tr>}):<tr><td className="bit-empty-cell" colSpan={5}>Este Punto todavía no tiene protocolos.</td></tr>}</tbody></table></div></section>

      <section className="ser-table-card bit-detail-card">
        {!draft?<div className="bit-detail-empty"><FileText size={28}/><strong>Seleccione o cree un protocolo</strong><span>Luego podrá definir una o más acreditaciones dentro de él.</span></div>:<>
          <header className="bit-detail-header"><div><h3>{draft.code} · {draft.name}</h3><span>{draft.objectType} · {appLabel(draft.applicationType)} · v{draft.versionNo} · {statusLabel(draft.status)}{!isDraft?` · ${applicablePosts(draft).length}/${point.posts.length} Puestos activos`:''}</span></div><div><button onClick={()=>void showHistory()}><History size={14}/>Historial</button>{isDraft?<><button onClick={()=>void saveProtocol()} disabled={savingProtocol}><Save size={14}/>{savingProtocol?'Guardando…':'Guardar borrador'}</button><button className="primary" onClick={()=>void publishProtocol()} disabled={savingProtocol}><ShieldCheck size={14}/>Publicar versión</button></>:<button onClick={()=>void forkProtocol()} disabled={savingProtocol}><Plus size={14}/>Crear nueva versión</button>}</div></header>
          {!appliesToPost(draft,selectedPostId)&&<div className="bit-selection-warning"><Info size={15}/><span>Este Protocolo está inactivo en el Puesto seleccionado. Puede activarlo marcando ese Puesto desde Definición.</span></div>}
          <div className="bit-accreditation-strip"><div className="bit-accreditation-label"><KeyRound size={15}/><div><strong>Acreditaciones ({draft.accreditations.length}/10)</strong><span>Máximo 10 por Protocolo. Si necesita más, cree un nuevo Protocolo.</span></div></div><div className="bit-accreditation-options">{draft.accreditations.map(acc=><button key={acc.id} className={selectedAccreditation?.id===acc.id?'active':''} onClick={()=>selectAccreditation(acc.id)}><b>{acc.code}</b><span>{acc.name}</span></button>)}{isDraft&&<button className="add" onClick={()=>void createAccreditation()} disabled={draft.accreditations.length>=10} title={draft.accreditations.length>=10?'Máximo 10 acreditaciones por Protocolo':''}><Plus size={14}/>Nueva acreditación</button>}</div></div>
          <div className="bit-step-tabs-rail"><div className="bit-step-tabs" ref={stepTabsRef}>{TABS.map(item=><button key={item.key} className={tab===item.key?'active':''} onClick={()=>setTab(item.key)}><b>{item.short}</b>{item.label}</button>)}</div></div>{!isDraft&&<div className="bit-version-lock"><ShieldCheck size={14}/><span>Versión publicada e inmutable en contenido. La activación por Puesto sí puede modificarse desde Definición sin crear una nueva versión.</span></div>}
          {tab==='DEFINICION'?<div className="bit-detail-body"><DefinitionTab draft={draft} posts={point.posts} editableContent={isDraft} scopeBusy={scopeBusy} update={updateDraft} onTogglePost={toggleProtocolPost}/></div>:<fieldset className="bit-detail-body bit-version-fieldset" disabled={!isDraft}>
            
            {tab==='ACREDITACION'&&selectedAccreditation&&<AccreditationTab accreditation={selectedAccreditation} update={updateAccreditation} canDelete={draft.accreditations.length>1} onDelete={()=>void deleteAccreditation()}/>} 
            {tab==='IDENTIFICACION'&&selectedAccreditation&&<FieldsTab title="Identificación" help="Defina los datos que debe presentar el objeto para esta acreditación." fields={fieldsFor('IDENTIFICACION')} logic={selectedAccreditation.identificationLogic} setLogic={value=>updateAccreditation('identificationLogic',value)} onEdit={openField} onAdd={()=>openNewField('IDENTIFICACION')} onDelete={removeField}/>} 
            {tab==='VERIFICACION'&&selectedAccreditation&&<FieldsTab title="Elementos sujetos a verificación" help="Defina las verificaciones que el agente debe realizar para esta acreditación." fields={fieldsFor('VERIFICACION')} logic={selectedAccreditation.verificationLogic} setLogic={value=>updateAccreditation('verificationLogic',value)} onEdit={openField} onAdd={()=>openNewField('VERIFICACION')} onDelete={removeField}/>} 
            {tab==='AUTORIZACION'&&selectedAccreditation&&<AuthorizationTab accreditation={selectedAccreditation} update={updateAccreditation}/>} 
            {tab==='EVIDENCIAS'&&selectedAccreditation&&<EvidenceTab fields={selectedAccreditation.fields} onEdit={openField}/>} 
            {tab==='CAPTURA'&&selectedAccreditation&&<CaptureTab accreditation={selectedAccreditation} update={updateAccreditation}/>} 
            {tab==='LISTAS'&&selectedAccreditation&&<ListsTab accreditation={selectedAccreditation} update={updateAccreditation}/>} 
            {tab==='TRAZABILIDAD'&&<TraceabilityTab/>}
          </fieldset>}
        </>}
      </section>
    </div>

    {versionHistory&&<div className="pat-modal-backdrop" onClick={()=>setVersionHistory(null)}><section className="pat-history-modal" onClick={e=>e.stopPropagation()}><header><div><h3>Historial · {draft?.code}</h3><span>Snapshots publicados del Protocolo de Bitácora.</span></div><button onClick={()=>setVersionHistory(null)}><X size={18}/></button></header><div>{versionHistory.map(v=><article key={v.id}><div><strong>v{v.versionNo}</strong><span className={`bit-protocol-state ${v.status.toLowerCase()}`}>{statusLabel(v.status)}</span></div><div><b>{v.name}</b><small>{v.accreditations.length} acreditaciones</small></div><small>{v.lastPublishedAt?new Date(v.lastPublishedAt).toLocaleString('es-EC'):'Borrador no publicado'}</small></article>)}</div></section></div>}
    {fieldDraft&&<FieldDrawer draft={fieldDraft} setDraft={setFieldDraft} imageUrl={fieldImageUrl} pendingImage={pendingImage} onImagesSelected={files=>void addStandardImages(files)} onRemoveImage={id=>void removeStandardImage(id)} onCancel={closeField} onSave={()=>void saveField()} saving={savingField}/>} 
    {confirmState&&<ConfirmDialog state={confirmState} busy={confirmBusy} onCancel={()=>!confirmBusy&&setConfirmState(null)} onConfirm={()=>void runConfirm()}/>}
  </div>;
}

function DefinitionTab({draft,posts,editableContent,scopeBusy,update,onTogglePost}:{draft:Protocol;posts:PointPost[];editableContent:boolean;scopeBusy:boolean;update:<K extends keyof Protocol>(key:K,value:Protocol[K])=>void;onTogglePost:(postId:string,checked:boolean)=>void}){
  const selected=new Set(applicablePosts(draft));
  return <div className="bit-section"><div className="bit-section-title"><div><b>Definición del Protocolo</b><span>El contenido versionado permanece inmutable después de publicar. La activación por Puesto puede ajustarse en cualquier momento.</span></div></div><div className="bit-definition-grid"><label><span>Código</span><input value={draft.code} disabled/></label><label className="wide"><span>Nombre del protocolo</span><input value={draft.name} disabled={!editableContent} onChange={e=>update('name',e.target.value)}/></label><label><span>Objeto</span><select value={draft.objectType} disabled={!editableContent} onChange={e=>update('objectType',e.target.value as Protocol['objectType'])}><option value="PAX">PAX</option><option value="VHL">VHL</option><option value="CONT">CONT</option></select></label><label><span>Aplicación</span><select value={draft.applicationType} disabled={!editableContent} onChange={e=>update('applicationType',e.target.value as Protocol['applicationType'])}><option value="INGRESO">Ingreso</option><option value="EGRESO">Egreso</option><option value="AMBOS">Ambos</option></select></label><label className="full"><span>Descripción</span><textarea value={draft.description} disabled={!editableContent} onChange={e=>update('description',e.target.value)} placeholder="Describa el alcance general del protocolo…"/></label><div className="full bit-applicability-block"><div className="bit-applicability-head"><span>Activación por Puesto</span><small>Marque los Puestos donde este Protocolo debe estar activo. Desmarcar un Puesto inactiva únicamente ese Puesto; no elimina el Protocolo ni afecta a los demás.</small></div><div className="bit-applicability-grid">{posts.map(post=>{const checked=selected.has(post.postId);return <label key={post.postId} className={`bit-applicability-option ${checked?'selected':''} ${scopeBusy?'busy':''}`}><input type="checkbox" checked={checked} disabled={scopeBusy} onChange={e=>onTogglePost(post.postId,e.target.checked)}/><div><strong>{post.postCode}</strong><span>{post.postName}</span><small>{checked?(draft.status==='BORRADOR'?'Se activará al publicar':'Activo en este Puesto'):'Inactivo en este Puesto'}</small></div></label>})}</div></div></div><div className="bit-info-box"><Info size={15}/><span>Un mismo protocolo puede estar activo simultáneamente en varios Puestos del Punto. El contenido del Protocolo se versiona una sola vez; su activación operativa se controla por Puesto.</span></div></div>;
}

function ConfirmDialog({state,busy,onCancel,onConfirm}:{state:ConfirmState;busy:boolean;onCancel:()=>void;onConfirm:()=>void}){
  return <div className="sgi-confirm-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)onCancel()}}><section className={`sgi-confirm-dialog ${state.tone}`} role="dialog" aria-modal="true" aria-labelledby="sgi-confirm-title"><div className="sgi-confirm-icon"><AlertTriangle size={22}/></div><div className="sgi-confirm-copy"><h3 id="sgi-confirm-title">{state.title}</h3><p>{state.message}</p></div><div className="sgi-confirm-actions"><button onClick={onCancel} disabled={busy}>Cancelar</button><button className={state.tone==='danger'?'danger':'warning'} onClick={onConfirm} disabled={busy}>{busy?'Procesando…':state.confirmLabel}</button></div></section></div>;
}

function AccreditationTab({accreditation,update,canDelete,onDelete}:{accreditation:Accreditation;update:<K extends keyof Accreditation>(key:K,value:Accreditation[K])=>void;canDelete:boolean;onDelete:()=>void}){
  return <div className="bit-section"><div className="bit-section-title"><div><b>Acreditación</b><span>Defina el perfil de acceso que debe cumplir el objeto para ser acreditado.</span></div>{canDelete&&<button className="bit-accreditation-delete" onClick={onDelete}><Trash2 size={14}/>Eliminar acreditación</button>}</div><div className="bit-definition-grid bit-accreditation-grid"><label><span>Código</span><input value={accreditation.code} disabled/></label><label className="wide"><span>Nombre de la acreditación</span><input value={accreditation.name} onChange={e=>update('name',e.target.value)} placeholder="Ej. Visitante autorizado"/></label><label className="full"><span>Descripción</span><textarea value={accreditation.description} onChange={e=>update('description',e.target.value)} placeholder="Explique cuándo aplica esta acreditación y qué perfil representa…"/></label></div><div className="bit-accreditation-flow"><article><b>1</b><strong>Identificación</strong><span>Quién / qué es</span></article><ChevronRight/><article><b>2</b><strong>Verificación</strong><span>Qué debe cumplir</span></article><ChevronRight/><article><b>3</b><strong>Autorización</strong><span>Quién autoriza</span></article><ChevronRight/><article><b>4</b><strong>Resultado</strong><span>Acreditar / Rechazar / Escalar</span></article></div></div>;
}

function FieldsTab({title,help,fields,logic,setLogic,onEdit,onAdd,onDelete}:{title:string;help:string;fields:ProtocolField[];logic:'ALL'|'ANY';setLogic:(value:'ALL'|'ANY')=>void;onEdit:(field:ProtocolField)=>void;onAdd:()=>void;onDelete:(field:ProtocolField)=>void}){
  return <div className="bit-section"><div className="bit-section-title"><div><b>{title}</b><span>{help}</span></div><div className="bit-logic"><span>Lógica</span><button className={logic==='ALL'?'active':''} onClick={()=>setLogic('ALL')}>ALL</button><button className={logic==='ANY'?'active':''} onClick={()=>setLogic('ANY')}>ANY</button></div></div><div className="bit-fields-table-wrap"><table className="bit-fields-table"><thead><tr><th>#</th><th>Campo</th><th>Tipo</th><th>Obligatorio</th><th>Evidencia</th><th>Captura</th><th>Estándar</th><th>Acciones</th></tr></thead><tbody>{fields.map(field=><tr key={field.id}><td>{field.sortOrder}</td><td><strong>{field.name}</strong><small>{field.description}</small></td><td>{fieldTypeLabel(field.fieldType)}</td><td>{field.required?<span className="bit-yes"><Check size={12}/>Sí</span>:<span className="bit-no">No</span>}</td><td>{field.evidenceRequired?<span className="bit-yes"><Check size={12}/>Sí</span>:<span className="bit-no">No</span>}</td><td>{captureLabel(field.captureMode)}</td><td><button className={`bit-standard ${field.hasStandardImage?'loaded':''}`} onClick={()=>onEdit(field)}>{field.hasStandardImage?<ImageIcon size={13}/>:<Plus size={13}/>} {field.hasStandardImage?`Cargado v${field.standardImageVersion}`:'Cargar'}</button></td><td><div className="bit-row-actions"><button onClick={()=>onEdit(field)}><FileText size={13}/></button><button className="danger" onClick={()=>onDelete(field)}><Trash2 size={13}/></button></div></td></tr>)}</tbody></table></div><button className="bit-add-field" onClick={onAdd}><Plus size={14}/>Agregar campo</button></div>;
}

function AuthorizationTab({accreditation,update}:{accreditation:Accreditation;update:<K extends keyof Accreditation>(key:K,value:Accreditation[K])=>void}){
  const items:{label:string;key:'authPreapproval'|'authClient'|'authSupervisor';help:string}[]=[{label:'Autorización previa requerida',key:'authPreapproval',help:'Debe existir una autorización previa antes del ingreso o egreso.'},{label:'Autorización del cliente',key:'authClient',help:'La autorización puede provenir del cliente responsable del Punto.'},{label:'Autorización del Supervisor',key:'authSupervisor',help:'Permite escalar la decisión al Supervisor de Seguridad.'}];
  return <div className="bit-section"><div className="bit-section-title"><div><b>Autorización de la acreditación</b><span>Defina las condiciones de decisión para “{accreditation.name}”.</span></div></div><div className="bit-toggle-list">{items.map(item=><label key={item.key}><div><strong>{item.label}</strong><span>{item.help}</span></div><input type="checkbox" checked={accreditation[item.key]} onChange={e=>update(item.key,e.target.checked)}/></label>)}</div><div className="bit-result-card"><b>Resultado de la acreditación</b><div><span className="good">Acreditar</span><span className="bad">Rechazar</span><span className="warn">Escalar</span></div><small>La observación manual permanece disponible para el agente en todos los resultados.</small></div></div>;
}

function EvidenceTab({fields,onEdit}:{fields:ProtocolField[];onEdit:(field:ProtocolField)=>void}){
  const evidence=fields.filter(field=>field.evidenceRequired);const withStandard=evidence.filter(field=>field.hasStandardImage).length;
  return <div className="bit-section"><div className="bit-section-title"><div><b>Evidencias</b><span>Resumen derivado de Identificación y Verificación para la acreditación seleccionada.</span></div></div><div className="bit-evidence-summary"><article><Camera/><strong>{evidence.length}</strong><span>evidencias requeridas</span></article><article><ImageIcon/><strong>{withStandard}</strong><span>con foto estándar</span></article><article><Sparkles/><strong>VISINT</strong><span>valida los campos tipo Documento</span></article></div><div className="bit-evidence-list">{evidence.map(field=><button key={field.id} onClick={()=>onEdit(field)}><span className="bit-evidence-icon"><FileImage size={17}/></span><div><strong>{field.name}</strong><span>{field.hasStandardImage?`Estándar cargado · v${field.standardImageVersion}`:'Sin foto estándar'}</span></div><span className={`bit-evidence-state ${field.hasStandardImage?'good':'warn'}`}>{field.hasStandardImage?'Preparado':'Pendiente'}</span></button>)}</div></div>;
}

function CaptureTab({accreditation,update}:{accreditation:Accreditation;update:<K extends keyof Accreditation>(key:K,value:Accreditation[K])=>void}){
  const items:{key:'captureManual'|'captureQr'|'captureBarcode'|'captureNfc'|'captureAutomatic';label:string;help:string;locked?:boolean}[]=[{key:'captureManual',label:'Captura manual',help:'Siempre disponible por regla institucional.',locked:true},{key:'captureQr',label:'QR',help:'Precarga información mediante código QR.'},{key:'captureBarcode',label:'Código de barras',help:'Permite precargar códigos compatibles.'},{key:'captureNfc',label:'NFC',help:'Lectura de credenciales NFC cuando exista hardware compatible.'},{key:'captureAutomatic',label:'Lectura automática',help:'Preparado para integraciones futuras.'}];
  return <div className="bit-section"><div className="bit-section-title"><div><b>Captura</b><span>Métodos permitidos para esta acreditación.</span></div></div><div className="bit-toggle-list">{items.map(item=><label key={item.key}><div><strong>{item.label}</strong><span>{item.help}</span></div><input type="checkbox" checked={item.locked?true:accreditation[item.key]} disabled={item.locked} onChange={e=>update(item.key,e.target.checked)}/></label>)}</div><div className="bit-info-box"><Info size={15}/><span>La lectura automática puede precargar información, pero no elimina la validación del agente.</span></div></div>;
}

function ListsTab({accreditation,update}:{accreditation:Accreditation;update:<K extends keyof Accreditation>(key:K,value:Accreditation[K])=>void}){
  return <div className="bit-section"><div className="bit-section-title"><div><b>Listas y referencias</b><span>Catálogos de autorización consultables por esta acreditación.</span></div></div><div className="bit-list-cards"><label><ShieldCheck/><div><strong>Lista blanca</strong><span>Objetos previamente autorizados.</span></div><input type="checkbox" checked={accreditation.whiteListEnabled} onChange={e=>update('whiteListEnabled',e.target.checked)}/></label><label><AlertTriangle/><div><strong>Lista negra</strong><span>Objetos bloqueados o sujetos a revisión.</span></div><input type="checkbox" checked={accreditation.blackListEnabled} onChange={e=>update('blackListEnabled',e.target.checked)}/></label><article><Users/><div><strong>Visitantes esperados</strong><span>Conector futuro con información del cliente.</span></div><em>Próximamente</em></article><article><ClipboardList/><div><strong>Proveedores autorizados</strong><span>Conector futuro con catálogos externos.</span></div><em>Próximamente</em></article></div></div>;
}

function TraceabilityTab(){
  const items=['Fecha y hora','Punto y Puesto','Turno','Agente','Protocolo y versión','Acreditación aplicada','Objeto PAX / VHL / CONT','Resultado','Evidencias','Observaciones','Firma o confirmación del agente'];
  return <div className="bit-section"><div className="bit-section-title"><div><b>Trazabilidad de ejecución</b><span>Estos datos se registrarán automáticamente cada vez que un agente ejecute el protocolo.</span></div></div><div className="bit-trace-grid">{items.map(item=><div key={item}><Check size={14}/><span>{item}</span></div>)}</div><div className="bit-info-box"><Info size={15}/><span>La ejecución deberá registrar explícitamente qué acreditación fue utilizada. La actividad real se visualizará posteriormente desde Servicios → Operación.</span></div></div>;
}

function FieldDrawer({draft,setDraft,imageUrl,pendingImage,onImagesSelected,onRemoveImage,onCancel,onSave,saving}:{draft:FieldDraft;setDraft:(value:FieldDraft|null)=>void;imageUrl:string;pendingImage:File|null;onImagesSelected:(files:File[])=>void;onRemoveImage:(id:string)=>void;onCancel:()=>void;onSave:()=>void;saving:boolean}){
  const inputRef=useRef<HTMLInputElement>(null);const update=<K extends keyof FieldDraft>(key:K,value:FieldDraft[K])=>setDraft({...draft,[key]:value});
  return <div className="bit-drawer-backdrop"><aside className="bit-field-drawer"><header><div><h3>Configurar campo de {draft.section==='IDENTIFICACION'?'identificación':'verificación'}</h3><span>{draft.customField?'Campo personalizado':'Campo predefinido'}</span></div><button onClick={onCancel}><X size={18}/></button></header><div className="bit-drawer-body"><label><span>Nombre del campo</span><input value={draft.name} disabled={!draft.customField} onChange={e=>update('name',e.target.value)} placeholder="Ej. Carné de contratista"/></label><label><span>Descripción</span><textarea value={draft.description} onChange={e=>update('description',e.target.value)} placeholder="Describa qué debe capturar o verificar el agente…"/></label><div className="bit-drawer-row"><label><span>Tipo de captura</span><select value={draft.fieldType} onChange={e=>update('fieldType',e.target.value as FieldType)}><option value="DOCUMENTO">Documento</option><option value="IMAGEN">Imagen</option><option value="TEXTO">Texto</option><option value="SELECCION">Selección</option><option value="CODIGO">Código</option><option value="OTRO">Otro</option></select></label><label><span>Método</span><select value={draft.captureMode} onChange={e=>update('captureMode',e.target.value as CaptureMode)}><option value="MANUAL">Manual</option><option value="CAMARA">Cámara</option><option value="MANUAL_QR">Manual / QR</option><option value="QR">QR</option><option value="CODIGO_BARRAS">Código de barras</option><option value="NFC">NFC</option></select></label></div><div className="bit-drawer-switches"><label><div><strong>Obligatorio</strong><span>Debe completarse para ejecutar esta acreditación.</span></div><input type="checkbox" checked={draft.required} onChange={e=>update('required',e.target.checked)}/></label><label><div><strong>Requiere evidencia</strong><span>La ejecución debe capturar evidencia asociada.</span></div><input type="checkbox" checked={draft.evidenceRequired} onChange={e=>update('evidenceRequired',e.target.checked)}/></label></div><section className="bit-standard-section"><div className="bit-standard-title"><div><strong>Fotos estándar</strong><span>Hasta 5 · JPG, PNG o WebP · máximo 5 MB.</span></div><CircleHelp size={15}/></div><input ref={inputRef} type="file" accept="image/png,image/jpeg,image/webp" multiple hidden onChange={event=>{const files=Array.from(event.currentTarget.files??[]);event.currentTarget.value='';onImagesSelected(files)}}/>{draft.id?<><StandardGallery images={draft.standardImages??[]} load={id=>api.bitacoraStandardImage(draft.id!,id) as Promise<Blob>} onAdd={()=>inputRef.current?.click()} onRemove={onRemoveImage}/><MatchThresholdField value={draft.matchThreshold} onChange={v=>update('matchThreshold',v)} disabled={!draft.evidenceRequired} disabledReason="Marca «Requiere evidencia» en este campo para que VISINT compare la foto."/></>:<p className="bit-standard-hint">Guarde el campo para agregar sus fotos estándar (hasta 5).</p>}<label><span>Notas del estándar</span><textarea value={draft.standardImageNotes} onChange={e=>update('standardImageNotes',e.target.value)} placeholder="Ej. documento completo, sin reflejos, cuatro esquinas visibles…"/></label></section><section className="bit-visint-card"><div><Sparkles size={18}/><strong>VISINT</strong><span>{draft.evidenceRequired&&draft.fieldType==='DOCUMENTO'?'Activo':'No aplica'}</span></div><p>{draft.evidenceRequired&&draft.fieldType==='DOCUMENTO'?'Compara la foto del agente con estas fotos estándar (foto clara y del documento correcto). Se necesita al menos 1 para publicar.':draft.fieldType==='IMAGEN'?'La foto se guarda en el registro sin VISINT (reconocimiento facial fuera de alcance).':'VISINT valida solo campos tipo Documento con evidencia.'}</p></section>{draft.id&&<div className="bit-version-note"><Info size={14}/><span>Reemplazar la foto estándar de un campo de una acreditación vigente constituye un cambio de estándar y deberá publicarse como nueva versión del protocolo.</span></div>}</div><footer><button onClick={onCancel}>Cancelar</button><button className="primary" onClick={onSave} disabled={saving||!draft.name.trim()}><Save size={14}/>{saving?'Guardando…':'Guardar cambios'}</button></footer></aside></div>;
}
