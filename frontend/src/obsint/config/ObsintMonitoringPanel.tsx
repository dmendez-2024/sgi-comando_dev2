import {useEffect, useState} from 'react';
import {Save, UsersRound} from 'lucide-react';
import {errorText, obsintConfigApi, type ObsintMonitorOption, type ObsintMonitoring} from '../obsintApi';
import './obsint-monitoring.css';

const formatDate=(value:string|null)=>value?new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)):'';
const sameSet=(a:string[],b:string[])=>a.length===b.length&&a.every(x=>b.includes(x));

/**
 * Monitoristas del Punto, en la pestaña OBSINT: uno o más, de la Compañía del Punto (la que se asigna en Servicios).
 * Sin ninguno (o si ninguno está de turno), lo atiende cualquier monitor de turno de esa Compañía.
 */
export function ObsintMonitoringPanel({pointId}:{pointId:string}){
  const [current,setCurrent]=useState<ObsintMonitoring|null>(null);
  const [monitors,setMonitors]=useState<ObsintMonitorOption[]>([]);
  const [selected,setSelected]=useState<string[]>([]);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');

  const reset=(m:ObsintMonitoring)=>{setCurrent(m);setSelected(m.monitors.map(x=>x.employeeId))};
  useEffect(()=>{
    let alive=true;
    Promise.all([obsintConfigApi.monitoring(pointId),obsintConfigApi.monitors(pointId)])
      .then(([m,list])=>{if(!alive)return;reset(m);setMonitors(list)})
      .catch(e=>{if(alive)setError(errorText(e))});
    return()=>{alive=false};
  },[pointId]);
  useEffect(()=>{if(!notice)return;const id=setTimeout(()=>setNotice(''),3200);return()=>clearTimeout(id)},[notice]);

  const companyName=current?.companyName??'la Compañía del Punto';
  const dirty=Boolean(current)&&!sameSet(selected,current?.monitors.map(x=>x.employeeId)??[]);
  const toggle=(id:string)=>setSelected(prev=>prev.includes(id)?prev.filter(x=>x!==id):[...prev,id]);
  /** Los ya elegidos que ya no están en la lista (dejaron la Compañía o el cargo) se muestran para poder quitarlos. */
  const options=[...monitors,...(current?.monitors??[]).filter(m=>!monitors.some(o=>o.employeeId===m.employeeId))];

  async function save(){
    setSaving(true);setError('');
    try{reset(await obsintConfigApi.saveMonitoring(pointId,{employeeIds:selected}));setNotice('Monitoristas guardados.')}
    catch(e){setError(errorText(e))}
    finally{setSaving(false)}
  }

  return <section className="obs-mon">
    <header>
      <div><h3><UsersRound size={17}/>Monitoristas OBSINT <small>{selected.length} seleccionado{selected.length===1?'':'s'}</small></h3>
        <p>Elija quiénes vigilan este Punto por cámara, entre los monitores de {companyName} (la Compañía asignada en Servicios).</p></div>
      <button type="button" className="obs-cfg-primary" disabled={!dirty||saving} onClick={()=>void save()}><Save size={15}/>{saving?'Guardando…':'Guardar'}</button>
    </header>
    {options.length
      ?<div className="obs-mon-list">{options.map(m=>{const checked=selected.includes(m.employeeId);
        return <label key={m.employeeId} className={checked?'checked':''}><input type="checkbox" checked={checked} onChange={()=>toggle(m.employeeId)}/>
          <span><b>{m.name}</b>{m.cargo&&<small>{m.cargo}</small>}</span></label>})}</div>
      :current&&<p className="obs-mon-rule"><b>{companyName} no tiene empleados con cargo de monitoreo.</b></p>}
    <p className="obs-mon-rule">{selected.length
      ?`Lo atiende cualquiera de los seleccionados que esté de turno. Si ninguno está de turno, cualquier monitor de turno de ${companyName}.`
      :`Sin monitoristas seleccionados: lo atiende cualquier monitor de turno de ${companyName}.`}</p>
    {current?.updatedBy&&<small className="obs-mon-meta">Actualizado por {current.updatedBy} · {formatDate(current.updatedAt)}</small>}
    {error&&<div className="obs-error">{error}</div>}
    {notice&&<div className="obs-toast" role="status">{notice}</div>}
  </section>;
}
