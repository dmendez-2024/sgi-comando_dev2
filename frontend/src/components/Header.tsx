import {useEffect,useState} from 'react';
import {Bell} from 'lucide-react';
import {getUser,setUser,type UatUser} from '../api';
import {identEmail,identEnabled,identPlatforms,identPortal,identSignOut} from '../security/identAuth';
const users:UatUser[]=['presidente','dnacional','dzonal','jregional','coord','asistente'];
const labels:Record<UatUser,string>={presidente:'Presidente',dlatam:'Director Operaciones LATAM',don:'Director Operaciones Nacional',dnacional:'Director Nacional',dzonal:'Director Zonal',jregional:'Jefe Regional',coord:'Coordinador',asistente:'Asistente de Coordinación',supervisor:'Supervisor',agente:'Agente',cliente:'Cliente'};
export default function Header({title,coreContext,onUserChange}:{title:string,coreContext?:any,onUserChange:()=>void}){
  // Con IDENT: la persona y su Rol vienen del login de IDENT; "← Plataformas" solo si tiene 2 o mas Sistemas.
  const [plataformas,setPlataformas]=useState(0);
  useEffect(()=>{if(identEnabled())identPlatforms().then(p=>setPlataformas(p?.count??0))},[]);
  return <header>
    <div><div className="eyebrow">{coreContext?.country||'Cargando país desde CORE…'} · {coreContext?.territoryCatalogSource||'CORE'}{coreContext?.territorialDatasetVersion?` · ${coreContext.territorialDatasetVersion}`:''}</div><h1>{title}</h1></div>
    <div className="header-actions">
      <button className="notification-button" aria-label="Notificaciones"><Bell size={19}/><span className="notification-dot">0</span></button>
      {identEnabled()
        ?<>
          <div className="userbox"><span>{identEmail()}</span><strong style={{whiteSpace:'nowrap'}}>{labels[getUser()]}</strong></div>
          {plataformas>1&&<button className="secondary-button" style={{whiteSpace:'nowrap'}} onClick={identPortal}>← Plataformas</button>}
          <button className="secondary-button" style={{whiteSpace:'nowrap'}} onClick={identSignOut}>Cerrar sesión</button>
        </>
        :<div className="userbox"><span>Usuario UAT</span><select defaultValue={getUser()} onChange={e=>{setUser(e.target.value as UatUser);onUserChange()}}>{users.map(u=><option key={u} value={u}>{labels[u]}</option>)}</select></div>}
    </div>
  </header>
}
