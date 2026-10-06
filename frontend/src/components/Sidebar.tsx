import {useEffect,useState} from 'react';
import {BarChart3,Bell,Building2,ChevronDown,ClipboardList,FileSearch,FileText,Gauge,MapPinned,MessageSquareText,Radio,Settings,ShieldCheck,Users,Warehouse,Map,Network,Smartphone} from 'lucide-react';

type NavItem={name:string;icon:any};
type NavGroup={name:string;icon:any;children:NavItem[]};

const operations:NavGroup={
  name:'Operaciones',icon:ShieldCheck,children:[
    {name:'Territorio',icon:Map},
    {name:'Compañías',icon:Building2},
    {name:'Servicios',icon:MapPinned},
    {name:'Coordinación',icon:Network},
    {name:'Asignaciones',icon:Users},
    {name:'Consola',icon:Bell},
    {name:'Bitácora',icon:FileText},
    {name:'Consignas',icon:ClipboardList},
    {name:'Simulador Agente (UAT)',icon:Smartphone}, // TEMPORAL (demo UAT)
    {name:'Novedades',icon:ShieldCheck},
    {name:'Requerimientos',icon:MessageSquareText},
    {name:'Comunicación',icon:Radio},
  ]
};

const reports:NavGroup={
  name:'Reportes',icon:ClipboardList,children:[
    {name:'Estadísticas',icon:BarChart3},
    {name:'Reportería Ad-Hoc',icon:FileSearch},
  ]
};

function Group({group,active,onChange}:{group:NavGroup;active:string;onChange:(v:string)=>void}){
  const contains=group.children.some((x)=>x.name===active);
  const [open,setOpen]=useState(group.name==='Operaciones'||contains);
  useEffect(()=>{if(contains)setOpen(true)},[contains]);
  const G=group.icon;
  return <div className={`navgroup ${open?'open':''}`}>
    <button className={`groupbutton ${contains?'group-active':''}`} onClick={()=>setOpen((v)=>!v)} aria-expanded={open}>
      <G size={18}/><span>{group.name}</span><ChevronDown className="chevron" size={16}/>
    </button>
    {open&&<div className="submenu">
      {group.children.map(({name,icon:I})=><button key={name} onClick={()=>onChange(name)} className={active===name?'active':''}><I size={16}/><span>{name}</span></button>)}
    </div>}
  </div>
}

export const SIMULATOR_MENU='Simulador Agente (UAT)';

export default function Sidebar({active,onChange,uatTools}:{active:string;onChange:(v:string)=>void;uatTools:boolean}){
  // Herramientas UAT (Simulador de Agente) solo visibles con la bandera UAT del backend.
  const ops={...operations,children:operations.children.filter(c=>uatTools||c.name!==SIMULATOR_MENU)};
  return <aside className="sidebar">
    <div className="brand"><img className="brand-logo" src={`${import.meta.env.BASE_URL}sgi-comando-logo-sidebar.png`} alt="SGI Comando"/></div>
    <nav>
      <button onClick={()=>onChange('Dashboard')} className={active==='Dashboard'?'active':''}><Gauge size={18}/><span>Dashboard</span></button>
      <Group group={ops} active={active} onChange={onChange}/>
      <button onClick={()=>onChange('Recurso Humano')} className={active==='Recurso Humano'?'active':''}><Users size={18}/><span>Recurso Humano</span></button>
      <button onClick={()=>onChange('Recurso Material')} className={active==='Recurso Material'?'active':''}><Warehouse size={18}/><span>Recurso Material</span></button>
      <Group group={reports} active={active} onChange={onChange}/>
      <button onClick={()=>onChange('Auditoría')} className={active==='Auditoría'?'active':''}><ShieldCheck size={18}/><span>Auditoría</span></button>
      <button onClick={()=>onChange('Configuración')} className={active==='Configuración'?'active':''}><Settings size={18}/><span>Configuración</span></button>
    </nav>
    <div className="version version-ledger" aria-label="Versiones de verticales">
      <div><span>TER v1.0</span><b>FROZEN</b></div>
      <div><span>COM v1.1.3</span><b>FROZEN</b></div>
      <div><span>ASI v0.7.4</span><b>FROZEN</b></div>
      <div><span>SER v0.10.10</span><b>FROZEN</b></div>
      <div><span>COO v0.1</span><b>FROZEN</b></div>
      <div><span>BIT v0.1</span><b>FROZEN</b></div>
      <div><span>CNS v0.1.2</span><b>FROZEN</b></div>
      <div><span>NOV v0.1</span><b>FROZEN</b></div>
      <div><span>CSL v0.1</span><em>UAT</em></div>
    </div>
  </aside>
}
