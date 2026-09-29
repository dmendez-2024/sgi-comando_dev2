import {useEffect,useMemo,useState} from 'react';
import {Activity,AlertTriangle,CalendarDays,CheckCircle2,ChevronDown,ClipboardCheck,FileText,MapPin,Search,ShieldAlert} from 'lucide-react';
import {api,getUser,type UatUser} from '../api';

type Company={id:string;name:string};
type ServiceRow={
  serviceId:string;pointId:string;postId:string;companyId:string|null;companyName:string;
  clientId:string;clientName:string;pointName:string;postCode:string;postName:string;tier:string;
  idAverage:number|null;icAverage:number|null;pendingNews:number;
  state:'ACTIVE'|'INACTIVE'|'TO_CONFIGURE'|'PENDING_ASSIGNMENT';assignmentStatus:string;atsLoaded:boolean;
};
type CoverageCompany={companyId:string;companyName:string;posts:number;requiredShifts:number;assignedShifts:number;unassignedShifts:number;coveragePct:number};
type PointLocation={id:string;province?:string;city?:string};
type DashboardData={companies:Company[];rows:ServiceRow[];coverage:any|null;locations:PointLocation[];errors:string[]};

function dateAtLocalNoon(value:Date){return new Date(value.getFullYear(),value.getMonth(),value.getDate(),12)}
function weekStartIso(offset=0){
  const date=dateAtLocalNoon(new Date());
  const mondayOffset=(date.getDay()+6)%7;
  date.setDate(date.getDate()-mondayOffset+offset*7);
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}
function shiftDate(value:string,days:number){const [year,month,day]=value.split('-').map(Number);const date=new Date(year,month-1,day,12);date.setDate(date.getDate()+days);return date}
function dateLabel(date:Date){return new Intl.DateTimeFormat('es-EC',{day:'numeric',month:'short'}).format(date)}
function weekLabel(value:string){return `${dateLabel(shiftDate(value,0))} – ${dateLabel(shiftDate(value,6))}`}
function average(values:(number|null|undefined)[]){const available=values.filter((value):value is number=>typeof value==='number'&&Number.isFinite(value));return available.length?available.reduce((sum,value)=>sum+value,0)/available.length:null}
function statusLabel(row:ServiceRow){
  if(row.state==='PENDING_ASSIGNMENT')return 'Pendiente de asignación';
  if(row.state==='TO_CONFIGURE')return 'Por configurar';
  if(row.state==='INACTIVE')return 'Inactivo';
  return row.pendingNews>0?'Novedad pendiente':'Activo';
}

const unavailableMetrics=[
  'Faltos registrados','Relevos tardíos','Incumplimiento de consignas','Incidentes críticos abiertos',
  'Incumplimiento de ruta de supervisión','Factor hombre','Rotación de personal','Experiencia del cliente','Índice de riesgo operacional'
];

const dashboardTitleByUser:Record<UatUser,string>={
  presidente:'Dashboard Nacional',
  dlatam:'Dashboard LATAM',
  don:'Dashboard Nacional',
  dnacional:'Dashboard Nacional',
  dzonal:'Dashboard Zonal',
  jregional:'Dashboard Regional',
  coord:'Dashboard de Compañía',
  asistente:'Dashboard de Compañía',
  supervisor:'Dashboard de Supervisión',
  agente:'Dashboard de Puesto',
  cliente:'Dashboard Cliente',
};

export default function Dashboard(){
  const [weekStart,setWeekStart]=useState(()=>weekStartIso());
  const [companyId,setCompanyId]=useState('');
  const [clientId,setClientId]=useState('');
  const [clientSearch,setClientSearch]=useState('');
  const [clientMenuOpen,setClientMenuOpen]=useState(false);
  const [period,setPeriod]=useState('30d');
  const [activeMetric,setActiveMetric]=useState('coverage');
  const [loading,setLoading]=useState(true);
  const [data,setData]=useState<DashboardData>({companies:[],rows:[],coverage:null,locations:[],errors:[]});

  useEffect(()=>{
    let cancelled=false;
    setLoading(true);
    void (async()=>{
      const [companiesResult,overviewResult,coverageResult]=await Promise.allSettled([
        api.companies(0,100),api.serviceOverview(),api.assignmentCoverage(weekStart)
      ]);
      const errors:string[]=[];
      const companies=companiesResult.status==='fulfilled'?(companiesResult.value.items??[]) as Company[]:[];
      const rows=overviewResult.status==='fulfilled'?((overviewResult.value.rows??[]) as ServiceRow[]):[];
      const coverage=coverageResult.status==='fulfilled'?coverageResult.value:null;
      if(companiesResult.status==='rejected')errors.push('No se pudieron cargar las compañías.');
      if(overviewResult.status==='rejected')errors.push('No se pudo cargar el resumen de Servicios.');
      if(coverageResult.status==='rejected')errors.push('No se pudo cargar la cobertura de la semana.');

      const serviceIds=Array.from(new Set(rows.map(row=>row.serviceId).filter(Boolean)));
      const locationGroups=await Promise.allSettled(serviceIds.map(id=>api.points(id)));
      const locations=locationGroups.flatMap(result=>result.status==='fulfilled'?result.value as PointLocation[]:[]);
      if(!cancelled){setData({companies,rows,coverage,locations,errors});setLoading(false)}
    })();
    return()=>{cancelled=true};
  },[weekStart]);

  const clients=useMemo(()=>{
    const unique=new Map<string,{id:string;name:string}>();
    data.rows.forEach(row=>{if(row.clientId&&row.clientName)unique.set(row.clientId,{id:row.clientId,name:row.clientName})});
    return Array.from(unique.values()).sort((a,b)=>a.name.localeCompare(b.name,'es'));
  },[data.rows]);
  const visibleClients=useMemo(()=>clients.filter(client=>client.name.toLocaleLowerCase('es').includes(clientSearch.toLocaleLowerCase('es'))),[clients,clientSearch]);
  const selectedClient=clients.find(client=>client.id===clientId);
  const filteredRows=useMemo(()=>data.rows.filter(row=>(!companyId||row.companyId===companyId)&&(!clientId||row.clientId===clientId)),[data.rows,companyId,clientId]);
  const pointIds=useMemo(()=>new Set(filteredRows.map(row=>row.pointId)),[filteredRows]);
  const companyOptions=useMemo(()=>data.companies.filter(company=>!clientId||filteredRows.some(row=>row.companyId===company.id)),[data.companies,filteredRows,clientId]);
  const coverageCompanies=(data.coverage?.companies??[]) as CoverageCompany[];
  const selectedCoverage=companyId?coverageCompanies.find(company=>company.companyId===companyId):null;
  const unassignedShifts=clientId?null:(selectedCoverage?.unassignedShifts??data.coverage?.totalUnassignedShifts??null);
  const matchingCoveragePct=clientId?null:(selectedCoverage?.coveragePct??data.coverage?.overallCoveragePct??null);
  const idAverage=average(filteredRows.map(row=>row.idAverage));
  const icAverage=average(filteredRows.map(row=>row.icAverage));
  const attentionRows=useMemo(()=>filteredRows.filter(row=>row.state!=='ACTIVE'||row.pendingNews>0)
    .sort((a,b)=>{
      const rank=(row:ServiceRow)=>row.state==='PENDING_ASSIGNMENT'?0:row.state==='TO_CONFIGURE'?1:row.state==='INACTIVE'?2:3;
      return rank(a)-rank(b)||b.pendingNews-a.pendingNews||a.clientName.localeCompare(b.clientName,'es');
    }).slice(0,12),[filteredRows]);
  const provinceCounts=useMemo(()=>{
    const byPoint=new Map(data.locations.map(point=>[point.id,point]));
    const counts=new Map<string,number>();
    pointIds.forEach(id=>{const province=byPoint.get(id)?.province;if(province)counts.set(province,(counts.get(province)??0)+1)});
    return Array.from(counts.entries()).sort((a,b)=>b[1]-a[1]||a[0].localeCompare(b[0],'es')).slice(0,6);
  },[data.locations,pointIds]);
  const today=new Intl.DateTimeFormat('es-EC',{weekday:'long',day:'numeric',month:'long',year:'numeric'}).format(new Date());
  const metrics=[
    {id:'coverage',label:'Turnos sin asignar',value:unassignedShifts==null?'N/D':unassignedShifts.toLocaleString('es-EC'),note:clientId?'La cobertura no tiene desglose por cliente':matchingCoveragePct==null?'Sin datos de cobertura':`${matchingCoveragePct}% de cobertura`,available:unassignedShifts!=null},
    {id:'id',label:'Índice de desempeño promedio',value:idAverage==null?'N/D':idAverage.toFixed(1),note:idAverage==null?'Sin evaluaciones disponibles':'Asignaciones cerradas · resumen actual',available:idAverage!=null},
    {id:'ic',label:'Índice de compatibilidad promedio',value:icAverage==null?'N/D':`${icAverage.toFixed(1)}%`,note:icAverage==null?'Sin evaluaciones disponibles':'Asignaciones cerradas · resumen actual',available:icAverage!=null},
    ...unavailableMetrics.map((label,index)=>({id:`pending-${index}`,label,value:'N/D',note:'Fuente o histórico no disponible en esta UAT',available:false}))
  ];
  const dateOptions=Array.from({length:10},(_,index)=>{const value=weekStartIso(-index);return {value,label:index===0?`Semana actual · ${weekLabel(value)}`:weekLabel(value)}});

  return <div className="dash-v6">
    {data.errors.length>0&&<div className="dash-v6-error"><AlertTriangle size={16}/><span>{data.errors.join(' ')}</span></div>}
    <div className="dash-v6-heading">
      <div><h2>{dashboardTitleByUser[getUser()]??'Dashboard Operacional'} · Seguridad Física</h2><p><Activity size={14}/>Actualizado {today}{selectedClient&&<span className="dash-v6-scope">Cliente: {selectedClient.name}</span>}</p></div>
      <div className="dash-v6-filters">
        <div className="dash-v6-client-filter"><span className="sr-only">Buscar cliente</span><Search size={16}/><input value={clientSearch} placeholder="Buscar cliente" aria-label="Buscar cliente" aria-expanded={clientMenuOpen} onFocus={()=>setClientMenuOpen(true)} onBlur={()=>window.setTimeout(()=>setClientMenuOpen(false),120)} onChange={event=>{setClientSearch(event.target.value);setClientId('');setCompanyId('');setClientMenuOpen(true)}} onKeyDown={event=>{if(event.key==='Escape')setClientMenuOpen(false)}}/>{clientSearch&&<button type="button" aria-label="Limpiar cliente" onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId('');setCompanyId('');setClientSearch('');setClientMenuOpen(false)}}>×</button>}{clientMenuOpen&&<div className="dash-v6-client-menu" role="listbox"><button type="button" role="option" aria-selected={!clientId} onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId('');setCompanyId('');setClientSearch('');setClientMenuOpen(false)}}>Todos los clientes</button>{visibleClients.map(client=><button type="button" role="option" aria-selected={clientId===client.id} key={client.id} onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId(client.id);setCompanyId('');setClientSearch(client.name);setClientMenuOpen(false)}}>{client.name}</button>)}{visibleClients.length===0&&<span>No hay clientes coincidentes</span>}</div>}</div>
        <label className="dash-v6-select"><MapPin size={15}/><select value={companyId} aria-label="Filtrar por compañía" onChange={event=>setCompanyId(event.target.value)}><option value="">Todas las compañías</option>{companyOptions.map(company=><option key={company.id} value={company.id}>{company.name}</option>)}</select><ChevronDown size={14}/></label>
        <label className="dash-v6-select"><CalendarDays size={15}/><select value={weekStart} aria-label="Filtrar por semana" onChange={event=>setWeekStart(event.target.value)}>{dateOptions.map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select><ChevronDown size={14}/></label>
        <label className="dash-v6-select dash-v6-period"><CalendarDays size={15}/><select value={period} aria-label="Período de tendencia" onChange={event=>setPeriod(event.target.value)}><option value="7d">Últimos 7 días</option><option value="30d">Últimos 30 días</option><option value="6m">Últimos 6 meses</option></select><ChevronDown size={14}/></label>
      </div>
    </div>

    <div className="dash-v6-kpis" aria-label="Indicadores operativos">
      {metrics.map(metric=><button key={metric.id} type="button" className={`dash-v6-kpi ${activeMetric===metric.id?'selected':''} ${metric.available?'':'unavailable'}`} onClick={()=>setActiveMetric(metric.id)} aria-pressed={activeMetric===metric.id}>
        <span className="dash-v6-kpi-mark">{metric.id==='coverage'?<ShieldAlert size={19}/>:metric.id==='id'?<ClipboardCheck size={19}/>:metric.id==='ic'?<CheckCircle2 size={19}/>:<Activity size={19}/>}</span>
        <span className="dash-v6-kpi-copy"><strong>{metric.label}</strong><b>{loading?'…':metric.value}</b><small>{metric.note}</small></span>
      </button>)}
    </div>

    <div className="dash-v6-lower">
      <section className="dash-v6-panel dash-v6-attention">
        <div className="dash-v6-panel-title"><span className="attention-icon"><AlertTriangle size={19}/></span><div><h3>Atención requerida</h3><small>{loading?'Cargando actividad…':`${attentionRows.length} registros con pendientes de operación`}</small></div><b>{loading?'…':attentionRows.length}</b></div>
        <div className="dash-v6-table-wrap"><table><thead><tr><th>Cliente / Punto</th><th>Compañía</th><th>Puesto</th><th>ID</th><th>IC</th><th>Estado</th></tr></thead><tbody>
          {!loading&&attentionRows.map(row=><tr key={row.postId}><td><strong>{row.clientName}</strong><small>{row.pointName}</small></td><td>{row.companyName||'—'}</td><td><strong>{row.postCode}</strong><small>{row.postName}</small></td><td>{row.idAverage==null?'—':row.idAverage.toFixed(1)}</td><td>{row.icAverage==null?'—':`${row.icAverage.toFixed(1)}%`}</td><td><span className={`dash-v6-state state-${row.state.toLowerCase()}`}>{statusLabel(row)}</span>{row.pendingNews>0&&<small className="dash-v6-news">{row.pendingNews} novedad{row.pendingNews===1?'':'es'}</small>}</td></tr>)}
          {!loading&&attentionRows.length===0&&<tr><td colSpan={6} className="dash-v6-empty">No hay pendientes para los filtros seleccionados.</td></tr>}
          {loading&&<tr><td colSpan={6} className="dash-v6-empty">Cargando datos operativos…</td></tr>}
        </tbody></table></div>
      </section>

      <div className="dash-v6-side-stack">
        <section className="dash-v6-panel dash-v6-trend">
          <div className="dash-v6-panel-title"><span className="blue-icon"><Activity size={18}/></span><div><h3>Tendencia operacional</h3><small>{period==='7d'?'Últimos 7 días':period==='6m'?'Últimos 6 meses':'Últimos 30 días'} · {metrics.find(metric=>metric.id===activeMetric)?.label}</small></div></div>
          <div className="dash-v6-no-history"><Activity size={24}/><strong>Histórico no disponible</strong><span>La API entrega el resumen actual, sin series históricas para graficar.</span></div>
        </section>
        <section className="dash-v6-panel dash-v6-geography">
          <div className="dash-v6-panel-title"><span className="blue-icon"><MapPin size={18}/></span><div><h3>Distribución operacional</h3><small>{pointIds.size} puntos en el alcance seleccionado</small></div></div>
          <div className="dash-v6-provinces">
            {provinceCounts.length?provinceCounts.map(([province,count])=><div className="dash-v6-province" key={province}><span>{province}</span><i><b style={{width:`${Math.max(8,(count/Math.max(...provinceCounts.map(item=>item[1])))*100)}%`}}/></i><strong>{count}</strong></div>):<div className="dash-v6-no-history"><MapPin size={23}/><strong>Ubicación detallada no disponible</strong><span>Los puntos no tienen coordenadas geográficas en este conjunto de datos.</span></div>}
          </div>
          {provinceCounts.length>0&&<small className="dash-v6-map-note">Agrupación por provincia; el sistema no proporciona coordenadas del Punto.</small>}
        </section>
      </div>
    </div>
    <div className="dash-v6-footnote"><FileText size={14}/><span>Los indicadores marcados N/D requieren una fuente de datos o una serie histórica que esta UAT todavía no expone.</span></div>
  </div>;
}
