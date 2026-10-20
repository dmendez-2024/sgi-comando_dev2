import {useEffect, useState} from 'react';
import {api, ApiError} from '../api';
import {ImageOff} from 'lucide-react';
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
export function PhotoEvidenceGallery({eventId,declaredCount=0,incident=false}:{eventId?:string;declaredCount?:number;incident?:boolean}){
  const {photos,loading,error,pending,available,missing}=useProgressiveImages(eventId,
    incident?api.incidentEvidences:api.operationalEventEvidences,
    incident?api.incidentEvidenceImage:api.operationalEventEvidenceImage,
    !incident,`${incident}:${declaredCount}`);
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
