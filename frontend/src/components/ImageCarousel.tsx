import {useEffect, useId, useRef, useState, type PointerEvent, type WheelEvent} from 'react';
import {createPortal} from 'react-dom';
import {ChevronLeft, ChevronRight, Download, RotateCcw, X, ZoomIn, ZoomOut} from 'lucide-react';

export type CarouselImage={id:string;src:string;label:string;mime?:string;fit?:'cover'|'contain'};

const extension=(mime?:string)=>({'image/png':'png','image/webp':'webp','image/gif':'gif'}[mime??'']??'jpg');

type ImageCarouselProps={images:CarouselImage[];index:number;onIndexChange:(index:number)=>void;onClose:()=>void;title?:string};
type Offset={x:number;y:number};

const MIN_ZOOM=1;
const MAX_ZOOM=5;
const ZOOM_STEP=.25;

/**
 * Visor de imágenes a pantalla completa sobre cualquier modal: flechas, miniaturas, teclado (←, →, Esc) y deslizamiento.
 * Se monta en document.body y captura Esc antes que el modal que lo abrió, para cerrar solo el visor.
 */
export function ImageCarousel({images,index,onIndexChange,onClose,title='Imágenes del registro'}:ImageCarouselProps){
  const titleId=useId();
  const closeRef=useRef<HTMLButtonElement>(null);
  const thumbsRef=useRef<HTMLDivElement>(null);
  const stageRef=useRef<HTMLDivElement>(null);
  const imageRef=useRef<HTMLImageElement>(null);
  const swipeStart=useRef<number|null>(null);
  const panStart=useRef<{pointerId:number;x:number;y:number;offset:Offset}|null>(null);
  const zoomRef=useRef(MIN_ZOOM);
  const [failed,setFailed]=useState<Record<string,boolean>>({});
  const [zoom,setZoom]=useState(MIN_ZOOM);
  const [offset,setOffset]=useState<Offset>({x:0,y:0});
  const [panning,setPanning]=useState(false);
  const total=images.length;
  const current=images[Math.min(Math.max(index,0),total-1)];
  const go=(step:number)=>{if(total>1)onIndexChange((index+step+total)%total)};

  const clampOffset=(candidate:Offset,scale=zoomRef.current):Offset=>{
    const stage=stageRef.current,image=imageRef.current;
    if(!stage||!image||scale<=MIN_ZOOM)return{x:0,y:0};
    const maxX=Math.max(0,(image.clientWidth*scale-stage.clientWidth)/2);
    const maxY=Math.max(0,(image.clientHeight*scale-stage.clientHeight)/2);
    return{x:Math.max(-maxX,Math.min(maxX,candidate.x)),y:Math.max(-maxY,Math.min(maxY,candidate.y))};
  };
  const applyZoom=(value:number)=>{
    const next=Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,Number(value.toFixed(2))));
    zoomRef.current=next;
    setZoom(next);
    setOffset(previous=>clampOffset(previous,next));
  };
  const resetZoom=()=>{zoomRef.current=MIN_ZOOM;setZoom(MIN_ZOOM);setOffset({x:0,y:0});setPanning(false);panStart.current=null};

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
      else if(event.key==='+'||event.key==='='){event.preventDefault();event.stopPropagation();applyZoom(zoomRef.current+ZOOM_STEP)}
      else if(event.key==='-'){event.preventDefault();event.stopPropagation();applyZoom(zoomRef.current-ZOOM_STEP)}
      else if(event.key==='0'){event.preventDefault();event.stopPropagation();resetZoom()}
    }
    // Fase de captura en window: el Esc no llega al listener del modal que está debajo.
    window.addEventListener('keydown',onKey,true);
    return()=>window.removeEventListener('keydown',onKey,true);
  });

  useEffect(()=>{
    thumbsRef.current?.querySelector<HTMLElement>('[aria-current="true"]')?.scrollIntoView({block:'nearest',inline:'center'});
  },[index]);

  useEffect(()=>{resetZoom()},[current?.id]);

  useEffect(()=>{
    const keepInside=()=>setOffset(previous=>clampOffset(previous));
    window.addEventListener('resize',keepInside);
    return()=>window.removeEventListener('resize',keepInside);
  },[]);

  if(!current)return null;

  function onPointerDown(event:PointerEvent<HTMLDivElement>){
    if(zoomRef.current>MIN_ZOOM){
      event.preventDefault();
      event.currentTarget.setPointerCapture(event.pointerId);
      panStart.current={pointerId:event.pointerId,x:event.clientX,y:event.clientY,offset};
      setPanning(true);
      return;
    }
    swipeStart.current=event.clientX;
  }
  function onPointerMove(event:PointerEvent<HTMLDivElement>){
    const start=panStart.current;
    if(!start||start.pointerId!==event.pointerId)return;
    event.preventDefault();
    setOffset(clampOffset({x:start.offset.x+event.clientX-start.x,y:start.offset.y+event.clientY-start.y}));
  }
  function onPointerUp(event:PointerEvent){
    if(panStart.current?.pointerId===event.pointerId){
      panStart.current=null;
      setPanning(false);
      return;
    }
    if(swipeStart.current===null)return;
    const dx=event.clientX-swipeStart.current;
    swipeStart.current=null;
    if(Math.abs(dx)>50)go(dx<0?1:-1);
  }
  function onWheel(event:WheelEvent<HTMLDivElement>){
    event.preventDefault();
    event.stopPropagation();
    applyZoom(zoomRef.current+(event.deltaY<0?ZOOM_STEP:-ZOOM_STEP));
  }

  const fileName=`${current.label.replace(/[^\p{L}\p{N}]+/gu,'_').replace(/^_|_$/g,'')||'imagen'}.${extension(current.mime)}`;
  return createPortal(<div className="img-carousel" role="dialog" aria-modal="true" aria-labelledby={titleId}
      onMouseDown={event=>{if(event.target===event.currentTarget)onClose()}} onClick={event=>event.stopPropagation()}>
    <header className="img-carousel-head">
      <div><h3 id={titleId}>{title}</h3><span aria-live="polite">{current.label} · {Math.min(index+1,total)} de {total}</span></div>
      <div className="img-carousel-tools">
        <button type="button" onClick={()=>applyZoom(zoomRef.current-ZOOM_STEP)} disabled={zoom<=MIN_ZOOM} aria-label="Alejar imagen" title="Alejar (-)"><ZoomOut size={18}/></button>
        <button type="button" className="img-carousel-zoom-value" onClick={resetZoom} disabled={zoom===MIN_ZOOM} aria-label={`Restablecer zoom, actualmente ${Math.round(zoom*100)} %`} title="Restablecer zoom (0)"><RotateCcw size={15}/><span>{Math.round(zoom*100)}%</span></button>
        <button type="button" onClick={()=>applyZoom(zoomRef.current+ZOOM_STEP)} disabled={zoom>=MAX_ZOOM} aria-label="Acercar imagen" title="Acercar (+)"><ZoomIn size={18}/></button>
        <a href={current.src} download={fileName} aria-label={`Descargar ${current.label}`} title="Descargar"><Download size={18}/></a>
        <button type="button" ref={closeRef} onClick={onClose} aria-label="Cerrar visor" title="Cerrar (Esc)"><X size={20}/></button>
      </div>
    </header>
    <div ref={stageRef} className={`img-carousel-stage${zoom>MIN_ZOOM?' zoomed':''}${panning?' panning':''}`} onMouseDown={event=>{if(event.target===event.currentTarget&&zoom===MIN_ZOOM)onClose()}}
      onWheel={onWheel} onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerCancel={onPointerUp}
      onDoubleClick={()=>zoom>MIN_ZOOM?resetZoom():applyZoom(2)} title="Use la rueda del mouse para acercar o alejar">
      {total>1&&<button type="button" className="img-carousel-nav prev" onClick={()=>go(-1)} aria-label="Imagen anterior" title="Anterior (←)"><ChevronLeft size={26}/></button>}
      {failed[current.id]
        ?<p className="img-carousel-error">No se pudo mostrar esta imagen.</p>
        :<img ref={imageRef} key={current.id} src={current.src} alt={current.label} draggable={false}
          style={{transform:`translate3d(${offset.x}px,${offset.y}px,0) scale(${zoom})`}}
          onLoad={()=>setOffset(previous=>clampOffset(previous))} onError={()=>setFailed(prev=>({...prev,[current.id]:true}))}/>}
      {total>1&&<button type="button" className="img-carousel-nav next" onClick={()=>go(1)} aria-label="Imagen siguiente" title="Siguiente (→)"><ChevronRight size={26}/></button>}
      <span className="img-carousel-zoom-help">Rueda: zoom{zoom>MIN_ZOOM?' · Arrastra para mover':' · Doble clic para ampliar'}</span>
    </div>
    {total>1&&<div className="img-carousel-thumbs" ref={thumbsRef} role="tablist" aria-label="Miniaturas">
      {images.map((image,i)=><button type="button" role="tab" key={image.id} aria-selected={i===index} aria-current={i===index} aria-label={`${image.label} (${i+1} de ${total})`}
        className={i===index?'active':''} onClick={()=>onIndexChange(i)}><img src={image.src} alt="" style={{objectFit:image.fit??'cover'}}/></button>)}
    </div>}
  </div>,document.body);
}
