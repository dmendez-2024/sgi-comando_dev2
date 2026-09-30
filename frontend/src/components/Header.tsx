import {Bell} from 'lucide-react';
import {getUser,setUser,type UatUser} from '../api';
const users:UatUser[]=['presidente','dnacional','dzonal','jregional','coord','asistente'];
const labels:Record<UatUser,string>={presidente:'Presidente',dlatam:'Director Operaciones LATAM',don:'Director Operaciones Nacional',dnacional:'Director Nacional',dzonal:'Director Zonal',jregional:'Jefe Regional',coord:'Coordinador',asistente:'Asistente de Coordinación',supervisor:'Supervisor',agente:'Agente',cliente:'Cliente'};
export default function Header({title,coreContext,onUserChange}:{title:string,coreContext?:any,onUserChange:()=>void}){
  return <header>
    <div><div className="eyebrow">{coreContext?.country||'Cargando país desde CORE…'} · {coreContext?.territoryCatalogSource||'CORE'}{coreContext?.territorialDatasetVersion?` · ${coreContext.territorialDatasetVersion}`:''}</div><h1>{title}</h1></div>
    <div className="header-actions">
      <button className="notification-button" aria-label="Notificaciones"><Bell size={19}/><span className="notification-dot">0</span></button>
      <div className="userbox"><span>Usuario UAT</span><select defaultValue={getUser()} onChange={e=>{setUser(e.target.value as UatUser);onUserChange()}}>{users.map(u=><option key={u} value={u}>{labels[u]}</option>)}</select></div>
    </div>
  </header>
}
