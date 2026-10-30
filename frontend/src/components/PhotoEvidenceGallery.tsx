import {useEffect, useState, type ReactNode} from 'react';
import {api, ApiError} from '../api';
import {AlertTriangle, Camera, CheckCircle2, ImageOff, SearchX, XCircle} from 'lucide-react';
import {ImageCarousel} from './ImageCarousel';

type Photo={id:string;url:string|null;purpose?:string;label?:string;source?:string;mime?:string;loading:boolean;missing?:boolean};
type PhotoRef={id:string;source:string;purpose?:string;label?:string};

const reliefLabel=(purpose:string|undefined,index:number)=>({
  entrant_face:'Rostro del agente entrante',entrant_full:'Cuerpo completo del agente entrante',
  outgoing_face:'Rostro del agente saliente',outgoing_full:'Cuerpo completo del agente saliente',
  station_0:'Vista general del puesto',station_1:'Área de trabajo o garita',station_2:'Acceso principal',
  inventory:'Activo de inventario con novedad'
}[purpose??'']??`Foto ${index+1}`);

/** Lista las fotos y descarga cada una por separado: se pinta al llegar y una descarga lenta no bloquea a las demás. */
function useProgressiveImages(eventId:string|undefined,list:(id:string)=>Promise<PhotoRef[]>,image:(id:string,photoId:string)=>Promise<Blob>,ignore404:boolean,key:unknown){
  const [photos,setPhotos]=useState<Photo[]>([]);
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');

  useEffect(()=>{
    let active=true;
    const urls:string[]=[];
    setPhotos([]);
    setError('');
    setLoading(false);
    if(!eventId)return;
    setLoading(true);
    void list(eventId).then(refs=>{
      if(!active)return;
      // MISSING: el registro declara la foto pero el archivo no llegó a Comando; se muestra su lugar sin intentar descargarla.
      setPhotos(refs.map(ref=>({id:ref.id,url:null,purpose:ref.purpose,label:ref.label,source:ref.source,loading:ref.source!=='MISSING',missing:ref.source==='MISSING'})));
      setLoading(false);
      refs.filter(ref=>ref.source!=='MISSING').forEach(ref=>{
        const settle=(url:string|null,mime?:string)=>{if(active)setPhotos(current=>current.map(photo=>photo.id===ref.id?{...photo,url,mime,loading:false}:photo))};
        void image(eventId,ref.id)
          .then(blob=>{const url=URL.createObjectURL(blob);if(active){urls.push(url);settle(url,blob.type)}else URL.revokeObjectURL(url)})
          .catch(()=>settle(null));
      });
    }).catch(reason=>{
      if(active && !(ignore404 && reason instanceof ApiError && reason.status===404))setError('No se pudieron consultar las fotografías.');
      if(active)setLoading(false);
    });
    return()=>{active=false;urls.forEach(url=>URL.revokeObjectURL(url))};
  // list/image son funciones estables de api; eventId y key deciden cuándo recargar.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[eventId,key]);

  const pending=photos.some(photo=>photo.loading);
  const available=photos.filter(photo=>photo.url).length;
  const missing=photos.filter(photo=>photo.missing).length;
  return {photos,loading,error,pending,available,missing};
}

/** Miniaturas del registro; al hacer clic se abre el visor en la misma página, sin navegar a la URL de la imagen. */
function PhotoGrid({photos,label,title}:{photos:Photo[];label:(photo:Photo,index:number)=>string;title:string}){
  const [open,setOpen]=useState<number|null>(null);
  const shown=photos.filter(photo=>photo.loading||photo.url||photo.missing);
  // El visor recorre solo las fotos ya descargadas; las que aún cargan se suman cuando llegan.
  const viewable=photos.filter((photo):photo is Photo&{url:string}=>Boolean(photo.url))
    .map(photo=>({id:photo.id,src:photo.url,mime:photo.mime,label:label(photo,photos.indexOf(photo)),fit:photo.source==='ATS_PLAN'?'contain' as const:'cover' as const}));
  if(!shown.length)return null;
  return <>
    <div className="nov-photo-grid">{shown.map(photo=>{const text=label(photo,photos.indexOf(photo));const plan=photo.source==='ATS_PLAN';return photo.url
      ?<button type="button" className={`nov-evidence-photo${plan?' plan':''}`} key={photo.id} aria-label={`Ver ${text}`} aria-haspopup="dialog"
          onClick={()=>setOpen(viewable.findIndex(image=>image.id===photo.id))}><img src={photo.url} alt={text}/><span>{text}</span></button>
      :photo.missing
        ?<div className="nov-evidence-photo missing" key={photo.id}><i aria-hidden="true"><ImageOff size={22}/><small>No llegó a Comando</small></i><span>{text}</span></div>
        :<div className="nov-evidence-photo pending" key={photo.id}><i aria-hidden="true"/><span>{text}</span></div>})}</div>
    {open!==null&&viewable.length>0&&<ImageCarousel images={viewable} index={Math.min(open,viewable.length-1)} onIndexChange={setOpen} onClose={()=>setOpen(null)} title={title}/>}
  </>;
}

/** Galería de archivos realmente almacenados; un contador declarado no crea fotos ficticias. */
export function PhotoEvidenceGallery({eventId,declaredCount=0,incident=false,hideInventory=false}:{eventId?:string;declaredCount?:number;incident?:boolean;hideInventory?:boolean}){
  // hideInventory: las fotos de los materiales se muestran en su tarjeta (ReliefInventoryCards), no en la galería.
  const list=incident?api.incidentEvidences:hideInventory
    ?(id:string)=>api.operationalEventEvidences(id).then(refs=>refs.filter(ref=>ref.source!=='INVENTORY'&&ref.purpose!=='inventory'))
    :api.operationalEventEvidences;
  const {photos,loading,error,pending,available,missing}=useProgressiveImages(eventId,list,
    incident?api.incidentEvidenceImage:api.operationalEventEvidenceImage,
    !incident,`${incident}:${declaredCount}:${hideInventory}`);
  // Las fotos MISSING ya se ven como tarjeta con su nombre; el aviso solo cuenta las que no se pueden ubicar.
  const unavailable=Math.max(0,declaredCount-available,photos.length-available)-missing;
  return <section className="nov-photo-block" aria-label="Evidencia fotográfica" aria-busy={loading||pending}>
    <div className="nov-section-title"><strong>Evidencia fotográfica ({pending||missing?`${available}/${photos.length}`:available})</strong></div>
    <PhotoGrid photos={photos} label={(photo,index)=>photo.label||reliefLabel(photo.purpose,index)} title="Evidencia fotográfica"/>
    {loading&&<p className="nov-evidence-message">Cargando fotografías…</p>}
    {!loading&&error&&<p className="nov-evidence-message">{error}</p>}
    {!loading&&!pending&&!error&&available>0&&unavailable>0&&<p className="nov-evidence-message">{unavailable} {unavailable===1?'fotografía declarada no tiene archivo disponible':'fotografías declaradas no tienen archivos disponibles'}.</p>}
    {!loading&&!pending&&!error&&available===0&&missing===0&&<p className="nov-evidence-message">{photos.length>0?'No se pudieron cargar las fotografías. Cierre y vuelva a abrir el detalle para reintentar.':unavailable>0?`Hay imágenes declaradas sin archivo disponible.${incident?' Vuelva a adjuntarlas desde Editar incidente.':''}`:'No contiene Imagen de Evidencia.'}</p>}
  </section>;
}

export type ReliefInventoryItem={assetId:string;code?:string;description?:string;condition?:string;previousCondition?:string;observation?:string;evidenceIds?:string[]};

// Mismos estados y textos que la tarjeta de Relevo completado en SGI Operador.
const CONDITIONS:Record<string,{label:string;tone:string;icon:ReactNode}>={
  GOOD:{label:'Bueno',tone:'good',icon:<CheckCircle2 size={13}/>},
  WITH_ISSUE:{label:'Con novedad',tone:'issue',icon:<AlertTriangle size={13}/>},
  DAMAGED:{label:'Dañado',tone:'bad',icon:<XCircle size={13}/>},
  MISSING:{label:'No encontrado',tone:'bad',icon:<SearchX size={13}/>}
};

/** Materiales informados en el relevo: foto, descripción, código, estado y observación de cada uno. */
export function ReliefInventoryCards({eventId,items}:{eventId?:string;items?:ReliefInventoryItem[]}){
  const assets=(items??[]).filter(item=>item.assetId);
  const [photos,setPhotos]=useState<Record<string,{url:string|null;mime?:string;loading:boolean}>>({});
  const [open,setOpen]=useState<number|null>(null);
  const evidenceKey=assets.map(item=>item.evidenceIds?.[0]??'').join(',');

  useEffect(()=>{
    let active=true;
    const urls:string[]=[];
    setOpen(null);
    // Como en Operador, cada material muestra su primera foto.
    const pending=eventId?assets.filter(item=>item.evidenceIds?.[0]):[];
    setPhotos(Object.fromEntries(pending.map(item=>[item.assetId,{url:null,loading:true}])));
    pending.forEach(item=>{
      const settle=(url:string|null,mime?:string)=>{if(active)setPhotos(current=>({...current,[item.assetId]:{url,mime,loading:false}}))};
      void api.operationalEventEvidenceImage(eventId!,item.evidenceIds![0])
        .then(blob=>{const url=URL.createObjectURL(blob);if(active){urls.push(url);settle(url,blob.type)}else URL.revokeObjectURL(url)})
        .catch(()=>settle(null));
    });
    return()=>{active=false;urls.forEach(url=>URL.revokeObjectURL(url))};
  // assets cambia de referencia en cada render; eventId y las fotos de cada material deciden cuándo recargar.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[eventId,evidenceKey]);

  if(!assets.length)return null;
  const title=(item:ReliefInventoryItem)=>item.description?.trim()||item.code?.trim()||'Activo';
  const viewable=assets.flatMap(item=>{const photo=photos[item.assetId];return photo?.url?[{id:item.assetId,src:photo.url,mime:photo.mime,label:title(item),fit:'cover' as const}]:[]});
  return <section className="rel-materials" aria-labelledby="rel-materials-title">
    <div className="rel-materials-head"><h4 id="rel-materials-title">Materiales</h4><span>{assets.length} {assets.length===1?'material':'materiales'}</span></div>
    <div className="rel-materials-grid">{assets.map(item=>{
      const name=title(item);
      const photo=photos[item.assetId];
      const condition=CONDITIONS[item.condition??''];
      return <article className="rel-material-card" key={item.assetId}>
        {photo?.url
          ?<button type="button" className="rel-material-image" aria-label={`Ver imagen de ${name}`} aria-haspopup="dialog"
              onClick={()=>setOpen(viewable.findIndex(image=>image.id===item.assetId))}><img src={photo.url} alt={`Imagen de ${name}`}/></button>
          :<div className={`rel-material-image${photo?.loading?' pending':' empty'}`} role="img" aria-label={photo?.loading?`Cargando imagen de ${name}`:`Sin imagen de ${name}`}>
            {!photo?.loading&&<><Camera size={24}/><small>Sin imagen</small></>}</div>}
        <div className="rel-material-body">
          <div className="rel-material-title"><strong>{name}</strong>{item.code?.trim()&&item.code.trim()!==name&&<code>{item.code.trim()}</code>}</div>
          <span className={`rel-material-status ${condition?.tone??'none'}`}>{condition?.icon}{condition?.label??'Sin estado'}</span>
          <div className="rel-material-observation"><b>Observación</b><p>{item.observation?.trim()||'Sin observación'}</p></div>
        </div>
      </article>})}</div>
    {open!==null&&viewable.length>0&&<ImageCarousel images={viewable} index={Math.min(open,viewable.length-1)} onIndexChange={setOpen} onClose={()=>setOpen(null)} title="Materiales del relevo"/>}
  </section>;
}

/** Fotos guía que recibe el agente (estándar del puesto, hitos, campos o consigna y plano ATS); se oculta si el registro no tiene. */
export function ReferenceImageGallery({eventId}:{eventId?:string}){
  const {photos,loading,error,pending,available}=useProgressiveImages(eventId,api.operationalEventReferences,api.operationalEventReferenceImage,true,'references');
  if(!loading&&!error&&photos.length===0)return null;
  return <section className="nov-photo-block" aria-label="Fotos de referencia" aria-busy={loading||pending}>
    <div className="nov-section-title"><strong>Fotos de referencia ({pending?`${available}/${photos.length}`:available})</strong></div>
    <PhotoGrid photos={photos} label={(photo,index)=>photo.label||`Referencia ${index+1}`} title="Fotos de referencia"/>
    {loading&&<p className="nov-evidence-message">Cargando fotos de referencia…</p>}
    {!loading&&error&&<p className="nov-evidence-message">No se pudieron consultar las fotos de referencia.</p>}
    {!loading&&!pending&&!error&&available<photos.length&&<p className="nov-evidence-message">{photos.length-available===1?'Una foto de referencia no se pudo cargar.':`${photos.length-available} fotos de referencia no se pudieron cargar.`}</p>}
  </section>;
}
