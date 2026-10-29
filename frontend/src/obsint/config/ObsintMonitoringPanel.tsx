import {useEffect, useState} from 'react';
import {Save, UserRound} from 'lucide-react';
import {errorText, obsintConfigApi, type ObsintMonitorOption, type ObsintMonitoring} from '../obsintApi';
import './obsint-monitoring.css';

const formatDate=(value:string|null)=>value?new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)):'';

/**
 * Monitorista del Punto, en la pestaña OBSINT: responsable y suplente, de la Compañía del Punto (la que se asigna en Servicios).
 * Sin responsable (o si ni él ni el suplente están de turno), lo atiende cualquier monitor de turno de esa Compañía.
 */
export function ObsintMonitoringPanel({pointId}:{pointId:string}){
  const [current,setCurrent]=useState<ObsintMonitoring|null>(null);
  const [monitors,setMonitors]=useState<ObsintMonitorOption[]>([]);
  const [responsible,setResponsible]=useState('');
  const [backup,setBackup]=useState('');
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');

  const reset=(m:ObsintMonitoring)=>{setCurrent(m);setResponsible(m.responsible?.employeeId??'');setBackup(m.backup?.employeeId??'')};
  useEffect(()=>{
    let alive=true;
    Promise.all([obsintConfigApi.monitoring(pointId),obsintConfigApi.monitors(pointId)])
      .then(([m,list])=>{if(!alive)return;reset(m);setMonitors(list)})
      .catch(e=>{if(alive)setError(errorText(e))});
    return()=>{alive=false};
  },[pointId]);
  useEffect(()=>{if(!notice)return;const id=setTimeout(()=>setNotice(''),3200);return()=>clearTimeout(id)},[notice]);

  const companyName=current?.companyName??'la Compañía del Punto';
  const dirty=Boolean(current)&&(responsible!==(current?.responsible?.employeeId??'')||backup!==(current?.backup?.employeeId??''));
  const chooseResponsible=(id:string)=>{setResponsible(id);if(!id||id===backup)setBackup('')};
  const label=(m:ObsintMonitorOption)=>`${m.name}${m.cargo?` · ${m.cargo}`:''}`;

  async function save(){
    setSaving(true);setError('');
    try{reset(await obsintConfigApi.saveMonitoring(pointId,{responsibleEmployeeId:responsible||null,backupEmployeeId:backup||null}));setNotice('Monitorista guardado.')}
    catch(e){setError(errorText(e))}
    finally{setSaving(false)}
  }

  return <section className="obs-mon">
    <header>
      <div><h3><UserRound size={17}/>Monitorista OBSINT</h3>
        <p>Elija quién vigila este Punto por cámara, entre los monitores de {companyName} (la Compañía asignada en Servicios).</p></div>
      <button type="button" className="obs-cfg-primary" disabled={!dirty||saving} onClick={()=>void save()}><Save size={15}/>{saving?'Guardando…':'Guardar'}</button>
    </header>
    <div className="obs-mon-fields two">
      <label><span>Monitorista responsable</span>
        <select value={responsible} onChange={e=>chooseResponsible(e.target.value)} disabled={!current||!monitors.length}>
          <option value="">Sin responsable</option>
          {monitors.map(m=><option key={m.employeeId} value={m.employeeId}>{label(m)}</option>)}
        </select></label>
      <label><span>Suplente (opcional)</span>
        <select value={backup} onChange={e=>setBackup(e.target.value)} disabled={!responsible}>
          <option value="">Sin suplente</option>
          {monitors.filter(m=>m.employeeId!==responsible).map(m=><option key={m.employeeId} value={m.employeeId}>{label(m)}</option>)}
        </select></label>
    </div>
    <p className="obs-mon-rule">{responsible
      ?`Lo atiende el responsable${backup?' o el suplente':''} cuando está de turno. Si no hay ninguno de turno, cualquier monitor de turno de ${companyName}.`
      :`Sin responsable: lo atiende cualquier monitor de turno de ${companyName}.`}
      {current&&!monitors.length&&<b> {companyName} no tiene empleados con cargo de monitoreo.</b>}</p>
    {current?.configured&&<small className="obs-mon-meta">Actualizado por {current.updatedBy} · {formatDate(current.updatedAt)}</small>}
    {error&&<div className="obs-error">{error}</div>}
    {notice&&<div className="obs-toast" role="status">{notice}</div>}
  </section>;
}
