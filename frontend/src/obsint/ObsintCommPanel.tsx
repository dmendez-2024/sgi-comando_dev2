import {useEffect, useState, type FormEvent} from 'react';
import {CheckCheck, CircleAlert, Info, MessageSquare, Send} from 'lucide-react';
import {errorText, obsintApi, type ObsintMessage} from './obsintApi';
import {formatClock} from './obsint';

/** Canal unidireccional monitorista → agente en sitio. */
export function ObsintCommPanel({taskId,disabled,onNotice}:{taskId:string;disabled:boolean;onNotice:(text:string)=>void}){
  const [messages,setMessages]=useState<ObsintMessage[]>([]);
  const [text,setText]=useState('');
  const [sending,setSending]=useState(false);

  useEffect(()=>{
    let current=true;
    setMessages([]);
    void obsintApi.messages(taskId).then(list=>{if(current)setMessages(list)}).catch(()=>{if(current)onNotice('No se pudieron cargar los mensajes.')});
    return()=>{current=false};
  },[taskId]);

  async function submit(e:FormEvent){
    e.preventDefault();
    const value=text.trim();
    if(!value)return onNotice('Escribe un mensaje antes de enviarlo.');
    setSending(true);
    try{const message=await obsintApi.sendMessage(taskId,value);setMessages(prev=>[...prev,message]);setText('');onNotice(message.deliveryStatus==='TEST'?'Mensaje registrado en modo prueba; no se envió al agente.':message.deliveryStatus==='SENT'?'Mensaje entregado al agente.':'No se pudo entregar el mensaje al agente.');}
    catch(error){onNotice(errorText(error))}
    finally{setSending(false)}
  }

  return <section className="obs-card obs-comm">
    <div className="obs-comm-head">
      <h3><MessageSquare size={19}/>Comunicación</h3>
      <span className="obs-comm-test"><i className="obs-dot"/><b>Modo prueba</b> · Los mensajes se registran, pero no llegan al agente <span title="El envío real a SGI Operador aún no está disponible."><Info size={14}/></span></span>
    </div>
    <div className="obs-messages">
      {messages.length===0
        ?<div className="obs-empty-msg"><MessageSquare size={20}/>Aún no hay mensajes registrados.</div>
        :messages.map(m=><div className="obs-msg" key={m.id}>
          <b>{m.operator} <small>{formatClock(m.sentAt)}</small></b>
          <div>{m.text} {m.deliveryStatus==='SENT'?<CheckCheck size={13} aria-label="Entregado"/>:<CircleAlert size={13} aria-label={m.deliveryStatus==='TEST'?'No enviado (prueba)':'No entregado'}/>}</div>
          <small className="obs-msg-status">{m.deliveryStatus==='TEST'?'No enviado (prueba)':m.deliveryStatus==='SENT'?'Entregado':'No entregado'}</small>
        </div>)}
    </div>
    <form className="obs-compose" onSubmit={submit}>
      <input value={text} onChange={e=>setText(e.target.value)} maxLength={500} autoComplete="off" placeholder="Escribe un mensaje de prueba..." disabled={disabled||sending}/>
      <button type="submit" disabled={disabled||sending}><Send size={16}/>Registrar</button>
    </form>
  </section>;
}
