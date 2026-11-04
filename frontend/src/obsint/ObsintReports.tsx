import {useEffect, useMemo, useState} from 'react';
import {ArrowLeft, ClipboardList, FileDown, RefreshCw, Search, ShieldCheck} from 'lucide-react';
import {binaryRequest, getUser, request} from '../api';
import {identEnabled, identRoles} from '../security/identAuth';
import {errorText} from './obsintApi';
import './obsint-reports.css';

/** Un "Informe Diario de Consignas": un Punto en un día (yyyy-MM-dd). compliance: 0–1, null si solo hubo respuestas Incierto. */
export type ObsintReport={pointId:string;date:string;client:string;point:string;executed:number;compliance:number|null};

/** Fecha de hoy en Ecuador como yyyy-MM-dd, desplazada days días. */
const ecuadorDay=(days=0)=>{
  const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Guayaquil',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const d=new Date(`${parts}T12:00:00`);d.setDate(d.getDate()+days);
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
};
const showDate=(day:string)=>new Date(`${day}T12:00:00`).toLocaleDateString('es-EC',{day:'numeric',month:'short',year:'numeric'}).replace('.','');
/** Páginas a mostrar: 1 2 3 4 5 … 25, con la actual siempre visible. */
const pageList=(current:number,total:number):(number|'…')[]=>{
  if(total<=7)return Array.from({length:total},(_,i)=>i+1);
  const start=Math.max(1,Math.min(current-2,total-5)),end=Math.min(total-1,start+4);
  const list:(number|'…')[]=[];
  for(let p=start;p<=end;p++)list.push(p);
  if(end<total-1)list.push('…');
  list.push(total);
  if(start>1)list.unshift(...(start>2?[1,'…' as const]:[1]));
  return list;
};

export function canViewObsintReports(){
  return identEnabled()
    ?identRoles().some(role=>role==='PRESIDENTE'||role==='DIRECTOR_NACIONAL'||role==='DIRECTOR_OPERACIONES_NACIONAL')
    :getUser()==='presidente'||getUser()==='dnacional'||getUser()==='don';
}

/** Reportes OBSINT (diapositiva 12): un informe diario por Cliente y Punto, con filtros y descarga en PDF (diapositiva 13). */
export default function ObsintReports({onBack}:{onBack:()=>void}){
  const initial={client:'',point:'',from:ecuadorDay(-29),to:ecuadorDay()};
  const [reports,setReports]=useState<ObsintReport[]>([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [client,setClient]=useState('');
  const [point,setPoint]=useState('');
  const [from,setFrom]=useState(initial.from);
  const [to,setTo]=useState(initial.to);
  const [applied,setApplied]=useState(initial);
  const [page,setPage]=useState(1);
  const [pageSize,setPageSize]=useState(10);
  const [downloading,setDownloading]=useState('');

  // El rango de fechas se consulta al servidor; cliente y punto se filtran aquí.
  useEffect(()=>{
    if(!canViewObsintReports()){setError('No tiene acceso a los reportes OBSINT.');setLoading(false);return}
    let active=true;
    setLoading(true);
    const query=new URLSearchParams({from:applied.from,to:applied.to});
    void request<ObsintReport[]>(`/api/obsint/reports?${query}`).then(rows=>{if(active){setReports(rows);setError('')}})
      .catch(cause=>{if(active)setError(errorText(cause))}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[applied.from,applied.to]);

  const clients=useMemo(()=>[...new Set(reports.map(report=>report.client))].sort((a,b)=>a.localeCompare(b,'es')),[reports]);
  const points=useMemo(()=>[...new Set(reports.filter(report=>!client||report.client===client).map(report=>report.point))].sort((a,b)=>a.localeCompare(b,'es')),[reports,client]);
  const filtered=useMemo(()=>reports.filter(report=>(!applied.client||report.client===applied.client)&&(!applied.point||report.point===applied.point)),[reports,applied]);
  const totalPages=Math.max(1,Math.ceil(filtered.length/pageSize));
  const currentPage=Math.min(page,totalPages);
  const rows=filtered.slice((currentPage-1)*pageSize,currentPage*pageSize);
  const rangeError=!from||!to?'Indique la fecha desde y hasta.':from>to?'La fecha inicial no puede ser posterior a la final.':'';

  function clear(){setClient('');setPoint('');setFrom(initial.from);setTo(initial.to);setApplied(initial);setPage(1)}
  async function download(report:ObsintReport){
    const key=`${report.pointId}:${report.date}`;
    setDownloading(key);setError('');
    try{
      const blob=await binaryRequest(`/api/obsint/reports/points/${encodeURIComponent(report.pointId)}/days/${report.date}/pdf`);
      const url=URL.createObjectURL(blob);
      const link=document.createElement('a');link.href=url;link.download=`obsint-${report.point.replace(/[^\p{L}\p{N}]+/gu,'-').toLowerCase()}-${report.date}.pdf`;link.click();
      window.setTimeout(()=>URL.revokeObjectURL(url),60_000);
    }catch(cause){setError(errorText(cause))}finally{setDownloading('')}
  }

  if(!canViewObsintReports())return <div className="obs-page"><div className="obs-error">No tiene acceso a los reportes OBSINT.</div><button className="obs-reports-back" onClick={onBack}>Volver a Consola</button></div>;
  return <div className="obs-page obs-reports">
    <nav className="obs-breadcrumbs"><button type="button" onClick={onBack}><ArrowLeft size={15}/>Consola</button><span>/</span><small>OBSINT</small><span>/</span><small>Reportes</small></nav>
    <div className="obs-reports-titlebar">
      <div className="obs-hero"><h2>Reportes OBSINT</h2><p>Listado de reportes generados por los monitores de OBSINT.</p><small>Consulte, filtre y descargue los reportes en formato PDF.</small></div>
      <div className="obs-reports-title-actions"><button type="button" disabled title="Modelos de Compañía no disponible"><ClipboardList size={15}/>Modelos de Compañía</button><button type="button" disabled title="Estándares Cajamarca no disponible"><ShieldCheck size={15}/>Estándares Cajamarca</button></div>
    </div>
    <section className="obs-card obs-reports-filters">
      <label>Cliente<select value={client} onChange={e=>{setClient(e.target.value);setPoint('')}}><option value="">Todos los clientes</option>{clients.map(value=><option key={value} value={value}>{value}</option>)}</select></label>
      <label>Punto<select value={point} onChange={e=>setPoint(e.target.value)}><option value="">Todos los puntos</option>{points.map(value=><option key={value} value={value}>{value}</option>)}</select></label>
      <label>Fecha desde<input type="date" value={from} max={to||undefined} onChange={e=>setFrom(e.target.value)}/></label>
      <label>Fecha hasta<input type="date" value={to} min={from||undefined} onChange={e=>setTo(e.target.value)}/></label>
      <button className="obs-reports-apply" disabled={Boolean(rangeError)} onClick={()=>{setApplied({client,point,from,to});setPage(1)}}><Search size={15}/>Aplicar filtros</button>
      <button className="obs-reports-clear" onClick={clear}><RefreshCw size={15}/>Limpiar</button>
    </section>
    {rangeError&&<div className="obs-error">{rangeError}</div>}
    {error&&<div className="obs-error" role="alert">{error}</div>}
    <section className="obs-card obs-reports-list">
      <header><h3>Reportes ({filtered.length})</h3><p>Lista de reportes generados en base a la ejecución de consignas OBSINT.</p></header>
      <div className="obs-reports-table-wrap"><table><thead><tr><th>#</th><th>Cliente</th><th>Punto</th><th>Fecha</th><th>Archivo</th></tr></thead><tbody>
        {loading?<tr><td colSpan={5} className="obs-reports-empty">Cargando reportes…</td></tr>
          :rows.length?rows.map((report,index)=>{const key=`${report.pointId}:${report.date}`;return <tr key={key}>
            <td>{(currentPage-1)*pageSize+index+1}</td><td><strong>{report.client}</strong></td><td>{report.point}</td><td>{showDate(report.date)}</td>
            <td><button className="obs-reports-download" disabled={Boolean(downloading)} onClick={()=>void download(report)}><FileDown size={15}/>{downloading===key?'Descargando…':'Descargar PDF'}</button></td></tr>})
          :<tr><td colSpan={5} className="obs-reports-empty">No hay reportes que coincidan con los filtros.</td></tr>}
      </tbody></table></div>
      <footer><span>Mostrando {filtered.length?(currentPage-1)*pageSize+1:0} a {Math.min(currentPage*pageSize,filtered.length)} de {filtered.length} reportes</span>
        <div><button aria-label="Página anterior" disabled={currentPage===1} onClick={()=>setPage(currentPage-1)}>‹</button>
          {pageList(currentPage,totalPages).map((p,i)=>p==='…'?<span key={`gap-${i}`} className="obs-reports-gap">…</span>
            :<button key={p} className={p===currentPage?'active':''} aria-current={p===currentPage?'page':undefined} onClick={()=>setPage(p)}>{p}</button>)}
          <button aria-label="Página siguiente" disabled={currentPage===totalPages} onClick={()=>setPage(currentPage+1)}>›</button>
          <select aria-label="Reportes por página" value={pageSize} onChange={e=>{setPageSize(Number(e.target.value));setPage(1)}}><option value={10}>10 por página</option><option value={20}>20 por página</option><option value={50}>50 por página</option></select></div>
      </footer>
    </section>
  </div>;
}
