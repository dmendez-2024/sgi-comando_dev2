import {useEffect, useMemo, useState} from 'react';
import {ArrowLeft, FileDown, RefreshCw, Search} from 'lucide-react';
import {binaryRequest, getUser, request} from '../api';
import {identEnabled, identRoles} from '../security/identAuth';
import {errorText} from './obsintApi';
import './obsint-reports.css';

export type ObsintReport={caseId:string;pointId:string;code:string;client:string;point:string;completedAt:string;tasks:number;answered:number;timedOut:number};
const reportDate=(value:string)=>{
  const parts=new Intl.DateTimeFormat('es-EC',{timeZone:'America/Guayaquil',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(value));
  const part=(type:string)=>parts.find(item=>item.type===type)?.value??'';
  return `${part('year')}-${part('month')}-${part('day')}`;
};

export function canViewObsintReports(){
  return identEnabled()
    ?identRoles().some(role=>role==='PRESIDENTE'||role==='DIRECTOR_NACIONAL')
    :getUser()==='presidente'||getUser()==='dnacional';
}

export default function ObsintReports({onBack}:{onBack:()=>void}){
  const [reports,setReports]=useState<ObsintReport[]>([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [client,setClient]=useState('');
  const [point,setPoint]=useState('');
  const [from,setFrom]=useState('');
  const [to,setTo]=useState('');
  const [applied,setApplied]=useState({client:'',point:'',from:'',to:''});
  const [page,setPage]=useState(1);
  const [pageSize,setPageSize]=useState(10);
  const [downloading,setDownloading]=useState('');

  useEffect(()=>{
    if(!canViewObsintReports()){setError('No tiene acceso a los reportes OBSINT.');setLoading(false);return}
    let active=true;
    void request<ObsintReport[]>('/api/obsint/reports').then(rows=>{if(active)setReports(rows)})
      .catch(cause=>{if(active)setError(errorText(cause))}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[]);

  const clients=useMemo(()=>[...new Set(reports.map(report=>report.client))].sort((a,b)=>a.localeCompare(b,'es')),[reports]);
  const points=useMemo(()=>[...new Set(reports.filter(report=>!client||report.client===client).map(report=>report.point))].sort((a,b)=>a.localeCompare(b,'es')),[reports,client]);
  const filtered=useMemo(()=>reports.filter(report=>{
    const date=reportDate(report.completedAt);
    return (!applied.client||report.client===applied.client)&&(!applied.point||report.point===applied.point)
      &&(!applied.from||date>=applied.from)&&(!applied.to||date<=applied.to);
  }),[reports,applied]);
  const totalPages=Math.max(1,Math.ceil(filtered.length/pageSize));
  const currentPage=Math.min(page,totalPages);
  const rows=filtered.slice((currentPage-1)*pageSize,currentPage*pageSize);
  const rangeError=from&&to&&from>to?'La fecha inicial no puede ser posterior a la final.':'';

  function clear(){setClient('');setPoint('');setFrom('');setTo('');setApplied({client:'',point:'',from:'',to:''});setPage(1)}
  async function download(report:ObsintReport){
    const key=`${report.caseId}:${report.pointId}`;
    setDownloading(key);setError('');
    try{
      const blob=await binaryRequest(`/api/obsint/reports/${encodeURIComponent(report.caseId)}/points/${encodeURIComponent(report.pointId)}/pdf`);
      const url=URL.createObjectURL(blob);
      const link=document.createElement('a');link.href=url;link.download=`obsint-${report.code}-${report.pointId.slice(0,8)}.pdf`;link.click();
      window.setTimeout(()=>URL.revokeObjectURL(url),60_000);
    }catch(cause){setError(errorText(cause))}finally{setDownloading('')}
  }

  if(!canViewObsintReports())return <div className="obs-page"><div className="obs-error">No tiene acceso a los reportes OBSINT.</div><button className="obs-reports-back" onClick={onBack}>Volver a Consola</button></div>;
  return <div className="obs-page obs-reports">
    <nav className="obs-breadcrumbs"><button type="button" onClick={onBack}><ArrowLeft size={15}/>Volver a Consola OBSINT</button><span>/</span><small>Reportes</small></nav>
    <div className="obs-hero"><h2>Reportes OBSINT</h2><p>Listado de reportes generados por los monitores de OBSINT.</p><small>Consulte, filtre y descargue los reportes en formato PDF.</small></div>
    <section className="obs-card obs-reports-filters">
      <label>Cliente<select value={client} onChange={e=>{setClient(e.target.value);setPoint('')}}><option value="">Todos los clientes</option>{clients.map(value=><option key={value} value={value}>{value}</option>)}</select></label>
      <label>Punto<select value={point} onChange={e=>setPoint(e.target.value)}><option value="">Todos los puntos</option>{points.map(value=><option key={value} value={value}>{value}</option>)}</select></label>
      <label>Fecha desde<input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label>
      <label>Fecha hasta<input type="date" value={to} onChange={e=>setTo(e.target.value)}/></label>
      <button className="obs-reports-apply" disabled={Boolean(rangeError)} onClick={()=>{setApplied({client,point,from,to});setPage(1)}}><Search size={15}/>Aplicar filtros</button>
      <button className="obs-reports-clear" onClick={clear}><RefreshCw size={15}/>Limpiar</button>
    </section>
    {rangeError&&<div className="obs-error">{rangeError}</div>}
    {error&&<div className="obs-error" role="alert">{error}</div>}
    <section className="obs-card obs-reports-list">
      <header><h3>Reportes ({filtered.length})</h3><p>Lista de reportes generados en base a la ejecución de consignas OBSINT.</p></header>
      <div className="obs-reports-table-wrap"><table><thead><tr><th>#</th><th>Cliente</th><th>Punto</th><th>Fecha</th><th>Archivo</th></tr></thead><tbody>
        {loading?<tr><td colSpan={5} className="obs-reports-empty">Cargando reportes…</td></tr>
          :rows.length?rows.map((report,index)=><tr key={`${report.caseId}:${report.pointId}`}><td>{(currentPage-1)*pageSize+index+1}</td><td><strong>{report.client}</strong></td><td>{report.point}</td><td>{new Date(report.completedAt).toLocaleDateString('es-EC',{day:'2-digit',month:'short',year:'numeric',timeZone:'America/Guayaquil'})}</td><td><button className="obs-reports-download" disabled={Boolean(downloading)} onClick={()=>void download(report)}><FileDown size={15}/>{downloading===`${report.caseId}:${report.pointId}`?'Descargando…':'Descargar PDF'}</button></td></tr>)
          :<tr><td colSpan={5} className="obs-reports-empty">No hay reportes que coincidan con los filtros.</td></tr>}
      </tbody></table></div>
      <footer><span>Mostrando {filtered.length?(currentPage-1)*pageSize+1:0} a {Math.min(currentPage*pageSize,filtered.length)} de {filtered.length} reportes</span>
        <div><button aria-label="Página anterior" disabled={currentPage===1} onClick={()=>setPage(currentPage-1)}>‹</button><strong>{currentPage}</strong><span>de {totalPages}</span><button aria-label="Página siguiente" disabled={currentPage===totalPages} onClick={()=>setPage(currentPage+1)}>›</button><select aria-label="Reportes por página" value={pageSize} onChange={e=>{setPageSize(Number(e.target.value));setPage(1)}}><option value={10}>10 por página</option><option value={20}>20 por página</option><option value={50}>50 por página</option></select></div>
      </footer>
    </section>
  </div>;
}
