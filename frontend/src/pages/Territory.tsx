import {useEffect,useMemo,useState} from 'react';
import {
  Building2,ChevronDown,ChevronRight,ChevronsDown,ChevronsUp,Clock3,Edit3,
  Filter,Map as MapIcon,MoreVertical,Plus,RefreshCw,Search,ShieldCheck,Trash2,UserRound,X
} from 'lucide-react';
import {ApiError,api,getUser} from '../api';

type Zone={id:string;code:string;name:string;status:string;responsibleEmployeeId?:string;responsibleName?:string;provinceCodes:string[]};
type Region={id:string;zoneId:string;code:string;name:string;status:string;responsibleEmployeeId?:string;responsibleName?:string;provinceCodes:string[]};
type Company={id:string;code:string;name:string;status:string;regionId:string};
type Province={id:string;code:string;name:string;zoneId?:string;regionId?:string;status:string};
type Responsible={employeeId:string;fullName:string;roleCode:string};
type Tree={zones:Zone[];regions:Region[];companies:Company[];provinces:Province[]};
type CoreContext={country?:string;countryCode?:string;subdivisionType?:string;subdivisionSingular?:string;subdivisionPlural?:string;territoryCatalogSource?:string};
type AuditEvent={id:string;entityType:string;entityId:string;eventType:string;actorUsername:string;payloadJson:string;occurredAt:string};
type EditorState={kind:'ZONE'|'REGION';mode:'CREATE'|'EDIT';id?:string;zoneId?:string;code:string;name:string;status:string;responsibleEmployeeId:string;provinceCodes:string[]}|null;
type MenuState={kind:'ZONE'|'REGION';id:string}|null;

function msg(e:unknown){return e instanceof ApiError?(e.body||e.message):String(e)}
function labelStatus(s:string){return s==='ACTIVE'?'Activa':s==='DRAFT'?'Borrador':'Inactiva'}
function statusClass(s:string){return s==='ACTIVE'?'active':s==='DRAFT'?'draft':'inactive'}
function titleCase(s:string){return s?`${s.charAt(0).toUpperCase()}${s.slice(1)}`:s}

export default function Territory(){
  const currentUser=getUser();
  const countryEditors=['presidente','dnacional'];
  const canEditZones=countryEditors.includes(currentUser);
  const canEditRegions=[...countryEditors,'dzonal'].includes(currentUser);
  const [tree,setTree]=useState<Tree>({zones:[],regions:[],companies:[],provinces:[]});
  const [core,setCore]=useState<CoreContext>({country:'Ecuador',countryCode:'EC',subdivisionType:'PROVINCE',subdivisionSingular:'Provincia',subdivisionPlural:'Provincias',territoryCatalogSource:'CORE LOCAL · UAT'});
  const [responsibles,setResponsibles]=useState<Responsible[]>([]);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [query,setQuery]=useState('');
  const [statusFilter,setStatusFilter]=useState('ALL');
  const [typeFilter,setTypeFilter]=useState('ALL');
  const [expandedZones,setExpandedZones]=useState<Set<string>>(new Set());
  const [expandedRegions,setExpandedRegions]=useState<Set<string>>(new Set());
  const [editor,setEditor]=useState<EditorState>(null);
  const [menu,setMenu]=useState<MenuState>(null);
  const [audit,setAudit]=useState<{title:string;events:AuditEvent[]}|null>(null);

  const subdivisionSingular=core.subdivisionSingular||'Provincia';
  const subdivisionPlural=core.subdivisionPlural||'Provincias';

  const load=async()=>{
    try{
      const [t,r,c]=await Promise.all([
        canEditRegions?api.territoryAdmin():api.territory(),
        api.territoryResponsibles().catch(()=>[]),
        api.context().catch(()=>({}))
      ]);
      setTree(t);setResponsibles(r);setCore(prev=>({...prev,...c}));setError('');
      setExpandedZones(prev=>prev.size?prev:new Set(t.zones.map((z:Zone)=>z.id)));
    }catch(e){setError(msg(e))}
  };
  useEffect(()=>{void load()},[currentUser]);
  useEffect(()=>{if(!notice)return;const id=window.setTimeout(()=>setNotice(''),5000);return()=>window.clearTimeout(id)},[notice]);
  useEffect(()=>{if(!error)return;const id=window.setTimeout(()=>setError(''),5000);return()=>window.clearTimeout(id)},[error]);
  useEffect(()=>{const close=()=>setMenu(null);window.addEventListener('click',close);return()=>window.removeEventListener('click',close)},[]);

  const regionsByZone=useMemo(()=>{const m=new Map<string,Region[]>();for(const r of tree.regions)m.set(r.zoneId,[...(m.get(r.zoneId)??[]),r]);return m},[tree]);
  const companiesByRegion=useMemo(()=>{const m=new Map<string,Company[]>();for(const c of tree.companies)m.set(c.regionId,[...(m.get(c.regionId)??[]),c]);return m},[tree]);
  const provincesByRegion=useMemo(()=>{const m=new Map<string,Province[]>();for(const p of tree.provinces){if(p.regionId)m.set(p.regionId,[...(m.get(p.regionId)??[]),p])}return m},[tree]);
  const provincesByZone=useMemo(()=>{const m=new Map<string,Province[]>();for(const p of tree.provinces){if(p.zoneId)m.set(p.zoneId,[...(m.get(p.zoneId)??[]),p])}return m},[tree]);

  const norm=query.trim().toLowerCase();
  const zoneMatches=(z:Zone)=>{
    if(statusFilter!=='ALL'&&z.status!==statusFilter)return false;
    const regions=regionsByZone.get(z.id)??[];
    const zoneText=[z.code,z.name,z.responsibleName,...(provincesByZone.get(z.id)??[]).map(p=>p.name),...regions.map(r=>r.name),...regions.flatMap(r=>(companiesByRegion.get(r.id)??[]).map(c=>c.name))].filter(Boolean).join(' ').toLowerCase();
    if(norm&&!zoneText.includes(norm))return false;
    if(typeFilter==='ZONE')return true;
    if(typeFilter==='REGION')return regions.some(r=>(!norm||`${r.code} ${r.name} ${r.responsibleName??''}`.toLowerCase().includes(norm))&&(statusFilter==='ALL'||r.status===statusFilter));
    if(typeFilter==='SUBDIVISION')return (provincesByZone.get(z.id)??[]).some(p=>!norm||`${p.code} ${p.name}`.toLowerCase().includes(norm));
    if(typeFilter==='COMPANY')return regions.some(r=>(companiesByRegion.get(r.id)??[]).some(c=>!norm||`${c.code} ${c.name}`.toLowerCase().includes(norm)));
    return true;
  };
  const visibleZones=tree.zones.filter(zoneMatches);

  const openCreateZone=()=>setEditor({kind:'ZONE',mode:'CREATE',code:'',name:'',status:'DRAFT',responsibleEmployeeId:'',provinceCodes:[]});
  const openEditZone=(z:Zone)=>setEditor({kind:'ZONE',mode:'EDIT',id:z.id,code:z.code,name:z.name,status:z.status,responsibleEmployeeId:z.responsibleEmployeeId??'',provinceCodes:[...z.provinceCodes]});
  const openCreateRegion=()=>setEditor({kind:'REGION',mode:'CREATE',zoneId:tree.zones[0]?.id??'',code:'',name:'',status:'DRAFT',responsibleEmployeeId:'',provinceCodes:[]});
  const openEditRegion=(r:Region)=>setEditor({kind:'REGION',mode:'EDIT',id:r.id,zoneId:r.zoneId,code:r.code,name:r.name,status:r.status,responsibleEmployeeId:r.responsibleEmployeeId??'',provinceCodes:[...r.provinceCodes]});

  const editorAllowedProvinces=useMemo(()=>{
    if(!editor)return [] as Province[];
    if(editor.kind==='ZONE')return tree.provinces;
    return tree.provinces.filter(p=>p.zoneId===editor.zoneId||editor.provinceCodes.includes(p.code));
  },[editor,tree.provinces]);

  const saveEditor=async()=>{
    if(!editor)return;
    try{
      const body={code:editor.code.trim(),name:editor.name.trim(),status:editor.status,responsibleEmployeeId:editor.responsibleEmployeeId||null,provinceCodes:editor.provinceCodes};
      if(editor.kind==='ZONE'){
        if(editor.mode==='CREATE')await api.createZone(body); else await api.updateZone(editor.id!,body);
        setNotice(editor.mode==='CREATE'?'Zona creada correctamente.':'Zona guardada correctamente.');
      }else{
        const regionBody={...body,zoneId:editor.zoneId};
        if(editor.mode==='CREATE')await api.createRegion(regionBody); else await api.updateRegion(editor.id!,regionBody);
        setNotice(editor.mode==='CREATE'?'Región creada correctamente.':'Región guardada correctamente.');
      }
      setEditor(null);await load();
    }catch(e){setError(msg(e))}
  };
  const removeZone=async(z:Zone)=>{try{await api.deleteZone(z.id);setNotice('Zona eliminada correctamente.');await load()}catch(e){setError(msg(e))}};
  const removeRegion=async(r:Region)=>{try{await api.deleteRegion(r.id);setNotice('Región eliminada correctamente.');await load()}catch(e){setError(msg(e))}};
  const showAudit=async(kind:'ZONE'|'REGION',id:string,title:string)=>{
    try{const events=await api.territoryAudit(kind,id);setAudit({title,events});setMenu(null)}catch(e){setError(msg(e))}
  };

  const toggleZone=(id:string)=>setExpandedZones(prev=>{const n=new Set(prev);n.has(id)?n.delete(id):n.add(id);return n});
  const toggleRegion=(id:string)=>setExpandedRegions(prev=>{const n=new Set(prev);n.has(id)?n.delete(id):n.add(id);return n});
  const expandAll=()=>{setExpandedZones(new Set(tree.zones.map(z=>z.id)));setExpandedRegions(new Set(tree.regions.map(r=>r.id)))};
  const collapseAll=()=>{setExpandedZones(new Set());setExpandedRegions(new Set())};
  const clearFilters=()=>{setQuery('');setStatusFilter('ALL');setTypeFilter('ALL')};

  return <div className="ter-page">
    {error&&<div className="uat-toast error">{error}</div>}{notice&&<div className="uat-toast notice">{notice}</div>}

    <div className="ter-page-head">
      <div><p className="ter-page-intro">Gestiona la estructura territorial del país. País → Zona → Región → {subdivisionSingular} → Compañía.</p></div>
      <div className="ter-page-actions">
        <button className="ter-btn secondary" onClick={()=>void load()}><RefreshCw size={16}/>Actualizar</button>
        {canEditZones&&<button className="ter-btn secondary accent" onClick={openCreateZone}><Plus size={17}/>Zona</button>}
        {canEditRegions&&<button className="ter-btn primary" onClick={openCreateRegion}><Plus size={17}/>Región</button>}
      </div>
    </div>

    <section className="ter-country-card">
      <div className="ter-country-copy">
        <div className="ter-country-title"><div className="ter-flag">🇪🇨</div><h3>{core.country||'Ecuador'}</h3><span className="ter-core-badge">{core.territoryCatalogSource||'CORE LOCAL · UAT'}</span></div>
        <div className="ter-country-stats"><strong>{tree.zones.length}</strong> Zonas <b>•</b> <strong>{tree.regions.length}</strong> Regiones <b>•</b> <strong>{tree.provinces.length}</strong> {subdivisionPlural} <b>•</b> <strong>{tree.companies.length}</strong> Compañías</div>
        <p>El catálogo y la denominación territorial ({subdivisionPlural}) provienen de CORE. SGI: Comando administra únicamente su agrupación operacional en Zonas y Regiones.</p>
      </div>
      <div className="ter-map-wrap">
        <img src="/territory/ecuador-operational-map-crop.png" alt="Mapa operacional de Ecuador por Zonas y Regiones"/>
        <div className="ter-map-legend">
          {tree.zones.filter(z=>['ZN','ZS'].includes(z.code)).map((z,i)=><div key={z.id}><i className={`zone-dot z${i%4}`}/><span>{z.name} ({z.code})</span></div>)}
          <div className="ter-map-note"><MapIcon size={14}/>Mapa base Ecuador; la estructura inferior es la configuración vigente.</div>
        </div>
      </div>
    </section>

    <section className="ter-structure-card">
      <div className="ter-structure-head"><h3>Estructura territorial</h3><div><button className="ter-btn ghost" onClick={expandAll}><ChevronsDown size={16}/>Expandir todo</button><button className="ter-btn ghost" onClick={collapseAll}><ChevronsUp size={16}/>Contraer todo</button></div></div>
      <div className="ter-filters">
        <label className="ter-search"><Search size={18}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder={`Buscar zonas, regiones, ${subdivisionPlural.toLowerCase()} o compañías...`}/></label>
        <label><span>Estado</span><select value={statusFilter} onChange={e=>setStatusFilter(e.target.value)}><option value="ALL">Todos</option><option value="ACTIVE">Activas</option><option value="DRAFT">Borrador</option><option value="INACTIVE">Inactivas</option></select></label>
        <label><span>Tipo</span><select value={typeFilter} onChange={e=>setTypeFilter(e.target.value)}><option value="ALL">Todos</option><option value="ZONE">Zona</option><option value="REGION">Región</option><option value="SUBDIVISION">{subdivisionSingular}</option><option value="COMPANY">Compañía</option></select></label>
        <button className="ter-btn ghost" onClick={clearFilters}><X size={16}/>Limpiar</button>
      </div>

      <div className="ter-grid-head"><span>Territorio</span><span>Responsable</span><span>Regiones</span><span>{subdivisionPlural}</span><span>Compañías</span><span>Estado</span><span>Acciones</span></div>
      <div className="ter-tree">
        {visibleZones.map((z,zi)=>{
          const zoneRegions=regionsByZone.get(z.id)??[];
          const zoneCompanies=zoneRegions.flatMap(r=>companiesByRegion.get(r.id)??[]);
          const zoneOpen=expandedZones.has(z.id);
          return <div className="ter-zone" key={z.id}>
            <div className="ter-row zone-row">
              <button className="ter-expand" onClick={()=>toggleZone(z.id)}>{zoneOpen?<ChevronDown/>:<ChevronRight/>}</button>
              <div className="ter-territory-cell"><div className="ter-code">{z.code}</div><div><strong>{z.name}</strong><small>{z.status==='ACTIVE'?'Zona operativa activa':'Configuración territorial'}</small></div></div>
              <div className="ter-responsible"><UserRound size={16}/>{z.responsibleName||'Sin asignar'}</div>
              <strong className="ter-count">{zoneRegions.length}</strong><strong className="ter-count">{(provincesByZone.get(z.id)??[]).length}</strong><strong className="ter-count">{zoneCompanies.length}</strong>
              <span className={`ter-status ${statusClass(z.status)}`}>{labelStatus(z.status)}</span>
              <div className="ter-menu-wrap"><button className="ter-menu-btn" onClick={e=>{e.stopPropagation();setMenu(menu?.kind==='ZONE'&&menu.id===z.id?null:{kind:'ZONE',id:z.id})}}><MoreVertical/></button>{menu?.kind==='ZONE'&&menu.id===z.id&&<div className="ter-menu" onClick={e=>e.stopPropagation()}>{canEditZones&&<button onClick={()=>{openEditZone(z);setMenu(null)}}><Edit3/>Editar</button>}<button onClick={()=>void showAudit('ZONE',z.id,z.name)}><Clock3/>Historial</button>{canEditZones&&<button className="danger" disabled={z.status==='ACTIVE'} onClick={()=>void removeZone(z)}><Trash2/>Eliminar</button>}</div>}</div>
            </div>
            {zoneOpen&&<div className="ter-zone-body">
              {zoneRegions.map(r=>{
                const regionOpen=expandedRegions.has(r.id);const regionProvinces=provincesByRegion.get(r.id)??[];const regionCompanies=companiesByRegion.get(r.id)??[];
                return <div className="ter-region" key={r.id}>
                  <div className="ter-row region-row">
                    <button className="ter-expand" onClick={()=>toggleRegion(r.id)}>{regionOpen?<ChevronDown/>:<ChevronRight/>}</button>
                    <div className="ter-territory-cell"><div className="ter-code region">{r.code}</div><div><strong>{r.name}</strong><small>{regionProvinces.length} {subdivisionPlural.toLowerCase()} · {regionCompanies.length} {regionCompanies.length===1?'compañía':'compañías'}</small></div></div>
                    <div className="ter-responsible"><UserRound size={16}/>{r.responsibleName||'Sin asignar'}</div><span className="ter-count muted">—</span><strong className="ter-count">{regionProvinces.length}</strong><strong className="ter-count">{regionCompanies.length}</strong>
                    <span className={`ter-status ${statusClass(r.status)}`}>{labelStatus(r.status)}</span>
                    <div className="ter-menu-wrap"><button className="ter-menu-btn" onClick={e=>{e.stopPropagation();setMenu(menu?.kind==='REGION'&&menu.id===r.id?null:{kind:'REGION',id:r.id})}}><MoreVertical/></button>{menu?.kind==='REGION'&&menu.id===r.id&&<div className="ter-menu" onClick={e=>e.stopPropagation()}>{canEditRegions&&<button onClick={()=>{openEditRegion(r);setMenu(null)}}><Edit3/>Editar</button>}<button onClick={()=>void showAudit('REGION',r.id,r.name)}><Clock3/>Historial</button>{canEditRegions&&<button className="danger" disabled={r.status==='ACTIVE'} onClick={()=>void removeRegion(r)}><Trash2/>Eliminar</button>}</div>}</div>
                  </div>
                  {regionOpen&&<div className="ter-region-detail">
                    <div className="ter-detail-block"><div className="ter-detail-title"><strong>{subdivisionPlural} ({regionProvinces.length})</strong><span>Catálogo: {core.territoryCatalogSource||'CORE LOCAL · UAT'}</span></div><div className="ter-detail-table"><div className="ter-detail-head"><span>Nombre</span><span>Código CORE</span><span>Estado</span></div>{regionProvinces.map(p=><div className="ter-detail-row" key={p.id}><span>{p.name}</span><code>{p.code}</code><span className={`ter-status small ${statusClass(p.status)}`}>{labelStatus(p.status)}</span></div>)}{!regionProvinces.length&&<div className="ter-empty">Sin {subdivisionPlural.toLowerCase()} asignadas.</div>}</div></div>
                    <div className="ter-detail-block companies"><div className="ter-detail-title"><strong>Compañías ({regionCompanies.length})</strong><span>La Región se asigna desde la vertical COM.</span></div><div className="ter-company-list">{regionCompanies.map(c=><div key={c.id}><Building2/><span><strong>{c.name}</strong><small>{c.code}</small></span><span className={`ter-status small ${statusClass(c.status)}`}>{labelStatus(c.status)}</span></div>)}{!regionCompanies.length&&<div className="ter-empty">Sin Compañías asignadas.</div>}</div></div>
                  </div>}
                </div>
              })}
              {!zoneRegions.length&&<div className="ter-zone-empty">Esta Zona todavía no tiene Regiones.</div>}
            </div>}
          </div>
        })}
        {!visibleZones.length&&<div className="ter-no-results"><Filter size={22}/><strong>Sin resultados</strong><span>Prueba limpiando los filtros.</span></div>}
      </div>
    </section>

    {editor&&<div className="ter-modal-backdrop" onMouseDown={()=>setEditor(null)}><div className="ter-modal" onMouseDown={e=>e.stopPropagation()}>
      <div className="ter-modal-head"><div><h3>{editor.mode==='CREATE'?'Nueva':'Editar'} {editor.kind==='ZONE'?'Zona':'Región'}</h3><p>{editor.kind==='ZONE'?`Define qué ${subdivisionPlural.toLowerCase()} pertenecen a la Zona.`:`La Región solo puede usar ${subdivisionPlural.toLowerCase()} previamente incluidas en su Zona.`}</p></div><button onClick={()=>setEditor(null)}><X/></button></div>
      <div className="ter-modal-grid">
        {editor.kind==='REGION'&&<label>Zona<select value={editor.zoneId} disabled={editor.mode==='EDIT'&&editor.status==='ACTIVE'} onChange={e=>setEditor({...editor,zoneId:e.target.value,provinceCodes:[]})}>{tree.zones.map(z=><option value={z.id} key={z.id}>{z.code} · {z.name}</option>)}</select></label>}
        <label>Código<input value={editor.code} disabled={editor.mode==='EDIT'} onChange={e=>setEditor({...editor,code:e.target.value.toUpperCase()})} placeholder={editor.kind==='ZONE'?'Ej. ZC':'Ej. R-C1'}/></label>
        <label>Nombre<input value={editor.name} disabled={editor.mode==='EDIT'&&editor.status==='ACTIVE'} onChange={e=>setEditor({...editor,name:e.target.value})} placeholder={editor.kind==='ZONE'?'Ej. Zona Centro':'Ej. Región Centro 1'}/></label>
        <label>Responsable<select value={editor.responsibleEmployeeId} onChange={e=>setEditor({...editor,responsibleEmployeeId:e.target.value})}><option value="">Sin asignar</option>{responsibles.map(p=><option key={p.employeeId} value={p.employeeId}>{p.fullName}</option>)}</select></label>
        <label>Estado<select value={editor.status} disabled={editor.mode==='EDIT'&&editor.status==='ACTIVE'} onChange={e=>setEditor({...editor,status:e.target.value})}><option value="DRAFT">Borrador</option><option value="ACTIVE">Activa</option><option value="INACTIVE">Inactiva</option></select></label>
      </div>
      <div className="ter-core-picker"><div><strong>{subdivisionPlural}</strong><span>Fuente: {core.territoryCatalogSource||'CORE LOCAL · UAT'}</span></div><div className="ter-core-grid">{editorAllowedProvinces.map(p=><label key={p.id}><input type="checkbox" checked={editor.provinceCodes.includes(p.code)} onChange={e=>setEditor({...editor,provinceCodes:e.target.checked?[...editor.provinceCodes,p.code]:editor.provinceCodes.filter(x=>x!==p.code)})}/><span>{p.name}</span><code>{p.code}</code></label>)}</div></div>
      {editor.mode==='EDIT'&&editor.status==='ACTIVE'&&<div className="ter-active-note"><ShieldCheck/>Esta entidad está Activa: no puede eliminarse ni cambiar su identidad, pero sí Responsable y {subdivisionPlural}.</div>}
      <div className="ter-modal-actions"><button className="ter-btn ghost" onClick={()=>setEditor(null)}>Cancelar</button><button className="ter-btn primary" disabled={!editor.code.trim()||!editor.name.trim()||(editor.kind==='REGION'&&!editor.zoneId)} onClick={()=>void saveEditor()}>Guardar</button></div>
    </div></div>}

    {audit&&<div className="ter-modal-backdrop" onMouseDown={()=>setAudit(null)}><div className="ter-modal audit" onMouseDown={e=>e.stopPropagation()}><div className="ter-modal-head"><div><h3>Historial · {audit.title}</h3><p>Trazabilidad territorial para auditoría.</p></div><button onClick={()=>setAudit(null)}><X/></button></div><div className="ter-audit-list">{audit.events.map(ev=><div key={ev.id}><Clock3/><span><strong>{ev.eventType.replaceAll('_',' ')}</strong><small>{ev.actorUsername} · {new Date(ev.occurredAt).toLocaleString('es-EC')}</small></span></div>)}{!audit.events.length&&<div className="ter-empty">Sin eventos registrados.</div>}</div></div></div>}
  </div>;
}
