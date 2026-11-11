import {useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode} from 'react';
import {attachStream, goLive, liveDelay, LIVE_BEHIND_S} from '../obsint/liveStream';
import {AlertTriangle, ArrowLeft, Bell, Camera, CheckCircle2, ChevronRight, Edit3, Eye, EyeOff, Flame, Fingerprint, Lightbulb, PlugZap, Plus, Radio, ShieldCheck, ShieldHalf, Trash2, X} from 'lucide-react';
import {errorText, FAMILIES, familyLabel, secelecApi, statusLabel, type BrandOption, type CameraBrand, type ComponentSave, type StreamQuality, type ElectronicDevice, type Family, type PointContext,
  type SecurityComponent, type CameraTestResult} from './secelecApi';
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
  /** Lista "Marca" de las cámaras (la entrega el backend con la ruta de cada una). */
  const [brands,setBrands]=useState<BrandOption[]>([]);
  useEffect(()=>{secelecApi.brands().then(setBrands).catch(e=>setError(errorText(e)))},[]);
  /** Dispositivos OBSINT que llegaron de SIC:COM: cada cámara se asigna a uno (su línea contratada y su horario). */
  const [devices,setDevices]=useState<ElectronicDevice[]>([]);

  async function load(){
    setLoading(true);
    try{const [list,devs]=await Promise.all([secelecApi.components(point.pointId),secelecApi.devices(point.pointId)]);setItems(list);setDevices(devs);setError('')}
    catch(e){setError(errorText(e))}
    finally{setLoading(false)}
  }
  useEffect(()=>{void load()},[point.pointId]);

  const ofFamily=useMemo(()=>items.filter(c=>c.family===family),[items,family]);
  const selected=items.find(c=>c.id===selectedId)??null;
  const active=items.filter(c=>c.status==='ACTIVE').length;
  const observed=items.filter(c=>c.status==='OBSERVED').length;

  function chooseFamily(f:Family){setFamily(f);setSelectedId('');setEditing(null);}
  function saved(c:SecurityComponent){setItems(prev=>prev.some(x=>x.id===c.id)?prev.map(x=>x.id===c.id?c:x):[...prev,c]);setSelectedId(c.id);setEditing(null);
    void secelecApi.devices(point.pointId).then(setDevices).catch(()=>undefined);}
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
    {devices.length>0&&<DeviceBar devices={devices} outside={items.filter(c=>c.family==='VIDEO'&&!c.electronicDeviceId&&c.status!=='INACTIVE').length}/>}

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
          <thead><tr><th>Código</th><th>Componente</th><th>{family==='VIDEO'&&devices.length?'Servicio OBSINT':'Tipo'}</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>
            {loading?<tr><td colSpan={5} className="sec-empty">Cargando componentes…</td></tr>
            :ofFamily.length?ofFamily.map(c=><tr key={c.id} className={c.id===selectedId?'selected':''} onClick={()=>{setSelectedId(c.id);setEditing(null)}}>
              <td><b>{c.code}</b></td><td>{c.name}</td><td>{family==='VIDEO'&&devices.length?(devices.find(d=>d.id===c.electronicDeviceId)?.code??<em className="sec-no-device">Fuera de OBSINT</em>):c.componentType}</td>
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
          ?<ComponentForm key={editing==='new'?`new-${family}`:editing.id} pointId={point.pointId} brands={brands} devices={devices} family={editing==='new'?family:editing.family}
              initial={editing==='new'?null:editing} onSaved={saved} onCancel={()=>setEditing(null)}/>
          :selected?<Detail c={selected} brands={brands} devices={devices} onEdit={()=>setEditing(selected)} onDelete={()=>void remove(selected)}/>
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

/**
 * Lo que contrató el cliente en SIC:COM: cada dispositivo es una línea con su horario; las cámaras se asignan a ellos.
 * outside: cámaras que no son del servicio OBSINT (videovigilancia del punto, sin dispositivo); es normal, no es un error.
 */
function DeviceBar({devices,outside}:{devices:ElectronicDevice[];outside:number}){
  return <section className="sec-card sec-devices"><header><h3>Servicio OBSINT contratado (SIC:COM)</h3><span>Asigne cada cámara al dispositivo que le corresponde</span></header>
    <div className="sec-device-list">{devices.map(d=><article key={d.id} className={d.cameras?'ok':'missing'}>
      <b>{d.code}</b><span>{d.name}</span><small>{d.schedule}</small>
      <em>{d.commercialStatus.toUpperCase()!=='ACTIVE'?'Inactivo':d.cameras?`${d.cameras} ${d.cameras===1?'cámara':'cámaras'}`:'Sin cámara asignada'}</em></article>)}
      {outside>0&&<article className="outside"><b>Fuera de OBSINT</b><span>Videovigilancia del punto</span><small>No se revisan en la Consola OBSINT</small>
        <em>{outside} {outside===1?'cámara':'cámaras'}</em></article>}</div>
  </section>;
}

function Kpi({icon,tone,label,value,hint}:{icon:ReactNode;tone:'blue'|'green'|'orange';label:string;value:number;hint:string}){
  return <article><span className={tone}>{icon}</span><div><small>{label}</small><strong>{value}</strong><em>{hint}</em></div></article>;
}

function Detail({c,brands,devices,onEdit,onDelete}:{c:SecurityComponent;brands:BrandOption[];devices:ElectronicDevice[];onEdit:()=>void;onDelete:()=>void}){
  const video=c.family==='VIDEO';
  const rows:[string,string|null][]=[['Código',c.code],['Nombre',c.name],['Familia',familyLabel(c.family)],['Tipo',c.componentType],['Ubicación',c.location]];
  if(video&&devices.length){const d=devices.find(x=>x.id===c.electronicDeviceId);rows.push(['Servicio OBSINT',d?`${d.code} · ${d.name} · ${d.schedule}`:'No es parte del servicio OBSINT'])}
  if(video)rows.push(['Marca',brands.find(b=>b.code===c.cameraBrand)?.label??c.cameraBrand],['Modelo',c.brandModel]);
  else rows.push(['Marca / Modelo',c.brandModel]);
  rows.push(['Cobertura',c.coverage]);
  if(video){
    rows.push(['Dirección IP',c.ip],['Puerto',c.port==null?null:String(c.port)]);
    // Con la marca "Otra" el canal no se usa (va dentro de la ruta escrita a mano): mostrarlo confunde.
    if(c.cameraBrand!=='OTHER')rows.push(['Canal',c.channel==null?null:String(c.channel)],
      ['Calidad',c.streamQuality==='SUB'?'Secundaria (liviana)':'Principal (alta calidad)']);
    rows.push(['Ruta del video',c.streamPath],['Códec',c.detectedCodec],
      ['Usuario',c.username],['Contraseña',c.hasPassword?'Guardada (cifrada)':'—'],
      ['Coordenadas',c.latitude==null||c.longitude==null?null:`${c.latitude}, ${c.longitude}`],
      ['Streaming',c.streamingEnabled==null?null:c.streamingEnabled?'Activo':'Desactivado']);
    if(c.streamUrl)rows.push(['Video de pruebas',c.streamUrl]);
  }
  return <>
    <header><h3>Detalle del componente</h3></header>
    <div className="sec-detail-head"><span className="sec-family-icon">{FAMILY_ICON[c.family]}</span><div><b>{c.code}</b><small>{c.name}</small></div>
      <span className={`sec-status ${c.status.toLowerCase()}`}><i/>{statusLabel(c.status)}</span></div>
    <dl className="sec-dl">{rows.map(([k,v])=><div key={k}><dt>{k}</dt><dd>{v||'—'}</dd></div>)}</dl>
    {c.observations&&<div className="sec-observations"><span>Observaciones</span><p>{c.observations}</p></div>}
    <div className="sec-detail-actions"><button type="button" onClick={onEdit}><Edit3 size={15}/>Editar componente</button><button type="button" className="danger" onClick={onDelete}><Trash2 size={15}/>Eliminar componente</button></div>
  </>;
}

function ComponentForm({pointId,family,brands,devices,initial,onSaved,onCancel}:{pointId:string;family:Family;brands:BrandOption[];devices:ElectronicDevice[];initial:SecurityComponent|null;
  onSaved:(c:SecurityComponent)=>void;onCancel:()=>void}){
  const types=FAMILIES.find(f=>f.value===family)?.types??[];
  const [form,setForm]=useState<ComponentSave>(()=>({family,componentType:initial?.componentType??types[0]??'',name:initial?.name??'',location:initial?.location??'',
    coverage:initial?.coverage??'',brandModel:initial?.brandModel??'',status:initial?.status??'ACTIVE',observations:initial?.observations??'',
    ip:initial?.ip??'',port:initial?.port??(family==='VIDEO'?554:null),channel:initial?.channel??(family==='VIDEO'?1:null),
    // Cámara nueva: secundaria (liviana, la que mejor anda en la consola). Las guardadas conservan la suya (antes, siempre principal).
    cameraBrand:initial?.cameraBrand??'HIKVISION',streamQuality:initial?.streamQuality??(initial?'MAIN':'SUB'),rtspPath:initial?.rtspPath??'',
    latitude:initial?.latitude??null,longitude:initial?.longitude??null,streamingEnabled:initial?.streamingEnabled??true,streamingMode:initial?.streamingMode??'COPY',
    username:initial?.username??'',password:'',streamUrl:initial?.streamUrl??'',electronicDeviceId:initial?.electronicDeviceId??(devices.length===1?devices[0].id:null)}));
  /*
   * Servicio OBSINT de la cámara: '' = sin elegir (cámara nueva con varios dispositivos; el campo es obligatorio para no olvidarlo),
   * NOT_OBSINT = no es parte del servicio OBSINT (se guarda sin dispositivo), o el id del dispositivo.
   */
  const [deviceChoice,setDeviceChoice]=useState(()=>initial?(initial.electronicDeviceId??NOT_OBSINT):devices.length===1?devices[0].id:'');
  const chooseDevice=(value:string)=>{setDeviceChoice(value);set('electronicDeviceId',value&&value!==NOT_OBSINT?value:null)};
  const [showPassword,setShowPassword]=useState(false);
  const [testing,setTesting]=useState(false);
  const [test,setTest]=useState<CameraTestResult|null>(null);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const video=family==='VIDEO';
  /** Solo con la marca "Otra" se escribe la ruta del video; las demás la arman solas en el backend. */
  const brand=brands.find(b=>b.code===form.cameraBrand);
  const needsPath=brand?.requiresPath??form.cameraBrand==='OTHER';
  /** Ruta que se va a pedir a la cámara con la marca, el canal y la calidad elegidos (con "Otra", la escrita). */
  const template=form.streamQuality==='SUB'?brand?.subPathTemplate:brand?.pathTemplate;
  const routePreview=needsPath?form.rtspPath:template?template.split('{canal}').join(String(form.channel??1)):null;
  function chooseBrand(code:CameraBrand){
    const next=brands.find(b=>b.code===code);
    setForm(prev=>({...prev,cameraBrand:code,port:prev.port??next?.defaultPort??554}));
    setTest(null);
  }
  const set=<K extends keyof ComponentSave>(key:K,value:ComponentSave[K])=>setForm(prev=>({...prev,[key]:value}));
  const num=(value:string)=>value.trim()===''?null:Number(value);

  async function testConnection(){
    setTesting(true);setTest(null);setError('');
    try{const result=await secelecApi.testCamera(pointId,{componentId:initial?.id,ip:form.ip,port:form.port,channel:form.channel,cameraBrand:form.cameraBrand,
      streamQuality:form.streamQuality,rtspPath:form.rtspPath,username:form.username,password:form.password});
      setTest(result);
      // H.265 no se ve en el navegador: se guarda para que el servidor de video la convierta a H.264.
      if(result.ok&&/H\.?265|HEVC/i.test(result.codec??''))set('streamingMode','TRANSCODE');}
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
      {devices.length>0&&<Field label="Servicio OBSINT (SIC:COM) *"><select className={deviceChoice?'':'sec-need'} value={deviceChoice} onChange={e=>chooseDevice(e.target.value)} required>
        <option value="" disabled>Seleccione…</option>
        {devices.map(d=><option key={d.id} value={d.id}>{d.code} · {d.name} · {d.schedule}{d.cameras===0&&d.id!==initial?.electronicDeviceId?' · sin cámaras':''}</option>)}
        <option value={NOT_OBSINT}>No es parte del servicio OBSINT</option></select>
        <small className="sec-field-help">Elija el dispositivo si esta cámara se revisa en la Consola OBSINT. Si es solo videovigilancia del punto, elija «No es parte del servicio OBSINT».</small></Field>}
      <div className="sec-form-row">
        <Field label="Marca *"><select value={form.cameraBrand} onChange={e=>chooseBrand(e.target.value as CameraBrand)} required>
          {brands.length?brands.map(b=><option key={b.code} value={b.code}>{b.label}</option>):<option value={form.cameraBrand}>{form.cameraBrand}</option>}</select></Field>
        <Field label="Modelo"><input value={form.brandModel} onChange={e=>set('brandModel',e.target.value)} maxLength={160} placeholder="DS-2CD2043G2-I (opcional)"/></Field>
      </div>
      {needsPath&&<Field label="Ruta del video *"><input value={form.rtspPath} onChange={e=>set('rtspPath',e.target.value)} required maxLength={300}
        pattern="/\S*" title="Empieza con / y sin espacios" placeholder="/stream1 (está en el manual de la cámara)"/></Field>}
      <div className={`sec-form-row${needsPath?' ip-port':' three'}`}>
        <Field label="Dirección IP *"><input value={form.ip} onChange={e=>set('ip',e.target.value)} required maxLength={255} placeholder="192.168.1.10"/></Field>
        <Field label="Puerto *"><input type="number" min={1} max={65535} value={form.port??''} onChange={e=>set('port',num(e.target.value))} required/></Field>
        {/* Con la marca "Otra" el canal va dentro de la ruta escrita a mano (p. ej. /Streaming/Channels/102): no se pide aparte. */}
        {!needsPath&&<Field label="Canal *"><input type="number" min={1} max={256} value={form.channel??''} onChange={e=>set('channel',num(e.target.value))} required
          title="1 si es una cámara sola; el número de cámara si es un grabador (NVR/DVR)"/></Field>}
      </div>
      {/* Calidad: con "Otra" va dentro de la ruta escrita. */}
      {!needsPath&&<Field label="Calidad del video *"><select value={form.streamQuality} onChange={e=>{set('streamQuality',e.target.value as StreamQuality);setTest(null)}} required>
        <option value="SUB">Secundaria (liviana, recomendada para la Consola)</option>
        <option value="MAIN">Principal (alta calidad, más pesada)</option></select></Field>}
      {routePreview&&<p className="sec-route-preview">Ruta del video: <code>{routePreview}</code></p>}
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
      <div className="sec-form-row one">
        <Field label="Streaming"><span className="sec-switch-row">
          <button type="button" role="switch" aria-checked={form.streamingEnabled} aria-label="Streaming" className={`sec-switch${form.streamingEnabled?' on':''}`}
            onClick={()=>set('streamingEnabled',!form.streamingEnabled)}><i/></button>
          {form.streamingEnabled?'Activo':'Desactivado'}
        </span></Field>
      </div>
    </>}
    {!video&&<Field label="Marca / Modelo"><input value={form.brandModel} onChange={e=>set('brandModel',e.target.value)} maxLength={160}/></Field>}
    <Field label="Cobertura"><input value={form.coverage} onChange={e=>set('coverage',e.target.value)} maxLength={300} placeholder="Ingreso principal y zona de parqueo"/></Field>
    <Field label="Estado"><select value={form.status} onChange={e=>set('status',e.target.value as ComponentSave['status'])}>
      <option value="ACTIVE">Activo</option><option value="OBSERVED">Con observación</option><option value="INACTIVE">Inactivo</option></select></Field>
    <Field label="Observaciones"><textarea value={form.observations} onChange={e=>set('observations',e.target.value)} maxLength={1000} rows={3}/></Field>
    {test&&<div className={`sec-test ${test.ok?'ok':'fail'}`} role="status">
      {test.ok?<CheckCircle2 size={16}/>:<AlertTriangle size={16}/>}
      <div><b>{test.message}</b><small>Ruta {test.streamPath} · {test.elapsedMs} ms</small>
        {test.ok&&isH265(test.codec)&&form.streamingEnabled&&<small>La cámara envía H.265: se convierte a H.264 automáticamente. La vista previa tarda unos segundos más.</small>}</div>
    </div>}
    {test?.ok&&(test.previewUrl
      ?<LivePreview url={test.previewUrl}/>
      :<p className="sec-preview-note">Vista previa no disponible: el servidor de video no está configurado.</p>)}
    {error&&<div className="sec-error"><AlertTriangle size={16}/>{error}</div>}
    {/* Cámaras: primero se prueba y luego se guarda; por eso Probar conexión va antes. */}
    <div className="sec-form-actions">
      {video&&<button type="button" onClick={()=>void testConnection()} disabled={testing||saving}><PlugZap size={15}/>{testing?'Probando…':'1. Probar conexión'}</button>}
      <button type="submit" className="sec-primary" disabled={saving}>{saving?'Guardando…':video?'2. Guardar componente':'Guardar componente'}</button>
      <button type="button" onClick={onCancel}>Cancelar</button></div>
  </form>;
}

/** Video en vivo de "Probar conexión" (HLS o WebRTC del servidor de video); muestra "Sin señal" si no llega imagen. */
function LivePreview({url}:{url:string}){
  const ref=useRef<HTMLVideoElement>(null);
  const [failed,setFailed]=useState(false);
  const [playing,setPlaying]=useState(false);
  /** El video quedó atrasado respecto al en vivo (pausa o red lenta): se ofrece volver al momento actual, igual que en la Consola. */
  const [behind,setBehind]=useState(false);
  useEffect(()=>{
    const video=ref.current;
    if(!video)return;
    setFailed(false);setPlaying(false);
    // attachStream informa cada estado (conectando, reproduciendo…); solo los finales sin video cuentan como "Sin señal".
    return attachStream(video,url,status=>{setPlaying(status==='playing');if(status==='failed'||status==='denied')setFailed(true)});
  },[url]);
  // Revisa cada segundo cuánto va atrasado el video.
  useEffect(()=>{
    if(!playing){setBehind(false);return}
    const id=setInterval(()=>{const video=ref.current;if(video)setBehind(video.paused||liveDelay(video)>LIVE_BEHIND_S)},1000);
    return()=>clearInterval(id);
  },[playing]);
  function toLive(){const video=ref.current;if(video){goLive(video);setBehind(false)}}
  return <div className="sec-preview">
    <div className="sec-preview-head">
      <span>Vista previa</span>
      {/* Siempre ocupan su lugar (se ocultan, no se quitan): así la vista previa no salta cuando el video reconecta. */}
      {!failed&&<div className={`sec-preview-live${playing?'':' idle'}`} aria-hidden={!playing}>
        <em className={`sec-preview-badge${behind?' behind':''}`}>{behind?'ATRASADO':'EN VIVO'}</em>
        <button type="button" className={`sec-go-live${behind?' behind':''}`} title={behind?'Ir al en vivo':'Estás viendo el en vivo'}
          aria-label="Ir al en vivo" onClick={toLive} disabled={!playing} tabIndex={playing?0:-1}><Radio size={13}/>En vivo</button>
      </div>}
    </div>
    {failed?<div className="sec-preview-fail"><AlertTriangle size={16}/>Sin señal: la cámara conectó, pero el video no llegó al navegador.</div>
      :<video ref={ref} autoPlay muted playsInline controls/>}
  </div>;
}

/** Valor del selector para "No es parte del servicio OBSINT" (se guarda sin dispositivo). */
const NOT_OBSINT='NOT_OBSINT';
/** El servidor de video informa "H265"; la prueba directa, "H.265"; algunas cámaras, "HEVC". */
const isH265=(codec:string|null|undefined)=>/H\.?265|HEVC/i.test(codec??'');

function Field({label,children}:{label:string;children:ReactNode}){return <label className="sec-field"><span>{label}</span>{children}</label>}
