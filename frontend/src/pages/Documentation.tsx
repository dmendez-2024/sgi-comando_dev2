import {useEffect,useMemo,useRef,useState} from 'react';
import {CheckCircle2,Clock3,Download,FileText,MoreHorizontal,Plus,RefreshCcw,RotateCcw,Search,UploadCloud,X} from 'lucide-react';
import {ApiError,api,getUser} from '../api';

type DocumentStatus='VIGENTE'|'NO_VIGENTE';
type Company={id:string;name:string};
type InstitutionalDocument={
  id:string;
  name:string;
  fileName:string;
  version:string;
  status:DocumentStatus;
  companyId:string|null;
  updatedAt:string;
};

const UPLOAD_ROLES=new Set(['presidente','ggeneral']);
const formatDate=(value:string)=>new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'short',year:'numeric'}).format(new Date(value.length===10?`${value}T12:00:00`:value));
const statusLabel=(status:DocumentStatus)=>status==='VIGENTE'?'Vigente':'No vigente';
function message(error:unknown){
  if(error instanceof ApiError)try{return JSON.parse(error.body)?.message||error.body}catch{return error.body}
  return error instanceof Error?error.message:String(error);
}

export default function Documentation(){
  const [documents,setDocuments]=useState<InstitutionalDocument[]>([]);
  const [companies,setCompanies]=useState<Company[]>([]);
  const [loading,setLoading]=useState(true);
  const [pageError,setPageError]=useState('');
  const [query,setQuery]=useState('');
  const [companyId,setCompanyId]=useState('');
  const [status,setStatus]=useState('');
  const [drawerOpen,setDrawerOpen]=useState(false);
  const [name,setName]=useState('');
  const [version,setVersion]=useState('');
  const [draftStatus,setDraftStatus]=useState<DocumentStatus>('VIGENTE');
  const [file,setFile]=useState<File|null>(null);
  const [dragging,setDragging]=useState(false);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const inputRef=useRef<HTMLInputElement|null>(null);
  const canUpload=UPLOAD_ROLES.has(getUser());

  useEffect(()=>{
    let active=true;
    void Promise.all([api.documents(),api.companies(0,100).catch(()=>({items:[]}))]).then(([stored,response])=>{
      if(!active)return;
      setDocuments(stored);setCompanies((response.items??[]).map((company:any)=>({id:company.id,name:company.name})));setPageError('');
    }).catch(reason=>{if(active)setPageError(message(reason))}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[]);

  useEffect(()=>{
    if(!drawerOpen)return;
    const close=(event:KeyboardEvent)=>{if(event.key==='Escape')closeDrawer()};
    window.addEventListener('keydown',close);
    return()=>window.removeEventListener('keydown',close);
  },[drawerOpen]);

  const filtered=useMemo(()=>documents.filter(document=>{
    const term=query.trim().toLocaleLowerCase('es-EC');
    return(!term||`${document.name} ${document.fileName} ${document.version}`.toLocaleLowerCase('es-EC').includes(term))
      &&(!companyId||document.companyId===companyId)
      &&(!status||document.status===status);
  }),[documents,query,companyId,status]);

  const vigente=documents.filter(document=>document.status==='VIGENTE').length;
  const noVigente=documents.length-vigente;
  const latest=documents.reduce((current,document)=>document.updatedAt>current?document.updatedAt:current,'');

  function closeDrawer(){
    setDrawerOpen(false);setName('');setVersion('');setDraftStatus('VIGENTE');setFile(null);setDragging(false);setError('');
  }

  function chooseFile(next:File|null){
    if(!next)return;
    if(next.type!=='application/pdf'&&!next.name.toLocaleLowerCase().endsWith('.pdf')){setError('Seleccione un archivo PDF.');return}
    setFile(next);setError('');
    if(!name)setName(next.name.replace(/\.pdf$/i,'').replace(/[_-]+/g,' '));
  }

  async function saveDocument(){
    if(!name.trim()||!version.trim()||!file){setError('Complete el nombre, la versión y seleccione un archivo PDF.');return}
    const form=new FormData();form.append('name',name.trim());form.append('version',version.trim());form.append('status',draftStatus);form.append('companyId',companyId);form.append('file',file,file.name);
    setSaving(true);setError('');
    try{
      const stored=await api.uploadDocument(form);
      setDocuments(current=>[stored,...current]);
      setSaving(false);closeDrawer();
    }catch(reason){setError(message(reason));setSaving(false)}
  }

  async function downloadDocument(document:InstitutionalDocument){
    try{
      const content=await api.documentContent(document.id);const href=URL.createObjectURL(content);
      const anchor=window.document.createElement('a');anchor.href=href;anchor.download=document.fileName;anchor.click();
      window.setTimeout(()=>URL.revokeObjectURL(href),0);
    }catch(reason){setPageError(message(reason))}
  }

  const clearFilters=()=>{setQuery('');setCompanyId('');setStatus('')};

  return <div className={`documentation-page ${drawerOpen?'drawer-visible':''}`}>
    <header className="documentation-titlebar">
      <div><h2>Documentación</h2><p>Repositorio institucional</p></div>
    </header>
    {pageError&&<div className="documentation-page-error">{pageError}<button type="button" onClick={()=>setPageError('')}><X size={14}/></button></div>}

    <div className="documentation-kpis">
      <article><span className="doc-kpi-icon blue"><FileText size={19}/></span><div><small>Documentos vigentes</small><strong>{vigente}</strong></div><i className="doc-spark blue"/></article>
      <article><span className="doc-kpi-icon green"><CheckCircle2 size={19}/></span><div><small>Vigentes</small><strong>{vigente}</strong></div><i className="doc-spark green"/></article>
      <article><span className="doc-kpi-icon sky"><Clock3 size={19}/></span><div><small>No vigentes</small><strong>{noVigente}</strong></div><i className="doc-spark sky"/></article>
      <article><span className="doc-kpi-icon amber"><RefreshCcw size={19}/></span><div><small>Última actualización</small><strong className="date-value">{latest?formatDate(latest):'—'}</strong></div><i className="doc-spark amber"/></article>
    </div>

    <div className="documentation-filters">
      <label className="documentation-search"><Search size={16}/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Buscar documento..." aria-label="Buscar documento"/></label>
      <label><span>Compañía</span><select value={companyId} onChange={event=>setCompanyId(event.target.value)}><option value="">Todas las compañías</option>{companies.map(company=><option value={company.id} key={company.id}>{company.name}</option>)}</select></label>
      <label><span>Estado</span><select value={status} onChange={event=>setStatus(event.target.value)}><option value="">Todos los estados</option><option value="VIGENTE">Vigente</option><option value="NO_VIGENTE">No vigente</option></select></label>
      <button type="button" className="documentation-clear" onClick={clearFilters}><RotateCcw size={15}/>Limpiar filtros</button>
    </div>

    <section className="documentation-card">
      <div className="documentation-card-head">
        <h3>Documentos ({filtered.length})</h3>
        <div><span>{filtered.length} {filtered.length===1?'archivo':'archivos'}</span>{canUpload&&<button type="button" onClick={()=>setDrawerOpen(true)}><Plus size={16}/>Cargar documento</button>}</div>
      </div>
      <div className="documentation-table-wrap">
        <div className="documentation-table-head"><span>Documento</span><span>Versión</span><span>Estado</span><span>Actualizado</span><span>Acciones</span></div>
        {filtered.map(document=><div className="documentation-row" key={document.id}>
          <div className="documentation-document"><span><FileText size={17}/></span><div><strong>{document.name}</strong><small>{document.fileName}</small></div></div>
          <div><b className="documentation-version">{document.version}</b></div>
          <div><b className={`documentation-status ${document.status==='VIGENTE'?'current':'expired'}`}><i/>{statusLabel(document.status)}</b></div>
          <time dateTime={document.updatedAt}>{formatDate(document.updatedAt)}</time>
          <div className="documentation-actions"><button type="button" onClick={()=>downloadDocument(document)}><Download size={14}/>Descargar</button><button type="button" className="icon-only" aria-label={`Más acciones para ${document.name}`}><MoreHorizontal size={18}/></button></div>
        </div>)}
        {!loading&&!filtered.length&&<div className="documentation-empty">No hay documentos cargados que coincidan con los filtros.</div>}
        {loading&&<div className="documentation-empty">Cargando documentos…</div>}
      </div>
    </section>

    {drawerOpen&&<><button className="documentation-drawer-backdrop" type="button" disabled={saving} aria-label="Cerrar panel" onClick={closeDrawer}/><aside className="documentation-drawer" role="dialog" aria-modal="true" aria-labelledby="documentation-drawer-title">
      <div className="documentation-drawer-head"><h3 id="documentation-drawer-title">Nuevo documento</h3><button type="button" onClick={closeDrawer} disabled={saving} aria-label="Cerrar"><X size={18}/></button></div>
      <div className="documentation-form">
        <label><span>Nombre del documento</span><input value={name} onChange={event=>setName(event.target.value)} placeholder="Ej. Reglamento interno"/></label>
        <div className="documentation-form-row">
          <label><span>Versión</span><input value={version} onChange={event=>setVersion(event.target.value)} placeholder="Ej. v2.5"/></label>
          <label><span>Estado</span><select value={draftStatus} onChange={event=>setDraftStatus(event.target.value as DocumentStatus)}><option value="VIGENTE">Vigente</option><option value="NO_VIGENTE">No vigente</option></select></label>
        </div>
        <div><span className="documentation-field-label">Archivo PDF</span><button type="button" className={`documentation-dropzone ${dragging?'dragging':''} ${file?'selected':''}`} onClick={()=>inputRef.current?.click()} onDragOver={event=>{event.preventDefault();setDragging(true)}} onDragLeave={()=>setDragging(false)} onDrop={event=>{event.preventDefault();setDragging(false);chooseFile(event.dataTransfer.files[0]??null)}}>
          <UploadCloud size={30}/><strong>{file?file.name:'Subir archivo PDF'}</strong><small>{file?'Archivo listo para guardar':'Arrastra y suelta el archivo o selecciónalo desde tu equipo'}</small>
        </button><input ref={inputRef} hidden type="file" accept="application/pdf,.pdf" onChange={event=>chooseFile(event.target.files?.[0]??null)}/></div>
        {error&&<p className="documentation-form-error">{error}</p>}
      </div>
      <div className="documentation-drawer-actions"><button type="button" onClick={closeDrawer} disabled={saving}>Cancelar</button><button type="button" className="primary" onClick={()=>void saveDocument()} disabled={saving}>{saving?'Guardando…':'Guardar documento'}</button></div>
    </aside></>}
  </div>;
}
