import {useEffect,useId,useState} from 'react';
import {api} from '../api';

/** Radio GPS predeterminado de la instancia (Configuración). Se pide una vez por sesión; 50 m si no se puede leer. */
let defaultPromise:Promise<number>|null=null;
export const loadDefaultRadius=()=>defaultPromise??=api.evidenceLocationSettings().then(s=>s.defaultRadiusM).catch(()=>{defaultPromise=null;return 50});
export const resetDefaultRadius=()=>{defaultPromise=null};
export function useDefaultRadius(){
 const [radius,setRadius]=useState<number|null>(null);
 useEffect(()=>{let live=true;void loadDefaultRadius().then(r=>{if(live)setRadius(r)});return()=>{live=false}},[]);
 return radius;
}

/**
 * Radio propio (m) de una referencia GPS (Hito, Consigna, Puesto). Vacío = usa el predeterminado de Configuración.
 * Solo define cuándo Operación muestra «Fuera del radio GPS»: nunca bloquea al agente.
 */
export default function RadiusField({value,onChange,disabled=false,className=''}:{value:number|null|undefined;onChange:(v:number|null)=>void;disabled?:boolean;className?:string}){
 const id=useId();
 const def=useDefaultRadius();
 const effective=value??def;
 return <label className={`radius-field ${className}`} htmlFor={id}>
  <span>Radio (m)</span>
  <input id={id} type="number" inputMode="numeric" min={5} max={5000} step={1} disabled={disabled}
   placeholder={def!=null?`${def} (predeterminado)`:'predeterminado'} value={value??''}
   onChange={e=>onChange(e.target.value===''?null:Math.round(Number(e.target.value)))}/>
  <small>{value==null?`Usa el predeterminado${def!=null?` de ${def} m`:''} (Configuración).`:`Radio propio de ${value} m.`}{effective!=null&&` Más lejos de ${effective} m: aviso «Fuera del radio GPS», sin bloquear.`}</small>
 </label>;
}
