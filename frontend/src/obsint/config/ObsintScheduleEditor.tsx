import {DAYS} from '../obsint';
import type {ObsintSlot} from '../obsintApi';
import './obsint-schedule.css';

/** Un día de la semana en el formulario: si se ejecuta y en qué horario. dayOfWeek 1 = lunes … 7 = domingo. */
export type ScheduleDay={on:boolean;start:string;end:string};
export type ScheduleWeek=Record<number,ScheduleDay>;

const ALL_DAY={start:'00:00',end:'23:59'};
const WEEK=[1,2,3,4,5,6,7];
const PRESETS:{label:string;days:number[]}[]=[
  {label:'Todos los días',days:WEEK},
  {label:'Lun a vie',days:[1,2,3,4,5]},
  {label:'Sáb y dom',days:[6,7]},
  {label:'Ninguno',days:[]},
];
const hhmm=(value:string|undefined)=>(value??'').slice(0,5);
const sameDays=(a:number[],b:number[])=>a.length===b.length&&a.every(d=>b.includes(d));

/** Semana del formulario a partir de lo guardado. Una tarea nueva arranca todos los días, todo el día. */
export function weekFromSlots(slots:ObsintSlot[]|null):ScheduleWeek{
  return Object.fromEntries(WEEK.map(day=>{
    if(!slots)return [day,{on:true,...ALL_DAY}];
    const saved=slots.find(s=>s.dayOfWeek===day);
    return [day,saved?{on:true,start:hhmm(saved.start),end:hhmm(saved.end)}:{on:false,start:'',end:''}];
  }));
}

/** Problema del horario de un día marcado, o null si está bien. */
function dayProblem(d:ScheduleDay){
  if(!d.on)return null;
  if(!d.start||!d.end)return 'falta la hora de inicio o de fin';
  if(d.end<=d.start)return 'el fin debe ser posterior al inicio';
  return null;
}

/** Horarios para guardar, o el mensaje de error que hay que mostrar. */
export function slotsFromWeek(week:ScheduleWeek):{slots:ObsintSlot[];error:string|null}{
  const on=WEEK.filter(d=>week[d].on);
  if(!on.length)return {slots:[],error:'Marque al menos un día de ejecución.'};
  const bad=on.find(d=>dayProblem(week[d]));
  if(bad)return {slots:[],error:`Horario del ${DAYS[bad-1].toLowerCase()}: ${dayProblem(week[bad])}.`};
  return {slots:on.map(d=>({dayOfWeek:d,start:week[d].start,end:week[d].end})),error:null};
}

/** Resumen corto: qué días y en qué horario. */
function summary(week:ScheduleWeek){
  const on=WEEK.filter(d=>week[d].on);
  if(!on.length)return 'Ningún día marcado';
  const days=sameDays(on,WEEK)?'Todos los días':sameDays(on,[1,2,3,4,5])?'Lunes a viernes':sameDays(on,[6,7])?'Sábado y domingo'
    :on.length===1?DAYS[on[0]-1]:`${on.length} días`;
  const hours=new Set(on.map(d=>`${week[d].start}–${week[d].end}`));
  if(hours.size>1)return `${days} · horarios distintos`;
  const [only]=on.map(d=>week[d]);
  return `${days} · ${only.start===ALL_DAY.start&&only.end===ALL_DAY.end?'todo el día':!only.start||!only.end?'sin horario':`${only.start} a ${only.end}`}`;
}

/**
 * Horario de ejecución de una tarea OBSINT: atajos de días, un horario común para los días marcados (con "Todo el día")
 * y, debajo, cada día con su casilla y sus horas para ajustar uno en particular.
 */
export function ObsintScheduleEditor({value,onChange}:{value:ScheduleWeek;onChange:(week:ScheduleWeek)=>void}){
  const on=WEEK.filter(d=>value[d].on);
  const common=on.length&&on.every(d=>value[d].start===value[on[0]].start&&value[d].end===value[on[0]].end)?value[on[0]]:null;
  const allDay=Boolean(common&&common.start===ALL_DAY.start&&common.end===ALL_DAY.end);
  /** Horario que toma un día al marcarlo: el común, el de otro día marcado o todo el día. */
  const template=()=>common&&common.start&&common.end?{start:common.start,end:common.end}:on.length?{start:value[on[0]].start,end:value[on[0]].end}:ALL_DAY;

  const setDay=(day:number,patch:Partial<ScheduleDay>)=>onChange({...value,[day]:{...value[day],...patch}});
  const toggleDay=(day:number)=>setDay(day,value[day].on?{on:false}:{on:true,...(value[day].start&&value[day].end?{}:template())});
  const chooseDays=(days:number[])=>{const t=template();onChange(Object.fromEntries(WEEK.map(d=>[d,days.includes(d)
    ?{on:true,start:value[d].start||t.start,end:value[d].end||t.end}:{...value[d],on:false}])))};
  const setCommon=(patch:Partial<ScheduleDay>)=>onChange(Object.fromEntries(WEEK.map(d=>[d,value[d].on?{...value[d],...patch}:value[d]])));

  return <fieldset className="obs-cfg-fieldset obs-sch">
    <legend>Horario de ejecución<span className="obs-sch-required" aria-hidden="true">*</span></legend>
    <div className="obs-sch-body">
    <p className="obs-sch-summary" aria-live="polite">{summary(value)}</p>

    <div className="obs-sch-presets" role="group" aria-label="Marcar días">
      {PRESETS.map(p=><button key={p.label} type="button" aria-pressed={sameDays(on,p.days)} onClick={()=>chooseDays(p.days)}>{p.label}</button>)}
    </div>

    <div className="obs-sch-common" role="group" aria-label="Horario de los días marcados">
      <span>Horario de los días marcados</span>
      <div>
        <input type="time" aria-label="Inicio de los días marcados" value={common?.start??''} disabled={!on.length||allDay}
          onChange={e=>setCommon({start:e.target.value})} title={common?undefined:'Los días marcados tienen horarios distintos; al escribir aquí se igualan.'}/>
        <span aria-hidden="true">a</span>
        <input type="time" aria-label="Fin de los días marcados" value={common?.end??''} disabled={!on.length||allDay}
          onChange={e=>setCommon({end:e.target.value})}/>
        <label className="obs-sch-allday"><input type="checkbox" checked={allDay} disabled={!on.length}
          onChange={e=>setCommon(e.target.checked?ALL_DAY:{start:'08:00',end:'18:00'})}/>Todo el día</label>
      </div>
      {on.length>0&&!common&&<small>Los días tienen horarios distintos. Si escribe aquí, se aplica a todos los marcados.</small>}
    </div>

    <ul className="obs-sch-days">
      {WEEK.map(day=>{const d=value[day],problem=dayProblem(d),name=DAYS[day-1];
        return <li key={day} className={`${d.on?'':'off'}${problem&&d.start&&d.end?' invalid':''}`}>
          <label title={name}><input type="checkbox" checked={d.on} onChange={()=>toggleDay(day)}/><span className="obs-sch-long">{name}</span><span className="obs-sch-short" aria-hidden="true">{name.slice(0,3)}</span></label>
          {d.on?<>
            <input type="time" aria-label={`Inicio del ${name}`} value={d.start} onChange={e=>setDay(day,{start:e.target.value})}/>
            <input type="time" aria-label={`Fin del ${name}`} value={d.end} onChange={e=>setDay(day,{end:e.target.value})} aria-invalid={Boolean(problem&&d.start&&d.end)}/>
          </>:<span className="obs-sch-off">No se ejecuta</span>}
        </li>})}
    </ul>
    <p className="obs-sch-hint">El fin debe ser posterior al inicio: un turno que cruza la medianoche (por ejemplo 18:00 a 06:00) se registra en dos días.</p>
    </div>
  </fieldset>;
}
