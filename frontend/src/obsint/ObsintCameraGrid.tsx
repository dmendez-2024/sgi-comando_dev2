import {useEffect, useRef, useState, type MouseEvent} from 'react';
import {Crosshair, Maximize2, Pause, Play, Video, VideoOff} from 'lucide-react';
import {formatDateTime, streamSrc, type ObsintCamera} from './obsint';

type Props={
  cameras:ObsintCamera[];
  selectedId:string|null;
  onSelect:(cameraId:string)=>void;
  now:Date;
  /** Entrega cada video al padre para capturar fotogramas; null al desmontar. */
  registerVideo:(cameraId:string,el:HTMLVideoElement|null)=>void;
  /** Todas las cámaras cargaron (o fallaron), o pasó el tiempo de espera. Una vez por juego de cámaras. */
  onAllSettled:()=>void;
};

const SETTLE_TIMEOUT_MS=5000;

/** Grilla de 1 a 4 cámaras (con 3, la tercera ocupa todo el ancho). Clic en una cámara la elige para capturar evidencia. */
export function ObsintCameraGrid({cameras,selectedId,onSelect,now,registerVideo,onAllSettled}:Props){
  const settled=useRef(new Set<string>());
  const notified=useRef(false);
  const onAllSettledRef=useRef(onAllSettled);
  onAllSettledRef.current=onAllSettled;
  const key=cameras.map(c=>c.id).join();
  const notify=()=>{if(notified.current)return;notified.current=true;onAllSettledRef.current();};

  useEffect(()=>{
    settled.current=new Set();
    notified.current=false;
    if(!cameras.length){notify();return;}
    const id=setTimeout(notify,SETTLE_TIMEOUT_MS);
    return()=>clearTimeout(id);
  },[key]);

  const handleSettled=(cameraId:string)=>{settled.current.add(cameraId);if(settled.current.size>=cameras.length)notify();};

  if(!cameras.length)return <div className="obs-no-cameras">Esta consigna no tiene cámaras asociadas.</div>;
  return <div className={`obs-camera-grid count-${Math.min(cameras.length,4)}`}>
    {cameras.slice(0,4).map((camera,i)=><Feed key={camera.id} camera={camera} index={i} now={now} selected={camera.id===selectedId}
      onSelect={()=>onSelect(camera.id)} registerVideo={registerVideo} onSettled={handleSettled}/>)}
  </div>;
}

function Feed({camera,index,now,selected,onSelect,registerVideo,onSettled}:{camera:ObsintCamera;index:number;now:Date;selected:boolean;onSelect:()=>void;
  registerVideo:(cameraId:string,el:HTMLVideoElement|null)=>void;onSettled:(cameraId:string)=>void}){
  const [paused,setPaused]=useState(false);
  const [failed,setFailed]=useState(false);
  const boxRef=useRef<HTMLDivElement|null>(null);
  const videoRef=useRef<HTMLVideoElement|null>(null);

  function togglePlay(e:MouseEvent){
    e.stopPropagation();
    const video=videoRef.current;if(!video)return;
    if(video.paused){void video.play();setPaused(false)}else{video.pause();setPaused(true)}
  }
  function fullscreen(e:MouseEvent){e.stopPropagation();void boxRef.current?.requestFullscreen?.();}

  return <div ref={boxRef} className={`obs-feed${selected?' selected':''}`} onClick={onSelect}>
    {failed
      ?<div className="obs-no-signal"><VideoOff size={34}/><span>Sin señal</span><small>{camera.streamUrl}</small></div>
      :<video ref={el=>{videoRef.current=el;registerVideo(camera.id,el)}} src={streamSrc(camera.streamUrl)} autoPlay muted playsInline preload="auto"
          loop={!camera.live} onLoadedData={()=>onSettled(camera.id)} onError={()=>{setFailed(true);onSettled(camera.id)}}/>}
    <div className="obs-feed-title"><Video size={16}/><div><b>{camera.name||`Cámara ${String(index+1).padStart(2,'0')}`}</b><span>{camera.location??camera.code}</span></div></div>
    <div className={`obs-feed-badge ${camera.live?'live':''}`}><i/>{camera.live?'EN VIVO':'GRABACIÓN'}</div>
    <div className="obs-feed-time">{formatDateTime(now)}</div>
    {!failed&&<div className="obs-feed-controls">
      <button type="button" title="Usar para evidencia" aria-label="Usar para evidencia" onClick={e=>{e.stopPropagation();onSelect()}}><Crosshair size={15}/></button>
      <button type="button" title={paused?'Reproducir':'Pausar'} aria-label={paused?'Reproducir':'Pausar'} onClick={togglePlay}>{paused?<Play size={15}/>:<Pause size={15}/>}</button>
      <button type="button" title="Pantalla completa" aria-label="Pantalla completa" onClick={fullscreen}><Maximize2 size={15}/></button>
    </div>}
  </div>;
}
