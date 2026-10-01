import {useEffect,useState} from 'react';
import {Camera,Trash2} from 'lucide-react';

export type StandardImageRef={id:string;position:number};

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
    {editable&&images.length<5&&<button type="button" className="pat-photo-empty pat-standard-add" onClick={onAdd}><Camera size={20}/><strong>{images.length?'Agregar foto estándar':'Tomar / cargar foto estándar'}</strong><span>{images.length} de 5</span></button>}
  </div>;
}
