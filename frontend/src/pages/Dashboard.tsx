import {useEffect,useMemo,useState} from 'react';
import {Activity,AlertTriangle,CalendarDays,CheckCircle2,ChevronDown,ClipboardCheck,FileText,MapPin,Search,ShieldAlert} from 'lucide-react';
import {api,getUser,type UatUser} from '../api';
import {DashboardProvinceMap,type DashboardProvinceCount,type DashboardProvinceSubdivision} from './Territory';

type Company={id:string;name:string};
type ServiceRow={
  serviceId:string;pointId:string;postId:string;companyId:string|null;companyName:string;
  clientId:string;clientName:string;pointName:string;postCode:string;postName:string;tier:string;
  idAverage:number|null;icAverage:number|null;pendingNews:number;
  state:'ACTIVE'|'INACTIVE'|'TO_CONFIGURE'|'PENDING_ASSIGNMENT';assignmentStatus:string;atsLoaded:boolean;
};
type CoverageCompany={companyId:string;companyName:string;posts:number;uncoveredPoints:number;requiredShifts:number;assignedShifts:number;unassignedShifts:number;coveragePct:number};
type Coverage={companies:CoverageCompany[];totalUnassignedShifts:number;totalUncoveredPoints:number;overallCoveragePct:number};
type PointLocation={id:string;province?:string;city?:string};
type Province={code:string;name:string;status?:string;geometryJson?:string;coreSubdivisionId?:string;coreDatasetVersion?:string};
type DashboardMetrics={calculatedAt:string;idAverage:number|null;icAverage:number|null;idSamples:number;icSamples:number;lateReliefs:number;recordedReliefs:number};
type RiskTrendPoint={date:string;scheduledPoints:number;uncoveredPoints:number;riskIndex:number|null};
type DashboardData={companies:Company[];rows:ServiceRow[];coverage:Coverage|null;metrics:DashboardMetrics|null;riskTrend:RiskTrendPoint[]|null;locations:PointLocation[];provinces:Province[];errors:string[]};

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
function normalize(value:string){return value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').trim()}
function statusLabel(row:ServiceRow){
  if(row.state==='PENDING_ASSIGNMENT')return 'Pendiente de asignación';
  if(row.state==='TO_CONFIGURE')return 'Por configurar';
  if(row.state==='INACTIVE')return 'Inactivo';
  return row.pendingNews>0?'Novedad pendiente':'Activo';
}

function RiskTrendChart({points}:{points:RiskTrendPoint[]}){
  const left=40,right=590,top=24,bottom=158;
  const plotted=points.map((point,index)=>({
    ...point,
    x:left+(right-left)*index/Math.max(1,points.length-1),
    y:point.riskIndex==null?null:bottom-(bottom-top)*point.riskIndex/100
  }));
  const path=plotted.map((point,index)=>point.y==null?'':`${index===0||plotted[index-1].y==null?'M':'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).filter(Boolean).join(' ');
  return <div className="dash-v6-line-chart">
    <div className="dash-v6-line-legend"><i/>Riesgo operacional</div>
    <svg viewBox="0 0 610 188" role="img" aria-label="Índice diario de riesgo operacional, de cero a cien, durante treinta días">
      {[0,25,50,75,100].map(value=>{
        const y=bottom-(bottom-top)*value/100;
        return <g key={value}><line className="dash-v6-line-grid" x1={left} x2={right} y1={y} y2={y}/><text className="dash-v6-line-tick" x={left-10} y={y+3} textAnchor="end">{value}</text></g>;
      })}
      <line className="dash-v6-line-axis" x1={left} x2={left} y1={top} y2={bottom}/>
      <line className="dash-v6-line-axis" x1={left} x2={right} y1={bottom} y2={bottom}/>
      <text className="dash-v6-line-unit" transform="translate(10 102) rotate(-90)">/ 100</text>
      <path className="dash-v6-line-path" d={path}/>
      {plotted.map((point,index)=>point.y==null?null:<circle className="dash-v6-line-point" key={point.date} cx={point.x} cy={point.y} r={index===plotted.length-1?3:2.5}>
        <title>{`${dateLabel(shiftDate(point.date,0))}: riesgo ${point.riskIndex}/100 · ${point.uncoveredPoints} de ${point.scheduledPoints} puntos sin cobertura`}</title>
      </circle>)}
      {plotted.map((point,index)=>index%7!==0&&index!==plotted.length-1?null:<text className="dash-v6-line-date" key={point.date} x={point.x} y={177} textAnchor={index===0?'start':index===plotted.length-1?'end':'middle'}>{dateLabel(shiftDate(point.date,0))}</text>)}
    </svg>
  </div>;
}

const visibleMetricsWithoutSource=[
  {label:'Faltos registrados',note:'Sin registro confirmado de asistencia'},
  {label:'Incumplimiento de consignas',note:'Sin registro confirmado de incumplimiento'},
  {label:'Incidentes críticos abiertos',note:'Las novedades no registran criticidad'},
  {label:'Incumplimiento de ruta de supervisión',note:'Sin registro de visitas ejecutadas'}
];
const provinceAliases:Record<string,string>={
  'azuay':'AZU','bolivar':'BOL','canar':'CAN','carchi':'CAR','chimborazo':'CHI','cotopaxi':'COT',
  'el oro':'EOR','esmeraldas':'ESM','galapagos':'GAL','guayas':'GUA','imbabura':'IMB','loja':'LOJ',
  'los rios':'LRI','manabi':'MAN','morona santiago':'MOS','napo':'NAP','orellana':'ORE','pastaza':'PAS',
  'pichincha':'PIC','santa elena':'SEL','santo domingo de los tsachilas':'SDE','sucumbios':'SUC',
  'tungurahua':'TUN','zamora chinchipe':'ZCH'
};

const dashboardTitleByUser:Record<UatUser,string>={
  presidente:'Dashboard Nacional',dlatam:'Dashboard LATAM',don:'Dashboard Nacional',dnacional:'Dashboard Nacional',
  dzonal:'Dashboard Zonal',jregional:'Dashboard Regional',coord:'Dashboard de Compañía',asistente:'Dashboard de Compañía',
  supervisor:'Dashboard de Supervisión',agente:'Dashboard de Puesto',cliente:'Dashboard Cliente',
};

export default function Dashboard(){
  const [weekStart,setWeekStart]=useState(()=>weekStartIso());
  const [companyId,setCompanyId]=useState('');
  const [clientId,setClientId]=useState('');
  const [clientSearch,setClientSearch]=useState('');
  const [clientMenuOpen,setClientMenuOpen]=useState(false);
  const [loading,setLoading]=useState(true);
  const [data,setData]=useState<DashboardData>({companies:[],rows:[],coverage:null,metrics:null,riskTrend:null,locations:[],provinces:[],errors:[]});

  useEffect(()=>{
    let cancelled=false;
    setLoading(true);
    void (async()=>{
      const [companiesResult,overviewResult,coverageResult,territoryResult,metricsResult,trendResult]=await Promise.allSettled([
        api.companies(0,100),api.serviceOverview(),api.assignmentCoverage(weekStart,companyId,clientId),api.territory(),api.dashboardMetrics(weekStart,companyId,clientId),api.dashboardRiskTrend(weekStart,companyId,clientId)
      ]);
      const errors:string[]=[];
      const companies=companiesResult.status==='fulfilled'?(companiesResult.value.items??[]) as Company[]:[];
      const rows=overviewResult.status==='fulfilled'?((overviewResult.value.rows??[]) as ServiceRow[]):[];
      const coverage=coverageResult.status==='fulfilled'?coverageResult.value as Coverage:null;
      const metrics=metricsResult.status==='fulfilled'?metricsResult.value as DashboardMetrics:null;
      const riskTrend=trendResult.status==='fulfilled'?trendResult.value as RiskTrendPoint[]:null;
      const provinces=territoryResult.status==='fulfilled'?((territoryResult.value.provinces??[]) as Province[]):[];
      if(companiesResult.status==='rejected')errors.push('No se pudieron cargar las compañías.');
      if(overviewResult.status==='rejected')errors.push('No se pudo cargar el resumen de Servicios.');
      if(coverageResult.status==='rejected')errors.push('No se pudo cargar la cobertura de la semana.');
      if(metricsResult.status==='rejected')errors.push('No se pudieron cargar los indicadores de la semana.');
      if(trendResult.status==='rejected')errors.push('No se pudo cargar la tendencia operacional.');
      if(territoryResult.status==='rejected')errors.push('No se pudo cargar el catálogo territorial.');

      const serviceIds=Array.from(new Set(rows.map(row=>row.serviceId).filter(Boolean)));
      const locationGroups=await Promise.allSettled(serviceIds.map(id=>api.points(id)));
      const locations=locationGroups.flatMap(result=>result.status==='fulfilled'?result.value as PointLocation[]:[]);
      if(locationGroups.some(result=>result.status==='rejected'))errors.push('No se pudieron cargar todas las ubicaciones de los puntos.');
      if(!cancelled){setData({companies,rows,coverage,metrics,riskTrend,locations,provinces,errors});setLoading(false)}
    })();
    return()=>{cancelled=true};
  },[weekStart,companyId,clientId]);

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
  const uncoveredPoints=data.coverage?.totalUncoveredPoints??null;
  const coveragePct=data.coverage?.overallCoveragePct??null;
  const idAverage=data.metrics?.idAverage??null;
  const icAverage=data.metrics?.icAverage??null;
  const attentionRows=useMemo(()=>filteredRows.filter(row=>row.state!=='ACTIVE'||row.pendingNews>0)
    .sort((a,b)=>{
      const rank=(row:ServiceRow)=>row.state==='PENDING_ASSIGNMENT'?0:row.state==='TO_CONFIGURE'?1:row.state==='INACTIVE'?2:3;
      return rank(a)-rank(b)||b.pendingNews-a.pendingNews||a.clientName.localeCompare(b.clientName,'es');
    }),[filteredRows]);
  const provinceCounts=useMemo<DashboardProvinceCount[]>(()=>{
    const provinceByName=new Map<string,Province>();
    data.provinces.forEach(province=>{provinceByName.set(normalize(province.name),province);provinceByName.set(normalize(province.code),province)});
    const byPoint=new Map(data.locations.map(point=>[point.id,point]));
    const counts=new Map<string,DashboardProvinceCount>();
    pointIds.forEach(id=>{
      const provinceName=byPoint.get(id)?.province?.trim();if(!provinceName)return;
      const normalized=normalize(provinceName);
      const province=provinceByName.get(normalized);
      const code=province?.code??provinceAliases[normalized];if(!code)return;
      const name=province?.name??provinceName;
      const current=counts.get(code)??{code,name,points:0,status:province?.status,geometryJson:province?.geometryJson};
      counts.set(code,{...current,points:current.points+1});
    });
    return Array.from(counts.values());
  },[data.locations,data.provinces,pointIds]);
  const companyName=companyOptions.find(company=>company.id===companyId)?.name;
  const today=data.metrics?new Intl.DateTimeFormat('es-EC',{weekday:'long',day:'numeric',month:'long',year:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'America/Guayaquil'}).format(new Date(data.metrics.calculatedAt)):'pendiente de cargar';
  const metrics=[
    {id:'uncovered',label:'Puntos sin cobertura',value:uncoveredPoints==null?'Sin datos':uncoveredPoints.toLocaleString('es-EC'),note:coveragePct==null?'No se pudo cargar la cobertura':`${coveragePct}% de cobertura planificada · semana seleccionada`,available:uncoveredPoints!=null},
    {id:'id',label:'Índice de desempeño promedio',value:idAverage==null?'Sin datos':idAverage.toFixed(1),note:idAverage==null?'Sin ID registrado en turnos terminados':`${data.metrics?.idSamples} asignaciones · ID registrado ponderado por duración`,available:idAverage!=null},
    {id:'ic',label:'Índice de compatibilidad promedio',value:icAverage==null?'Sin datos':`${icAverage.toFixed(1)}%`,note:icAverage==null?'Sin IC registrado en turnos terminados':`${data.metrics?.icSamples} asignaciones · IC ponderado por duración`,available:icAverage!=null},
    {id:'pending-0',...visibleMetricsWithoutSource[0],value:'Sin datos',available:false},
    {id:'late-reliefs',label:'Relevos tardíos',value:data.metrics?data.metrics.lateReliefs.toLocaleString('es-EC'):'Sin datos',note:data.metrics?`${data.metrics.recordedReliefs} relevos registrados · ejecución posterior a hora programada`:'No se pudieron cargar los relevos',available:data.metrics!=null},
    ...visibleMetricsWithoutSource.slice(1).map((metric,index)=>({id:`pending-${index+1}`,...metric,value:'Sin datos',available:false}))
  ];
  const dateOptions=useMemo(()=>[-4,-3,-2,-1,0,1,2,3,4].map(offset=>{
    const value=weekStartIso(offset);
    return {value,label:`${offset===0?'Semana actual · ':''}${weekLabel(value)}`};
  }),[]);
  return <div className="dash-v6">
    {data.errors.length>0&&<div className="dash-v6-error"><AlertTriangle size={16}/><span>{data.errors.join(' ')}</span></div>}
    <div className="dash-v6-heading">
      <div><h2>{dashboardTitleByUser[getUser()]??'Dashboard Operacional'} · Seguridad Física</h2><p><Activity size={14}/>Actualizado {today}{selectedClient&&<span className="dash-v6-scope">Cliente: {selectedClient.name}</span>}</p></div>
      <div className="dash-v6-filters">
        <div className="dash-v6-client-filter"><span className="sr-only">Buscar cliente</span><Search size={16}/><input value={clientSearch} placeholder="Buscar cliente" aria-label="Buscar cliente" aria-expanded={clientMenuOpen} onFocus={()=>setClientMenuOpen(true)} onBlur={()=>window.setTimeout(()=>setClientMenuOpen(false),120)} onChange={event=>{setClientSearch(event.target.value);setClientId('');setCompanyId('');setClientMenuOpen(true)}} onKeyDown={event=>{if(event.key==='Escape')setClientMenuOpen(false)}}/>{clientSearch&&<button type="button" aria-label="Limpiar cliente" onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId('');setCompanyId('');setClientSearch('');setClientMenuOpen(false)}}>×</button>}{clientMenuOpen&&<div className="dash-v6-client-menu" role="listbox"><button type="button" role="option" aria-selected={!clientId} onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId('');setCompanyId('');setClientSearch('');setClientMenuOpen(false)}}>Todos los clientes</button>{visibleClients.map(client=><button type="button" role="option" aria-selected={clientId===client.id} key={client.id} onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId(client.id);setCompanyId('');setClientSearch(client.name);setClientMenuOpen(false)}}>{client.name}</button>)}{visibleClients.length===0&&<span>No hay clientes coincidentes</span>}</div>}</div>
        <label className="dash-v6-select"><MapPin size={15}/><select value={companyId} aria-label="Filtrar por compañía" onChange={event=>setCompanyId(event.target.value)}><option value="">Todas las compañías</option>{companyOptions.map(company=><option key={company.id} value={company.id}>{company.name}</option>)}</select><ChevronDown size={14}/></label>
        <label className="dash-v6-select"><CalendarDays size={15}/><select value={weekStart} aria-label="Semana de cobertura" onChange={event=>setWeekStart(event.target.value)}>{dateOptions.map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select><ChevronDown size={14}/></label>
      </div>
    </div>

    <div className="dash-v6-kpis" aria-label="Indicadores operativos">
      {metrics.map(metric=><article key={metric.id} className={`dash-v6-kpi ${metric.available?'':'unavailable'}`}>
        <span className="dash-v6-kpi-mark">{metric.id==='uncovered'?<ShieldAlert size={19}/>:metric.id==='id'?<ClipboardCheck size={19}/>:metric.id==='ic'?<CheckCircle2 size={19}/>:<Activity size={19}/>}</span>
        <span className="dash-v6-kpi-copy"><strong>{metric.label}</strong><b>{loading?'…':metric.value}</b><small>{metric.note}</small></span>
      </article>)}
    </div>

    <div className="dash-v6-lower">
      <section className="dash-v6-panel dash-v6-attention">
        <div className="dash-v6-panel-title"><span className="attention-icon"><AlertTriangle size={19}/></span><div><h3>Atención requerida</h3><small>{loading?'Cargando actividad…':`${attentionRows.length} registros con pendientes de operación${companyName?` · ${companyName}`:''}`}</small></div><b>{loading?'…':attentionRows.length}</b></div>
        <div className="dash-v6-table-wrap"><table><thead><tr><th>Cliente / Punto</th><th>Compañía</th><th>Puesto</th><th>ID</th><th>IC</th><th>Estado</th></tr></thead><tbody>
          {!loading&&attentionRows.map(row=><tr key={row.postId}><td><strong>{row.clientName}</strong><small>{row.pointName}</small></td><td>{row.companyName||'—'}</td><td><strong>{row.postCode}</strong><small>{row.postName}</small></td><td>{row.idAverage==null?'—':row.idAverage.toFixed(1)}</td><td>{row.icAverage==null?'—':`${row.icAverage.toFixed(1)}%`}</td><td><span className={`dash-v6-state state-${row.state.toLowerCase()}`}>{statusLabel(row)}</span>{row.pendingNews>0&&<small className="dash-v6-news">{row.pendingNews} novedad{row.pendingNews===1?'':'es'}</small>}</td></tr>)}
          {!loading&&attentionRows.length===0&&<tr><td colSpan={6} className="dash-v6-empty">No hay pendientes para la compañía seleccionada.</td></tr>}
          {loading&&<tr><td colSpan={6} className="dash-v6-empty">Cargando datos operativos…</td></tr>}
        </tbody></table></div>
      </section>

      <div className="dash-v6-side-stack">
        <section className="dash-v6-panel dash-v6-trend">
          <div className="dash-v6-panel-title"><span className="blue-icon"><Activity size={18}/></span><div><h3>Tendencia — Índice de riesgo operacional</h3><small>Puntos sin cobertura por día · últimos 30 días del alcance seleccionado</small></div></div>
          {loading?<div className="dash-v6-no-history">Cargando tendencia operacional…</div>:!data.riskTrend?<div className="dash-v6-no-history"><Activity size={24}/><strong>Datos no disponibles</strong><span>No se pudo cargar la tendencia desde SGI Comando.</span></div>:data.riskTrend.every(point=>point.riskIndex==null)?<div className="dash-v6-no-history"><Activity size={24}/><strong>Sin datos</strong><span>No hay turnos requeridos registrados en este período y alcance.</span></div>:<RiskTrendChart points={data.riskTrend}/>}
        </section>
        <section className="dash-v6-panel dash-v6-geography">
          <div className="dash-v6-panel-title"><span className="blue-icon"><MapPin size={18}/></span><div><h3>Mapa operacional por provincia</h3><small>{pointIds.size} puntos en el alcance seleccionado</small></div></div>
          <DashboardProvinceMap provinces={provinceCounts} subdivisions={data.provinces as DashboardProvinceSubdivision[]}/>
        </section>
      </div>
    </div>
    <div className="dash-v6-footnote"><FileText size={14}/><span>Cobertura, ID, IC y relevos usan la semana, compañía y cliente seleccionados. La tendencia muestra, por día, el porcentaje de puntos programados con al menos un turno requerido sin asignar en los 30 días que terminan en la semana elegida. Los días sin turnos registrados quedan sin valor. ID e IC son valores guardados en asignaciones de turnos terminados, ponderados por su duración; no acreditan ejecución real. Los indicadores sin fuente confirmada muestran «Sin datos». Atención requerida y mapa muestran el estado actual del alcance seleccionado.</span></div>
  </div>;
}
