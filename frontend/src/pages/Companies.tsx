import {useEffect,useMemo,useRef,useState} from 'react';
import {
  AlertTriangle,Building2,History,ImagePlus,Layers3,MapPin,Pencil,Plus,RefreshCw,
  Save,ShieldCheck,Upload,X
} from 'lucide-react';
import {ApiError,api,getUser} from '../api';

type Zone={id:string;code:string;name:string;status:string};
type Region={id:string;zoneId:string;code:string;name:string;status:string};
type Territory={zones:Zone[];regions:Region[]};
type RegionLoad={regionId:string;activeServiceCount:number};
type ServiceBlocker={id:string;code:string;name:string;clientName:string;regionId?:string};
type Company={
  id:string;code:string;name:string;status:string;requiredChangeCount:number;zoneId:string;regionIds:string[];
  logoDataUrl?:string;historicalReview?:string;versionNumber:number;activeServiceCount:number;
  regionLoads:RegionLoad[];activeServices:ServiceBlocker[];
};
type Version={versionNumber:number;changeType:string;changeReason?:string;actorUsername:string;effectiveAt:string;snapshotJson:string};
type Editor={mode:'CREATE'|'EDIT';companyId?:string;name:string;status:string;zoneId:string;regionIds:string[];logoDataUrl:string;historicalReview:string;changeReason:string;activeServiceCount:number;regionLoads:RegionLoad[]}|null;

function msg(e:unknown){return e instanceof ApiError?(e.body||e.message):String(e)}
function statusLabel(v:string){return v==='ACTIVE'?'Activa':v==='INACTIVE'?'Inactiva':'Borrador'}
function changeLabel(v:string){return ({CREATED:'Creación',UPDATED:'Actualización',TERRITORY_UPDATED:'Territorio actualizado',INACTIVATED:'Inactivación',REACTIVATED:'Reactivación',BASELINE:'Versión base'} as Record<string,string>)[v]??v}

export default function Companies(){
  const user=getUser();
  const canManage=['presidente','dlatam','don','dnacional','dzonal'].includes(user);
  const [data,setData]=useState<Company[]>([]);
  const [territory,setTerritory]=useState<Territory>({zones:[],regions:[]});
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [editor,setEditor]=useState<Editor>(null);
  const [history,setHistory]=useState<{company:Company;versions:Version[]}|null>(null);
  const [busy,setBusy]=useState(false);
  const fileRef=useRef<HTMLInputElement|null>(null);

  const load=async()=>{
    try{
      const [companies,t]=await Promise.all([api.companies(),canManage?api.territoryAdmin():api.territory()]);
      setData(companies.items??[]);setTerritory(t);setError('');
    }catch(e){setError(msg(e))}
  };
  useEffect(()=>{void load()},[user]);
  useEffect(()=>{if(!notice)return;const id=window.setTimeout(()=>setNotice(''),5000);return()=>window.clearTimeout(id)},[notice]);
  useEffect(()=>{if(!error)return;const id=window.setTimeout(()=>setError(''),6500);return()=>window.clearTimeout(id)},[error]);

  const zoneMap=useMemo(()=>new Map(territory.zones.map(z=>[z.id,z])),[territory]);
  const regionMap=useMemo(()=>new Map(territory.regions.map(r=>[r.id,r])),[territory]);
  const activeCount=data.filter(c=>c.status==='ACTIVE').length;
  const inactiveCount=data.filter(c=>c.status==='INACTIVE').length;
  const regionCoverage=new Set(data.flatMap(c=>c.regionIds)).size;

  const openCreate=()=>{
    const zone=territory.zones.find(z=>z.status==='ACTIVE')??territory.zones[0];
    const regions=zone?territory.regions.filter(r=>r.zoneId===zone.id&&r.status==='ACTIVE'):[];
    setEditor({mode:'CREATE',name:'',status:'ACTIVE',zoneId:zone?.id??'',regionIds:regions[0]?[regions[0].id]:[],logoDataUrl:'',historicalReview:'',changeReason:'',activeServiceCount:0,regionLoads:[]});
  };

  const openEdit=async(c:Company)=>{
    try{
      const detail=await api.company(c.id);
      setEditor({mode:'EDIT',companyId:c.id,name:detail.name,status:detail.status,zoneId:detail.zoneId,regionIds:[...(detail.regionIds??[])],logoDataUrl:detail.logoDataUrl??'',historicalReview:detail.historicalReview??'',changeReason:'',activeServiceCount:detail.activeServiceCount??0,regionLoads:detail.regionLoads??[]});
    }catch(e){setError(msg(e))}
  };

  const showHistory=async(c:Company)=>{
    try{setHistory({company:c,versions:await api.companyHistory(c.id)})}catch(e){setError(msg(e))}
  };

  const allowedRegions=useMemo(()=>editor?territory.regions.filter(r=>r.zoneId===editor.zoneId):[],[editor,territory.regions]);
  const selectedZoneChanged=useMemo(()=>{
    if(!editor||editor.mode!=='EDIT')return false;
    const original=data.find(c=>c.id===editor.companyId);
    return !!original&&original.zoneId!==editor.zoneId;
  },[editor,data]);

  const setZone=(zoneId:string)=>{
    if(!editor)return;
    const regions=territory.regions.filter(r=>r.zoneId===zoneId&&r.status==='ACTIVE');
    setEditor({...editor,zoneId,regionIds:regions[0]?[regions[0].id]:[]});
  };
  const toggleRegion=(id:string)=>{
    if(!editor)return;
    const selected=editor.regionIds.includes(id);
    if(selected){
      const load=editor.regionLoads.find(x=>x.regionId===id)?.activeServiceCount??0;
      if(load>0){setError(`No se puede retirar esta Región: tiene ${load} Servicio(s) activo(s) de la Compañía.`);return}
      if(editor.regionIds.length===1){setError('La Compañía debe operar en al menos una Región.');return}
      setEditor({...editor,regionIds:editor.regionIds.filter(x=>x!==id)});
    }else setEditor({...editor,regionIds:[...editor.regionIds,id]});
  };

  const readLogo=(file?:File)=>{
    if(!editor||!file)return;
    if(!['image/png','image/jpeg','image/webp'].includes(file.type)){setError('El logo debe ser PNG, JPG o WEBP.');return}
    if(file.size>300*1024){setError('El logo no puede superar 300 KB en esta UAT.');return}
    const reader=new FileReader();reader.onload=()=>setEditor(prev=>prev?{...prev,logoDataUrl:String(reader.result??'')}:prev);reader.readAsDataURL(file);
  };

  const save=async()=>{
    if(!editor)return;
    if(!editor.name.trim()){setError('Nombre obligatorio.');return}
    if(!editor.zoneId){setError('Zona obligatoria.');return}
    if(!editor.regionIds.length){setError('Seleccione al menos una Región.');return}
    if(editor.historicalReview.length>750){setError('La reseña histórica no puede superar 750 caracteres.');return}
    setBusy(true);
    try{
      const body={name:editor.name.trim(),status:editor.status,zoneId:editor.zoneId,regionIds:editor.regionIds,logoDataUrl:editor.logoDataUrl||null,historicalReview:editor.historicalReview.trim()||null,changeReason:editor.changeReason.trim()||null};
      if(editor.mode==='CREATE'){await api.createCompany(body);setNotice('Compañía creada correctamente.');}
      else {await api.updateCompany(editor.companyId!,body);setNotice('Compañía actualizada correctamente.');}
      setEditor(null);await load();
    }catch(e){setError(msg(e))}finally{setBusy(false)}
  };

  return <div className="com-page">
    {error&&<div className="uat-toast error">{error}</div>}{notice&&<div className="uat-toast notice">{notice}</div>}

    <div className="com-head">
      <div><h2>Compañías</h2><p>Unidad operacional de mando. Cada Compañía pertenece a una sola Zona y puede operar en una o más Regiones de esa Zona.</p></div>
      <div className="com-actions"><button className="com-btn secondary" onClick={()=>void load()}><RefreshCw size={16}/>Actualizar</button>{canManage&&<button className="com-btn primary" onClick={openCreate}><Plus size={17}/>Nueva Compañía</button>}</div>
    </div>

    <div className="com-kpis">
      <div><Building2/><span><small>Compañías</small><strong>{data.length}</strong></span></div>
      <div><ShieldCheck/><span><small>Activas</small><strong>{activeCount}</strong></span></div>
      <div><MapPin/><span><small>Regiones cubiertas</small><strong>{regionCoverage}</strong></span></div>
      <div><Layers3/><span><small>Inactivas</small><strong>{inactiveCount}</strong></span></div>
    </div>

    <section className="com-card">
      <div className="com-table-head"><span>Compañía</span><span>Zona</span><span>Regiones</span><span>Servicios activos</span><span>Cambio requerido</span><span>Estado</span><span>Versión</span><span>Acciones</span></div>
      <div className="com-list">
        {data.map(c=>{
          const zone=zoneMap.get(c.zoneId);const regions=c.regionIds.map(id=>regionMap.get(id)).filter(Boolean) as Region[];
          return <div className="com-row" key={c.id}>
            <div className="com-company-cell">
              <div className="com-logo">{c.logoDataUrl?<img src={c.logoDataUrl} alt={`Logo ${c.name}`}/>:<Building2/>}</div>
              <div><strong>{c.name}</strong><small>{c.code}{c.historicalReview?` · ${c.historicalReview.slice(0,72)}${c.historicalReview.length>72?'…':''}`:''}</small></div>
            </div>
            <div><strong className="com-zone">{zone?zone.name:'—'}</strong><small>{zone?.code??''}</small></div>
            <div className="com-region-chips">{regions.map(r=><span key={r.id}>{r.code}</span>)}</div>
            <div className="com-service-count"><strong>{c.activeServiceCount??0}</strong><small>{c.activeServiceCount===1?'servicio':'servicios'}</small></div>
            <span className="com-change">{c.requiredChangeCount??0}</span>
            <span className={`com-status ${c.status.toLowerCase()}`}>{statusLabel(c.status)}</span>
            <span className="com-version">v{c.versionNumber}</span>
            <div className="com-row-actions">{canManage&&<button title="Editar" onClick={()=>void openEdit(c)}><Pencil/></button>}<button title="Historial" onClick={()=>void showHistory(c)}><History/></button></div>
          </div>
        })}
        {!data.length&&<div className="com-empty">No hay Compañías visibles para este alcance.</div>}
      </div>
    </section>

    {editor&&<div className="com-modal-backdrop" onMouseDown={()=>setEditor(null)}><div className="com-modal" onMouseDown={e=>e.stopPropagation()}>
      <div className="com-modal-head"><div><h3>{editor.mode==='CREATE'?'Nueva Compañía':'Editar Compañía'}</h3><p>{editor.mode==='CREATE'?'Registra identidad, territorio, logo y reseña histórica.':'Los cambios generan una nueva versión y quedan en el historial.'}</p></div><button onClick={()=>setEditor(null)}><X/></button></div>
      <div className="com-editor-grid">
        <div className="com-logo-editor">
          <div className="com-logo-preview">{editor.logoDataUrl?<img src={editor.logoDataUrl} alt="Vista previa del logo"/>:<ImagePlus/>}</div>
          <div><strong>Logo de la Compañía</strong><small>PNG, JPG o WEBP · máximo 300 KB</small><div className="com-logo-actions"><button className="com-btn secondary small" onClick={()=>fileRef.current?.click()}><Upload/>Subir logo</button>{editor.logoDataUrl&&<button className="com-btn ghost small" onClick={()=>setEditor({...editor,logoDataUrl:''})}>Quitar</button>}</div></div>
          <input ref={fileRef} className="com-hidden-file" type="file" accept="image/png,image/jpeg,image/webp" onChange={e=>readLogo(e.target.files?.[0])}/>
        </div>
        <label>Nombre<input value={editor.name} maxLength={160} onChange={e=>setEditor({...editor,name:e.target.value})}/></label>
        <label>Zona<select value={editor.zoneId} disabled={editor.mode==='EDIT'&&editor.activeServiceCount>0} onChange={e=>setZone(e.target.value)}>{territory.zones.filter(z=>z.status!=='INACTIVE'||z.id===editor.zoneId).map(z=><option key={z.id} value={z.id}>{z.code} · {z.name}</option>)}</select>{editor.mode==='EDIT'&&editor.activeServiceCount>0&&<small>No puede cambiar de Zona mientras existan Servicios activos.</small>}</label>
        <label className="com-review">Reseña histórica<textarea value={editor.historicalReview} maxLength={750} onChange={e=>setEditor({...editor,historicalReview:e.target.value})} placeholder="Breve reseña histórica de la Compañía..."/><small>{editor.historicalReview.length} / 750</small></label>
        {editor.mode==='EDIT'&&<label>Estado<select value={editor.status} onChange={e=>setEditor({...editor,status:e.target.value})}><option value="ACTIVE">Activa</option><option value="INACTIVE" disabled={editor.activeServiceCount>0}>Inactiva</option></select>{editor.activeServiceCount>0&&<small>Para inactivar debe migrar o finalizar los {editor.activeServiceCount} Servicio(s) activo(s).</small>}</label>}
        {editor.mode==='EDIT'&&<label>Motivo del cambio<input value={editor.changeReason} maxLength={500} onChange={e=>setEditor({...editor,changeReason:e.target.value})} placeholder="Opcional · queda en auditoría"/></label>}
      </div>

      <div className="com-region-picker">
        <div><strong>Regiones operativas</strong><span>Puede seleccionar una o más Regiones, siempre dentro de la misma Zona.</span></div>
        <div className="com-region-grid">{allowedRegions.map(r=>{
          const selected=editor.regionIds.includes(r.id);const load=editor.regionLoads.find(x=>x.regionId===r.id)?.activeServiceCount??0;
          return <label key={r.id} className={selected?'selected':''}><input type="checkbox" checked={selected} onChange={()=>toggleRegion(r.id)}/><span><strong>{r.code}</strong>{r.name}</span>{load>0&&<em>{load} servicio{load===1?'':'s'} activo{load===1?'':'s'}</em>}</label>
        })}</div>
      </div>

      {editor.mode==='EDIT'&&editor.activeServiceCount>0&&<div className="com-warning"><AlertTriangle/><span><strong>Operación activa.</strong> Puede agregar Regiones de la misma Zona. No puede retirar una Región que tenga Servicios activos ni inactivar/cambiar de Zona hasta migrarlos o finalizarlos.</span></div>}
      {selectedZoneChanged&&editor.activeServiceCount===0&&<div className="com-info"><MapPin/><span>El cambio de Zona quedará versionado. Todas las Regiones seleccionadas pertenecen a la nueva Zona.</span></div>}

      <div className="com-modal-actions"><button className="com-btn ghost" onClick={()=>setEditor(null)}>Cancelar</button><button className="com-btn primary" disabled={busy||!editor.name.trim()||!editor.zoneId||!editor.regionIds.length} onClick={()=>void save()}><Save/> {busy?'Guardando…':'Guardar'}</button></div>
    </div></div>}

    {history&&<div className="com-modal-backdrop" onMouseDown={()=>setHistory(null)}><div className="com-modal history" onMouseDown={e=>e.stopPropagation()}>
      <div className="com-modal-head"><div><h3>Historial · {history.company.name}</h3><p>{history.company.code} · Versionamiento completo de COM.</p></div><button onClick={()=>setHistory(null)}><X/></button></div>
      <div className="com-history-list">{history.versions.map(v=>{let snap:any={};try{snap=JSON.parse(v.snapshotJson)}catch{}return <div key={v.versionNumber}>
        <div className="com-history-version"><span>v{v.versionNumber}</span><div><strong>{changeLabel(v.changeType)}</strong><small>{v.actorUsername} · {new Date(v.effectiveAt).toLocaleString('es-EC')}</small></div></div>
        <div className="com-history-snapshot"><span><b>Nombre</b>{snap.name??'—'}</span><span><b>Estado</b>{statusLabel(snap.status??'')}</span><span><b>Zona</b>{zoneMap.get(snap.zoneId)?.name??'—'}</span><span className="wide"><b>Regiones</b>{Array.isArray(snap.regionIds)?snap.regionIds.map((id:string)=>regionMap.get(id)?.code??id).join(' · '):'—'}</span>{snap.historicalReview&&<span className="wide"><b>Reseña histórica</b>{snap.historicalReview}</span>}{v.changeReason&&<span className="wide"><b>Motivo</b>{v.changeReason}</span>}</div>
      </div>})}{!history.versions.length&&<div className="com-empty">Sin versiones registradas.</div>}</div>
    </div></div>}
  </div>;
}
