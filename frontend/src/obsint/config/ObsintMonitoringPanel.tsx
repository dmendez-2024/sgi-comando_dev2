import {useEffect, useState} from 'react';
import {LockKeyhole, Save, UsersRound} from 'lucide-react';
import {errorText, obsintConfigApi, type ObsintMonitorOption, type ObsintMonitoring} from '../obsintApi';
import './obsint-monitoring.css';

const formatDate=(value:string|null)=>value?new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)):'';
const sameSet=(a:string[],b:string[])=>a.length===b.length&&a.every(x=>b.includes(x));

/** Monitoristas del Punto: el backend define el alcance y marca los registros confidenciales. */
export function ObsintMonitoringPanel({pointId}:{pointId:string}){
  const [current,setCurrent]=useState<ObsintMonitoring|null>(null);
  const [monitors,setMonitors]=useState<ObsintMonitorOption[]>([]);
  const [selected,setSelected]=useState<string[]>([]);
  const [companyFilter,setCompanyFilter]=useState('');
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');

  const reset=(m:ObsintMonitoring)=>{setCurrent(m);setSelected(m.monitors.map(x=>x.employeeId))};
  useEffect(()=>{
    let alive=true;
    setCompanyFilter('');
    Promise.all([obsintConfigApi.monitoring(pointId),obsintConfigApi.monitors(pointId)])
      .then(([m,list])=>{if(!alive)return;reset(m);setMonitors(list)})
      .catch(e=>{if(alive)setError(errorText(e))});
    return()=>{alive=false};
  },[pointId]);
  useEffect(()=>{if(!notice)return;const id=setTimeout(()=>setNotice(''),3200);return()=>clearTimeout(id)},[notice]);

  const companyName=current?.companyName??'la Compañía del Punto';
  const protectedIds=current?.monitors.filter(m=>m.confidential).map(m=>m.employeeId)??[];
  const dirty=Boolean(current)&&!sameSet(selected,current?.monitors.map(x=>x.employeeId)??[]);
  const toggle=(id:string)=>setSelected(prev=>prev.includes(id)?prev.filter(x=>x!==id):[...prev,id]);
  /** Los ya elegidos que ya no están en la lista siguen visibles; la marca confidencial del registro asignado prevalece. */
  const assigned=new Map((current?.monitors??[]).map(m=>[m.employeeId,m]));
  const options=[...monitors.map(m=>assigned.get(m.employeeId)?.confidential?{...m,confidential:true}:m),
    ...(current?.monitors??[]).filter(m=>!monitors.some(o=>o.employeeId===m.employeeId))];
  const companies=Array.from(new Map(options.filter(m=>!m.confidential&&m.companyId&&m.companyName)
    .map(m=>[m.companyId!,m.companyName!] as const)).entries()).sort((a,b)=>a[1].localeCompare(b[1],'es'));
  const multipleCompanies=companies.length>1;
  const visibleOptions=companyFilter?options.filter(m=>m.confidential||m.companyId===companyFilter):options;

  async function save(){
    setSaving(true);setError('');
    try{reset(await obsintConfigApi.saveMonitoring(pointId,{employeeIds:Array.from(new Set([...selected,...protectedIds]))}));setNotice('Monitoristas guardados.')}
    catch(e){setError(errorText(e))}
    finally{setSaving(false)}
  }

  return <section className="obs-mon">
    <header>
      <div><h3><UsersRound size={17}/>Monitoristas OBSINT <small>{selected.length} seleccionado{selected.length===1?'':'s'}</small></h3>
        <p>{multipleCompanies
          ?'Elija quiénes vigilan este Punto por cámara entre los monitoristas de las Compañías de su alcance.'
          :`Elija quiénes vigilan este Punto por cámara, entre los monitores de ${companyName} (la Compañía asignada en Servicios).`}</p></div>
      <button type="button" className="obs-cfg-primary" disabled={!dirty||saving} onClick={()=>void save()}><Save size={15}/>{saving?'Guardando…':'Guardar'}</button>
    </header>
    {multipleCompanies&&<label className="obs-mon-filter"><span>Compañía</span><select value={companyFilter} onChange={e=>setCompanyFilter(e.target.value)}>
      <option value="">Todas</option>{companies.map(([id,name])=><option key={id} value={id}>{name}</option>)}
    </select></label>}
    {options.length
      ?visibleOptions.length?<div className="obs-mon-list">{visibleOptions.map(m=>{const checked=selected.includes(m.employeeId);
        return <label key={m.employeeId} className={`${checked?'checked':''}${m.confidential?' confidential':''}`.trim()} title={m.confidential?'Monitorista de otra Compañía':undefined}>
          <input type="checkbox" checked={checked} disabled={m.confidential||saving} onChange={()=>{if(!m.confidential)toggle(m.employeeId)}}/>
          <span><b>{m.confidential?<><LockKeyhole size={14} aria-hidden="true"/>Confidencial</>:m.name}</b>
            {(m.cargo||m.confidential||multipleCompanies)&&<small>{[m.cargo,m.confidential?'Confidencial':multipleCompanies?m.companyName:null].filter(Boolean).join(' · ')}</small>}</span>
        </label>})}</div>:<p className="obs-mon-rule">No hay monitoristas de esta Compañía.</p>
      :current&&<p className="obs-mon-rule"><b>{companyName} no tiene empleados con cargo de monitoreo.</b></p>}
    <p className="obs-mon-rule">{selected.length
      ?`Lo atiende cualquiera de los seleccionados que esté de turno. Si ninguno está de turno, cualquier monitor de turno de ${companyName}.`
      :`Sin monitoristas seleccionados: lo atiende cualquier monitor de turno de ${companyName}.`}</p>
    {current?.updatedBy&&<small className="obs-mon-meta">Actualizado por {current.updatedBy} · {formatDate(current.updatedAt)}</small>}
    {error&&<div className="obs-error">{error}</div>}
    {notice&&<div className="obs-toast" role="status">{notice}</div>}
  </section>;
}
