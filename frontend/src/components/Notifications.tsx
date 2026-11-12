import {useCallback,useEffect,useRef,useState} from 'react';
import {AlertTriangle,Bell,ChevronRight,RefreshCw,X} from 'lucide-react';
import {api,getUser,type OperationalNotification} from '../api';
import './Notifications.css';

const statusLabels:Record<string,string>={PENDING:'Pendiente',CRITICAL:'Crítica',IN_PROGRESS:'En gestión',OVERDUE:'Vencida',NEW:'Nueva',ESCALATED:'Escalada',ACKNOWLEDGED:'Reconocida'};
const typeLabels:Record<string,string>={INCIDENT:'Incidente',FINDING:'Hallazgo',VULNERABILITY:'Vulnerabilidad'};
export default function Notifications({onOpen}:{onOpen:(item?:OperationalNotification)=>void}){
  const user=getUser();
  const [open,setOpen]=useState(false);
  const [feed,setFeed]=useState<{total:number;items:OperationalNotification[]}>({total:0,items:[]});
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const container=useRef<HTMLDivElement>(null);
  const trigger=useRef<HTMLButtonElement>(null);
  const generation=useRef(0);
  const load=useCallback(async()=>{
    const revision=++generation.current;
    setLoading(true);
    try{
      const result=await api.notifications();
      if(revision===generation.current){setFeed(result);setError('');}
    }catch{
      if(revision===generation.current){setFeed({total:0,items:[]});setError('No se pudieron cargar las notificaciones.');}
    }finally{if(revision===generation.current)setLoading(false);}
  },[user]);
  useEffect(()=>{
    setOpen(false);setFeed({total:0,items:[]});setError('');
    void load();
    const timer=window.setInterval(()=>{if(document.visibilityState==='visible')void load()},60000);
    return ()=>{++generation.current;window.clearInterval(timer)};
  },[load]);
  useEffect(()=>{
    if(!open)return;
    const outside=(event:PointerEvent)=>{if(!container.current?.contains(event.target as Node))setOpen(false)};
    const escape=(event:KeyboardEvent)=>{if(event.key==='Escape'){setOpen(false);trigger.current?.focus()}};
    document.addEventListener('pointerdown',outside);document.addEventListener('keydown',escape);
    container.current?.querySelector<HTMLButtonElement>('.notification-panel button')?.focus();
    return ()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',escape)};
  },[open]);
  const navigate=(item?:OperationalNotification)=>{setOpen(false);onOpen(item)};
  return <div className="notifications" ref={container}>
    <button ref={trigger} className="notification-button" aria-label={`Notificaciones${error?' no disponibles':`: ${feed.total} casos activos`}`} aria-expanded={open} aria-controls="operational-notifications" onClick={()=>{setOpen(value=>!value);if(!open)void load()}}>
      <Bell size={19}/>{feed.total>0&&<span className="notification-dot">{feed.total>99?'99+':feed.total}</span>}{error&&<span className="notification-unavailable">!</span>}
    </button>
    {open&&<div id="operational-notifications" className="notification-panel" role="region" aria-label="Notificaciones operativas">
      <div className="notification-panel-heading"><div><strong>Notificaciones</strong><small>Casos activos de tu alcance autorizado</small></div><button onClick={()=>{setOpen(false);trigger.current?.focus()}} aria-label="Cerrar notificaciones"><X size={17}/></button></div>
      <div className="notification-panel-tools"><span>{feed.total} casos activos</span><button onClick={()=>void load()} disabled={loading} aria-label="Actualizar notificaciones"><RefreshCw size={14}/>{loading?'Actualizando…':'Actualizar'}</button></div>
      <div className="notification-list" aria-busy={loading}>
        {error?<div className="notification-empty" role="alert">{error}<button onClick={()=>void load()}>Reintentar</button></div>:!feed.items.length?<p className="notification-empty">{loading?'Cargando notificaciones…':'No tienes novedades ni alertas pendientes.'}</p>:feed.items.slice(0,10).map(item=><button className="notification-item" key={item.id} onClick={()=>navigate(item)}>
          <span className={`notification-item-icon ${item.priority==='CRITICAL'?'critical':''}`}><AlertTriangle size={17}/></span>
          <span className="notification-item-copy"><span className="notification-item-meta">{typeLabels[item.type]??item.type} · {statusLabels[item.status]??item.status}</span><strong>{item.title}</strong><small>{item.companyName} · {item.clientName} · {item.pointName}</small><time dateTime={item.occurredAt}>{new Intl.DateTimeFormat('es-EC',{dateStyle:'short',timeStyle:'short',timeZone:'America/Guayaquil'}).format(new Date(item.occurredAt))}</time></span><ChevronRight size={15}/>
        </button>)}
      </div>
      <button className="notification-view-all" onClick={()=>navigate()}>Ver casos en Consola <ChevronRight size={15}/></button>
    </div>}
  </div>
}
