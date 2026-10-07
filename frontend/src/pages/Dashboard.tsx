import {useEffect,useMemo,useState} from 'react';
import {Activity,AlertTriangle,CalendarDays,CheckCircle2,ChevronDown,ClipboardCheck,Clock3,FileText,MapPin,Route,Search,ShieldAlert,Star,Users,UserRoundX,Repeat2,BookCheck,Flame} from 'lucide-react';
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

function CardSparkline({values,color}:{values:number[];color:string}){
  if(values.length<2)return null;
  const min=Math.min(...values),span=Math.max(1,Math.max(...values)-min);
  const path=values.map((value,index)=>`${index?'L':'M'}${(index*70/(values.length-1)).toFixed(1)} ${(25-(value-min)*19/span).toFixed(1)}`).join(' ');
  return <svg className="dash-v6-kpi-spark spark" viewBox="0 0 72 32" aria-hidden="true"><path d={path} stroke={color}/></svg>;
}

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

function RiskTrendChart({points,mode}:{points:RiskTrendPoint[];mode:'risk'|'uncovered'}){
  const left=40,right=590,top=24,bottom=158;
  const labelStep=Math.max(1,Math.ceil((points.length-1)/4));
  const markerStep=Math.max(1,Math.ceil(points.length/30));
  const maximum=mode==='risk'?100:Math.max(4,...points.map(point=>point.uncoveredPoints));
  const ticks=mode==='risk'?[0,25,50,75,100]:[0,.25,.5,.75,1].map(fraction=>Math.round(maximum*fraction));
  const plotted=points.map((point,index)=>({
    ...point,
    x:left+(right-left)*index/Math.max(1,points.length-1),
    y:point.riskIndex==null?null:bottom-(bottom-top)*(mode==='risk'?point.riskIndex:point.uncoveredPoints)/maximum
  }));
  const path=plotted.map((point,index)=>point.y==null?'':`${index===0||plotted[index-1].y==null?'M':'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).filter(Boolean).join(' ');
  return <div className="dash-v6-line-chart">
    <div className="dash-v6-line-legend"><i/>{mode==='risk'?'Riesgo operacional':'Puntos sin cobertura'}</div>
    <svg viewBox="0 0 610 188" role="img" aria-label={mode==='risk'?'Índice diario de riesgo operacional, de cero a cien':'Puntos diarios sin cobertura'}>
      {ticks.map((value,index)=>{
        const y=bottom-(bottom-top)*index/4;
        return <g key={index}><line className="dash-v6-line-grid" x1={left} x2={right} y1={y} y2={y}/><text className="dash-v6-line-tick" x={left-10} y={y+3} textAnchor="end">{value}</text></g>;
      })}
      <line className="dash-v6-line-axis" x1={left} x2={left} y1={top} y2={bottom}/>
      <line className="dash-v6-line-axis" x1={left} x2={right} y1={bottom} y2={bottom}/>
      <text className="dash-v6-line-unit" transform="translate(10 102) rotate(-90)">{mode==='risk'?'/ 100':'puntos'}</text>
      <path className="dash-v6-line-path" d={path}/>
      {plotted.map((point,index)=>point.y==null||index%markerStep!==0&&index!==plotted.length-1?null:<circle className="dash-v6-line-point" key={point.date} cx={point.x} cy={point.y} r={index===plotted.length-1?3:2.5}>
        <title>{`${dateLabel(shiftDate(point.date,0))}: ${point.uncoveredPoints} de ${point.scheduledPoints} puntos sin cobertura · riesgo ${point.riskIndex}/100`}</title>
      </circle>)}
      {plotted.map((point,index)=>index%labelStep!==0&&index!==plotted.length-1?null:<text className="dash-v6-line-date" key={point.date} x={point.x} y={177} textAnchor={index===0?'start':index===plotted.length-1?'end':'middle'}>{dateLabel(shiftDate(point.date,0))}</text>)}
    </svg>
  </div>;
}

const cardCatalog=[
  {id:'coverageGap',label:'Puntos sin cobertura',tone:'red',icon:ShieldAlert},
  {id:'nonIdealPerformance',label:'Puntos sin cobertura idónea',sub:'Índice de Desempeño',tone:'green',icon:ClipboardCheck},
  {id:'nonIdealCompat',label:'Puntos sin cobertura idónea',sub:'Índice de Compatibilidad',tone:'blue',icon:CheckCircle2},
  {id:'faltos',label:'Faltos registrados',tone:'blue',icon:UserRoundX},
  {id:'lateRelief',label:'Relevos tardíos',tone:'orange',icon:Clock3},
  {id:'consignas',label:'Incumplimiento de consignas',tone:'red',icon:BookCheck},
  {id:'critical',label:'Incidentes críticos abiertos',tone:'red',icon:Flame},
  {id:'route',label:'Incumplimiento ruta de supervisión',tone:'orange',icon:Route},
  {id:'factor',label:'Factor hombre',tone:'blue',icon:Users},
  {id:'rotation',label:'Rotación de personal',tone:'purple',icon:Repeat2},
  {id:'cx',label:'Experiencia del cliente',tone:'yellow',icon:Star},
  {id:'risk',label:'Índice de riesgo operacional',tone:'red',icon:Activity}
] as const;
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
  const [period,setPeriod]=useState<'7d'|'30d'|'6m'>('30d');
  const [companyId,setCompanyId]=useState('');
  const [clientId,setClientId]=useState('');
  const [clientSearch,setClientSearch]=useState('');
  const [clientMenuOpen,setClientMenuOpen]=useState(false);
  const [activeCard,setActiveCard]=useState<(typeof cardCatalog)[number]['id']>('coverageGap');
  const [loading,setLoading]=useState(true);
  const [data,setData]=useState<DashboardData>({companies:[],rows:[],coverage:null,metrics:null,riskTrend:null,locations:[],provinces:[],errors:[]});

  useEffect(()=>{
    let cancelled=false;
    setLoading(true);
    void (async()=>{
      const [companiesResult,overviewResult,coverageResult,territoryResult,metricsResult,trendResult]=await Promise.allSettled([
        api.companies(0,100),api.serviceOverview(),api.assignmentCoverage(weekStart,companyId,clientId),api.territory(),api.dashboardMetrics(weekStart,companyId,clientId),api.dashboardRiskTrend(weekStart,companyId,clientId,period)
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
  },[weekStart,companyId,clientId,period]);

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
  const riskPoint=data.riskTrend?.slice().reverse().find(point=>point.riskIndex!=null);
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
  const metricValues:Record<(typeof cardCatalog)[number]['id'],{value:string;note:string;available:boolean}>={
    coverageGap:{value:uncoveredPoints==null?'S/D':uncoveredPoints.toLocaleString('es-EC'),note:coveragePct==null?'Cobertura S/D':`${coveragePct}% de cobertura`,available:uncoveredPoints!=null},
    nonIdealPerformance:{value:'S/D',note:'ID sin umbral',available:false},
    nonIdealCompat:{value:'S/D',note:'IC sin umbral',available:false},
    faltos:{value:'S/D',note:'Sin asistencia',available:false},
    lateRelief:{value:data.metrics?data.metrics.lateReliefs.toLocaleString('es-EC'):'S/D',note:data.metrics?`${data.metrics.recordedReliefs} relevos registrados`:'Relevos S/D',available:data.metrics!=null},
    consignas:{value:'S/D',note:'Sin cumplimiento',available:false},
    critical:{value:'S/D',note:'Sin criticidad',available:false},
    route:{value:'S/D',note:'Sin visitas',available:false},
    factor:{value:'S/D',note:'Sin dotación',available:false},
    rotation:{value:'S/D',note:'Sin bajas',available:false},
    cx:{value:'S/D',note:'Sin evaluaciones',available:false},
    risk:{value:riskPoint?.riskIndex==null?'S/D':`${riskPoint.riskIndex.toFixed(1)} / 100`,note:riskPoint?`Al ${dateLabel(shiftDate(riskPoint.date,0))}`:'Sin turnos',available:riskPoint?.riskIndex!=null}
  };
  const coverageSpark=data.riskTrend?.filter(point=>point.riskIndex!=null).map(point=>point.uncoveredPoints)??[];
  const riskSpark=data.riskTrend?.filter(point=>point.riskIndex!=null).map(point=>point.riskIndex as number)??[];
  const activeCardConfig=cardCatalog.find(card=>card.id===activeCard)!;
  const activeCardTitle=`${activeCardConfig.label}${'sub' in activeCardConfig?` — ${activeCardConfig.sub}`:''}`;
  const periodLabel=period==='7d'?'últimos 7 días':period==='6m'?'últimos 6 meses':'últimos 30 días';
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
        <label className="dash-v6-select dash-v6-period"><CalendarDays size={15}/><select value={period} aria-label="Período de tendencia" onChange={event=>setPeriod(event.target.value as '7d'|'30d'|'6m')}><option value="7d">Últimos 7 días</option><option value="30d">Últimos 30 días</option><option value="6m">Últimos 6 meses</option></select><ChevronDown size={14}/></label>
      </div>
    </div>

    <div className="dash-v6-kpis" aria-label="Indicadores operativos">
      {cardCatalog.map(card=>{const metric=metricValues[card.id];const Icon=card.icon;const spark=card.id==='coverageGap'?coverageSpark:card.id==='risk'?riskSpark:[];return <button type="button" key={card.id} data-kpi={card.id} title={`Clic para visualizar ${card.label.toLocaleLowerCase('es')}. ${metric.note}`} aria-pressed={activeCard===card.id} onClick={()=>setActiveCard(card.id)} className={`dash-v6-kpi kpi tone-${card.tone} ${activeCard===card.id?'selected':''} ${metric.available?'':'unavailable'}`}>
        <span className="dash-v6-kpi-mark kicon"><Icon size={21}/></span>
        <span className="dash-v6-kpi-copy kcontent"><strong className="ktitle">{card.label}</strong>{'sub' in card&&<span className="dash-v6-kpi-sub ksub">{card.sub}</span>}{card.id==='factor'?<span className="dash-v6-factor factor"><span>FHT:</span><b>S/D</b><span>Disponible:</span><b>S/D</b><span>Exceso / Déficit:</span><b>S/D</b></span>:<span className="dash-v6-kpi-value-row value-row"><b className="kvalue">{loading?'…':metric.value}</b>{spark.length>1&&<CardSparkline values={spark} color="#ff243e"/>}</span>}<small className="dash-v6-kpi-delta delta">{metric.note}</small></span>
      </button>})}
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
          <div className="dash-v6-panel-title"><span className="blue-icon"><Activity size={18}/></span><div><h3>Tendencia — {activeCardTitle}</h3><small>{activeCard==='coverageGap'||activeCard==='risk'?`Histórico de ${periodLabel}`:'Histórico S/D'}</small></div></div>
          {loading?<div className="dash-v6-no-history">Cargando tendencia operacional…</div>:activeCard!=='coverageGap'&&activeCard!=='risk'?<div className="dash-v6-no-history"><Activity size={24}/><strong>S/D</strong><span>Sin histórico para este indicador.</span></div>:!data.riskTrend?<div className="dash-v6-no-history"><Activity size={24}/><strong>S/D</strong><span>Tendencia no disponible.</span></div>:data.riskTrend.every(point=>point.riskIndex==null)?<div className="dash-v6-no-history"><Activity size={24}/><strong>S/D</strong><span>Sin turnos en el período.</span></div>:<RiskTrendChart points={data.riskTrend} mode={activeCard==='risk'?'risk':'uncovered'}/>}
        </section>
        <section className="dash-v6-panel dash-v6-geography">
          <div className="dash-v6-panel-title"><span className="blue-icon"><MapPin size={18}/></span><div><h3>Mapa operacional por provincia</h3><small>{pointIds.size} puntos en el alcance seleccionado</small></div></div>
          <DashboardProvinceMap provinces={provinceCounts} subdivisions={data.provinces as DashboardProvinceSubdivision[]}/>
        </section>
      </div>
    </div>
    <div className="dash-v6-footnote"><FileText size={14}/><span>Cobertura, relevos y riesgo usan datos registrados del alcance seleccionado. S/D indica que falta una fuente confirmada.</span></div>
  </div>;
}
