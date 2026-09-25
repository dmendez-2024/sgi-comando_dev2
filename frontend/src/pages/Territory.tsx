import {useEffect,useMemo,useState} from 'react';
import {
  Building2,ChevronDown,ChevronRight,ChevronsDown,ChevronsUp,Clock3,Edit3,
  Filter,Map as MapIcon,MoreVertical,Plus,RefreshCw,Search,ShieldCheck,Trash2,UserRound,X
} from 'lucide-react';
import {ApiError,api,getUser} from '../api';

type Zone={id:string;code:string;name:string;status:string;responsibleEmployeeId?:string;responsibleName?:string;provinceCodes:string[]};
type Region={id:string;zoneId:string;code:string;name:string;status:string;responsibleEmployeeId?:string;responsibleName?:string;provinceCodes:string[]};
type Company={id:string;code:string;name:string;status:string;regionId:string};
type Province={id:string;code:string;name:string;zoneId?:string;regionId?:string;status:string};
type Responsible={employeeId:string;fullName:string;roleCode:string};
type Tree={zones:Zone[];regions:Region[];companies:Company[];provinces:Province[]};
type VisibleRegion={region:Region;provinces:Province[];companies:Company[]};
type VisibleZone={zone:Zone;regions:VisibleRegion[]};
type CoreContext={country?:string;countryCode?:string;subdivisionType?:string;subdivisionSingular?:string;subdivisionPlural?:string;territoryCatalogSource?:string};
type AuditEvent={id:string;entityType:string;entityId:string;eventType:string;actorUsername:string;payloadJson:string;occurredAt:string};
type EditorState={kind:'ZONE'|'REGION';mode:'CREATE'|'EDIT';id?:string;zoneId?:string;code:string;name:string;status:string;responsibleEmployeeId:string;provinceCodes:string[]}|null;
type MenuState={kind:'ZONE'|'REGION';id:string}|null;
type MapPoint=[number,number];
type MapShape=MapPoint[]|MapPoint[][];

const CODE_MAX_LENGTH=32;
const NAME_MAX_LENGTH=160;
const ZONE_COLORS=['#4d7fa9','#d18b47','#8666a9','#3b998c','#c85f68','#9a8f3d','#4770b3','#9c6748'];
const FIXED_ZONE_COLORS:Record<string,string>={ZN:'#aa7f52',ZS:'#6aa94d'};

// Límites provinciales simplificados para el SVG territorial. Se mantienen en este
// componente para que el mapa no dependa de archivos ni librerías adicionales.
const PROVINCE_SHAPES:Record<string,MapShape>={
  SDE:[[-79.39,.03],[-79.62,-.04],[-79.41,-.19],[-79.48,-.54],[-79.35,-.54],[-79.3,-.7],[-78.95,-.31],[-78.75,-.28],[-78.88,-.11],[-79.39,.03]],
  SEL:[[-80.75,-1.67],[-80.75,-2.11],[-81.01,-2.19],[-80.89,-2.34],[-80.46,-2.49],[-80.45,-2.37],[-80.2,-2.21],[-80.52,-1.92],[-80.65,-1.67],[-80.75,-1.67]],
  LOJ:[[-79.46,-3.33],[-79.5,-3.61],[-79.37,-3.64],[-79.41,-3.79],[-79.62,-3.75],[-80.3,-4.02],[-80.45,-4],[-80.45,-4.21],[-80.31,-4.2],[-80.45,-4.45],[-80.36,-4.48],[-80.1,-4.29],[-79.81,-4.49],[-79.64,-4.43],[-79.5,-4.52],[-79.43,-4.75],[-79.33,-4.51],[-79.12,-4.43],[-79.2,-3.73],[-79.1,-3.62],[-79.18,-3.44],[-79.28,-3.48],[-79.31,-3.36],[-79.46,-3.33]],
  ESM:[[-78.78,1.39],[-78.87,1.47],[-79.17,1.09],[-80.05,.83],[-80.1,.68],[-80,.57],[-80.06,.47],[-79.98,.27],[-79.72,.37],[-79.71,.18],[-79.57,.12],[-79.6,-.03],[-79.45,-.04],[-79.27,.34],[-79.02,.31],[-78.83,.44],[-78.66,.38],[-78.45,.54],[-78.54,.77],[-78.44,.81],[-78.55,.94],[-78.5,1.19],[-78.78,1.39]],
  ZCH:[[-78.95,-3.34],[-79.2,-3.73],[-79.11,-4.41],[-79.33,-4.51],[-79.43,-4.75],[-79.27,-4.97],[-79.02,-5.02],[-78.86,-4.67],[-78.65,-4.59],[-78.57,-3.99],[-78.41,-3.79],[-78.37,-3.53],[-78.83,-3.55],[-78.95,-3.34]],
  CAR:[[-78.49,1.2],[-78.55,.94],[-78.2,.72],[-78.09,.48],[-77.82,.35],[-77.69,.64],[-77.53,.65],[-77.7,.74],[-77.71,.85],[-77.97,.82],[-78.2,.95],[-78.31,1.19],[-78.49,1.2]],
  SUC:[[-77.49,.66],[-77.69,.64],[-77.83,.27],[-77.97,.14],[-77.78,.02],[-77.78,-.08],[-77.58,-.11],[-77.46,-.02],[-77.38,-.15],[-77.29,-.05],[-76.93,-.06],[-76.63,-.43],[-76.39,-.52],[-76.09,-.44],[-75.93,-.57],[-75.96,-.42],[-75.86,-.4],[-75.23,-.65],[-75.24,-.52],[-75.51,-.2],[-75.61,-.19],[-75.61,-.11],[-75.26,-.11],[-75.81,.05],[-76.01,.3],[-76.28,.44],[-76.41,.38],[-76.41,.24],[-76.59,.22],[-77.53,.41],[-77.49,.66]],
  MOS:[[-78.16,-1.45],[-78.35,-1.5],[-78.47,-1.75],[-78.43,-2.09],[-78.59,-2.32],[-78.46,-2.42],[-78.55,-2.61],[-78.42,-2.63],[-78.58,-2.71],[-78.71,-3.14],[-78.95,-3.3],[-78.83,-3.55],[-78.37,-3.53],[-78.35,-3.38],[-78.18,-3.5],[-78.19,-3.36],[-77.84,-3],[-76.69,-2.61],[-76.94,-2.55],[-77.28,-2.2],[-77.72,-2.03],[-77.89,-1.69],[-78.02,-1.68],[-78.16,-1.45]],
  CAN:[[-79.14,-2.21],[-79.26,-2.39],[-79.42,-2.34],[-79.52,-2.53],[-79.4,-2.5],[-79.18,-2.75],[-79.03,-2.65],[-78.94,-2.83],[-78.82,-2.82],[-78.77,-2.68],[-78.57,-2.56],[-78.8,-2.38],[-79.01,-2.38],[-79.14,-2.21]],
  PIC:[[-79.22,.32],[-79.36,.25],[-79.37,.02],[-78.84,-.14],[-78.75,-.28],[-78.95,-.32],[-78.72,-.66],[-78.61,-.6],[-78.33,-.66],[-78.19,-.2],[-77.84,.02],[-78.02,.19],[-78.23,.12],[-78.37,.16],[-78.37,.26],[-78.95,.2],[-79.22,.32]],
  ORE:[[-77.28,-.05],[-77.33,-.42],[-77.63,-.54],[-77.5,-.7],[-77.59,-.93],[-77.12,-.7],[-77.21,-.8],[-77.02,-.92],[-77.07,-1.09],[-76.85,-1.16],[-76.53,-1.05],[-76.47,-1.26],[-75.88,-1.33],[-75.56,-1.56],[-75.39,-.93],[-75.19,-.97],[-75.29,-.62],[-75.86,-.4],[-75.96,-.42],[-75.93,-.57],[-76.09,-.44],[-76.39,-.52],[-76.63,-.43],[-76.93,-.06],[-77.28,-.05]],
  MAN:[[-79.76,.38],[-79.93,.25],[-80.04,.37],[-80.07,.05],[-80.49,-.37],[-80.42,-.59],[-80.55,-.88],[-80.91,-1.06],[-80.74,-1.36],[-80.85,-1.6],[-80.81,-1.71],[-80.65,-1.67],[-80.47,-1.95],[-80.33,-1.68],[-80.21,-1.69],[-80.28,-1.57],[-80.17,-1.53],[-80.14,-1.32],[-79.73,-.88],[-79.61,-.86],[-79.41,-.41],[-79.41,-.19],[-79.61,-.05],[-79.57,.12],[-79.71,.18],[-79.67,.26],[-79.76,.38]],
  IMB:[[-78.45,.87],[-78.54,.77],[-78.43,.57],[-78.65,.38],[-78.83,.44],[-79.02,.31],[-79.27,.32],[-78.95,.2],[-78.37,.26],[-78.37,.16],[-78.23,.12],[-77.87,.21],[-77.81,.35],[-78.09,.48],[-78.2,.72],[-78.45,.87]],
  GUA:[[-79.57,-.85],[-79.73,-.88],[-80.14,-1.32],[-80.17,-1.53],[-80.28,-1.57],[-80.21,-1.69],[-80.42,-1.79],[-80.44,-1.96],[-80.2,-2.21],[-80.56,-2.51],[-80.25,-2.74],[-80.26,-2.64],[-79.91,-2.3],[-80.07,-2.59],[-79.94,-2.66],[-79.82,-2.41],[-79.87,-3.06],[-79.55,-2.89],[-79.67,-2.82],[-79.5,-2.77],[-79.38,-2.53],[-79.52,-2.49],[-79.42,-2.34],[-79.22,-2.35],[-79.14,-2.21],[-79.25,-2.2],[-79.11,-2.14],[-79.27,-2.11],[-79.49,-1.88],[-79.66,-1.92],[-79.86,-1.67],[-79.87,-1.42],[-79.52,-.97],[-79.57,-.85]],
  EOR:[[-79.85,-3.05],[-80,-3.26],[-80.03,-3.21],[-80.31,-3.39],[-80.22,-3.44],[-80.13,-3.89],[-79.62,-3.75],[-79.41,-3.78],[-79.37,-3.64],[-79.5,-3.61],[-79.46,-3.33],[-79.67,-3.32],[-79.62,-3.14],[-79.85,-3.05]],
  AZU:[[-79.4,-2.52],[-79.5,-2.77],[-79.67,-2.82],[-79.55,-2.89],[-79.76,-3.07],[-79.6,-3.18],[-79.67,-3.32],[-79.33,-3.34],[-79.08,-3.62],[-79.05,-3.4],[-78.71,-3.14],[-78.58,-2.71],[-78.42,-2.62],[-78.57,-2.55],[-78.88,-2.84],[-79.03,-2.65],[-79.18,-2.75],[-79.4,-2.52]],
  COT:[[-78.94,-.33],[-79.31,-.93],[-79.29,-1.21],[-78.67,-1.16],[-78.39,-.99],[-78.38,-.64],[-78.72,-.66],[-78.94,-.33]],
  TUN:[[-78.4,-.99],[-78.47,-1.09],[-78.86,-1.17],[-78.94,-1.36],[-78.89,-1.46],[-78.2,-1.47],[-78.12,-1.19],[-78.35,-1.17],[-78.33,-1.04],[-78.4,-.99]],
  BOL:[[-79.11,-1.15],[-79.39,-1.32],[-79.27,-1.33],[-79.36,-1.45],[-79.25,-1.56],[-79.36,-1.64],[-79.23,-1.73],[-79.27,-2.03],[-79.13,-2.2],[-79.03,-2.1],[-79.01,-1.77],[-78.88,-1.74],[-78.85,-1.63],[-78.94,-1.36],[-78.86,-1.19],[-79.11,-1.15]],
  PAS:[[-77.06,-1.01],[-77.88,-1.24],[-78.12,-1.22],[-78.18,-1.45],[-78.02,-1.68],[-77.89,-1.69],[-77.72,-2.03],[-77.28,-2.2],[-76.94,-2.55],[-76.69,-2.61],[-76.04,-2.12],[-75.59,-1.51],[-76.06,-1.28],[-76.47,-1.26],[-76.53,-1.05],[-76.86,-1.16],[-77.07,-1.09],[-77.06,-1.01]],
  NAP:[[-77.82,.03],[-78.19,-.2],[-78.27,-.54],[-78.41,-.72],[-78.35,-1.17],[-77.88,-1.24],[-77.03,-.99],[-77.2,-.85],[-77.12,-.69],[-77.59,-.93],[-77.5,-.7],[-77.63,-.54],[-77.33,-.42],[-77.29,-.1],[-77.45,-.14],[-77.47,-.02],[-77.58,-.11],[-77.78,-.08],[-77.82,.03]],
  CHI:[[-78.77,-1.43],[-78.88,-1.49],[-78.86,-1.72],[-79.01,-1.77],[-79.04,-2.13],[-79.25,-2.22],[-79.14,-2.21],[-78.91,-2.42],[-78.5,-2.24],[-78.43,-2.09],[-78.49,-1.96],[-78.4,-1.85],[-78.47,-1.75],[-78.36,-1.64],[-78.36,-1.5],[-78.77,-1.43]],
  LRI:[[-79.42,-.54],[-79.6,-.75],[-79.52,-.97],[-79.75,-1.19],[-79.87,-1.6],[-79.66,-1.92],[-79.49,-1.88],[-79.24,-2.13],[-79.23,-1.74],[-79.36,-1.64],[-79.25,-1.56],[-79.36,-1.45],[-79.27,-1.33],[-79.39,-1.32],[-79.29,-1.21],[-79.31,-.93],[-79.08,-.57],[-79.32,-.7],[-79.42,-.54]]
};

function zoneColor(zone:Zone){
  if(FIXED_ZONE_COLORS[zone.code])return FIXED_ZONE_COLORS[zone.code];
  let hash=0;for(const char of zone.code||zone.id)hash=((hash<<5)-hash+char.charCodeAt(0))|0;
  return ZONE_COLORS[Math.abs(hash)%ZONE_COLORS.length];
}
function projectMapPoint([longitude,latitude]:MapPoint):MapPoint{return [54+(longitude+81.1)*61,8+(1.55-latitude)*46]}
function mapPath(shape:MapShape){
  const rings=typeof shape[0][0]==='number'?[shape as MapPoint[]]:shape as MapPoint[][];
  return rings.map(ring=>ring.map((point,index)=>{const [x,y]=projectMapPoint(point);return `${index?'L':'M'}${x.toFixed(1)} ${y.toFixed(1)}`}).join(' ')+' Z').join(' ');
}

function TerritoryOperationalMap({tree}:{tree:Tree}){
  const assignments=useMemo(()=>{
    const zones=new Map(tree.zones.map(zone=>[zone.id,zone]));
    const regions=new Map(tree.regions.map(region=>[region.id,region]));
    const mapped=new Map<string,{zone:Zone;region:Region;province:Province}>();
    for(const province of tree.provinces){
      if(!province.zoneId||!province.regionId)continue;
      const zone=zones.get(province.zoneId);const region=regions.get(province.regionId);
      if(zone?.status==='ACTIVE'&&region?.status==='ACTIVE'&&region.zoneId===zone.id){
        mapped.set(province.code,{zone,region,province});
      }
    }
    return mapped;
  },[tree.zones,tree.regions,tree.provinces]);
  const activeZones=useMemo(()=>{
    const unique=new Map<string,Zone>();
    for(const item of assignments.values())unique.set(item.zone.id,item.zone);
    return [...unique.values()].sort((a,b)=>a.code.localeCompare(b.code));
  },[assignments]);
  const labels=useMemo(()=>activeZones.map(zone=>{
    const centers=[...assignments.entries()].filter(([,item])=>item.zone.id===zone.id&&PROVINCE_SHAPES[item.province.code]).map(([code])=>{
      const shape=PROVINCE_SHAPES[code];const ring=(typeof shape[0][0]==='number'?shape:(shape as MapPoint[][])[0]) as MapPoint[];
      const total=ring.reduce((sum,point)=>{const [x,y]=projectMapPoint(point);return [sum[0]+x,sum[1]+y] as MapPoint},[0,0] as MapPoint);
      return [total[0]/ring.length,total[1]/ring.length] as MapPoint;
    });
    if(!centers.length&&assignments.get('GAL')?.zone.id===zone.id)return {zone,x:26,y:193};
    const total=centers.reduce((sum,point)=>[sum[0]+point[0],sum[1]+point[1]] as MapPoint,[0,0]);
    return {zone,x:total[0]/centers.length,y:total[1]/centers.length};
  }).filter(label=>Number.isFinite(label.x)&&Number.isFinite(label.y)),[activeZones,assignments]);
  const galapagos=assignments.get('GAL');
  return <div className="ter-map-wrap">
    <svg className="ter-operational-map" viewBox="0 0 450 320" role="img" aria-label="Mapa operacional de Ecuador según Zonas y Regiones activas">
      <g className="ter-galapagos-map">
        <path d="M12 183l8-7 9 3 5 10-6 9-13-3zM34 169l5-4 5 5-3 6-6-1z" fill={galapagos?zoneColor(galapagos.zone):'#e2e7ed'}><title>{galapagos?`Galápagos · ${galapagos.zone.name} · ${galapagos.region.name}`:'Galápagos · Sin Zona y Región activas'}</title></path>
        <text x="8" y="214">Galápagos</text>
      </g>
      {Object.entries(PROVINCE_SHAPES).map(([code,shape])=>{
        const item=assignments.get(code);
        return <path key={code} className={`ter-province-shape ${item?'assigned':'pending'}`} d={mapPath(shape)} fill={item?zoneColor(item.zone):'#e2e7ed'}>
          <title>{item?`${item.province.name} · ${item.zone.name} · ${item.region.name}`:`${tree.provinces.find(province=>province.code===code)?.name||code} · Sin Zona y Región activas`}</title>
        </path>
      })}
      {labels.map(({zone,x,y})=><text key={zone.id} className="ter-zone-map-label" x={x} y={y}>{zone.code}</text>)}
    </svg>
    <div className="ter-map-legend">
      {activeZones.map(zone=><div key={zone.id}><i className="zone-dot" style={{background:zoneColor(zone)}}/><span>{zone.name} ({zone.code})</span></div>)}
      {!activeZones.length&&<div className="ter-map-empty">Sin Zonas y Regiones activas para representar.</div>}
      <div className="ter-map-note"><MapIcon size={14}/>Mapa base Ecuador; la estructura inferior es la configuración vigente.</div>
    </div>
  </div>
}

function msg(e:unknown){return e instanceof ApiError?(e.body||e.message):String(e)}
function labelStatus(s:string){return s==='ACTIVE'?'Activa':s==='DRAFT'?'Borrador':'Inactiva'}
function statusClass(s:string){return s==='ACTIVE'?'active':s==='DRAFT'?'draft':'inactive'}
function titleCase(s:string){return s?`${s.charAt(0).toUpperCase()}${s.slice(1)}`:s}

export default function Territory(){
  const currentUser=getUser();
  const countryEditors=['presidente','dnacional'];
  const canEditZones=countryEditors.includes(currentUser);
  const canEditRegions=[...countryEditors,'dzonal'].includes(currentUser);
  const [tree,setTree]=useState<Tree>({zones:[],regions:[],companies:[],provinces:[]});
  const [core,setCore]=useState<CoreContext>({country:'Ecuador',countryCode:'EC',subdivisionType:'PROVINCE',subdivisionSingular:'Provincia',subdivisionPlural:'Provincias',territoryCatalogSource:'CORE LOCAL · UAT'});
  const [responsibles,setResponsibles]=useState<Responsible[]>([]);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [query,setQuery]=useState('');
  const [statusFilter,setStatusFilter]=useState('ALL');
  const [typeFilter,setTypeFilter]=useState('ALL');
  const [expandedZones,setExpandedZones]=useState<Set<string>>(new Set());
  const [expandedRegions,setExpandedRegions]=useState<Set<string>>(new Set());
  const [editor,setEditor]=useState<EditorState>(null);
  const [menu,setMenu]=useState<MenuState>(null);
  const [audit,setAudit]=useState<{title:string;events:AuditEvent[]}|null>(null);

  const subdivisionSingular=core.subdivisionSingular||'Provincia';
  const subdivisionPlural=core.subdivisionPlural||'Provincias';

  const load=async()=>{
    try{
      const [t,r,c]=await Promise.all([
        canEditRegions?api.territoryAdmin():api.territory(),
        api.territoryResponsibles().catch(()=>[]),
        api.context().catch(()=>({}))
      ]);
      setTree(t);setResponsibles(r);setCore(prev=>({...prev,...c}));setError('');
      setExpandedZones(prev=>prev.size?prev:new Set(t.zones.map((z:Zone)=>z.id)));
    }catch(e){setError(msg(e))}
  };
  useEffect(()=>{void load()},[currentUser]);
  useEffect(()=>{if(!notice)return;const id=window.setTimeout(()=>setNotice(''),5000);return()=>window.clearTimeout(id)},[notice]);
  useEffect(()=>{if(!error)return;const id=window.setTimeout(()=>setError(''),5000);return()=>window.clearTimeout(id)},[error]);
  useEffect(()=>{const close=()=>setMenu(null);window.addEventListener('click',close);return()=>window.removeEventListener('click',close)},[]);

  const regionsByZone=useMemo(()=>{const m=new Map<string,Region[]>();for(const r of tree.regions)m.set(r.zoneId,[...(m.get(r.zoneId)??[]),r]);return m},[tree]);
  const companiesByRegion=useMemo(()=>{const m=new Map<string,Company[]>();for(const c of tree.companies)m.set(c.regionId,[...(m.get(c.regionId)??[]),c]);return m},[tree]);
  const provincesByCode=useMemo(()=>new Map(tree.provinces.map(province=>[province.code,province])),[tree.provinces]);
  const provincesByRegion=useMemo(()=>new Map(tree.regions.map(region=>[region.id,region.provinceCodes.map(code=>provincesByCode.get(code)).filter((province):province is Province=>Boolean(province))])),[tree.regions,provincesByCode]);
  const provincesByZone=useMemo(()=>new Map(tree.zones.map(zone=>[zone.id,zone.provinceCodes.map(code=>provincesByCode.get(code)).filter((province):province is Province=>Boolean(province))])),[tree.zones,provincesByCode]);

  const norm=query.trim().toLowerCase();
  const matches=(status:string,...values:(string|undefined)[])=>
    (statusFilter==='ALL'||status===statusFilter)&&(!norm||values.filter(Boolean).join(' ').toLowerCase().includes(norm));
  const visibleZones=useMemo<VisibleZone[]>(()=>tree.zones.flatMap(zone=>{
    const regions=regionsByZone.get(zone.id)??[];
    if(typeFilter==='ZONE')return matches(zone.status,zone.code,zone.name,zone.responsibleName)?[{
      zone,
      regions:regions.map(region=>({region,provinces:provincesByRegion.get(region.id)??[],companies:companiesByRegion.get(region.id)??[]}))
    }]:[];
    if(typeFilter==='REGION'){
      const visibleRegions=regions.filter(region=>matches(region.status,region.code,region.name,region.responsibleName)).map(region=>({
        region,
        provinces:provincesByRegion.get(region.id)??[],
        companies:companiesByRegion.get(region.id)??[]
      }));
      return visibleRegions.length?[{zone,regions:visibleRegions}]:[];
    }
    if(typeFilter==='SUBDIVISION'){
      const visibleRegions=regions.flatMap(region=>{
        const provinces=(provincesByRegion.get(region.id)??[]).filter(province=>matches(province.status,province.code,province.name));
        return provinces.length?[{region,provinces,companies:companiesByRegion.get(region.id)??[]}]:[];
      });
      return visibleRegions.length?[{zone,regions:visibleRegions}]:[];
    }
    if(typeFilter==='COMPANY'){
      const visibleRegions=regions.flatMap(region=>{
        const companies=(companiesByRegion.get(region.id)??[]).filter(company=>matches(company.status,company.code,company.name));
        return companies.length?[{region,provinces:provincesByRegion.get(region.id)??[],companies}]:[];
      });
      return visibleRegions.length?[{zone,regions:visibleRegions}]:[];
    }

    if(statusFilter!=='ALL'&&zone.status!==statusFilter)return [];
    const zoneText=[zone.code,zone.name,zone.responsibleName,...(provincesByZone.get(zone.id)??[]).map(p=>p.name),...regions.map(r=>r.name),...regions.flatMap(r=>(companiesByRegion.get(r.id)??[]).map(c=>c.name))].filter(Boolean).join(' ').toLowerCase();
    if(norm&&!zoneText.includes(norm))return [];
    return [{zone,regions:regions.map(region=>({region,provinces:provincesByRegion.get(region.id)??[],companies:companiesByRegion.get(region.id)??[]}))}];
  }),[tree.zones,typeFilter,statusFilter,norm,regionsByZone,companiesByRegion,provincesByRegion,provincesByZone]);

  const openCreateZone=()=>setEditor({kind:'ZONE',mode:'CREATE',code:'',name:'',status:'DRAFT',responsibleEmployeeId:'',provinceCodes:[]});
  const openEditZone=(z:Zone)=>setEditor({kind:'ZONE',mode:'EDIT',id:z.id,code:z.code,name:z.name,status:z.status,responsibleEmployeeId:z.responsibleEmployeeId??'',provinceCodes:[...z.provinceCodes]});
  const openCreateRegion=()=>setEditor({kind:'REGION',mode:'CREATE',zoneId:tree.zones[0]?.id??'',code:'',name:'',status:'DRAFT',responsibleEmployeeId:'',provinceCodes:[]});
  const openEditRegion=(r:Region)=>setEditor({kind:'REGION',mode:'EDIT',id:r.id,zoneId:r.zoneId,code:r.code,name:r.name,status:r.status,responsibleEmployeeId:r.responsibleEmployeeId??'',provinceCodes:[...r.provinceCodes]});

  const editorAllowedProvinces=useMemo(()=>{
    if(!editor)return [] as Province[];
    if(editor.kind==='ZONE')return tree.provinces;
    const zone=tree.zones.find(item=>item.id===editor.zoneId);
    const allowed=new Set([...(zone?.provinceCodes??[]),...editor.provinceCodes]);
    return tree.provinces.filter(p=>allowed.has(p.code));
  },[editor,tree.provinces,tree.zones]);

  const saveEditor=async()=>{
    if(!editor)return;
    const code=editor.code.trim();const name=editor.name.trim();
    if(code.length>CODE_MAX_LENGTH){setError(`El código no puede superar ${CODE_MAX_LENGTH} caracteres.`);return}
    if(name.length>NAME_MAX_LENGTH){setError(`El nombre no puede superar ${NAME_MAX_LENGTH} caracteres.`);return}
    try{
      const body={code,name,status:editor.status,responsibleEmployeeId:editor.responsibleEmployeeId||null,provinceCodes:editor.provinceCodes};
      if(editor.kind==='ZONE'){
        if(editor.mode==='CREATE')await api.createZone(body); else await api.updateZone(editor.id!,body);
        setNotice(editor.mode==='CREATE'?'Zona creada correctamente.':'Zona guardada correctamente.');
      }else{
        const regionBody={...body,zoneId:editor.zoneId};
        if(editor.mode==='CREATE')await api.createRegion(regionBody); else await api.updateRegion(editor.id!,regionBody);
        setNotice(editor.mode==='CREATE'?'Región creada correctamente.':'Región guardada correctamente.');
      }
      setEditor(null);await load();
    }catch(e){setError(msg(e))}
  };
  const removeZone=async(z:Zone)=>{try{await api.deleteZone(z.id);setNotice('Zona eliminada correctamente.');await load()}catch(e){setError(msg(e))}};
  const removeRegion=async(r:Region)=>{try{await api.deleteRegion(r.id);setNotice('Región eliminada correctamente.');await load()}catch(e){setError(msg(e))}};
  const showAudit=async(kind:'ZONE'|'REGION',id:string,title:string)=>{
    try{const events=await api.territoryAudit(kind,id);setAudit({title,events});setMenu(null)}catch(e){setError(msg(e))}
  };

  const toggleZone=(id:string)=>setExpandedZones(prev=>{const n=new Set(prev);n.has(id)?n.delete(id):n.add(id);return n});
  const toggleRegion=(id:string)=>setExpandedRegions(prev=>{const n=new Set(prev);n.has(id)?n.delete(id):n.add(id);return n});
  const expandAll=()=>{setExpandedZones(new Set(tree.zones.map(z=>z.id)));setExpandedRegions(new Set(tree.regions.map(r=>r.id)))};
  const collapseAll=()=>{setExpandedZones(new Set());setExpandedRegions(new Set())};
  const clearFilters=()=>{setQuery('');setStatusFilter('ALL');setTypeFilter('ALL')};

  return <div className="ter-page">
    {error&&<div className="uat-toast error">{error}</div>}{notice&&<div className="uat-toast notice">{notice}</div>}

    <div className="ter-page-head">
      <div><p className="ter-page-intro">Gestiona la estructura territorial del país. País → Zona → Región → {subdivisionSingular} → Compañía.</p></div>
      <div className="ter-page-actions">
        <button className="ter-btn secondary" onClick={()=>void load()}><RefreshCw size={16}/>Actualizar</button>
        {canEditZones&&<button className="ter-btn secondary accent" onClick={openCreateZone}><Plus size={17}/>Zona</button>}
        {canEditRegions&&<button className="ter-btn primary" onClick={openCreateRegion}><Plus size={17}/>Región</button>}
      </div>
    </div>

    <section className="ter-country-card">
      <div className="ter-country-copy">
        <div className="ter-country-title"><div className="ter-flag">🇪🇨</div><h3>{core.country||'Ecuador'}</h3><span className="ter-core-badge">{core.territoryCatalogSource||'CORE LOCAL · UAT'}</span></div>
        <div className="ter-country-stats"><strong>{tree.zones.length}</strong> Zonas <b>•</b> <strong>{tree.regions.length}</strong> Regiones <b>•</b> <strong>{tree.provinces.length}</strong> {subdivisionPlural} <b>•</b> <strong>{tree.companies.length}</strong> Compañías</div>
        <p>El catálogo y la denominación territorial ({subdivisionPlural}) provienen de CORE. SGI: Comando administra únicamente su agrupación operacional en Zonas y Regiones.</p>
      </div>
      <TerritoryOperationalMap tree={tree}/>
    </section>

    <section className="ter-structure-card">
      <div className="ter-structure-head"><h3>Estructura territorial</h3><div><button className="ter-btn ghost" onClick={expandAll}><ChevronsDown size={16}/>Expandir todo</button><button className="ter-btn ghost" onClick={collapseAll}><ChevronsUp size={16}/>Contraer todo</button></div></div>
      <div className="ter-filters">
        <label className="ter-search"><Search size={18}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder={`Buscar zonas, regiones, ${subdivisionPlural.toLowerCase()} o compañías...`}/></label>
        <label><span>Estado</span><select value={statusFilter} onChange={e=>setStatusFilter(e.target.value)}><option value="ALL">Todos</option><option value="ACTIVE">Activas</option><option value="DRAFT">Borrador</option><option value="INACTIVE">Inactivas</option></select></label>
        <label><span>Tipo</span><select value={typeFilter} onChange={e=>setTypeFilter(e.target.value)}><option value="ALL">Todos</option><option value="ZONE">Zona</option><option value="REGION">Región</option><option value="SUBDIVISION">{subdivisionSingular}</option><option value="COMPANY">Compañía</option></select></label>
        <button className="ter-btn ghost" onClick={clearFilters}><X size={16}/>Limpiar</button>
      </div>

      <div className="ter-grid-head"><span>Territorio</span><span>Responsable</span><span>Regiones</span><span>{subdivisionPlural}</span><span>Compañías</span><span>Estado</span><span>Acciones</span></div>
      <div className="ter-tree">
        {visibleZones.map(({zone:z,regions:zoneRegions})=>{
          const zoneProvinces=zoneRegions.flatMap(item=>item.provinces);
          const zoneCompanies=zoneRegions.flatMap(item=>item.companies);
          const zoneOpen=(typeFilter!=='ALL'&&typeFilter!=='ZONE')||expandedZones.has(z.id);
          return <div className="ter-zone" key={z.id}>
            <div className="ter-row zone-row">
              {typeFilter==='ZONE'?<span className="ter-expand"/>:<button className="ter-expand" onClick={()=>toggleZone(z.id)}>{zoneOpen?<ChevronDown/>:<ChevronRight/>}</button>}
              <div className="ter-territory-cell"><div className="ter-code">{z.code}</div><div><strong>{z.name}</strong><small>{z.status==='ACTIVE'?'Zona operativa activa':'Configuración territorial'}</small></div></div>
              <div className="ter-responsible"><UserRound size={16}/>{z.responsibleName||'Sin asignar'}</div>
              <strong className="ter-count">{zoneRegions.length}</strong><strong className="ter-count">{typeFilter==='ALL'?(provincesByZone.get(z.id)??[]).length:zoneProvinces.length}</strong><strong className="ter-count">{zoneCompanies.length}</strong>
              <span className={`ter-status ${statusClass(z.status)}`}>{labelStatus(z.status)}</span>
              <div className="ter-menu-wrap"><button className="ter-menu-btn" onClick={e=>{e.stopPropagation();setMenu(menu?.kind==='ZONE'&&menu.id===z.id?null:{kind:'ZONE',id:z.id})}}><MoreVertical/></button>{menu?.kind==='ZONE'&&menu.id===z.id&&<div className="ter-menu" onClick={e=>e.stopPropagation()}>{canEditZones&&<button onClick={()=>{openEditZone(z);setMenu(null)}}><Edit3/>Editar</button>}<button onClick={()=>void showAudit('ZONE',z.id,z.name)}><Clock3/>Historial</button>{canEditZones&&<button className="danger" disabled={z.status==='ACTIVE'} onClick={()=>void removeZone(z)}><Trash2/>Eliminar</button>}</div>}</div>
            </div>
            {zoneOpen&&<div className="ter-zone-body">
              {zoneRegions.map(({region:r,provinces:regionProvinces,companies:regionCompanies})=>{
                const regionOpen=['SUBDIVISION','COMPANY'].includes(typeFilter)||expandedRegions.has(r.id);
                return <div className="ter-region" key={r.id}>
                  <div className="ter-row region-row">
                    {typeFilter==='REGION'?<span className="ter-expand"/>:<button className="ter-expand" onClick={()=>toggleRegion(r.id)}>{regionOpen?<ChevronDown/>:<ChevronRight/>}</button>}
                    <div className="ter-territory-cell"><div className="ter-code region">{r.code}</div><div><strong>{r.name}</strong><small>{regionProvinces.length} {subdivisionPlural.toLowerCase()} · {regionCompanies.length} {regionCompanies.length===1?'compañía':'compañías'}</small></div></div>
                    <div className="ter-responsible"><UserRound size={16}/>{r.responsibleName||'Sin asignar'}</div><span className="ter-count muted">—</span><strong className="ter-count">{regionProvinces.length}</strong><strong className="ter-count">{regionCompanies.length}</strong>
                    <span className={`ter-status ${statusClass(r.status)}`}>{labelStatus(r.status)}</span>
                    <div className="ter-menu-wrap"><button className="ter-menu-btn" onClick={e=>{e.stopPropagation();setMenu(menu?.kind==='REGION'&&menu.id===r.id?null:{kind:'REGION',id:r.id})}}><MoreVertical/></button>{menu?.kind==='REGION'&&menu.id===r.id&&<div className="ter-menu" onClick={e=>e.stopPropagation()}>{canEditRegions&&<button onClick={()=>{openEditRegion(r);setMenu(null)}}><Edit3/>Editar</button>}<button onClick={()=>void showAudit('REGION',r.id,r.name)}><Clock3/>Historial</button>{canEditRegions&&<button className="danger" disabled={r.status==='ACTIVE'} onClick={()=>void removeRegion(r)}><Trash2/>Eliminar</button>}</div>}</div>
                  </div>
                  {regionOpen&&typeFilter!=='REGION'&&<div className={`ter-region-detail ${typeFilter==='SUBDIVISION'||typeFilter==='COMPANY'?'single':''}`}>
                    {typeFilter!=='COMPANY'&&<div className="ter-detail-block"><div className="ter-detail-title"><strong>{subdivisionPlural} ({regionProvinces.length})</strong><span>Catálogo: {core.territoryCatalogSource||'CORE LOCAL · UAT'}</span></div><div className="ter-detail-table"><div className="ter-detail-head"><span>Nombre</span><span>Código CORE</span><span>Estado</span></div>{regionProvinces.map(p=><div className="ter-detail-row" key={p.id}><span>{p.name}</span><code>{p.code}</code><span className={`ter-status small ${statusClass(p.status)}`}>{labelStatus(p.status)}</span></div>)}{!regionProvinces.length&&<div className="ter-empty">Sin {subdivisionPlural.toLowerCase()} asignadas.</div>}</div></div>}
                    {typeFilter!=='SUBDIVISION'&&<div className="ter-detail-block companies"><div className="ter-detail-title"><strong>Compañías ({regionCompanies.length})</strong><span>La Región se asigna desde la vertical COM.</span></div><div className="ter-company-list">{regionCompanies.map(c=><div key={c.id}><Building2/><span><strong>{c.name}</strong><small>{c.code}</small></span><span className={`ter-status small ${statusClass(c.status)}`}>{labelStatus(c.status)}</span></div>)}{!regionCompanies.length&&<div className="ter-empty">Sin Compañías asignadas.</div>}</div></div>}
                  </div>}
                </div>
              })}
              {!zoneRegions.length&&<div className="ter-zone-empty">Esta Zona todavía no tiene Regiones.</div>}
            </div>}
          </div>
        })}
        {!visibleZones.length&&<div className="ter-no-results"><Filter size={22}/><strong>Sin resultados</strong><span>Prueba limpiando los filtros.</span></div>}
      </div>
    </section>

    {editor&&<div className="ter-modal-backdrop" onMouseDown={()=>setEditor(null)}><div className="ter-modal" onMouseDown={e=>e.stopPropagation()}>
      <div className="ter-modal-head"><div><h3>{editor.mode==='CREATE'?'Nueva':'Editar'} {editor.kind==='ZONE'?'Zona':'Región'}</h3><p>{editor.kind==='ZONE'?`Define qué ${subdivisionPlural.toLowerCase()} pertenecen a la Zona.`:`La Región solo puede usar ${subdivisionPlural.toLowerCase()} previamente incluidas en su Zona.`}</p></div><button onClick={()=>setEditor(null)}><X/></button></div>
      <div className="ter-modal-grid">
        {editor.kind==='REGION'&&<label>Zona<select value={editor.zoneId} disabled={editor.mode==='EDIT'&&editor.status==='ACTIVE'} onChange={e=>setEditor({...editor,zoneId:e.target.value,provinceCodes:[]})}>{tree.zones.map(z=><option value={z.id} key={z.id}>{z.code} · {z.name}</option>)}</select></label>}
        <label>Código<input value={editor.code} maxLength={CODE_MAX_LENGTH} disabled={editor.mode==='EDIT'} onChange={e=>setEditor({...editor,code:e.target.value.toUpperCase()})} placeholder={editor.kind==='ZONE'?'Ej. ZC':'Ej. R-C1'}/><small>{editor.code.length}/{CODE_MAX_LENGTH} caracteres</small></label>
        <label>Nombre<input value={editor.name} maxLength={NAME_MAX_LENGTH} disabled={editor.mode==='EDIT'&&editor.status==='ACTIVE'} onChange={e=>setEditor({...editor,name:e.target.value})} placeholder={editor.kind==='ZONE'?'Ej. Zona Centro':'Ej. Región Centro 1'}/><small>{editor.name.length}/{NAME_MAX_LENGTH} caracteres</small></label>
        <label>Responsable<select value={editor.responsibleEmployeeId} onChange={e=>setEditor({...editor,responsibleEmployeeId:e.target.value})}><option value="">Sin asignar</option>{responsibles.map(p=><option key={p.employeeId} value={p.employeeId}>{p.fullName}</option>)}</select></label>
        <label>Estado<select value={editor.status} disabled={editor.mode==='EDIT'&&editor.status==='ACTIVE'} onChange={e=>setEditor({...editor,status:e.target.value})}><option value="DRAFT">Borrador</option><option value="ACTIVE">Activa</option><option value="INACTIVE">Inactiva</option></select></label>
      </div>
      <div className="ter-core-picker"><div><strong>{subdivisionPlural}</strong><span>Fuente: {core.territoryCatalogSource||'CORE LOCAL · UAT'}</span></div><div className="ter-core-grid">{editorAllowedProvinces.map(p=><label key={p.id}><input type="checkbox" checked={editor.provinceCodes.includes(p.code)} onChange={e=>setEditor({...editor,provinceCodes:e.target.checked?[...editor.provinceCodes,p.code]:editor.provinceCodes.filter(x=>x!==p.code)})}/><span>{p.name}</span><code>{p.code}</code></label>)}</div></div>
      {editor.mode==='EDIT'&&editor.status==='ACTIVE'&&<div className="ter-active-note"><ShieldCheck/>Esta entidad está Activa: no puede eliminarse ni cambiar su identidad, pero sí Responsable y {subdivisionPlural}.</div>}
      <div className="ter-modal-actions"><button className="ter-btn ghost" onClick={()=>setEditor(null)}>Cancelar</button><button className="ter-btn primary" disabled={!editor.code.trim()||!editor.name.trim()||(editor.kind==='REGION'&&!editor.zoneId)} onClick={()=>void saveEditor()}>Guardar</button></div>
    </div></div>}

    {audit&&<div className="ter-modal-backdrop" onMouseDown={()=>setAudit(null)}><div className="ter-modal audit" onMouseDown={e=>e.stopPropagation()}><div className="ter-modal-head"><div><h3>Historial · {audit.title}</h3><p>Trazabilidad territorial para auditoría.</p></div><button onClick={()=>setAudit(null)}><X/></button></div><div className="ter-audit-list">{audit.events.map(ev=><div key={ev.id}><Clock3/><span><strong>{ev.eventType.replaceAll('_',' ')}</strong><small>{ev.actorUsername} · {new Date(ev.occurredAt).toLocaleString('es-EC')}</small></span></div>)}{!audit.events.length&&<div className="ter-empty">Sin eventos registrados.</div>}</div></div></div>}
  </div>;
}
