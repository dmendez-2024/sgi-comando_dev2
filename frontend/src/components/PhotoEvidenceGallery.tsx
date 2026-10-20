import {useEffect, useState} from 'react';
import {api, ApiError} from '../api';

type Photo={id:string;url:string|null;purpose?:string};
type PhotoRef={id:string;source:string;purpose?:string};

const reliefLabel=(purpose:string|undefined,index:number)=>({
  entrant_face:'Rostro del agente entrante',entrant_full:'Cuerpo completo del agente entrante',
  outgoing_face:'Rostro del agente saliente',outgoing_full:'Cuerpo completo del agente saliente',
  station_0:'Vista general del puesto',station_1:'Área de trabajo o garita',station_2:'Acceso principal'
}[purpose??'']??`Foto ${index+1}`);

/** Galería de archivos realmente almacenados; un contador declarado no crea fotos ficticias. */
export function PhotoEvidenceGallery({eventId,declaredCount=0,incident=false}:{eventId?:string;declaredCount?:number;incident?:boolean}){
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
    void (incident?api.incidentEvidences(eventId):api.operationalEventEvidences(eventId)).then(async (refs:PhotoRef[])=>{
      const loaded=await Promise.all(refs.map(async ref=>{
        try{const blob=await (incident?api.incidentEvidenceImage(eventId,ref.id):api.operationalEventEvidenceImage(eventId,ref.id));const url=URL.createObjectURL(blob);urls.push(url);return {id:ref.id,url,purpose:ref.purpose}}
        catch{return {id:ref.id,url:null,purpose:ref.purpose}}
      }));
      if(active)setPhotos(loaded);
      else urls.forEach(url=>URL.revokeObjectURL(url));
    }).catch(reason=>{
      if(active && (incident || !(reason instanceof ApiError && reason.status===404)))setError('No se pudieron consultar las fotografías.');
    }).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false;urls.forEach(url=>URL.revokeObjectURL(url))};
  },[eventId,incident,declaredCount]);

  const available=photos.filter((photo):photo is Photo&{url:string}=>Boolean(photo.url));
  const unavailable=Math.max(0,declaredCount-available.length,photos.length-available.length);
  return <section className="nov-photo-block" aria-label="Evidencia fotográfica">
    <div className="nov-section-title"><strong>Evidencia fotográfica ({available.length})</strong></div>
    {!loading&&available.length>0&&<div className="nov-photo-grid">{available.map((photo,index)=><a className="nov-evidence-photo" href={photo.url} target="_blank" rel="noopener noreferrer" key={photo.id} aria-label={`Abrir ${reliefLabel(photo.purpose,index)}`}><img src={photo.url} alt={reliefLabel(photo.purpose,index)}/><span>{reliefLabel(photo.purpose,index)}</span></a>)}</div>}
    {loading&&<p className="nov-evidence-message">Cargando fotografías…</p>}
    {!loading&&error&&<p className="nov-evidence-message">{error}</p>}
    {!loading&&!error&&available.length>0&&unavailable>0&&<p className="nov-evidence-message">{unavailable} {unavailable===1?'fotografía declarada no tiene archivo disponible':'fotografías declaradas no tienen archivos disponibles'}.</p>}
    {!loading&&!error&&available.length===0&&<p className="nov-evidence-message">{photos.length>0?'No se pudieron cargar las fotografías. Cierre y vuelva a abrir el detalle para reintentar.':unavailable>0?`Hay imágenes declaradas sin archivo disponible.${incident?' Vuelva a adjuntarlas desde Editar incidente.':''}`:'No contiene Imagen de Evidencia.'}</p>}
  </section>;
}
