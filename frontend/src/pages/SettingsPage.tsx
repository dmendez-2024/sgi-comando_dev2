import {useEffect,useState} from 'react';
import {AlertTriangle,CheckCircle2,Lock,MapPin,Save} from 'lucide-react';
import {api,ApiError} from '../api';
import {resetDefaultRadius} from '../components/RadiusField';

type Settings={defaultRadiusM:number;systemRadiusM:number;configured:boolean;updatedBy:string|null;updatedAt:string|null;canEdit:boolean;minRadiusM:number;maxRadiusM:number};
const errorText=(e:unknown)=>{if(!(e instanceof ApiError))return String(e);try{const j=JSON.parse(e.body);return j.message??j.title??e.body}catch{return e.body||`Error ${e.status}`}};
const when=(iso:string)=>new Date(iso).toLocaleString('es-EC',{day:'numeric',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'});

/** Configuración → parámetros operativos de la instancia (país). Por ahora: radio GPS predeterminado de las fotos del agente. */
export default function SettingsPage(){
 const [s,setS]=useState<Settings|null>(null);const [radius,setRadius]=useState('');const [error,setError]=useState('');const [notice,setNotice]=useState('');const [saving,setSaving]=useState(false);
 const load=()=>api.evidenceLocationSettings().then(r=>{setS(r);setRadius(String(r.defaultRadiusM))}).catch(e=>setError(errorText(e)));
 useEffect(()=>{void load()},[]);
 const value=Number(radius);
 const invalid=!s||!/^\d+$/.test(radius)||value<s.minRadiusM||value>s.maxRadiusM;
 const changed=!!s&&!invalid&&value!==s.defaultRadiusM;
 const save=async()=>{
  if(!s||invalid)return;setSaving(true);setError('');setNotice('');
  try{await api.saveEvidenceLocationSettings(value);resetDefaultRadius();await load();setNotice(`Radio predeterminado guardado: ${value} m.`)}
  catch(e){setError(errorText(e))}finally{setSaving(false)}
 };
 return <div className="settings-page">
  <div className="settings-intro"><h2>Configuración</h2><p>Parámetros operativos de la instancia. Aplican a todos los Puntos y Puestos del país.</p></div>
  {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>setError('')}>Cerrar</button></div>}
  {notice&&<div className="posts-notice"><CheckCircle2 size={15}/><span>{notice}</span></div>}
  <section className="settings-card" aria-labelledby="settings-radius-title">
   <div className="settings-head"><span className="settings-icon"><MapPin size={18}/></span><div><h3 id="settings-radius-title">Radio GPS de las fotos del agente</h3>
    <p>Distancia máxima entre el GPS de la foto y la ubicación de referencia antes de marcar el aviso «Fuera del radio GPS» en Operación. Nunca bloquea al agente.</p></div></div>
   {!s?<p className="settings-loading">Cargando…</p>:<>
    <div className="settings-field">
     <label htmlFor="settings-radius">Radio predeterminado</label>
     <div className="settings-input"><input id="settings-radius" type="number" inputMode="numeric" min={s.minRadiusM} max={s.maxRadiusM} step={1} value={radius} disabled={!s.canEdit||saving}
      aria-invalid={invalid} aria-describedby="settings-radius-help" onChange={e=>setRadius(e.target.value)} onKeyDown={e=>{if(e.key==='Enter')void save()}}/><span>m</span></div>
     <small id="settings-radius-help">{invalid?`Ingresa un número entero entre ${s.minRadiusM} y ${s.maxRadiusM} m.`:`Entre ${s.minRadiusM} y ${s.maxRadiusM} m. En interiores el GPS del teléfono puede fallar por 30–80 m.`}</small>
    </div>
    <dl className="settings-uses">
     <div><dt>Lo usan</dt><dd>Hitos de patrulla, consignas en modo «Coordenadas GPS» y Puestos (Bitácora y Relevo) que no tienen radio propio.</dd></div>
     <div><dt>Radio propio</dt><dd>Se define junto a la latitud y la longitud de cada Hito, consigna o Puesto, y tiene prioridad sobre este valor.</dd></div>
     <div><dt>Historial</dt><dd>Cada foto guarda el radio con que se evaluó: cambiarlo aquí no altera los avisos ya registrados.</dd></div>
     <div><dt>Estado</dt><dd>{s.configured&&s.updatedAt?`Configurado por ${s.updatedBy} · ${when(s.updatedAt)}`:`Sin configurar: rige el valor del sistema (${s.systemRadiusM} m).`}</dd></div>
    </dl>
    <div className="settings-actions">{s.canEdit?<button type="button" className="ser-action primary" disabled={!changed||saving} onClick={()=>void save()}><Save size={15}/>{saving?'Guardando…':'Guardar radio'}</button>
     :<p className="settings-readonly"><Lock size={14}/>Solo Presidencia y las Direcciones de Operaciones o Nacional pueden cambiar este valor.</p>}</div>
   </>}
  </section>
 </div>;
}
