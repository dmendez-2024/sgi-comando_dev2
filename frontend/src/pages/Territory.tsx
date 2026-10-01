import {useEffect,useMemo,useState} from 'react';
import {
  Building2,ChevronDown,ChevronRight,ChevronsDown,ChevronsUp,Clock3,Edit3,
  Filter,Map as MapIcon,MoreVertical,Plus,RefreshCw,Search,ShieldCheck,Trash2,UserRound,X
} from 'lucide-react';
import {ApiError,api,getUser} from '../api';

type Zone={id:string;code:string;name:string;status:string;responsibleEmployeeId?:string;responsibleName?:string;provinceCodes:string[]};
type Region={id:string;zoneId:string;code:string;name:string;status:string;responsibleEmployeeId?:string;responsibleName?:string;provinceCodes:string[]};
type Company={id:string;code:string;name:string;status:string;regionId:string};
type Province={id:string;code:string;name:string;zoneId?:string;regionId?:string;status:string;coreSubdivisionId?:string;geometryJson?:string;coreDatasetVersion?:string};
type Responsible={employeeId:string;fullName:string;roleCode:string};
type Tree={zones:Zone[];regions:Region[];companies:Company[];provinces:Province[]};
type VisibleRegion={region:Region;provinces:Province[];companies:Company[]};
type VisibleZone={zone:Zone;regions:VisibleRegion[]};
type CoreContext={country?:string;countryCode?:string;subdivisionType?:string;subdivisionSingular?:string;subdivisionPlural?:string;territoryCatalogSource?:string;territorialDatasetVersion?:string};
type AuditEvent={id:string;entityType:string;entityId:string;eventType:string;actorUsername:string;payloadJson:string;occurredAt:string};
type EditorState={kind:'ZONE'|'REGION';mode:'CREATE'|'EDIT';id?:string;zoneId?:string;code:string;name:string;status:string;responsibleEmployeeId:string;provinceCodes:string[]}|null;
type MenuState={kind:'ZONE'|'REGION';id:string}|null;
type MapPoint=[number,number];
type GeoGeometry={type:'Polygon'|'MultiPolygon';coordinates:MapPoint[][]|MapPoint[][][]};
type GeoFeature={id:string;type:'Feature';properties:{code:string;officialCode?:string;name:string;type?:string;typeLabel?:string;labelPoint?:MapPoint};geometry:GeoGeometry};
type TerritoryGeoJson={type:'FeatureCollection';countryName:string;countryCode?:string;countryIsoAlpha3?:string;datasetVersion:string;detail?:string;total:number;subdivisionType?:string;subdivisionSingular?:string;subdivisionPlural?:string;features:GeoFeature[]};

const CODE_MAX_LENGTH=32;
const NAME_MAX_LENGTH=160;
const ZONE_COLORS=['#4d7fa9','#d18b47','#8666a9','#3b998c','#c85f68','#9a8f3d','#4770b3','#9c6748'];
const FIXED_ZONE_COLORS:Record<string,string>={ZN:'#aa7f52',ZS:'#6aa94d'};

function zoneColor(zone:Zone){
  if(FIXED_ZONE_COLORS[zone.code])return FIXED_ZONE_COLORS[zone.code];
  let hash=0;for(const char of zone.code||zone.id)hash=((hash<<5)-hash+char.charCodeAt(0))|0;
  return ZONE_COLORS[Math.abs(hash)%ZONE_COLORS.length];
}
function geoRings(feature:GeoFeature):MapPoint[][]{
  return feature.geometry.type==='Polygon'
    ? feature.geometry.coordinates as MapPoint[][]
    : (feature.geometry.coordinates as MapPoint[][][]).flat();
/* =======
type MapBounds={minX:number;maxX:number;minY:number;maxY:number};
function geometryMapShape(raw?:string):MapShape|null{
  if(!raw)return null;
  try{
    let geometry=JSON.parse(raw);
    if(geometry?.type==='Feature')geometry=geometry.geometry;
    if(geometry?.type==='Polygon'&&Array.isArray(geometry.coordinates))return geometry.coordinates as MapPoint[][];
    if(geometry?.type==='MultiPolygon'&&Array.isArray(geometry.coordinates))return geometry.coordinates.flat(1) as MapPoint[][];
  }catch{}
  return null;
}
function shapeRings(shape:MapShape):MapPoint[][]{return typeof shape[0]?.[0]==='number'?[shape as MapPoint[]]:shape as MapPoint[][]}
function shapeBounds(shapes:MapShape[]):MapBounds|null{
  const points=shapes.flatMap(shape=>shapeRings(shape).flat());
  if(!points.length)return null;
  return{minX:Math.min(...points.map(p=>p[0])),maxX:Math.max(...points.map(p=>p[0])),minY:Math.min(...points.map(p=>p[1])),maxY:Math.max(...points.map(p=>p[1]))};
}
function projectDynamicPoint([x,y]:MapPoint,bounds:MapBounds):MapPoint{
  const width=450,height=320,padding=24;
  const scale=Math.min((width-padding*2)/Math.max(bounds.maxX-bounds.minX,.001),(height-padding*2)/Math.max(bounds.maxY-bounds.minY,.001));
  const mapWidth=(bounds.maxX-bounds.minX)*scale,mapHeight=(bounds.maxY-bounds.minY)*scale;
  return[(width-mapWidth)/2+(x-bounds.minX)*scale,(height-mapHeight)/2+(bounds.maxY-y)*scale];
}
function dynamicMapPath(shape:MapShape,bounds:MapBounds){
  return shapeRings(shape).map(ring=>ring.map((point,index)=>{const [x,y]=projectDynamicPoint(point,bounds);return`${index?'L':'M'}${x.toFixed(1)} ${y.toFixed(1)}`}).join(' ')+' Z').join(' ');
>>>>>>> local */
}
function projector(features:GeoFeature[],frame:{x:number;y:number;width:number;height:number}){
  const points=features.flatMap(feature=>geoRings(feature).flat());
  if(!points.length)return null;
  const longitudes=points.map(point=>point[0]);const latitudes=points.map(point=>point[1]);
  const minLon=Math.min(...longitudes),maxLon=Math.max(...longitudes),minLat=Math.min(...latitudes),maxLat=Math.max(...latitudes);
  const spanLon=Math.max(maxLon-minLon,.001),spanLat=Math.max(maxLat-minLat,.001);
  const scale=Math.min(frame.width/spanLon,frame.height/spanLat);
  const offsetX=frame.x+(frame.width-spanLon*scale)/2;
  const offsetY=frame.y+(frame.height-spanLat*scale)/2;
  return ([longitude,latitude]:MapPoint):MapPoint=>[offsetX+(longitude-minLon)*scale,offsetY+(maxLat-latitude)*scale];
}
function geoPath(feature:GeoFeature,project:(point:MapPoint)=>MapPoint){
  return geoRings(feature).map(ring=>ring.map((point,index)=>{const [x,y]=project(point);return `${index?'L':'M'}${x.toFixed(2)} ${y.toFixed(2)}`}).join(' ')+' Z').join(' ');
}
function geoKey(value:string){return (value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase()}

function TerritoryOperationalMap({tree,geo,error}:{tree:Tree;geo:TerritoryGeoJson|null;error:string}){
//function TerritoryOperationalMap({tree,datasetVersion}:{tree:Tree;datasetVersion?:string}){
  const assignments=useMemo(()=>{
    const zones=new Map(tree.zones.map(zone=>[zone.id,zone]));
    const regions=new Map(tree.regions.map(region=>[region.id,region]));
    const provincesByCode=new Map(tree.provinces.map(province=>[geoKey(province.code),province]));
    const provincesByName=new Map(tree.provinces.map(province=>[geoKey(province.name),province]));
    const mapped=new Map<string,{zone:Zone;region:Region;province:Province}>();
    for(const feature of geo?.features??[]){
      const province=provincesByCode.get(geoKey(feature.properties.code))??provincesByName.get(geoKey(feature.properties.name));
      if(!province)continue;
      if(!province.zoneId||!province.regionId)continue;
      const zone=zones.get(province.zoneId);const region=regions.get(province.regionId);
      if(zone?.status==='ACTIVE'&&region?.status==='ACTIVE'&&region.zoneId===zone.id){
        mapped.set(feature.id,{zone,region,province});
      }
    }
    return mapped;
  },[tree.zones,tree.regions,tree.provinces,geo]);
  const activeZones=useMemo(()=>{
    const unique=new Map<string,Zone>();
    for(const item of assignments.values())unique.set(item.zone.id,item.zone);
    return [...unique.values()].sort((a,b)=>a.code.localeCompare(b.code));
  },[assignments]);
  const features=geo?.features??[];
  const insetCodes=new Set(['EC-W']);
  const mainFeatures=features.filter(feature=>!insetCodes.has(feature.properties.code));
  const insetFeatures=features.filter(feature=>insetCodes.has(feature.properties.code));
  const mainProject=useMemo(()=>projector(mainFeatures,{x:82,y:8,width:360,height:302}),[geo]);
  const insetProject=useMemo(()=>projector(insetFeatures,{x:8,y:198,width:66,height:72}),[geo]);
  const labels=useMemo(()=>activeZones.map(zone=>{
    const centers=features.flatMap(feature=>{
      if(assignments.get(feature.id)?.zone.id!==zone.id||!feature.properties.labelPoint)return [];
      const project=insetCodes.has(feature.properties.code)?insetProject:mainProject;
      return project?[project(feature.properties.labelPoint)]:[];
    });
    const total=centers.reduce((sum,point)=>[sum[0]+point[0],sum[1]+point[1]] as MapPoint,[0,0] as MapPoint);
    return centers.length?{zone,x:total[0]/centers.length,y:total[1]/centers.length}:null;
  }).filter((label):label is {zone:Zone;x:number;y:number}=>Boolean(label)),[activeZones,assignments,features,mainProject,insetProject]);

  return <div className="ter-map-wrap">
    {geo&&mainProject?<svg className="ter-operational-map" viewBox="0 0 450 320" role="img" aria-label={`Mapa operacional de ${geo.countryName} según Zonas y Regiones activas`}>
      {insetFeatures.length>0&&<rect className="ter-map-inset-border" x="4" y="190" width="74" height="104" rx="6"/>}
      {mainFeatures.map(feature=>{const item=assignments.get(feature.id);return <path key={feature.id} fillRule="evenodd" className={`ter-province-shape ${item?'assigned':'pending'}`} d={geoPath(feature,mainProject)} fill={item?zoneColor(item.zone):'#e2e7ed'}><title>{item?`${feature.properties.name} · ${item.zone.name} · ${item.region.name}`:`${feature.properties.name} · Sin Zona y Región activas`}</title></path>})}
      {insetProject&&insetFeatures.map(feature=>{const item=assignments.get(feature.id);return <path key={feature.id} fillRule="evenodd" className={`ter-province-shape ${item?'assigned':'pending'}`} d={geoPath(feature,insetProject)} fill={item?zoneColor(item.zone):'#e2e7ed'}><title>{item?`${feature.properties.name} · ${item.zone.name} · ${item.region.name}`:`${feature.properties.name} · Sin Zona y Región activas`}</title></path>})}
      {insetFeatures.length>0&&<text className="ter-map-inset-label" x="41" y="288">Galápagos</text>}
/*=======
  const shapes=useMemo(()=>tree.provinces.flatMap(province=>{if(province.status!=='ACTIVE')return[];const shape=geometryMapShape(province.geometryJson);return shape?[{province,shape}]:[]}),[tree.provinces]);
  const bounds=useMemo(()=>shapeBounds(shapes.map(item=>item.shape)),[shapes]);
  const labels=useMemo(()=>activeZones.map(zone=>{
    const centers=shapes.filter(({province})=>assignments.get(province.code)?.zone.id===zone.id).map(({shape})=>{
      const ring=shapeRings(shape)[0];
      const total=ring.reduce((sum,point)=>{const [x,y]=bounds?projectDynamicPoint(point,bounds):[0,0];return [sum[0]+x,sum[1]+y] as MapPoint},[0,0] as MapPoint);
      return [total[0]/ring.length,total[1]/ring.length] as MapPoint;
    });
    if(!centers.length)return null;
    const total=centers.reduce((sum,point)=>[sum[0]+point[0],sum[1]+point[1]] as MapPoint,[0,0]);
    return {zone,x:total[0]/centers.length,y:total[1]/centers.length};
  }).filter((label):label is {zone:Zone;x:number;y:number}=>label!==null&&Number.isFinite(label.x)&&Number.isFinite(label.y)),[activeZones,assignments,shapes,bounds]);
  return <div className="ter-map-wrap">
    <svg className="ter-operational-map" viewBox="0 0 450 320" role="img" aria-label="Mapa operacional generado con las subdivisiones oficiales de CORE">
      {shapes.map(({province,shape})=>{
        const item=assignments.get(province.code);
        const path=bounds?dynamicMapPath(shape,bounds):'';
        return <path key={province.id} className={`ter-province-shape ${item?'assigned':'pending'}`} d={path} fill={item?zoneColor(item.zone):'#e2e7ed'}>
          <title>{item?`${province.name} · ${item.zone.name} · ${item.region.name}`:`${province.name} · Sin Zona y Región activas`}</title>
        </path>
      })}
-- local */
      {labels.map(({zone,x,y})=><text key={zone.id} className="ter-zone-map-label" x={x} y={y}>{zone.code}</text>)}
    </svg>:<div className="ter-map-unavailable"><MapIcon size={24}/><strong>Mapa no disponible</strong><span>{error||'CORE no devolvió geometrías para el país seleccionado.'}</span></div>}
    <div className="ter-map-legend">
      {activeZones.map(zone=><div key={zone.id}><i className="zone-dot" style={{background:zoneColor(zone)}}/><span>{zone.name} ({zone.code})</span></div>)}
      {!activeZones.length&&<div className="ter-map-empty">Sin Zonas y Regiones activas para representar.</div>}
      <div className="ter-map-note"><MapIcon size={14}/>Mapa base {geo?.countryName||'Ecuador'}; la estructura inferior es la configuración vigente.</div>
      {/* <div className="ter-map-note"><MapIcon size={14}/>{shapes.length?`Polígonos CORE · ${datasetVersion||'versión actual'}.`:'CORE aún no entrega geometrías para este país.'}</div> */}
    </div>
  </div>
}

export type DashboardProvinceCount={code:string;name:string;points:number;status?:string;geometryJson?:string};
export type DashboardProvinceSubdivision={code:string;name:string;status?:string;geometryJson?:string};

export function DashboardProvinceMap({provinces,subdivisions=[]}:{provinces:DashboardProvinceCount[];subdivisions?:DashboardProvinceSubdivision[]}){
  const byCode=new Map(provinces.map(province=>[province.code,province]));
  const maxPoints=Math.max(1,...provinces.map(province=>province.points));
  const totalPoints=provinces.reduce((sum,province)=>sum+province.points,0);
  const catalog=subdivisions.length?subdivisions:provinces;
  const shapes=catalog.flatMap(province=>{if(province.status&&province.status!=='ACTIVE')return[];const shape=geometryMapShape(province.geometryJson);return shape?[{province,shape}]:[]});
  const bounds=shapeBounds(shapes.map(item=>item.shape));
  return <div className="dash-v6-map-wrap">
    <svg className="dash-v6-ecuador-map" viewBox="0 0 450 320" role="img" aria-label={`Mapa de subdivisiones CORE con ${totalPoints} puntos operativos`}>
      {shapes.map(({province,shape})=>{
        const count=byCode.get(province.code)?.points??0;
        const ring=(typeof shape[0][0]==='number'?shape:(shape as MapPoint[][])[0]) as MapPoint[];
        const center=ring.reduce((sum,point)=>{const [x,y]=bounds?projectDynamicPoint(point,bounds):[0,0];return [sum[0]+x,sum[1]+y] as MapPoint},[0,0] as MapPoint);
        const x=center[0]/ring.length;const y=center[1]/ring.length;
        const mapped=byCode.get(province.code);
        return <g key={province.code} className="dash-v6-map-province">
          <path d={bounds?dynamicMapPath(shape,bounds):''} fill={count?'#2483d5':'#e6edf4'} fillOpacity={count?0.4+(count/maxPoints)*0.6:1}>
            <title>{mapped?.name||province.name}: {count} punto{count===1?'':'s'}</title>
          </path>
          {count>0&&<text x={x} y={y}>{count}</text>}
        </g>
      })}
    </svg>
    <div className="dash-v6-map-legend">{shapes.length?<><span><i className="low"/>Sin puntos</span><span><i className="high"/>Más puntos</span></>:<span>CORE aún no entrega geometrías para este país.</span>}<strong>{totalPoints} puntos</strong></div>
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
  const [territoryMap,setTerritoryMap]=useState<TerritoryGeoJson|null>(null);
  const [mapError,setMapError]=useState('');
  // const [core,setCore]=useState<CoreContext>({});
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

  const subdivisionSingular=core.subdivisionSingular||'Subdivisión';
  const subdivisionPlural=core.subdivisionPlural||'Subdivisiones';

  const load=async()=>{
    try{
      const map=await api.territoryMap().catch(e=>{setMapError(msg(e));return null});
      const [t,r,c]=await Promise.all([
        canEditRegions?api.territoryAdmin():api.territory(),
        api.territoryResponsibles().catch(()=>[]),
        api.context().catch(()=>({}))
      ]);
      setTree(t);setResponsibles(r);setTerritoryMap(map);if(map)setMapError('');
      setCore(prev=>({...prev,...c,...(map?{country:map.countryName,countryCode:map.countryCode||'EC',subdivisionType:map.subdivisionType,subdivisionSingular:map.subdivisionSingular,subdivisionPlural:map.subdivisionPlural,territoryCatalogSource:`CORE · ${map.datasetVersion}`}:{})}));setError('');
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
        <div className="ter-country-title"><div className="ter-flag">{core.countryCode==='EC'?'🇪🇨':'🌐'}</div><h3>{core.country||'Cargando país desde CORE…'}</h3><span className="ter-core-badge">{core.territoryCatalogSource||'CORE sin sincronizar'}</span></div>
        <div className="ter-country-stats"><strong>{tree.zones.length}</strong> Zonas <b>•</b> <strong>{tree.regions.length}</strong> Regiones <b>•</b> <strong>{tree.provinces.length}</strong> {subdivisionPlural} <b>•</b> <strong>{tree.companies.length}</strong> Compañías</div>
        <p>El catálogo y la denominación territorial ({subdivisionPlural}) provienen de CORE. SGI: Comando administra únicamente su agrupación operacional en Zonas y Regiones.</p>
      </div>
      <TerritoryOperationalMap tree={tree} geo={territoryMap} error={mapError}/>
      {/* <TerritoryOperationalMap tree={tree} datasetVersion={core.territorialDatasetVersion}/> */}
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
                    {typeFilter!=='COMPANY'&&<div className="ter-detail-block"><div className="ter-detail-title"><strong>{subdivisionPlural} ({regionProvinces.length})</strong><span>Catálogo: {core.territoryCatalogSource||'CORE sin sincronizar'}</span></div><div className="ter-detail-table"><div className="ter-detail-head"><span>Nombre</span><span>Código CORE</span><span>Estado</span></div>{regionProvinces.map(p=><div className="ter-detail-row" key={p.id}><span>{p.name}</span><code>{p.code}</code><span className={`ter-status small ${statusClass(p.status)}`}>{labelStatus(p.status)}</span></div>)}{!regionProvinces.length&&<div className="ter-empty">Sin {subdivisionPlural.toLowerCase()} asignadas.</div>}</div></div>}
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
      <div className="ter-core-picker"><div><strong>{subdivisionPlural}</strong><span>Fuente: {core.territoryCatalogSource||'CORE sin sincronizar'}</span></div><div className="ter-core-grid">{editorAllowedProvinces.map(p=><label key={p.id}><input type="checkbox" checked={editor.provinceCodes.includes(p.code)} onChange={e=>setEditor({...editor,provinceCodes:e.target.checked?[...editor.provinceCodes,p.code]:editor.provinceCodes.filter(x=>x!==p.code)})}/><span>{p.name}</span><code>{p.code}</code></label>)}</div></div>
      {editor.mode==='EDIT'&&editor.status==='ACTIVE'&&<div className="ter-active-note"><ShieldCheck/>Esta entidad está Activa: no puede eliminarse ni cambiar su identidad, pero sí Responsable y {subdivisionPlural}.</div>}
      <div className="ter-modal-actions"><button className="ter-btn ghost" onClick={()=>setEditor(null)}>Cancelar</button><button className="ter-btn primary" disabled={!editor.code.trim()||!editor.name.trim()||(editor.kind==='REGION'&&!editor.zoneId)} onClick={()=>void saveEditor()}>Guardar</button></div>
    </div></div>}

    {audit&&<div className="ter-modal-backdrop" onMouseDown={()=>setAudit(null)}><div className="ter-modal audit" onMouseDown={e=>e.stopPropagation()}><div className="ter-modal-head"><div><h3>Historial · {audit.title}</h3><p>Trazabilidad territorial para auditoría.</p></div><button onClick={()=>setAudit(null)}><X/></button></div><div className="ter-audit-list">{audit.events.map(ev=><div key={ev.id}><Clock3/><span><strong>{ev.eventType.replaceAll('_',' ')}</strong><small>{ev.actorUsername} · {new Date(ev.occurredAt).toLocaleString('es-EC')}</small></span></div>)}{!audit.events.length&&<div className="ter-empty">Sin eventos registrados.</div>}</div></div></div>}
  </div>;
}
