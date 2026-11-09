import {useEffect,useRef,useState,type FormEvent} from 'react';
import {Check,CheckCheck,Clock,CircleAlert,Info,MessageSquare,Send} from 'lucide-react';
import {errorText,obsintApi,type ObsintMessage,type ObsintDeliveryStatus,type ObsintAgentOnShift,type ObsintTarget} from './obsintApi';
import {formatClock} from './obsint';

const delivery:Record<ObsintDeliveryStatus,{label:string;icon:typeof Check}>={
  TEST:{label:'No enviado (prueba)',icon:CircleAlert},PENDING:{label:'Enviando…',icon:Clock},
  SENT:{label:'Enviado',icon:Check},DELIVERED:{label:'Entregado',icon:CheckCheck},
  READ:{label:'Leído',icon:CheckCheck},FAILED:{label:'No entregado',icon:CircleAlert},
};
const reason=(value?:string|null)=>value==='NO_PERSONA_LINK'?'Sin app (sin vínculo IDENT)':value==='EXPIRED'?'Venció el envío':value?.startsWith('SOCKET_400')?'Rechazado por el servicio':value??'';
const readClock=(value:string)=>new Intl.DateTimeFormat('es-EC',{timeZone:'America/Guayaquil',hour:'2-digit',minute:'2-digit',second:'2-digit'}).format(new Date(value));

/** Mensajería unidireccional con selección de agentes y recibos individuales. */
export function ObsintCommPanel({taskId,disabled,onNotice}:{taskId:string;disabled:boolean;onNotice:(text:string)=>void}){
  const [messages,setMessages]=useState<ObsintMessage[]>([]);
  const [agents,setAgents]=useState<ObsintAgentOnShift[]>([]);
  const [agentsError,setAgentsError]=useState('');
  const [loadingAgents,setLoadingAgents]=useState(false);
  const [target,setTarget]=useState<ObsintTarget>('POINT');
  const [selected,setSelected]=useState<string[]>([]);
  const [text,setText]=useState('');
  const [sending,setSending]=useState(false);
  const activeTask=useRef(taskId); activeTask.current=taskId;
  const notices=useRef(onNotice); notices.current=onNotice;
  const agentRequest=useRef(0);
  const refreshing=messages.some(m=>m.recipients.some(r=>['PENDING','SENT','DELIVERED'].includes(r.status)));

  async function loadAgents(){
    const sequence=++agentRequest.current;
    setLoadingAgents(true);
    try{
      const list=await obsintApi.agentsOnShift(taskId);
      if(activeTask.current!==taskId||sequence!==agentRequest.current)return;
      setAgents(list);setAgentsError('');
      setSelected(prev=>prev.filter(id=>list.some(a=>a.employeeId===id&&a.reachable)));
    }catch(e){if(activeTask.current===taskId&&sequence===agentRequest.current)setAgentsError(errorText(e))}
    finally{if(activeTask.current===taskId&&sequence===agentRequest.current)setLoadingAgents(false)}
  }
  useEffect(()=>{
    let current=true;
    setMessages([]);setText('');setSending(false);setAgents([]);setSelected([]);setTarget('POINT');
    void loadAgents();
    void obsintApi.messages(taskId).then(list=>{if(current)setMessages(prev=>[...list,...prev.filter(m=>!list.some(item=>item.id===m.id))])})
      .catch(()=>{if(current)notices.current('No se pudieron cargar los mensajes.')});
    return()=>{current=false;agentRequest.current++};
  },[taskId]);
  useEffect(()=>{
    if(!refreshing)return;
    let current=true,busy=false;
    const timer=setInterval(()=>{
      if(busy)return;busy=true;
      void obsintApi.messages(taskId).then(list=>{if(current)setMessages(prev=>[...list,...prev.filter(m=>!list.some(item=>item.id===m.id))])})
        .catch(()=>{}).finally(()=>{busy=false});
    },10_000);
    return()=>{current=false;clearInterval(timer)};
  },[taskId,refreshing]);

  async function submit(e:FormEvent){
    e.preventDefault();const value=text.trim();
    if(!value)return onNotice('Escribe un mensaje antes de enviarlo.');
    if(target==='AGENTS'&&!selected.length)return onNotice('Selecciona al menos un agente.');
    setSending(true);
    try{
      const message=await obsintApi.sendMessage(taskId,value,target,target==='AGENTS'?selected:undefined);
      if(activeTask.current!==taskId)return;
      setMessages(prev=>prev.some(m=>m.id===message.id)?prev.map(m=>m.id===message.id?message:m):[...prev,message]);setText('');
      const withoutApp=message.recipients.filter(r=>r.failureReason==='NO_PERSONA_LINK').length;
      notices.current(message.deliveryStatus==='TEST'?'Mensaje registrado en modo prueba; no se envió al agente.':
        message.failureReason==='NO_AGENT_ON_SHIFT'?'No hay agente de turno en el punto; use otro canal.':
        withoutApp?withoutApp+' agente(s) sin app; use otro canal.':
        message.deliveryStatus==='FAILED'?'No se pudo entregar el mensaje al agente.':'Mensaje enviado a '+message.counts.total+' agente(s).');
    }catch(e){if(activeTask.current===taskId)notices.current(errorText(e))}
    finally{if(activeTask.current===taskId)setSending(false)}
  }
  return <section className="obs-card obs-comm">
    <div className="obs-comm-head"><h3><MessageSquare size={19}/>Comunicación</h3>
      <span className="obs-comm-test"><Info size={14}/>Mensajes a los agentes de turno · Revisa cada entrega</span>
    </div>
    <div className="obs-messages">
      {!messages.length?<div className="obs-empty-msg"><MessageSquare size={20}/>Aún no hay mensajes registrados.</div>:messages.map(m=>{
        const state=delivery[m.deliveryStatus],Icon=state.icon;
        const label=(m.deliveryStatus==='READ'?'Mensaje leído':state.label)+(m.failureReason==='NO_AGENT_ON_SHIFT'?' · sin agente de turno':'');
        return <div className="obs-msg" key={m.id}>
          <b>{m.operator} <small>{formatClock(m.sentAt)}</small></b>
          <div>{m.text} <Icon size={13} aria-label={label} style={m.deliveryStatus==='READ'?{color:'var(--primary, #2563eb)'}:undefined}/></div>
          <small className="obs-msg-status" role="status" style={m.deliveryStatus==='READ'?{color:'var(--primary, #2563eb)',fontWeight:600}:undefined}>{label}</small>
          {m.deliveryStatus!=='READ'&&m.counts.read>0&&<small className="obs-msg-status">Leído por {m.counts.read} de {m.counts.total} agentes</small>}
          {m.deliveryStatus!=='TEST'&&m.recipients.length>0&&<details style={{marginTop:8}}>
            <summary style={{cursor:'pointer'}}>{m.target==='POINT'?'A todo el punto':'A '+m.counts.total+' agentes'} · Leído {m.counts.read}/{m.counts.total} · Entregado {m.counts.delivered}/{m.counts.total}{m.counts.failed>0?' · No entregado '+m.counts.failed:''}</summary>
            <ul style={{listStyle:'none',padding:0}}>{m.recipients.map(r=>{
              const RecipientIcon=delivery[r.status].icon;
              return <li key={r.employeeId} style={{padding:'8px 0',borderBottom:'1px solid var(--border, #e5e7eb)'}}>
                <b>{r.fullName}</b>{r.postName&&<span> · {r.postName}</span>}
                <div><RecipientIcon size={13}/> {r.status==='READ'&&r.readVia==='OPERATOR_API'?'Leído (confirmado en la app)':delivery[r.status].label}
                  {r.readAt&&<time dateTime={r.readAt}> · {readClock(r.readAt)}</time>}
                  {r.status==='FAILED'&&<span> · {reason(r.failureReason)}</span>}
                </div>
                {r.sentAt&&<small>Enviado {readClock(r.sentAt)}{r.deliveredAt?' · Entregado '+readClock(r.deliveredAt):''}</small>}
              </li>;
            })}</ul>
          </details>}
        </div>;
      })}
    </div>
    <form onSubmit={submit}>
      <label style={{display:'block',margin:'12px 0'}}>Destino
        <select aria-label="Destino del mensaje" value={target} disabled={disabled||sending}
          onClick={()=>{void loadAgents()}} onChange={e=>{setTarget(e.target.value as ObsintTarget);void loadAgents()}} style={{display:'block',width:'100%',padding:8}}>
          <option value="POINT">Todos los agentes del punto</option><option value="AGENTS">Elegir agentes</option>
        </select>
      </label>
      {target==='AGENTS'&&<fieldset disabled={disabled||sending} style={{border:'1px solid #e5e7eb',borderRadius:8,marginBottom:12}}>
        <legend>Agentes de turno</legend>
        {loadingAgents&&<small>Cargando agentes…</small>}
        {agentsError&&<div role="alert">No se pudieron consultar los agentes. <button type="button" onClick={()=>{void loadAgents()}}>Reintentar</button></div>}
        {!loadingAgents&&!agentsError&&!agents.length&&<p>No hay agentes de turno en este punto.</p>}
        {agents.map(a=><label key={a.employeeId} style={{display:'block',padding:'6px 0'}}>
          <input type="checkbox" checked={selected.includes(a.employeeId)} disabled={!a.reachable}
            onChange={e=>setSelected(prev=>e.target.checked?[...prev,a.employeeId]:prev.filter(id=>id!==a.employeeId))}/>{' '}
          {a.online!==null&&<span style={{color:a.online?'#16a34a':'#9ca3af'}} aria-label={a.online?'App conectada':'App desconectada'}>● </span>}
          {a.fullName} · {a.postName}{!a.reachable?' · Sin app (sin vínculo IDENT)':''}
        </label>)}
      </fieldset>}
      <div className="obs-compose">
        <input aria-label="Mensaje al agente" value={text} onChange={e=>setText(e.target.value)} maxLength={500} autoComplete="off" placeholder="Escribe un mensaje al agente..." disabled={disabled||sending}/>
        <button type="submit" disabled={disabled||sending||(target==='AGENTS'&&(!selected.length||loadingAgents||!!agentsError))}><Send size={16}/>Enviar</button>
      </div>
    </form>
  </section>;
}
