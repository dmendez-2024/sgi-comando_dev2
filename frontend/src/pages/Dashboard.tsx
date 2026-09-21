import {useEffect,useMemo,useState} from 'react';
import {AlertTriangle,Building2,CalendarDays,CheckCircle2,ClipboardCheck,FileText,MapPinned,Radio,ShieldCheck,Sparkles,Users} from 'lucide-react';
import {api} from '../api';

type DashboardState={companies:any[];points:any[];posts:any[];regesep:number;coverage:any|null;error:string};

const sparkLines=[
  '0,30 18,24 36,27 54,18 72,22 90,10 108,16 126,5',
  '0,28 18,26 36,18 54,21 72,14 90,8 108,10 126,4',
  '0,29 18,24 36,15 54,12 72,18 90,11 108,8 126,12',
  '0,30 18,30 36,30 54,20 72,25 90,14 108,18 126,6'
];

function Sparkline({index}:{index:number}){return <svg className={`spark spark-${index}`} viewBox="0 0 126 36" aria-hidden="true"><polyline points={sparkLines[index]} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>}

export default function Dashboard(){
  const [data,setData]=useState<DashboardState>({companies:[],points:[],posts:[],regesep:0,coverage:null,error:''});

  useEffect(()=>{void (async()=>{
    try{
      const companiesRes=await api.companies();
      const services=await api.services();
      const pointGroups=await Promise.all(services.map((s:any)=>api.points(s.id)));
      const points=pointGroups.flat();
      const postGroups=await Promise.all(points.map(async(p:any)=>{try{return await api.posts(p.id)}catch{return []}}));
      const regesepResults=await Promise.all(points.map(async(p:any)=>{try{await api.currentRegesep(p.id);return 1}catch{return 0}}));
      const now=new Date(); now.setHours(12,0,0,0); const day=(now.getDay()+6)%7; now.setDate(now.getDate()-day); const weekStart=now.toISOString().slice(0,10);
      let coverage=null; try{coverage=await api.assignmentCoverage(weekStart)}catch{}
      setData({companies:companiesRes.items??[],points,posts:postGroups.flat(),regesep:regesepResults.reduce<number>((a,b)=>a+b,0),coverage,error:''});
    }catch(error){setData((old)=>({...old,error:String(error)}))}
  })()},[]);

  const today=useMemo(()=>new Intl.DateTimeFormat('es-EC',{weekday:'long',day:'numeric',month:'long',year:'numeric'}).format(new Date()),[]);
  const stats=[
    {label:'Compañías',value:data.companies.length,icon:Building2,index:0},
    {label:'Puntos',value:data.points.length,icon:MapPinned,index:1},
    {label:'Puestos',value:data.posts.length,icon:Users,index:2},
    {label:'REGESEP vigente',value:data.regesep,icon:FileText,index:3},
  ];
  const companyName=data.companies[0]?.name??'Compañía UAT';
  const companyCoverage=data.coverage?.companies?.[0];
  const unassigned=data.coverage?.totalUnassignedShifts??0;
  const coveragePct=companyCoverage?.coveragePct??0;

  return <div className="dashboard-shell">
    {data.error&&<div className="error dashboard-error">Datos parciales: {data.error}</div>}
    <div className="dashboard-stats">
      {stats.map(({label,value,icon:Icon,index})=><div className="dashboard-stat" key={label}>
        <div className={`stat-icon stat-icon-${index}`}><Icon size={21}/></div>
        <div className="stat-copy"><span>{label}</span><strong>{value}</strong><small><b>UAT</b> · dato actual</small></div>
        <Sparkline index={index}/>
      </div>)}
    </div>

    <div className="dashboard-grid dashboard-grid-primary">
      <div className="dashboard-card alerts-card">
        <div className="card-title-row"><h2>Alertas críticas</h2><button>Ver todas</button></div>
        <div className="alert-list">
          <div className="alert-item"><span className="alert-icon red"><AlertTriangle size={18}/></span><div><strong>{unassigned} turnos sin asignar</strong><small>{unassigned? 'Requieren planificación en Asignaciones':'Cobertura semanal completa'}</small></div><em>{data.coverage?'Actual':'Pendiente'}</em></div>
          <div className="alert-item"><span className="alert-icon orange"><FileText size={18}/></span><div><strong>{data.regesep} REGESEP vigentes</strong><small>Versionado estructurado habilitado</small></div><em>UAT</em></div>
          <div className="alert-item"><span className="alert-icon amber"><ShieldCheck size={18}/></span><div><strong>ATS pendiente</strong><small>Integración técnica preparada por adapter</small></div><em>Adapter</em></div>
          <div className="alert-item"><span className="alert-icon blue"><Radio size={18}/></span><div><strong>Sistema operativo</strong><small>Frontend, backend y PostgreSQL disponibles</small></div><em>En línea</em></div>
        </div>
      </div>

      <div className="dashboard-card coverage-card">
        <div className="card-title-row"><h2>Cobertura por compañía</h2><button>Ver reporte</button></div>
        <div className="coverage-head"><span>Compañía</span><span>Puestos</span><span>Cobertura</span><span>Estado</span></div>
        <div className="coverage-row"><div className="company-cell"><span className="company-emblem"><ShieldCheck size={18}/></span><strong>{companyName}</strong></div><strong>{data.posts.length}</strong><div className="coverage-pending"><span style={{background:`linear-gradient(90deg,#24a05a ${coveragePct}%,#e5e9ee ${coveragePct}%)`}}></span><small>{data.coverage?`${coveragePct}%`:'N/D'}</small></div><div className="status-muted"><span style={{background:coveragePct===100?'#24a05a':'#efa51c'}}></span>{data.coverage?(coveragePct===100?'Cobertura completa':`${companyCoverage?.unassignedShifts??0} turnos pendientes`):'Asignaciones pendiente'}</div></div>
      </div>

      <div className="dashboard-card map-card">
        <div className="card-title-row"><h2>Mapa de cobertura</h2><button>Ver mapa</button></div>
        <div className="map-placeholder">
          <div className="map-orbit orbit-a"></div><div className="map-orbit orbit-b"></div>
          <div className="ecuador-mark"><MapPinned size={42}/><strong>Ecuador</strong><span>{data.points.length} puntos registrados</span></div>
          <div className="map-point point-a"></div><div className="map-point point-b"></div>
        </div>
        <div className="map-legend"><span><i className="legend-green"></i>Configurado</span><span><i className="legend-gray"></i>ATS pendiente</span></div>
      </div>
    </div>

    <div className="dashboard-grid dashboard-grid-secondary">
      <div className="dashboard-card summary-card">
        <div className="card-title-row"><h2>Resumen operativo hoy</h2><div className="date-label"><CalendarDays size={15}/>{today}</div></div>
        <div className="summary-metrics">
          <div className="summary-metric"><span className="summary-icon green"><ClipboardCheck size={18}/></span><small>Incidentes hoy</small><strong>0</strong><em>Vertical pendiente</em></div>
          <div className="summary-metric"><span className="summary-icon blue"><Users size={18}/></span><small>Puestos configurados</small><strong>{data.posts.length}</strong><em>UAT actual</em></div>
          <div className="summary-metric"><span className="summary-icon red"><AlertTriangle size={18}/></span><small>Novedades abiertas</small><strong>0</strong><em>Vertical pendiente</em></div>
          <div className="summary-metric"><span className="summary-icon purple"><ShieldCheck size={18}/></span><small>REGESEP</small><strong>{data.regesep}</strong><em>Vigentes</em></div>
        </div>
      </div>

      <div className="dashboard-card activity-card">
        <div className="card-title-row"><h2>Actividad del día</h2><button>Ver detalle</button></div>
        <div className="activity-list">
          <div className="activity-item"><time>Ahora</time><span className="timeline-dot green"></span><div><strong>SGI: Comando operativo</strong><small>Backend y base de datos disponibles</small></div><b className="activity-status complete">Activo</b></div>
          <div className="activity-item"><time>UAT</time><span className="timeline-dot blue"></span><div><strong>CORE · LOCAL</strong><small>Contexto Instancia–País disponible</small></div><b className="activity-status progress">Local</b></div>
          <div className="activity-item"><time>UAT</time><span className="timeline-dot orange"></span><div><strong>ATS</strong><small>Contrato preparado para integración</small></div><b className="activity-status pending">Pendiente</b></div>
          <div className="activity-item"><time>UAT</time><span className="timeline-dot purple"></span><div><strong>STC / SMC</strong><small>Adapters definidos en baseline</small></div><b className="activity-status planned">Adapter</b></div>
        </div>
      </div>
    </div>

    <div className="dashboard-card recommendations-card">
      <div className="recommendation-title"><Sparkles size={20}/><strong>SGI Copilot · Recomendaciones UAT</strong></div>
      <ul><li>Asignaciones ya alimenta cobertura semanal real por Compañía; revisar turnos pendientes antes de publicar.</li><li>Conectar ATS para habilitar el mapa técnico y enriquecer REGESEP.</li><li>Mantener la arquitectura de dashboard como superficie de mando, no como repositorio de configuración.</li></ul>
      <button className="recommendation-button">Ver más recomendaciones</button>
    </div>
  </div>
}
