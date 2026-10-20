import {useEffect, useId, useRef, useState, type PointerEvent} from 'react';
import {createPortal} from 'react-dom';
import {ChevronLeft, ChevronRight, Download, X} from 'lucide-react';

export type CarouselImage={id:string;src:string;label:string;mime?:string;fit?:'cover'|'contain'};

const extension=(mime?:string)=>({'image/png':'png','image/webp':'webp','image/gif':'gif'}[mime??'']??'jpg');

type ImageCarouselProps={images:CarouselImage[];index:number;onIndexChange:(index:number)=>void;onClose:()=>void;title?:string};

/**
 * Visor de imágenes a pantalla completa sobre cualquier modal: flechas, miniaturas, teclado (←, →, Esc) y deslizamiento.
 * Se monta en document.body y captura Esc antes que el modal que lo abrió, para cerrar solo el visor.
 */
export function ImageCarousel({images,index,onIndexChange,onClose,title='Imágenes del registro'}:ImageCarouselProps){
  const titleId=useId();
  const closeRef=useRef<HTMLButtonElement>(null);
  const thumbsRef=useRef<HTMLDivElement>(null);
  const swipeStart=useRef<number|null>(null);
  const [failed,setFailed]=useState<Record<string,boolean>>({});
  const total=images.length;
  const current=images[Math.min(Math.max(index,0),total-1)];
  const go=(step:number)=>{if(total>1)onIndexChange((index+step+total)%total)};

  useEffect(()=>{
    const previous=document.activeElement as HTMLElement|null;
    closeRef.current?.focus();
    const overflow=document.body.style.overflow;
    document.body.style.overflow='hidden';
    return()=>{document.body.style.overflow=overflow;previous?.focus?.()};
  },[]);

  useEffect(()=>{
    function onKey(event:KeyboardEvent){
      if(event.key==='Escape'){event.preventDefault();event.stopPropagation();onClose()}
      else if(event.key==='ArrowRight'){event.preventDefault();event.stopPropagation();go(1)}
      else if(event.key==='ArrowLeft'){event.preventDefault();event.stopPropagation();go(-1)}
    }
    // Fase de captura en window: el Esc no llega al listener del modal que está debajo.
    window.addEventListener('keydown',onKey,true);
    return()=>window.removeEventListener('keydown',onKey,true);
  });

  useEffect(()=>{
    thumbsRef.current?.querySelector<HTMLElement>('[aria-current="true"]')?.scrollIntoView({block:'nearest',inline:'center'});
  },[index]);

  if(!current)return null;

  function onPointerDown(event:PointerEvent){swipeStart.current=event.clientX}
  function onPointerUp(event:PointerEvent){
    if(swipeStart.current===null)return;
    const dx=event.clientX-swipeStart.current;
    swipeStart.current=null;
    if(Math.abs(dx)>50)go(dx<0?1:-1);
  }

  const fileName=`${current.label.replace(/[^\p{L}\p{N}]+/gu,'_').replace(/^_|_$/g,'')||'imagen'}.${extension(current.mime)}`;
  return createPortal(<div className="img-carousel" role="dialog" aria-modal="true" aria-labelledby={titleId}
      onMouseDown={event=>{if(event.target===event.currentTarget)onClose()}} onClick={event=>event.stopPropagation()}>
    <header className="img-carousel-head">
      <div><h3 id={titleId}>{title}</h3><span aria-live="polite">{current.label} · {Math.min(index+1,total)} de {total}</span></div>
      <div className="img-carousel-tools">
        <a href={current.src} download={fileName} aria-label={`Descargar ${current.label}`} title="Descargar"><Download size={18}/></a>
        <button type="button" ref={closeRef} onClick={onClose} aria-label="Cerrar visor" title="Cerrar (Esc)"><X size={20}/></button>
      </div>
    </header>
    <div className="img-carousel-stage" onMouseDown={event=>{if(event.target===event.currentTarget)onClose()}} onPointerDown={onPointerDown} onPointerUp={onPointerUp}>
      {total>1&&<button type="button" className="img-carousel-nav prev" onClick={()=>go(-1)} aria-label="Imagen anterior" title="Anterior (←)"><ChevronLeft size={26}/></button>}
      {failed[current.id]
        ?<p className="img-carousel-error">No se pudo mostrar esta imagen.</p>
        :<img key={current.id} src={current.src} alt={current.label} draggable={false} onError={()=>setFailed(prev=>({...prev,[current.id]:true}))}/>}
      {total>1&&<button type="button" className="img-carousel-nav next" onClick={()=>go(1)} aria-label="Imagen siguiente" title="Siguiente (→)"><ChevronRight size={26}/></button>}
    </div>
    {total>1&&<div className="img-carousel-thumbs" ref={thumbsRef} role="tablist" aria-label="Miniaturas">
      {images.map((image,i)=><button type="button" role="tab" key={image.id} aria-selected={i===index} aria-current={i===index} aria-label={`${image.label} (${i+1} de ${total})`}
        className={i===index?'active':''} onClick={()=>onIndexChange(i)}><img src={image.src} alt="" style={{objectFit:image.fit??'cover'}}/></button>)}
    </div>}
  </div>,document.body);
}
