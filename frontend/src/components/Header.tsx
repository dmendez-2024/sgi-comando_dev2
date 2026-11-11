import {useEffect,useState} from 'react';
import {Menu,X} from 'lucide-react';
import Notifications from './Notifications';
import {getUser,setUser,type UatUser} from '../api';
import {identEmail,identEnabled,identPlatforms,identPortal,identSignOut} from '../security/identAuth';
const users:UatUser[]=['presidente','ggeneral','don','dnacional','dzonal','jregional','coord','asistente','monitor'];
const labels:Record<UatUser,string>={presidente:'Presidente',ggeneral:'Gerente General Ecuador',dlatam:'Director Operaciones LATAM',don:'Director Nacional de Operaciones',dnacional:'Director Nacional de Operaciones',dzonal:'Director Zonal de Operaciones',jregional:'Jefe Regional de Operaciones',coord:'Coordinador de Compañía',monitor:'Monitor',asistente:'Asistente de Operaciones',supervisor:'Supervisor',agente:'Agente',cliente:'Cliente'};
export default function Header({title,coreContext,onUserChange,onMenuToggle,navOpen=false,onNotification}:{title:string,coreContext?:any,onUserChange:()=>void,onMenuToggle?:()=>void,navOpen?:boolean,onNotification:(id?:string)=>void}){
  // Con IDENT: la persona y su Rol vienen del login de IDENT; "← Plataformas" solo si tiene 2 o mas Sistemas.
  const [plataformas,setPlataformas]=useState(0);
  useEffect(()=>{if(identEnabled())identPlatforms().then(p=>setPlataformas(p?.count??0))},[]);
  return <header className="app-header">
    <div className="app-header-title">
      <button className="mobile-nav-toggle" type="button" aria-label={navOpen?'Cerrar menú':'Abrir menú'} aria-expanded={navOpen} onClick={onMenuToggle}>{navOpen?<X size={21}/>:<Menu size={21}/>}</button>
      <div><div className="eyebrow">{coreContext?.country||'Cargando país desde CORE…'} · {coreContext?.territoryCatalogSource||'CORE'}{coreContext?.territorialDatasetVersion?` · ${coreContext.territorialDatasetVersion}`:''}</div><h1>{title}</h1></div>
    </div>
    <div className="header-actions">
      <Notifications key={getUser()} onOpen={onNotification}/>
      {identEnabled()
        ?<>
          <div className="userbox ident-userbox"><span>{identEmail()}</span><strong>{labels[getUser()]}</strong></div>
          {plataformas>1&&<button className="secondary-button" style={{whiteSpace:'nowrap'}} onClick={identPortal}>← Plataformas</button>}
          <button className="secondary-button" style={{whiteSpace:'nowrap'}} onClick={identSignOut}>Cerrar sesión</button>
        </>
        :<div className="userbox"><span>Usuario UAT</span><select defaultValue={getUser()} onChange={e=>{setUser(e.target.value as UatUser);onUserChange()}}>{users.map(u=><option key={u} value={u}>{labels[u]}{u==='don'?' (OPERACIONES_NACIONAL)':u==='dnacional'?' (DIRECTOR_NACIONAL)':''}</option>)}</select></div>}
    </div>
  </header>
}
