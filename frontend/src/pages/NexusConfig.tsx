import {useEffect,useMemo,useState} from 'react';
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  CircleDot,
  Clock3,
  GitBranch,
  History,
  Link2,
  Network,
  Plus,
  Save,
  Search,
  ShieldCheck,
  X,
} from 'lucide-react';

type PointPost={postId:string;postCode:string;postName:string;tier:string;state:string};
type PointContext={pointId:string;pointName:string;pointCode:string;clientName:string;companyName:string;serviceName:string;serviceCode:string;posts:PointPost[]};
type RuleStatus='BORRADOR'|'INACTIVA'|'ACTIVA';
type Relation='ANTES'|'DURANTE'|'DESPUES';
type Tab='REGLAS'|'HISTORIAL';
type Rule={
  code:string;name:string;description:string;version:string;status:RuleStatus;
  objectiveObject:string;objectiveState:string;relation:Relation;referenceObject:string;referenceState:string;
  windowValue:string;windowUnit:string;correlations:string[];schedule:string;days:string[];from:string;to:string;
  actionWhen:string;actionWhenNot:string;updatedAt:string;updatedBy:string;
};
type HistoryRow={date:string;rule:string;version:string;action:string;user:string;detail:string};

const OBJECT_STATES:Record<string,string[]>={
  'Relevo':['Programado','Iniciado','Finalizado','No realizado'],
  'Registro Vehicular':['Registrado','Salida registrada','Rechazado'],
  'Barrera Vehicular':['Apertura','Cierre','Bloqueada','Forzada'],
  'Bitácora':['Ingreso registrado','Salida registrada','Acreditación registrada'],
  'Patrulla KILO':['Programada','Habilitada','Iniciada','Finalizada','Incumplida'],
  'Patrulla':['Programada','Habilitada','Iniciada','Finalizada','Incumplida'],
  'Consigna':['Creada','Activada','Ejecutada','Vencida'],
  'Incidente':['Registrado','Escalado','Finalizado'],
  'Novedad':['Reportada','Aprobada','Descartada'],
  'Puerta Principal (IoT)':['Abierta','Cerrada','Forzada','Offline'],
  'Sensor Perimetral (IoT)':['Normal','Alarma','Offline'],
  'Botón de Pánico (IoT)':['Activado','Restablecido'],
  'Turno':['Programado','Iniciado','Finalizado'],
};
const OBJECTS=Object.keys(OBJECT_STATES);
const CORRELATIONS=['Misma placa','Mismo Punto','Mismo acceso','Mismo Puesto','Mismo colaborador'];
const DAYS=[['L','Lun'],['M','Mar'],['X','Mié'],['J','Jue'],['V','Vie'],['S','Sáb'],['D','Dom']] as const;
const ACTIONS=['Sin acción','Generar alerta','Crear Consigna Ad Hoc','Crear Incidente','Notificar Monitor','Habilitar Patrulla','Bloquear acción'];

const INITIAL_RULES:Rule[]=[
  {code:'NEX-0001',name:'Relevo finalizado → Patrulla KILO',description:'La Patrulla KILO puede habilitarse después de finalizar el relevo.',version:'1.0',status:'ACTIVA',objectiveObject:'Patrulla KILO',objectiveState:'Habilitada',relation:'DESPUES',referenceObject:'Relevo',referenceState:'Finalizado',windowValue:'10',windowUnit:'Minutos',correlations:['Mismo Punto','Mismo Puesto'],schedule:'Siempre activa',days:['L','M','X','J','V','S','D'],from:'00:00',to:'23:59',actionWhen:'Habilitar Patrulla',actionWhenNot:'Generar alerta',updatedAt:'27/09/2026 16:42',updatedBy:'Coordinador Galvarino'},
  {code:'NEX-0002',name:'Apertura vehicular autorizada',description:'La barrera solo puede abrirse si existe un registro vehicular previo relacionado.',version:'1.1',status:'ACTIVA',objectiveObject:'Barrera Vehicular',objectiveState:'Apertura',relation:'DESPUES',referenceObject:'Registro Vehicular',referenceState:'Registrado',windowValue:'5',windowUnit:'Minutos',correlations:['Misma placa','Mismo Punto','Mismo acceso'],schedule:'Siempre activa',days:['L','M','X','J','V','S','D'],from:'00:00',to:'23:59',actionWhen:'Sin acción',actionWhenNot:'Bloquear acción',updatedAt:'27/09/2026 15:18',updatedBy:'Asistente Galvarino'},
  {code:'NEX-0003',name:'Puerta abierta sin cierre',description:'Control de permanencia de puerta principal en estado abierta.',version:'1.0',status:'INACTIVA',objectiveObject:'Puerta Principal (IoT)',objectiveState:'Abierta',relation:'DURANTE',referenceObject:'Turno',referenceState:'Iniciado',windowValue:'3',windowUnit:'Minutos',correlations:['Mismo Punto'],schedule:'Horario específico',days:['L','M','X','J','V'],from:'19:00',to:'07:00',actionWhen:'Generar alerta',actionWhenNot:'Sin acción',updatedAt:'26/09/2026 11:04',updatedBy:'Coordinador Galvarino'},
  {code:'NEX-0004',name:'Registro previo para ingreso',description:'Borrador de validación para acceso vehicular.',version:'0.1',status:'BORRADOR',objectiveObject:'Barrera Vehicular',objectiveState:'Apertura',relation:'DESPUES',referenceObject:'Registro Vehicular',referenceState:'Registrado',windowValue:'10',windowUnit:'Minutos',correlations:['Misma placa'],schedule:'Siempre activa',days:['L','M','X','J','V','S','D'],from:'00:00',to:'23:59',actionWhen:'Sin acción',actionWhenNot:'Crear Incidente',updatedAt:'25/09/2026 09:32',updatedBy:'Asistente Galvarino'},
];
const INITIAL_HISTORY:HistoryRow[]=[
  {date:'27/09/2026 16:42',rule:'NEX-0001 · Relevo finalizado → Patrulla KILO',version:'1.0',action:'Activación',user:'Coordinador Galvarino',detail:'Regla activada para el Punto.'},
  {date:'27/09/2026 15:18',rule:'NEX-0002 · Apertura vehicular autorizada',version:'1.1',action:'Nueva versión',user:'Asistente Galvarino',detail:'Se agregó correlación por mismo acceso.'},
  {date:'26/09/2026 11:04',rule:'NEX-0003 · Puerta abierta sin cierre',version:'1.0',action:'Inactivación',user:'Coordinador Galvarino',detail:'Regla inactivada para revisión operativa.'},
  {date:'25/09/2026 09:32',rule:'NEX-0004 · Registro previo para ingreso',version:'0.1',action:'Creación',user:'Asistente Galvarino',detail:'Borrador inicial creado.'},
];

function statusLabel(status:RuleStatus){return status==='ACTIVA'?'Activa':status==='INACTIVA'?'Inactiva':'Borrador'}
function relationLabel(relation:Relation){return relation==='DESPUES'?'DESPUÉS':relation}
function freshRule(code:string):Rule{
  return {code,name:'',description:'',version:'0.1',status:'BORRADOR',objectiveObject:'Barrera Vehicular',objectiveState:'Apertura',relation:'DESPUES',referenceObject:'Registro Vehicular',referenceState:'Registrado',windowValue:'5',windowUnit:'Minutos',correlations:['Misma placa','Mismo Punto'],schedule:'Siempre activa',days:['L','M','X','J','V','S','D'],from:'00:00',to:'23:59',actionWhen:'Sin acción',actionWhenNot:'Generar alerta',updatedAt:'',updatedBy:'Usuario UAT'};
}

export default function NexusConfig({point,onBack}:{point:PointContext;onBack:()=>void}){
  const storageKey=`sgi-comando:nexus:${point.pointId}`;
  const [tab,setTab]=useState<Tab>('REGLAS');
  const [rules,setRules]=useState<Rule[]>(()=>{try{const raw=localStorage.getItem(`${storageKey}:rules`);return raw?JSON.parse(raw) as Rule[]:INITIAL_RULES}catch{return INITIAL_RULES}});
  const [history,setHistory]=useState<HistoryRow[]>(()=>{try{const raw=localStorage.getItem(`${storageKey}:history`);return raw?JSON.parse(raw) as HistoryRow[]:INITIAL_HISTORY}catch{return INITIAL_HISTORY}});
  const [query,setQuery]=useState('');
  const [status,setStatus]=useState('');
  const [editor,setEditor]=useState<Rule|null>(null);
  const [notice,setNotice]=useState('');

  useEffect(()=>{try{localStorage.setItem(`${storageKey}:rules`,JSON.stringify(rules))}catch{}},[rules,storageKey]);
  useEffect(()=>{try{localStorage.setItem(`${storageKey}:history`,JSON.stringify(history))}catch{}},[history,storageKey]);

  const filtered=useMemo(()=>rules.filter(rule=>{
    const q=query.trim().toLowerCase();
    return (!q||`${rule.code} ${rule.name} ${rule.description}`.toLowerCase().includes(q))&&(!status||rule.status===status);
  }),[rules,query,status]);
  const active=rules.filter(rule=>rule.status==='ACTIVA').length;
  const inactive=rules.filter(rule=>rule.status==='INACTIVA').length;
  const drafts=rules.filter(rule=>rule.status==='BORRADOR').length;

  const nextCode=()=>`NEX-${String(Math.max(0,...rules.map(rule=>Number(rule.code.replace(/\D/g,''))||0))+1).padStart(4,'0')}`;
  const startNew=()=>{setNotice('');setEditor(freshRule(nextCode()))};
  const updateEditor=(patch:Partial<Rule>)=>setEditor(current=>current?{...current,...patch}:current);
  const changeObject=(side:'objective'|'reference',value:string)=>{
    if(!editor)return;
    const first=OBJECT_STATES[value]?.[0]??'';
    updateEditor(side==='objective'?{objectiveObject:value,objectiveState:first}:{referenceObject:value,referenceState:first});
  };
  const toggleCorrelation=(item:string)=>{
    if(!editor)return;
    updateEditor({correlations:editor.correlations.includes(item)?editor.correlations.filter(value=>value!==item):[...editor.correlations,item]});
  };
  const toggleDay=(day:string)=>{
    if(!editor)return;
    updateEditor({days:editor.days.includes(day)?editor.days.filter(value=>value!==day):[...editor.days,day]});
  };
  const saveRule=()=>{
    if(!editor||!editor.name.trim()){setNotice('Ingrese un nombre para guardar la regla.');return}
    const now='27/09/2026 17:08';
    const isExisting=rules.some(rule=>rule.code===editor.code);
    const saved={...editor,name:editor.name.trim(),status:isExisting?editor.status:'BORRADOR' as RuleStatus,updatedAt:now,updatedBy:'Usuario UAT'};
    setRules(current=>[saved,...current.filter(rule=>rule.code!==saved.code)]);
    setHistory(current=>[{date:now,rule:`${saved.code} · ${saved.name}`,version:saved.version,action:isExisting?'Modificación':'Creación',user:'Usuario UAT',detail:isExisting?'Se actualizó la configuración de la regla.':'Regla guardada como borrador en Nexus.'},...current]);
    setEditor(null);setNotice(isExisting?`${saved.code} actualizada.`:`${saved.code} guardada como borrador.`);
  };

  return <div className="nexus-page">
    <div className="nexus-breadcrumbs"><button onClick={onBack}><ArrowLeft size={15}/>Volver a Configuración del Punto</button><span>/</span><small>Nexus</small></div>

    <section className="nexus-hero">
      <div className="nexus-brand"><span className="nexus-brand-icon"><Network size={27}/></span><div><div className="nexus-wordmark">NE<span>X</span>US</div><strong>Motor de Eventos y Reglas Operativas</strong><small>{point.clientName} · {point.pointName} · {point.companyName}</small></div></div>
      <button className="nexus-new" onClick={startNew}><Plus size={17}/>Nueva regla</button>
    </section>

    <nav className="nexus-tabs">
      <button className={tab==='REGLAS'?'active':''} onClick={()=>setTab('REGLAS')}>Reglas</button>
      <button className={tab==='HISTORIAL'?'active':''} onClick={()=>setTab('HISTORIAL')}>Historial</button>
    </nav>

    {notice&&<div className={`nexus-notice ${notice.startsWith('Ingrese')?'warning':''}`}><CheckCircle2 size={16}/><span>{notice}</span></div>}

    {tab==='REGLAS'?<>
      <div className="nexus-kpis">
        <article><span><GitBranch/></span><div><small>Reglas</small><strong>{rules.length}</strong><em>configuradas en el Punto</em></div></article>
        <article><span className="green"><CheckCircle2/></span><div><small>Activas</small><strong>{active}</strong><em>en evaluación operacional</em></div></article>
        <article><span className="red"><CircleDot/></span><div><small>Inactivas</small><strong>{inactive}</strong><em>publicadas sin ejecución</em></div></article>
        <article><span className="amber"><CalendarClock/></span><div><small>Borradores</small><strong>{drafts}</strong><em>pendientes de publicación</em></div></article>
      </div>

      <section className="nexus-card">
        <div className="nexus-filterbar">
          <label className="nexus-search"><Search size={16}/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Buscar por código, nombre o descripción…"/></label>
          <label><span>Estado</span><select value={status} onChange={event=>setStatus(event.target.value)}><option value="">Todos</option><option value="ACTIVA">Activas</option><option value="INACTIVA">Inactivas</option><option value="BORRADOR">Borradores</option></select></label>
        </div>
        <div className="nexus-table-wrap"><table className="nexus-table"><thead><tr><th>Código</th><th>Nombre</th><th>Relación</th><th>Evento objetivo</th><th>Evento de referencia</th><th>Estado</th><th>Versión</th><th>Última modificación</th></tr></thead><tbody>{filtered.map(rule=><tr key={rule.code} onClick={()=>setEditor({...rule})}><td><b>{rule.code}</b></td><td><strong>{rule.name}</strong><small>{rule.description}</small></td><td><span className={`nexus-relation ${rule.relation.toLowerCase()}`}>{relationLabel(rule.relation)}</span></td><td><b>{rule.objectiveObject}</b><small>{rule.objectiveState}</small></td><td><b>{rule.referenceObject}</b><small>{rule.referenceState}</small></td><td><span className={`nexus-status ${rule.status.toLowerCase()}`}>{statusLabel(rule.status)}</span></td><td>v{rule.version}</td><td>{rule.updatedAt}<small>{rule.updatedBy}</small></td></tr>)}</tbody></table></div>
      </section>
    </>:<section className="nexus-card">
      <header className="nexus-card-title"><div><History size={18}/><div><h3>Historial de reglas</h3><span>Registro de creación, publicación, activación, inactivación y nuevas versiones.</span></div></div></header>
      <div className="nexus-table-wrap"><table className="nexus-table nexus-history"><thead><tr><th>Fecha / Hora</th><th>Regla</th><th>Versión</th><th>Modificación</th><th>Usuario</th><th>Detalle</th></tr></thead><tbody>{history.map((row,index)=><tr key={`${row.date}-${index}`}><td>{row.date}</td><td><b>{row.rule}</b></td><td>v{row.version}</td><td><span className="nexus-history-action">{row.action}</span></td><td>{row.user}</td><td>{row.detail}</td></tr>)}</tbody></table></div>
    </section>}

    {editor&&<div className="nexus-editor-backdrop" onMouseDown={event=>{if(event.currentTarget===event.target)setEditor(null)}}>
      <aside className="nexus-editor" role="dialog" aria-modal="true" aria-label="Editor de regla Nexus">
        <header><div><span className="nexus-brand-icon small"><Network size={19}/></span><div><small>{editor.code} · v{editor.version}</small><h3>{rules.some(rule=>rule.code===editor.code)?'Regla Nexus':'Nueva regla'}</h3></div></div><button onClick={()=>setEditor(null)} aria-label="Cerrar"><X size={19}/></button></header>
        <div className="nexus-editor-scroll">
          <section className="nexus-step blue"><div className="nexus-step-title"><b>1</b><div><strong>Información general</strong><span>Identidad, alcance y horario de la regla.</span></div></div>
            <div className="nexus-form-grid two"><label><span>Código</span><input value={editor.code} disabled/></label><label><span>Versión</span><input value={`v${editor.version}`} disabled/></label></div>
            <label><span>Nombre *</span><input value={editor.name} onChange={event=>updateEditor({name:event.target.value})} placeholder="Ej. Apertura vehicular autorizada"/></label>
            <label><span>Descripción</span><textarea value={editor.description} onChange={event=>updateEditor({description:event.target.value})} placeholder="Describa el propósito operativo de la regla…"/></label>
            <div className="nexus-form-grid two"><label><span>Estado</span><select value={editor.status} disabled={!rules.some(rule=>rule.code===editor.code)} onChange={event=>updateEditor({status:event.target.value as RuleStatus})}><option value="BORRADOR">Borrador</option><option value="INACTIVA">Inactiva</option><option value="ACTIVA">Activa</option></select></label><label><span>Aplicación</span><select value={editor.schedule} onChange={event=>updateEditor({schedule:event.target.value})}><option>Siempre activa</option><option>Horario específico</option></select></label></div>
            {editor.schedule==='Horario específico'&&<><div className="nexus-days">{DAYS.map(([value,label])=><button key={value} className={editor.days.includes(value)?'selected':''} onClick={()=>toggleDay(value)}>{label}</button>)}</div><div className="nexus-form-grid two"><label><span>Desde</span><input type="time" value={editor.from} onChange={event=>updateEditor({from:event.target.value})}/></label><label><span>Hasta</span><input type="time" value={editor.to} onChange={event=>updateEditor({to:event.target.value})}/></label></div></>}
          </section>

          <section className="nexus-step red"><div className="nexus-step-title"><b>2</b><div><strong>Evento objetivo</strong><span>¿Qué objeto–evento/estado se evaluará?</span></div></div><div className="nexus-form-grid two"><label><span>Objeto *</span><select value={editor.objectiveObject} onChange={event=>changeObject('objective',event.target.value)}>{OBJECTS.map(item=><option key={item}>{item}</option>)}</select></label><label><span>Evento / Estado *</span><select value={editor.objectiveState} onChange={event=>updateEditor({objectiveState:event.target.value})}>{(OBJECT_STATES[editor.objectiveObject]??[]).map(item=><option key={item}>{item}</option>)}</select></label></div></section>

          <section className="nexus-step amber"><div className="nexus-step-title"><b>3</b><div><strong>Relación temporal</strong><span>Cómo se relaciona el evento objetivo con el evento de referencia.</span></div></div><div className="nexus-relation-picker">{(['ANTES','DURANTE','DESPUES'] as Relation[]).map(item=><button key={item} className={editor.relation===item?'selected':''} onClick={()=>updateEditor({relation:item})}>{relationLabel(item)}</button>)}</div><div className="nexus-form-grid two"><label><span>Ventana máxima (opcional)</span><input type="number" min="0" value={editor.windowValue} onChange={event=>updateEditor({windowValue:event.target.value})}/></label><label><span>Unidad</span><select value={editor.windowUnit} onChange={event=>updateEditor({windowUnit:event.target.value})}><option>Segundos</option><option>Minutos</option><option>Horas</option><option>Días</option></select></label></div></section>

          <section className="nexus-step purple"><div className="nexus-step-title"><b>4</b><div><strong>Evento de referencia</strong><span>¿Qué objeto–evento/estado debe haber ocurrido o estar vigente?</span></div></div><div className="nexus-form-grid two"><label><span>Objeto *</span><select value={editor.referenceObject} onChange={event=>changeObject('reference',event.target.value)}>{OBJECTS.map(item=><option key={item}>{item}</option>)}</select></label><label><span>Evento / Estado *</span><select value={editor.referenceState} onChange={event=>updateEditor({referenceState:event.target.value})}>{(OBJECT_STATES[editor.referenceObject]??[]).map(item=><option key={item}>{item}</option>)}</select></label></div></section>

          <section className="nexus-step green"><div className="nexus-step-title"><b>5</b><div><strong>Correlación <em>(opcional)</em></strong><span>Relaciona ambos eventos por atributos compartidos.</span></div></div><div className="nexus-checks">{CORRELATIONS.map(item=><label key={item}><input type="checkbox" checked={editor.correlations.includes(item)} onChange={()=>toggleCorrelation(item)}/><span>{item}</span></label>)}</div></section>

          <section className="nexus-step cyan"><div className="nexus-step-title"><b>6</b><div><strong>Acciones</strong><span>Consecuencias opcionales cuando la relación se cumple o no se cumple.</span></div></div><div className="nexus-form-grid two"><label><span>Si se cumple</span><select value={editor.actionWhen} onChange={event=>updateEditor({actionWhen:event.target.value})}>{ACTIONS.map(item=><option key={item}>{item}</option>)}</select></label><label><span>Si NO se cumple</span><select value={editor.actionWhenNot} onChange={event=>updateEditor({actionWhenNot:event.target.value})}>{ACTIONS.map(item=><option key={item}>{item}</option>)}</select></label></div></section>

          <div className="nexus-rule-summary"><Activity size={17}/><div><strong>Lectura de la regla</strong><span><b>{editor.objectiveObject} · {editor.objectiveState}</b> debe ocurrir <b>{relationLabel(editor.relation)}</b> de <b>{editor.referenceObject} · {editor.referenceState}</b>{editor.windowValue?` dentro de una ventana de ${editor.windowValue} ${editor.windowUnit.toLowerCase()}`:''}{editor.correlations.length?`, correlando ${editor.correlations.join(', ').toLowerCase()}`:''}.</span></div></div>
        </div>
        <footer><button className="ghost" onClick={()=>setEditor(null)}>Cancelar</button><button className="primary" onClick={saveRule}><Save size={15}/>Guardar regla</button></footer>
      </aside>
    </div>}
  </div>;
}
