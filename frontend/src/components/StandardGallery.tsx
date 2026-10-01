import {useEffect,useState} from 'react';
import {Camera,Trash2} from 'lucide-react';

export type StandardImageRef={id:string;position:number};
export const MAX_STANDARD_IMAGES=5;
const MAX_BYTES=5*1024*1024;

/**
 * Sube varias fotos estándar elegidas a la vez. El backend recibe una por petición, así que se envían en orden, una por una.
 * Respeta el máximo de 5 por destino y 5 MB por foto; las que no entran o fallan se informan sin cortar el resto.
 */
export async function uploadStandardImages<T>(files:File[],current:number,upload:(file:File)=>Promise<T>,
  onProgress:(done:number,total:number)=>void,describeError:(e:unknown)=>string):Promise<{last:T|null;uploaded:number;problems:string[]}>{
  const list=files;const problems:string[]=[];let last:T|null=null;let uploaded=0;
  const room=Math.max(0,MAX_STANDARD_IMAGES-current);
  const accepted=list.filter(f=>{if(f.size>MAX_BYTES){problems.push(`${f.name}: supera 5 MB`);return false}return true});
  if(accepted.length>room)problems.push(`Se admiten hasta ${MAX_STANDARD_IMAGES} fotos estándar: no se cargaron ${accepted.slice(room).map(f=>f.name).join(', ')}`);
  const queue=accepted.slice(0,room);
  for(const [i,f] of queue.entries()){
    onProgress(i+1,queue.length);
    try{last=await upload(f);uploaded++}catch(e){problems.push(`${f.name}: ${describeError(e)}`)}
  }
  return {last,uploaded,problems};
}

/** Texto del aviso final de una carga múltiple. */
export const uploadSummary=(uploaded:number)=>uploaded===1?'Foto estándar agregada.':`${uploaded} fotos estándar agregadas.`;

/** Fotos estándar (hasta 5) de un Hito, evidencia de Consigna o campo de Bitácora; VISINT las recibe todas como referencias. */
export default function StandardGallery({images,load,onAdd,onRemove,editable=true}:{images:StandardImageRef[];load:(id:string)=>Promise<Blob>;onAdd:()=>void;onRemove:(id:string)=>void;editable?:boolean}){
  const [urls,setUrls]=useState<Record<string,string>>({});
  const key=images.map(i=>i.id).join(',');
  useEffect(()=>{
    let live=true;const made:string[]=[];
    void Promise.all(images.map(async i=>{try{const u=URL.createObjectURL(await load(i.id));made.push(u);return [i.id,u] as const}catch{return [i.id,''] as const}}))
      .then(pairs=>{if(live)setUrls(Object.fromEntries(pairs));else made.forEach(u=>URL.revokeObjectURL(u))});
    return()=>{live=false;made.forEach(u=>URL.revokeObjectURL(u))};
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[key]);
  return <div className="pat-standard-gallery">
    {images.map(i=><figure key={i.id}>{urls[i.id]?<img src={urls[i.id]} alt={`Foto estándar ${i.position}`}/>:<div className="pat-standard-loading"/>}
      <figcaption><span>{i.position}</span>{editable&&<button type="button" title="Quitar foto estándar" onClick={()=>onRemove(i.id)}><Trash2 size={13}/></button>}</figcaption></figure>)}
    {editable&&images.length<5&&<button type="button" className="pat-photo-empty pat-standard-add" onClick={onAdd}><Camera size={20}/><strong>{images.length?'Agregar fotos estándar':'Tomar / cargar fotos estándar'}</strong><span>{images.length} de {MAX_STANDARD_IMAGES} · puede elegir varias</span></button>}
  </div>;
}
