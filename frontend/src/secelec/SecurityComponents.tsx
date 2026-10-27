import {useEffect, useMemo, useState, type FormEvent, type ReactNode} from 'react';
import {AlertTriangle, ArrowLeft, Bell, Camera, CheckCircle2, ChevronRight, Edit3, Eye, EyeOff, Flame, Fingerprint, Lightbulb, PlugZap, Plus, ShieldCheck, ShieldHalf, Trash2, X} from 'lucide-react';
import {errorText, FAMILIES, familyLabel, secelecApi, statusLabel, streamingModeLabel, type ComponentSave, type Family, type PointContext, type SecurityComponent,
  type CameraTestResult, type StreamingMode} from './secelecApi';
import './secelec.css';

const FAMILY_ICON:Record<Family,ReactNode>={PERIMETER:<ShieldHalf size={20}/>,ACCESS_CONTROL:<Fingerprint size={20}/>,VIDEO:<Camera size={20}/>,
  INTRUSION:<Bell size={20}/>,LIGHTING:<Lightbulb size={20}/>,FIRE:<Flame size={20}/>};

/**
 * Seguridad Electrónica · Componentes de Seguridad del Punto (diapositivas 7 y 8 de "OBSINT Estructura").
 * Se abre desde Configuración del Punto: <SecurityComponents point={…} onBack={…}/>.
 */
export default function SecurityComponents({point,onBack}:{point:PointContext;onBack:()=>void}){
  const [items,setItems]=useState<SecurityComponent[]>([]);
  const [family,setFamily]=useState<Family>('VIDEO');
  const [selectedId,setSelectedId]=useState('');
  const [editing,setEditing]=useState<SecurityComponent|'new'|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');

  async function load(){
    setLoading(true);
    try{setItems(await secelecApi.components(point.pointId));setError('')}
    catch(e){setError(errorText(e))}
    finally{setLoading(false)}
  }
  useEffect(()=>{void load()},[point.pointId]);

  const ofFamily=useMemo(()=>items.filter(c=>c.family===family),[items,family]);
  const selected=items.find(c=>c.id===selectedId)??null;
  const active=items.filter(c=>c.status==='ACTIVE').length;
  const observed=items.filter(c=>c.status==='OBSERVED').length;

  function chooseFamily(f:Family){setFamily(f);setSelectedId('');setEditing(null);}
  function saved(c:SecurityComponent){setItems(prev=>prev.some(x=>x.id===c.id)?prev.map(x=>x.id===c.id?c:x):[...prev,c]);setSelectedId(c.id);setEditing(null);}
  async function remove(c:SecurityComponent){
    if(!window.confirm(`¿Eliminar ${c.code} · ${c.name}?`))return;
    try{await secelecApi.remove(c.id);setItems(prev=>prev.filter(x=>x.id!==c.id));setSelectedId('');setError('')}
    catch(e){setError(errorText(e))}
  }

  return <div className="sec-page">
    <nav className="sec-breadcrumbs"><button type="button" onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración</button><span>/</span><small>Componentes de Seguridad</small></nav>
    <div className="sec-hero"><h2>Componentes de Seguridad</h2><p>Configuración, gestión y mantenimiento de los componentes de seguridad del punto.</p></div>
    <PointBar point={point}/>
    {error&&<div className="sec-error"><AlertTriangle size={16}/>{error}</div>}

    <div className="sec-kpis">
      <Kpi icon={<ShieldCheck size={20}/>} tone="blue" label="Total de componentes" value={items.length} hint="en el punto"/>
      <Kpi icon={<ShieldCheck size={20}/>} tone="green" label="Componentes activos" value={active} hint={items.length?`${Math.round(active*100/items.length)}% del total`:'—'}/>
      <Kpi icon={<AlertTriangle size={20}/>} tone="orange" label="Con observaciones" value={observed} hint="requieren revisión"/>
    </div>

    <div className="sec-layout">
      <section className="sec-card sec-families">
        <header><h3>Familia de componentes</h3><span>Seleccione una familia para ver sus componentes</span></header>
        {FAMILIES.map(f=>{const list=items.filter(c=>c.family===f.value);const warn=list.some(c=>c.status!=='ACTIVE');
          return <button key={f.value} type="button" className={`sec-family ${f.value.toLowerCase()}${family===f.value?' active':''}`} onClick={()=>chooseFamily(f.value)}>
            <span className="sec-family-icon">{FAMILY_ICON[f.value]}</span>
            <span><b>{f.label}</b><small>{f.hint}</small></span>
            <span className="sec-family-count">{list.length}{list.length>0&&<i className={warn?'warn':'ok'}/>}</span>
            <ChevronRight size={16}/>
          </button>})}
      </section>

      <section className="sec-card sec-list">
        <header><div><h3>Componentes configurados</h3><span>{familyLabel(family)} · {ofFamily.length} componentes</span></div>
          <button type="button" className="sec-primary" onClick={()=>{setEditing('new');setSelectedId('')}}><Plus size={16}/>Agregar componente</button></header>
        <table className="sec-table">
          <thead><tr><th>Código</th><th>Componente</th><th>Tipo</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>
            {loading?<tr><td colSpan={5} className="sec-empty">Cargando componentes…</td></tr>
            :ofFamily.length?ofFamily.map(c=><tr key={c.id} className={c.id===selectedId?'selected':''} onClick={()=>{setSelectedId(c.id);setEditing(null)}}>
              <td><b>{c.code}</b></td><td>{c.name}</td><td>{c.componentType}</td>
              <td><span className={`sec-status ${c.status.toLowerCase()}`}><i/>{statusLabel(c.status)}</span></td>
              <td className="sec-actions">
                <button type="button" title="Editar" aria-label="Editar" onClick={e=>{e.stopPropagation();setSelectedId(c.id);setEditing(c)}}><Edit3 size={15}/></button>
                <button type="button" title="Eliminar" aria-label="Eliminar" className="danger" onClick={e=>{e.stopPropagation();void remove(c)}}><Trash2 size={15}/></button>
              </td>
            </tr>)
            :<tr><td colSpan={5} className="sec-empty">No hay componentes de {familyLabel(family).toLowerCase()} en este punto.</td></tr>}
          </tbody>
        </table>
      </section>

      <section className="sec-card sec-detail">
        {editing
          ?<ComponentForm key={editing==='new'?`new-${family}`:editing.id} pointId={point.pointId} family={editing==='new'?family:editing.family}
              initial={editing==='new'?null:editing} onSaved={saved} onCancel={()=>setEditing(null)}/>
          :selected?<Detail c={selected} onEdit={()=>setEditing(selected)} onDelete={()=>void remove(selected)}/>
          :<div className="sec-empty-detail"><ShieldCheck size={30}/>Seleccione un componente para ver su detalle o agregue uno nuevo.</div>}
      </section>
    </div>
  </div>;
}

export function PointBar({point}:{point:PointContext}){
  return <div className="sec-point-bar">
    {point.companyName&&<div><span>Compañía</span><b>{point.companyName}</b></div>}
    {point.serviceName&&<div><span>Servicio</span><b>{point.serviceName}</b></div>}
    {point.clientName&&<div><span>Cliente</span><b>{point.clientName}</b></div>}
    <div><span>Punto</span><b>{point.pointName}</b></div>
  </div>;
}

function Kpi({icon,tone,label,value,hint}:{icon:ReactNode;tone:'blue'|'green'|'orange';label:string;value:number;hint:string}){
  return <article><span className={tone}>{icon}</span><div><small>{label}</small><strong>{value}</strong><em>{hint}</em></div></article>;
}

function Detail({c,onEdit,onDelete}:{c:SecurityComponent;onEdit:()=>void;onDelete:()=>void}){
  const rows:[string,string|null][]=[['Código',c.code],['Nombre',c.name],['Familia',familyLabel(c.family)],['Tipo',c.componentType],['Ubicación',c.location],
    ['Marca / Modelo',c.brandModel],['Cobertura',c.coverage]];
  if(c.family==='VIDEO')rows.push(['Dirección IP',c.ip],['Puerto',c.port==null?null:String(c.port)],['Canal',c.channel==null?null:String(c.channel)],
    ['Usuario',c.username],['Contraseña',c.hasPassword?'Guardada (cifrada)':'—'],
    ['Coordenadas',c.latitude==null||c.longitude==null?null:`${c.latitude}, ${c.longitude}`],
    ['Streaming',c.streamingEnabled==null?null:c.streamingEnabled?`Activo · ${streamingModeLabel(c.streamingMode??'COPY')}`:'Desactivado'],
    ['Video de pruebas',c.streamUrl]);
  return <>
    <header><h3>Detalle del componente</h3></header>
    <div className="sec-detail-head"><span className="sec-family-icon">{FAMILY_ICON[c.family]}</span><div><b>{c.code}</b><small>{c.name}</small></div>
      <span className={`sec-status ${c.status.toLowerCase()}`}><i/>{statusLabel(c.status)}</span></div>
    <dl className="sec-dl">{rows.map(([k,v])=><div key={k}><dt>{k}</dt><dd>{v||'—'}</dd></div>)}</dl>
    {c.observations&&<div className="sec-observations"><span>Observaciones</span><p>{c.observations}</p></div>}
    <div className="sec-detail-actions"><button type="button" onClick={onEdit}><Edit3 size={15}/>Editar componente</button><button type="button" className="danger" onClick={onDelete}><Trash2 size={15}/>Eliminar componente</button></div>
  </>;
}

function ComponentForm({pointId,family,initial,onSaved,onCancel}:{pointId:string;family:Family;initial:SecurityComponent|null;onSaved:(c:SecurityComponent)=>void;onCancel:()=>void}){
  const types=FAMILIES.find(f=>f.value===family)?.types??[];
  const [form,setForm]=useState<ComponentSave>(()=>({family,componentType:initial?.componentType??types[0]??'',name:initial?.name??'',location:initial?.location??'',
    coverage:initial?.coverage??'',brandModel:initial?.brandModel??'',status:initial?.status??'ACTIVE',observations:initial?.observations??'',
    ip:initial?.ip??'',port:initial?.port??(family==='VIDEO'?554:null),channel:initial?.channel??(family==='VIDEO'?1:null),
    latitude:initial?.latitude??null,longitude:initial?.longitude??null,streamingEnabled:initial?.streamingEnabled??true,streamingMode:initial?.streamingMode??'COPY',
    username:initial?.username??'',password:'',streamUrl:initial?.streamUrl??''}));
  const [showPassword,setShowPassword]=useState(false);
  const [testing,setTesting]=useState(false);
  const [test,setTest]=useState<CameraTestResult|null>(null);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const video=family==='VIDEO';
  const set=<K extends keyof ComponentSave>(key:K,value:ComponentSave[K])=>setForm(prev=>({...prev,[key]:value}));
  const num=(value:string)=>value.trim()===''?null:Number(value);

  async function testConnection(){
    setTesting(true);setTest(null);setError('');
    try{setTest(await secelecApi.testCamera(pointId,{componentId:initial?.id,ip:form.ip,port:form.port,channel:form.channel,username:form.username,password:form.password}))}
    catch(err){setError(errorText(err))}
    finally{setTesting(false)}
  }

  async function submit(e:FormEvent){
    e.preventDefault();
    setSaving(true);setError('');
    try{onSaved(initial?await secelecApi.update(initial.id,form):await secelecApi.create(pointId,form))}
    catch(err){setError(errorText(err))}
    finally{setSaving(false)}
  }

  return <form className="sec-form" onSubmit={submit}>
    <header><h3>{initial?`Editar ${initial.code}`:'Agregar componente'}</h3><button type="button" className="sec-icon-button" aria-label="Cancelar" onClick={onCancel}><X size={16}/></button></header>
    <p className="sec-form-family">{FAMILY_ICON[family]}{familyLabel(family)}</p>
    <Field label="Tipo de componente *"><input list={`sec-types-${family}`} value={form.componentType} onChange={e=>set('componentType',e.target.value)} required maxLength={80}/>
      <datalist id={`sec-types-${family}`}>{types.map(t=><option key={t} value={t}/>)}</datalist></Field>
    <Field label="Nombre del componente *"><input value={form.name} onChange={e=>set('name',e.target.value)} required maxLength={160} placeholder="Cámara Bala Acceso Norte"/></Field>
    <Field label="Ubicación"><input value={form.location} onChange={e=>set('location',e.target.value)} maxLength={200} placeholder="Acceso Norte"/></Field>
    {video&&<>
      <div className="sec-form-row three">
        <Field label="Dirección IP *"><input value={form.ip} onChange={e=>set('ip',e.target.value)} required maxLength={255} placeholder="192.168.1.10"/></Field>
        <Field label="Puerto *"><input type="number" min={1} max={65535} value={form.port??''} onChange={e=>set('port',num(e.target.value))} required/></Field>
        <Field label="Canal *"><input type="number" min={1} max={256} value={form.channel??''} onChange={e=>set('channel',num(e.target.value))} required/></Field>
      </div>
      <div className="sec-form-row">
        <Field label="Usuario *"><input value={form.username} onChange={e=>set('username',e.target.value)} required maxLength={120} autoComplete="off"/></Field>
        <Field label={initial?.hasPassword?'Contraseña':'Contraseña *'}><span className="sec-password">
          <input type={showPassword?'text':'password'} value={form.password} onChange={e=>set('password',e.target.value)}
            required={!initial?.hasPassword} autoComplete="new-password" placeholder={initial?.hasPassword?'Sin cambios':''}/>
          <button type="button" aria-label={showPassword?'Ocultar contraseña':'Mostrar contraseña'} title={showPassword?'Ocultar':'Mostrar'}
            onClick={()=>setShowPassword(v=>!v)}>{showPassword?<EyeOff size={16}/>:<Eye size={16}/>}</button>
        </span></Field>
      </div>
      <div className="sec-form-row">
        <Field label="Latitud"><input type="number" step="any" min={-90} max={90} value={form.latitude??''} onChange={e=>set('latitude',num(e.target.value))}
          required={form.longitude!=null} placeholder="-0.113972"/></Field>
        <Field label="Longitud"><input type="number" step="any" min={-180} max={180} value={form.longitude??''} onChange={e=>set('longitude',num(e.target.value))}
          required={form.latitude!=null} placeholder="-78.472500"/></Field>
      </div>
      <div className="sec-form-row">
        <Field label="Streaming"><span className="sec-switch-row">
          <button type="button" role="switch" aria-checked={form.streamingEnabled} aria-label="Streaming" className={`sec-switch${form.streamingEnabled?' on':''}`}
            onClick={()=>set('streamingEnabled',!form.streamingEnabled)}><i/></button>
          {form.streamingEnabled?'Activo':'Desactivado'}
        </span></Field>
        <Field label="Modo de streaming"><select value={form.streamingMode} onChange={e=>set('streamingMode',e.target.value as StreamingMode)} disabled={!form.streamingEnabled}>
          <option value="COPY">{streamingModeLabel('COPY')}</option><option value="TRANSCODE">{streamingModeLabel('TRANSCODE')}</option></select></Field>
      </div>
      <Field label="URL de video (pruebas, opcional)"><input value={form.streamUrl} onChange={e=>set('streamUrl',e.target.value)} maxLength={500} placeholder="obsint/videos/1-1.mp4"/></Field>
    </>}
    <Field label="Marca / Modelo"><input value={form.brandModel} onChange={e=>set('brandModel',e.target.value)} maxLength={160} placeholder="Hikvision DS-2CD2043G2-I"/></Field>
    <Field label="Cobertura"><input value={form.coverage} onChange={e=>set('coverage',e.target.value)} maxLength={300} placeholder="Ingreso principal y zona de parqueo"/></Field>
    <Field label="Estado"><select value={form.status} onChange={e=>set('status',e.target.value as ComponentSave['status'])}>
      <option value="ACTIVE">Activo</option><option value="OBSERVED">Con observación</option><option value="INACTIVE">Inactivo</option></select></Field>
    <Field label="Observaciones"><textarea value={form.observations} onChange={e=>set('observations',e.target.value)} maxLength={1000} rows={3}/></Field>
    {test&&<div className={`sec-test ${test.ok?'ok':'fail'}`} role="status">
      {test.ok?<CheckCircle2 size={16}/>:<AlertTriangle size={16}/>}
      <div><b>{test.message}</b><small>Canal {form.channel??1} · {test.streamPath} · {test.elapsedMs} ms</small>
        {test.codec==='H.265'&&form.streamingEnabled&&form.streamingMode==='COPY'&&<small>El navegador no reproduce H.265: use el modo Transcodificar o cambie la cámara a H.264.</small>}</div>
    </div>}
    {error&&<div className="sec-error"><AlertTriangle size={16}/>{error}</div>}
    <div className="sec-form-actions"><button type="submit" className="sec-primary" disabled={saving}>{saving?'Guardando…':'Guardar componente'}</button>
      {video&&<button type="button" onClick={()=>void testConnection()} disabled={testing||saving}><PlugZap size={15}/>{testing?'Probando…':'Probar conexión'}</button>}
      <button type="button" onClick={onCancel}>Cancelar</button></div>
  </form>;
}

function Field({label,children}:{label:string;children:ReactNode}){return <label className="sec-field"><span>{label}</span>{children}</label>}
