import {useCallback,useEffect,useMemo,useState} from 'react';
import {Activity,AlertTriangle,CalendarDays,ChevronDown,FileText,MapPin,Search} from 'lucide-react';
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
type Coverage={companies:CoverageCompany[];totalUnassignedShifts:number;totalUncoveredPoints?:number;overallCoveragePct:number};
type PointLocation={id:string;province?:string;city?:string};
type Province={code:string;name:string;status?:string;geometryJson?:string;coreSubdivisionId?:string;coreDatasetVersion?:string};
type DashboardMetrics={calculatedAt:string;idAverage:number|null;icAverage:number|null;idSamples:number;icSamples:number;lateReliefs:number;recordedReliefs:number};
type Suitability={pointsBelowIdMinimum:number;idEvaluatedPoints:number;idGapPct:number|null;pointsBelowFullCompatibility:number;icEvaluatedPoints:number;icGapPct:number|null};
type ConsignmentAttention={pointId:string;clientName:string;pointName:string;date:string;nonCompliant:number};
type ConsignmentCompliance={nonCompliant:number;activeConsignments:number;nonCompliancePct:number|null;attention?:ConsignmentAttention[]};
type SupervisionRouteCompliance={missedVisits:number;expectedVisits:number;nonCompliancePct:number|null};
type MissedReliefs={missedShifts:number;assignedShifts:number};
type CriticalIncidents={reportedCriticalIncidents:number};
type RiskTrendPoint={date:string;scheduledPoints:number;uncoveredPoints:number;riskIndex:number|null};
type DashboardData={companies:Company[];rows:ServiceRow[];coverage:Coverage|null;metrics:DashboardMetrics|null;suitability:Suitability|null;consignments:ConsignmentCompliance|null;supervisionRoute:SupervisionRouteCompliance|null;missedReliefs:MissedReliefs|null;criticalIncidents:CriticalIncidents|null;riskTrend:RiskTrendPoint[]|null;locations:PointLocation[];provinces:Province[];errors:string[]};
type DashboardField=Exclude<keyof DashboardData,'errors'>;
type DashboardRequest={field:DashboardField;request:()=>Promise<unknown>;message:string;valid?:(value:any)=>boolean};
const metricFields:DashboardField[]=['coverage','metrics','suitability','consignments','supervisionRoute','missedReliefs','criticalIncidents','riskTrend'];

function hasNumber(value:unknown):value is number{return typeof value==='number'&&Number.isFinite(value)}
function hasMetrics(value:unknown):value is DashboardMetrics{return typeof value==='object'&&value!==null&&typeof (value as Partial<DashboardMetrics>).calculatedAt==='string'&&hasNumber((value as Partial<DashboardMetrics>).lateReliefs)&&hasNumber((value as Partial<DashboardMetrics>).recordedReliefs)}
function hasSuitability(value:unknown):value is Suitability{return typeof value==='object'&&value!==null&&hasNumber((value as Partial<Suitability>).pointsBelowIdMinimum)&&hasNumber((value as Partial<Suitability>).idEvaluatedPoints)&&hasNumber((value as Partial<Suitability>).pointsBelowFullCompatibility)&&hasNumber((value as Partial<Suitability>).icEvaluatedPoints)}
function hasConsignmentCompliance(value:unknown):value is ConsignmentCompliance{return typeof value==='object'&&value!==null&&hasNumber((value as Partial<ConsignmentCompliance>).nonCompliant)&&hasNumber((value as Partial<ConsignmentCompliance>).activeConsignments)}
function hasSupervisionRouteCompliance(value:unknown):value is SupervisionRouteCompliance{return typeof value==='object'&&value!==null&&hasNumber((value as Partial<SupervisionRouteCompliance>).missedVisits)&&hasNumber((value as Partial<SupervisionRouteCompliance>).expectedVisits)}
function hasMissedReliefs(value:unknown):value is MissedReliefs{return typeof value==='object'&&value!==null&&hasNumber((value as Partial<MissedReliefs>).missedShifts)&&hasNumber((value as Partial<MissedReliefs>).assignedShifts)}
function hasCriticalIncidents(value:unknown):value is CriticalIncidents{return typeof value==='object'&&value!==null&&hasNumber((value as Partial<CriticalIncidents>).reportedCriticalIncidents)}
function metricPercent(value:number|null|undefined){return hasNumber(value)?`${Number(value.toFixed(1))}%`:'0'}

function CardSparkline({values,color}:{values:number[];color:string}){
  const hasHistory=values.length>=2;
  const series=hasHistory?values:[0,0];
  const min=Math.min(...series),span=Math.max(1,Math.max(...series)-min);
  const path=series.map((value,index)=>`${index?'L':'M'}${(index*70/(series.length-1)).toFixed(1)} ${(25-(value-min)*19/span).toFixed(1)}`).join(' ');
  const label=hasHistory?'Tendencia histórica':'Sin histórico disponible: línea en 0 por defecto';
  return <svg className="dash-v6-kpi-spark spark" viewBox="0 0 72 32" role="img" aria-label={label}><title>{label}</title><path d={path} stroke={color}/></svg>;
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
  {id:'coverageGap',label:'Puntos sin cobertura',tone:'red',glyph:'◆'},
  {id:'nonIdealPerformance',label:'Puntos sin cobertura idónea',sub:'Índice de Desempeño',tone:'green',glyph:'⊙'},
  {id:'nonIdealCompat',label:'Puntos sin cobertura idónea',sub:'Índice de Compatibilidad',tone:'blue',glyph:'⚙'},
  {id:'faltos',label:'Faltos registrados',tone:'blue',glyph:'▦'},
  {id:'lateRelief',label:'Relevos tardíos',tone:'orange',glyph:'◷'},
  {id:'consignas',label:'Incumplimiento de consignas',tone:'red',glyph:'▤'},
  {id:'critical',label:'Incidentes críticos abiertos',tone:'red',glyph:'♨'},
  {id:'route',label:'Incumplimiento ruta de supervisión',tone:'orange',glyph:'⌁'},
  {id:'factor',label:'Factor hombre',tone:'blue',glyph:'●'},
  {id:'rotation',label:'Rotación de personal',tone:'purple',glyph:'♟'},
  {id:'cx',label:'Experiencia del cliente',tone:'yellow',glyph:'★'},
  {id:'risk',label:'Índice de riesgo operacional',tone:'red',glyph:'♦'}
] as const;
const provinceAliases:Record<string,string>={
  'azuay':'AZU','bolivar':'BOL','canar':'CAN','carchi':'CAR','chimborazo':'CHI','cotopaxi':'COT',
  'el oro':'EOR','esmeraldas':'ESM','galapagos':'GAL','guayas':'GUA','imbabura':'IMB','loja':'LOJ',
  'los rios':'LRI','manabi':'MAN','morona santiago':'MOS','napo':'NAP','orellana':'ORE','pastaza':'PAS',
  'pichincha':'PIC','santa elena':'SEL','santo domingo de los tsachilas':'SDE','sucumbios':'SUC',
  'tungurahua':'TUN','zamora chinchipe':'ZCH'
};

const dashboardTitleByUser:Record<UatUser,string>={
  presidente:'Dashboard Nacional',ggeneral:'Dashboard Nacional',dlatam:'Dashboard LATAM',don:'Dashboard Nacional',dnacional:'Dashboard Nacional',
  dzonal:'Dashboard Zonal',jregional:'Dashboard Regional',coord:'Dashboard de Compañía',asistente:'Dashboard de Compañía',
  supervisor:'Dashboard de Supervisión',agente:'Dashboard de Puesto',cliente:'Dashboard Cliente',
};

export default function Dashboard(){
  const [showAllAttention,setShowAllAttention]=useState(false);
  const [weekStart,setWeekStart]=useState(()=>weekStartIso());
  const [period,setPeriod]=useState<'7d'|'30d'|'6m'>('30d');
  const [companyId,setCompanyId]=useState('');
  const [clientId,setClientId]=useState('');
  const [clientSearch,setClientSearch]=useState('');
  const [clientMenuOpen,setClientMenuOpen]=useState(false);
  const [activeCard,setActiveCard]=useState<(typeof cardCatalog)[number]['id']>('coverageGap');
  const [pending,setPending]=useState<Set<DashboardField>>(()=>new Set(metricFields));
  const [loadErrors,setLoadErrors]=useState<Partial<Record<DashboardField,string>>>({});
  const [data,setData]=useState<DashboardData>({companies:[],rows:[],coverage:null,metrics:null,suitability:null,consignments:null,supervisionRoute:null,missedReliefs:null,criticalIncidents:null,riskTrend:null,locations:[],provinces:[],errors:[]});


  const startRequests=useCallback((requests:DashboardRequest[])=>{
    let cancelled=false;
    const fields=new Set(requests.map(item=>item.field));
    setPending(previous=>new Set([...previous,...fields]));
    setLoadErrors(previous=>Object.fromEntries(Object.entries(previous).filter(([field])=>!fields.has(field as DashboardField))));
    // Defer requests so React StrictMode can cancel its discarded initial effect.
    const timer=window.setTimeout(()=>{
      requests.forEach(({field,request,message,valid})=>{
        void Promise.resolve().then(request).then(value=>{
          if(valid&&!valid(value))throw new Error(message);
          if(!cancelled)setData(previous=>({...previous,[field]:value}));
        }).catch(()=>{
          if(cancelled)return;
          setData(previous=>({...previous,[field]:metricFields.includes(field)?null:[]}));
          setLoadErrors(previous=>({...previous,[field]:message}));
        }).finally(()=>{
          if(!cancelled)setPending(previous=>{const next=new Set(previous);next.delete(field);return next});
        });
      });
    },0);
    return()=>{cancelled=true;window.clearTimeout(timer)};
  },[]);

  useEffect(()=>{
    let overviewPromise:ReturnType<typeof api.serviceOverview>|undefined;
    const overview=()=>overviewPromise??=api.serviceOverview();
    return startRequests([
      {field:'companies',request:()=>api.companies(0,100).then(value=>value.items??[]),message:'No se pudieron cargar las compañías.'},
      {field:'rows',request:()=>overview().then(value=>value.rows??[]),message:'No se pudo cargar el resumen de Servicios.'},
      {field:'provinces',request:()=>api.territory().then(value=>value.provinces??[]),message:'No se pudo cargar el catálogo territorial.'},
      {field:'locations',request:()=>api.dashboardPointLocations(),message:'No se pudieron cargar las ubicaciones de los puntos.'}
    ]);
  },[startRequests]);

  useEffect(()=>startRequests([
    {field:'coverage',request:()=>api.assignmentCoverage(weekStart,companyId,clientId),valid:value=>hasNumber(value?.totalUncoveredPoints),message:'No se pudo cargar la cobertura de la semana.'},
    {field:'metrics',request:()=>api.dashboardMetrics(weekStart,companyId,clientId),valid:hasMetrics,message:'No se pudieron cargar los indicadores de la semana.'},
    {field:'suitability',request:()=>api.dashboardSuitability(weekStart,companyId,clientId),valid:hasSuitability,message:'No se pudo cargar la idoneidad de ID e IC.'},
    {field:'supervisionRoute',request:()=>api.dashboardSupervisionRouteCompliance(weekStart,companyId,clientId),valid:hasSupervisionRouteCompliance,message:'No se pudo cargar el incumplimiento de rutas de supervisión.'},
    {field:'missedReliefs',request:()=>api.dashboardMissedReliefs(weekStart,companyId,clientId),valid:hasMissedReliefs,message:'No se pudieron cargar los relevos no realizados.'},
    {field:'criticalIncidents',request:()=>api.dashboardCriticalIncidents(weekStart,companyId,clientId),valid:hasCriticalIncidents,message:'No se pudieron cargar los incidentes críticos.'}
  ]),[startRequests,weekStart,companyId,clientId]);

  useEffect(()=>{
    setShowAllAttention(false);
    return startRequests([
      {field:'consignments',request:()=>api.dashboardConsignmentCompliance(weekStart,companyId,clientId,period),valid:hasConsignmentCompliance,message:'No se pudo cargar el incumplimiento de consignas.'},
      {field:'riskTrend',request:()=>api.dashboardRiskTrend(weekStart,companyId,clientId,period),valid:Array.isArray,message:'No se pudo cargar la tendencia operacional.'}
    ]);
  },[startRequests,weekStart,companyId,clientId,period]);
  const attentionLoading=pending.has('consignments');
  const trendLoading=pending.has('riskTrend');
  const errors=Object.values(loadErrors);

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
  const attentionRows=(data.consignments?.attention??[]).filter(row=>row.nonCompliant>0);
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
  const today=data.metrics?new Intl.DateTimeFormat('es-EC',{weekday:'long',day:'numeric',month:'long',year:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'America/Guayaquil'}).format(new Date(data.metrics.calculatedAt)):'pendiente de cargar';
  const metricValues:Record<(typeof cardCatalog)[number]['id'],{value:string;note:string;available:boolean}>={
    coverageGap:{value:uncoveredPoints==null?'0':uncoveredPoints.toLocaleString('es-EC'),note:coveragePct==null?'Cobertura S/D':`${coveragePct}% de cobertura`,available:uncoveredPoints!=null},
    nonIdealPerformance:{value:metricPercent(data.suitability?.idGapPct),note:data.suitability?.idEvaluatedPoints?`${data.suitability.pointsBelowIdMinimum} de ${data.suitability.idEvaluatedPoints} puntos`:'Sin ID evaluado',available:hasNumber(data.suitability?.idGapPct)},
    nonIdealCompat:{value:metricPercent(data.suitability?.icGapPct),note:data.suitability?.icEvaluatedPoints?`${data.suitability.pointsBelowFullCompatibility} de ${data.suitability.icEvaluatedPoints} puntos`:'Sin IC evaluado',available:hasNumber(data.suitability?.icGapPct)},
    faltos:{value:data.missedReliefs?.assignedShifts?data.missedReliefs.missedShifts.toLocaleString('es-EC'):'0',note:data.missedReliefs?.assignedShifts?`${data.missedReliefs.missedShifts} de ${data.missedReliefs.assignedShifts} relevos`:'Sin turnos terminados',available:!!data.missedReliefs?.assignedShifts},
    lateRelief:{value:data.metrics?data.metrics.lateReliefs.toLocaleString('es-EC'):'0',note:data.metrics?`${data.metrics.recordedReliefs} relevos registrados`:'Relevos S/D',available:data.metrics!=null},
    consignas:{value:metricPercent(data.consignments?.nonCompliancePct),note:data.consignments?.activeConsignments?`${data.consignments.nonCompliant} de ${data.consignments.activeConsignments} activas`:'Sin consignas activas',available:hasNumber(data.consignments?.nonCompliancePct)},
    critical:{value:data.criticalIncidents?data.criticalIncidents.reportedCriticalIncidents.toLocaleString('es-EC'):'0',note:'Críticos reportados',available:data.criticalIncidents!=null},
    route:{value:metricPercent(data.supervisionRoute?.nonCompliancePct),note:data.supervisionRoute?.expectedVisits?`${data.supervisionRoute.missedVisits} de ${data.supervisionRoute.expectedVisits} visitas`:'Sin visitas previstas',available:hasNumber(data.supervisionRoute?.nonCompliancePct)},
    factor:{value:'0',note:'Sin dotación',available:false},
    rotation:{value:'0',note:'Sin bajas',available:false},
    cx:{value:'0',note:'Sin evaluaciones',available:false},
    risk:{value:riskPoint?.riskIndex==null?'0':`${riskPoint.riskIndex.toFixed(1)} / 100`,note:riskPoint?`Al ${dateLabel(shiftDate(riskPoint.date,0))}`:'Sin turnos',available:riskPoint?.riskIndex!=null}
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
    {errors.length>0&&<div className="dash-v6-error"><AlertTriangle size={16}/><span>{errors.join(' ')}</span></div>}
    <div className="dash-v6-heading">
      <div><h2>{dashboardTitleByUser[getUser()]??'Dashboard Operacional'} · Seguridad Física</h2><p><Activity size={14}/>Actualizado {today}{selectedClient&&<span className="dash-v6-scope">Cliente: {selectedClient.name}</span>}</p></div>
      <div className="dash-v6-filters" role="group" aria-label="Filtros del Dashboard">
        <div className="dash-v6-client-filter"><span className="sr-only">Buscar cliente</span><Search size={16}/><input value={clientSearch} placeholder="Buscar cliente" aria-label="Buscar cliente" aria-expanded={clientMenuOpen} onFocus={()=>setClientMenuOpen(true)} onBlur={()=>window.setTimeout(()=>setClientMenuOpen(false),120)} onChange={event=>{setClientSearch(event.target.value);setClientId('');setCompanyId('');setClientMenuOpen(true)}} onKeyDown={event=>{if(event.key==='Escape')setClientMenuOpen(false)}}/>{clientSearch&&<button type="button" aria-label="Limpiar cliente" onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId('');setCompanyId('');setClientSearch('');setClientMenuOpen(false)}}>×</button>}<ChevronDown size={14} aria-hidden="true"/>{clientMenuOpen&&<div className="dash-v6-client-menu" role="listbox"><button type="button" role="option" aria-selected={!clientId} onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId('');setCompanyId('');setClientSearch('');setClientMenuOpen(false)}}>Todos los clientes</button>{visibleClients.map(client=><button type="button" role="option" aria-selected={clientId===client.id} key={client.id} onMouseDown={event=>event.preventDefault()} onClick={()=>{setClientId(client.id);setCompanyId('');setClientSearch(client.name);setClientMenuOpen(false)}}>{client.name}</button>)}{visibleClients.length===0&&<span>No hay clientes coincidentes</span>}</div>}</div>
        <label className="dash-v6-select dash-v6-company"><MapPin size={15}/><span className="dash-v6-select-value" aria-hidden="true">{companyOptions.find(company=>company.id===companyId)?.name??'Todas las compañías'}</span><select value={companyId} title={companyOptions.find(company=>company.id===companyId)?.name??'Todas las compañías'} aria-label="Filtrar por compañía" onChange={event=>setCompanyId(event.target.value)}><option value="">Todas las compañías</option>{companyOptions.map(company=><option key={company.id} value={company.id}>{company.name}</option>)}</select><ChevronDown size={14}/></label>
        <label className="dash-v6-select"><CalendarDays size={15}/><span className="dash-v6-select-value" aria-hidden="true">{dateOptions.find(option=>option.value===weekStart)?.label.replace('Semana actual · ','Semana · ')}</span><select value={weekStart} title={dateOptions.find(option=>option.value===weekStart)?.label} aria-label="Semana de cobertura" onChange={event=>setWeekStart(event.target.value)}>{dateOptions.map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select><ChevronDown size={14}/></label>
        <label className="dash-v6-select dash-v6-period"><CalendarDays size={15}/><span className="dash-v6-select-value" aria-hidden="true">{period==='7d'?'Últimos 7 días':period==='6m'?'Últimos 6 meses':'Últimos 30 días'}</span><select value={period} aria-label="Período de tendencia y consignas" onChange={event=>setPeriod(event.target.value as '7d'|'30d'|'6m')}><option value="7d">Últimos 7 días</option><option value="30d">Últimos 30 días</option><option value="6m">Últimos 6 meses</option></select><ChevronDown size={14}/></label>
      </div>
    </div>

    <div className="dash-v6-kpis" aria-label="Indicadores operativos">
      {cardCatalog.map(card=>{const metric=metricValues[card.id];const field:DashboardField|null=({coverageGap:'coverage',nonIdealPerformance:'suitability',nonIdealCompat:'suitability',faltos:'missedReliefs',lateRelief:'metrics',consignas:'consignments',critical:'criticalIncidents',route:'supervisionRoute',factor:null,rotation:null,cx:null,risk:'riskTrend'} as const)[card.id];const cardLoading=field!==null&&pending.has(field);const spark=card.id==='coverageGap'?coverageSpark:card.id==='risk'?riskSpark:[];return <button type="button" key={card.id} data-kpi={card.id} title={`Clic para visualizar ${card.label.toLocaleLowerCase('es')}. ${metric.note}`} aria-pressed={activeCard===card.id} onClick={()=>setActiveCard(card.id)} className={`dash-v6-kpi kpi tone-${card.tone} ${activeCard===card.id?'selected':''} ${metric.available?'':'unavailable'}`}>
        <span className="dash-v6-kpi-mark kicon" aria-hidden="true">{card.glyph}</span>
        <span className="dash-v6-kpi-copy kcontent"><span className="dash-v6-kpi-heading"><strong className="ktitle">{card.id==='coverageGap'?<>Puntos sin<br/>cobertura</>:card.label}</strong>{'sub' in card&&<span className="dash-v6-kpi-sub ksub">{card.sub}</span>}</span>{card.id==='factor'?<span className="dash-v6-factor factor"><span>FHT:</span><b>0</b><span>Disponible:</span><b>0</b><span>Exceso:</span><b>0</b></span>:<span className="dash-v6-kpi-value-row value-row"><b className="kvalue">{cardLoading?'…':card.id==='risk'&&hasNumber(riskPoint?.riskIndex)?<>{riskPoint.riskIndex.toFixed(1)}<span className="dash-v6-kpi-unit"> / 100</span></>:metric.value}</b>{!cardLoading&&<CardSparkline values={spark} color={({red:"#ff243e",green:"#00bf7d",blue:"#1687ff",orange:"#ff7b00",purple:"#8a38ff",yellow:"#f4a100"})[card.tone]}/>}</span>}{card.id!=='critical'&&<small className="dash-v6-kpi-delta delta dash-v6-kpi-comparison" title="0 % por defecto: la fuente actual no entrega una comparación con el período anterior.">0% <span>vs. período anterior</span></small>}</span>
      </button>})}
    </div>

    <div className="dash-v6-lower">
      <section className="dash-v6-panel dash-v6-attention">
        <div className="dash-v6-panel-title dash-v6-attention-title"><span className="attention-icon" aria-hidden="true">▲</span><h3>Atención requerida</h3><b aria-label="Puntos con consignas incumplidas">{attentionLoading?'…':attentionRows.length}</b>{attentionRows.length>10&&<button type="button" className="dash-v6-attention-toggle" aria-expanded={showAllAttention} onClick={()=>setShowAllAttention(value=>!value)}>{showAllAttention?'Ver menos':'Ver todos'} →</button>}</div>
        <div className="dash-v6-table-wrap"><table aria-label="Atención requerida"><thead><tr><th scope="col">#</th><th scope="col">Cliente</th><th scope="col">Punto</th><th scope="col">Fecha</th><th scope="col">Riesgo</th><th scope="col">Incumplimiento de<br/>Consignas</th><th scope="col">Estado</th></tr></thead><tbody>
          {!attentionLoading&&(showAllAttention?attentionRows:attentionRows.slice(0,10)).map((row,index)=><tr key={row.pointId}><td>{index+1}</td><td>{row.clientName||'—'}</td><td>{row.pointName||'—'}</td><td>{new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'2-digit',year:'numeric'}).format(shiftDate(row.date,0))}</td><td title="No hay una regla confirmada de riesgo por incumplimiento de consigna.">—</td><td>{row.nonCompliant}</td><td><span className="dash-v6-state state-pending_assignment">Incumplida</span></td></tr>)}
          {!attentionLoading&&attentionRows.length===0&&<tr><td colSpan={7} className="dash-v6-empty">{!data.consignments?'No se pudieron consultar los incumplimientos de consignas.':data.consignments.nonCompliant>0?'El detalle de los incumplimientos requiere actualizar el backend.':'No hay incumplimientos de consignas en el período y el alcance seleccionados.'}</td></tr>}
          {attentionLoading&&<tr><td colSpan={7} className="dash-v6-empty">Cargando datos operativos…</td></tr>}
        </tbody></table></div>
      </section>

      <div className="dash-v6-side-stack">
        <section className="dash-v6-panel dash-v6-trend">
          <div className="dash-v6-panel-title"><span className="blue-icon"><Activity size={18}/></span><div><h3>Tendencia — {activeCardTitle}</h3><small>{activeCard==='coverageGap'||activeCard==='risk'?`Histórico de ${periodLabel}`:'Histórico S/D'}</small></div></div>
          {trendLoading?<div className="dash-v6-no-history">Cargando tendencia operacional…</div>:activeCard!=='coverageGap'&&activeCard!=='risk'?<div className="dash-v6-no-history"><Activity size={24}/><strong>S/D</strong><span>Sin histórico para este indicador.</span></div>:!data.riskTrend?<div className="dash-v6-no-history"><Activity size={24}/><strong>S/D</strong><span>Tendencia no disponible.</span></div>:data.riskTrend.every(point=>point.riskIndex==null)?<div className="dash-v6-no-history"><Activity size={24}/><strong>S/D</strong><span>Sin turnos en el período.</span></div>:<RiskTrendChart points={data.riskTrend} mode={activeCard==='risk'?'risk':'uncovered'}/>}
        </section>
        <section className="dash-v6-panel dash-v6-geography">
          <div className="dash-v6-panel-title"><span className="blue-icon"><MapPin size={18}/></span><div><h3>Mapa operacional por provincia</h3><small>{pointIds.size} punto{pointIds.size===1?'':'s'} en el alcance seleccionado</small></div></div>
          <DashboardProvinceMap provinces={provinceCounts} subdivisions={data.provinces as DashboardProvinceSubdivision[]}/>
        </section>
      </div>
    </div>
    <div className="dash-v6-footnote"><FileText size={14}/><span>Cobertura, relevos y riesgo usan datos registrados del alcance seleccionado. Los indicadores sin datos o sin regla de cálculo muestran 0 por defecto.</span></div>
  </div>;
}
