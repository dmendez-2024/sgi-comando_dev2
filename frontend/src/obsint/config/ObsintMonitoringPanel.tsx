import {useEffect, useState} from 'react';
import {Building2, Save, UserRound} from 'lucide-react';
import {errorText, obsintConfigApi, type ObsintCompanyOption, type ObsintMonitorOption, type ObsintMonitoring} from '../obsintApi';
import './obsint-monitoring.css';

const formatDate=(value:string|null)=>value?new Intl.DateTimeFormat('es-EC',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)):'';

/**
 * Monitoreo del Punto, en la pestaña OBSINT: qué Compañía lo monitorea y quién es el monitorista responsable.
 * La orden OBSINT llega a Kaibil; aquí se puede pasar a otra Compañía. Sin responsable (o si ni él ni el suplente están de turno),
 * lo atiende cualquier monitor de turno de esa Compañía.
 */
export function ObsintMonitoringPanel({pointId}:{pointId:string}){
  const [current,setCurrent]=useState<ObsintMonitoring|null>(null);
  const [companies,setCompanies]=useState<ObsintCompanyOption[]>([]);
  const [monitors,setMonitors]=useState<ObsintMonitorOption[]>([]);
  const [companyId,setCompanyId]=useState('');
  const [responsible,setResponsible]=useState('');
  const [backup,setBackup]=useState('');
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');

  const reset=(m:ObsintMonitoring)=>{setCurrent(m);setCompanyId(m.companyId??'');setResponsible(m.responsible?.employeeId??'');setBackup(m.backup?.employeeId??'')};
  useEffect(()=>{
    let alive=true;
    Promise.all([obsintConfigApi.monitoring(pointId),obsintConfigApi.monitoringCompanies(pointId)])
      .then(([m,list])=>{if(!alive)return;reset(m);setCompanies(list)})
      .catch(e=>{if(alive)setError(errorText(e))});
    return()=>{alive=false};
  },[pointId]);
  useEffect(()=>{
    if(!companyId){setMonitors([]);return}
    let alive=true;
    obsintConfigApi.monitors(pointId,companyId).then(list=>{if(alive)setMonitors(list)}).catch(e=>{if(alive)setError(errorText(e))});
    return()=>{alive=false};
  },[pointId,companyId]);
  useEffect(()=>{if(!notice)return;const id=setTimeout(()=>setNotice(''),3200);return()=>clearTimeout(id)},[notice]);

  const companyName=companies.find(c=>c.id===companyId)?.name??current?.companyName??'la Compañía';
  const dirty=Boolean(current)&&(companyId!==(current?.companyId??'')||responsible!==(current?.responsible?.employeeId??'')||backup!==(current?.backup?.employeeId??''));
  /** Al cambiar de Compañía, el responsable y el suplente de la anterior ya no aplican. */
  const chooseCompany=(id:string)=>{setCompanyId(id);setResponsible('');setBackup('')};
  const chooseResponsible=(id:string)=>{setResponsible(id);if(!id||id===backup)setBackup('')};

  async function save(){
    if(!companyId){setError('Seleccione la Compañía que monitorea el Punto.');return}
    setSaving(true);setError('');
    try{reset(await obsintConfigApi.saveMonitoring(pointId,{companyId,responsibleEmployeeId:responsible||null,backupEmployeeId:backup||null}));setNotice('Monitoreo guardado.')}
    catch(e){setError(errorText(e))}
    finally{setSaving(false)}
  }

  return <section className="obs-mon">
    <header>
      <div><h3><Building2 size={17}/>Monitoreo OBSINT</h3>
        <p>La orden OBSINT llega a Kaibil. Puede pasar el monitoreo a otra Compañía y elegir quién es el responsable.</p></div>
      <button type="button" className="obs-cfg-primary" disabled={!dirty||saving} onClick={()=>void save()}><Save size={15}/>{saving?'Guardando…':'Guardar'}</button>
    </header>
    <div className="obs-mon-fields">
      <label><span>Compañía de monitoreo</span>
        <select value={companyId} onChange={e=>chooseCompany(e.target.value)} disabled={!current}>
          {!companyId&&<option value="">Seleccione…</option>}
          {companies.map(c=><option key={c.id} value={c.id}>{c.name}{c.kaibil?' (por defecto)':''}</option>)}
        </select></label>
      <label><span><UserRound size={13}/>Monitorista responsable</span>
        <select value={responsible} onChange={e=>chooseResponsible(e.target.value)} disabled={!companyId}>
          <option value="">Sin responsable</option>
          {monitors.map(m=><option key={m.employeeId} value={m.employeeId}>{m.name}{m.cargo?` · ${m.cargo}`:''}</option>)}
        </select></label>
      <label><span>Suplente (opcional)</span>
        <select value={backup} onChange={e=>setBackup(e.target.value)} disabled={!responsible}>
          <option value="">Sin suplente</option>
          {monitors.filter(m=>m.employeeId!==responsible).map(m=><option key={m.employeeId} value={m.employeeId}>{m.name}{m.cargo?` · ${m.cargo}`:''}</option>)}
        </select></label>
    </div>
    <p className="obs-mon-rule">{responsible
      ?`Lo atiende el responsable${backup?' o el suplente':''} cuando está de turno. Si no hay ninguno de turno, cualquier monitor de turno de ${companyName}.`
      :`Lo atiende cualquier monitor de turno de ${companyName}.`}
      {companyId&&!monitors.length&&<b> {companyName} no tiene empleados con cargo de monitoreo.</b>}</p>
    {current&&<small className="obs-mon-meta">{current.configured?`Actualizado por ${current.updatedBy} · ${formatDate(current.updatedAt)}`:'Sin cambios desde que llegó la orden.'}</small>}
    {error&&<div className="obs-error">{error}</div>}
    {notice&&<div className="obs-toast" role="status">{notice}</div>}
  </section>;
}
