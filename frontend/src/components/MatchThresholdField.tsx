import {useEffect,useId,useState} from 'react';
import {AlertTriangle,Info} from 'lucide-react';

/** Umbral que SGI envía a VISINT (matchThreshold): "Cumple" si la mejor coincidencia con alguna foto estándar lo alcanza o supera. */
export const DEFAULT_MATCH_THRESHOLD=0.8;
/** Hasta aquí el umbral es bajo: VISINT podría aceptar fotos de otro lugar. */
export const LOW_MATCH_THRESHOLD=0.4;
const STRICT_MATCH_THRESHOLD=0.95;

const round2=(v:number)=>Math.round(v*100)/100;
const fmt=(v:number)=>v.toFixed(2);
export const thresholdLevel=(v:number)=>v<=LOW_MATCH_THRESHOLD?{key:'low',label:'Bajo'}:v>=STRICT_MATCH_THRESHOLD?{key:'strict',label:'Muy exigente'}:{key:'ok',label:'Adecuado'};

/**
 * Umbral de coincidencia junto a las fotos estándar: regla 0.00–1.00 con la zona baja (≤ 0.40) marcada, casilla de dos decimales
 * y explicación en vivo. value null = predeterminado (0.80). Cambia con onChange; se guarda con el botón de la tarea.
 */
export default function MatchThresholdField({value,onChange,disabled=false,disabledReason}:{value:number|null|undefined;onChange:(v:number)=>void;disabled?:boolean;disabledReason?:string}){
 const id=useId();
 const current=value??DEFAULT_MATCH_THRESHOLD;
 const [text,setText]=useState(fmt(current));
 useEffect(()=>{setText(fmt(current))},[current]);
 const level=thresholdLevel(current);
 const commitText=()=>{const n=Number(text.replace(',','.'));if(Number.isFinite(n))onChange(round2(Math.min(1,Math.max(0,n))));else setText(fmt(current))};
 const pct=`${current*100}%`;
 return <div className={`mt-field ${level.key}${disabled?' disabled':''}`}>
  <div className="mt-head">
   <label htmlFor={`${id}-range`}>Exigencia de coincidencia</label>
   <span className="mt-reading" aria-live="polite"><b>{fmt(current)}</b><em>{level.label}</em>{value==null&&<small>predeterminado</small>}</span>
  </div>
  <div className="mt-scale" style={{['--mt-value' as string]:pct,['--mt-low' as string]:`${LOW_MATCH_THRESHOLD*100}%`}}>
   <div className="mt-track" aria-hidden="true"><span className="mt-low-zone">Bajo</span><span className="mt-fill"/><i className="mt-tick"/></div>
   <input id={`${id}-range`} type="range" min={0} max={1} step={0.01} value={current} disabled={disabled}
    aria-valuetext={`${fmt(current)}, ${level.label.toLowerCase()}`} aria-describedby={`${id}-help`}
    onChange={e=>onChange(round2(Number(e.target.value)))}/>
   <div className="mt-ends" aria-hidden="true"><span><b>0.00</b>cualquier foto</span><span className="mt-mark"><b>0.40</b></span><span><b>1.00</b>idéntica</span></div>
  </div>
  <div className="mt-row">
   <label className="mt-number"><span>Valor exacto</span>
    <input type="text" inputMode="decimal" pattern="[01]([.,][0-9]{1,2})?" maxLength={4} aria-label="Valor exacto del umbral, de 0.00 a 1.00" value={text} disabled={disabled}
     onChange={e=>setText(e.target.value)} onBlur={commitText} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();commitText()}}}/>
   </label>
   {!disabled&&current!==DEFAULT_MATCH_THRESHOLD&&<button type="button" className="mt-reset" onClick={()=>onChange(DEFAULT_MATCH_THRESHOLD)}>Usar {fmt(DEFAULT_MATCH_THRESHOLD)} (recomendado)</button>}
  </div>
  <p id={`${id}-help`} className="mt-help">{disabled&&disabledReason?disabledReason:<>VISINT da «Cumple» si la foto del agente se parece al menos <b>{fmt(current)}</b> a alguna foto estándar.</>}</p>
  {!disabled&&level.key==='low'&&<p className="mt-warn" role="note"><AlertTriangle size={14}/>Umbral bajo (0.40 o menos): VISINT podría dar «Cumple» a fotos de otro lugar.</p>}
  {!disabled&&level.key==='strict'&&<p className="mt-note" role="note"><Info size={14}/>Muy exigente: podría rechazar fotos correctas tomadas con otra luz o ángulo.</p>}
 </div>;
}
