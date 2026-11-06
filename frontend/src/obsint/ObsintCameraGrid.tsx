import {useCallback, useEffect, useRef, useState, type MouseEvent} from 'react';
import {Crosshair, Maximize2, Pause, Play, Video, VideoOff} from 'lucide-react';
import {formatDateTime, streamSrc} from './obsint';
import type {ObsintCamera} from './obsintApi';
import {attachStream, streamKind, type RenewUrl, type StreamStatus} from './liveStream';

type Props={
  cameras:ObsintCamera[];
  selectedId:string|null;
  onSelect:(cameraId:string)=>void;
  now:Date;
  /** Entrega cada video al padre para capturar fotogramas; null al desmontar. */
  registerVideo:(cameraId:string,el:HTMLVideoElement|null)=>void;
  /** Todas las cámaras cargaron (o fallaron), o pasó el tiempo de espera. Una vez por juego de cámaras. */
  onAllSettled:()=>void;
  /** URL nueva de una cámara cuando la actual pierde su permiso (vence); null si ya no tiene video. */
  renewStreamUrl?:(cameraId:string)=>Promise<string|null>;
};

const SETTLE_TIMEOUT_MS=5000;

/** Cámara seleccionada en grande y las demás en miniaturas desplazables; todas mantienen su video activo para capturar evidencia. */
export function ObsintCameraGrid({cameras,selectedId,onSelect,now,registerVideo,onAllSettled,renewStreamUrl}:Props){
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
  const main=cameras.find(camera=>camera.id===selectedId)??cameras[0];
  const thumbnails=cameras.filter(camera=>camera.id!==main.id);
  const feed=(camera:ObsintCamera)=><Feed key={camera.id} camera={camera} index={cameras.indexOf(camera)} now={now} selected={camera.id===main.id}
    onSelect={()=>onSelect(camera.id)} registerVideo={registerVideo} onSettled={handleSettled}
    renew={renewStreamUrl?()=>renewStreamUrl(camera.id):undefined}/>;
  return <div className={`obs-camera-grid${cameras.length===1?' count-1':''}`}>
    <div className="obs-camera-main">{feed(main)}</div>
    {thumbnails.length>0&&<div className="obs-camera-thumbs" aria-label="Otras cámaras"><div className="obs-camera-thumbs-track">{thumbnails.map(feed)}</div></div>}
  </div>;
}

function Feed({camera,index,now,selected,onSelect,registerVideo,onSettled,renew}:{camera:ObsintCamera;index:number;now:Date;selected:boolean;onSelect:()=>void;
  registerVideo:(cameraId:string,el:HTMLVideoElement|null)=>void;onSettled:(cameraId:string)=>void;renew?:RenewUrl}){
  const renewRef=useRef(renew);
  renewRef.current=renew;
  const [paused,setPaused]=useState(false);
  const [status,setStatus]=useState<StreamStatus|'no-signal'>(camera.streamUrl?'connecting':'no-signal');
  const boxRef=useRef<HTMLDivElement|null>(null);
  const videoRef=useRef<HTMLVideoElement|null>(null);
  const videoEl=useCallback((el:HTMLVideoElement|null)=>{videoRef.current=el;registerVideo(camera.id,el)},[camera.id,registerVideo]);
  // HLS y WebRTC son video en vivo; un archivo (MP4 de pruebas) se repite como grabación.
  const live=camera.live||(!!camera.streamUrl&&streamKind(camera.streamUrl)!=='file');
  const kind=camera.streamUrl?streamKind(camera.streamUrl):null;
  // Sin video configurado no hay nada que esperar para arrancar el tiempo.
  useEffect(()=>{setStatus(camera.streamUrl?'connecting':'no-signal');if(!camera.streamUrl)onSettled(camera.id)},[camera.id,camera.streamUrl]);
  // Conecta el video según su formato (MP4, HLS o WebRTC); al cambiar de cámara o desmontar, lo desconecta.
  useEffect(()=>{
    const video=videoRef.current;
    if(!video||!camera.streamUrl)return;
    return attachStream(video,camera.streamUrl,next=>{setStatus(next);if(next==='playing'||next==='retrying'||next==='denied'||next==='failed')onSettled(camera.id)},
      renewRef.current?()=>renewRef.current!():undefined);
  },[camera.id,camera.streamUrl]);

  const loaded=()=>{setStatus('playing');onSettled(camera.id)};
  const fileError=()=>{
    if(kind!=='file'||!camera.streamUrl)return;
    onSettled(camera.id);
    void fetch(streamSrc(camera.streamUrl),{method:'HEAD'}).then(res=>setStatus(res.status===403?'denied':'failed')).catch(()=>setStatus('failed'));
  };

  function togglePlay(e:MouseEvent){
    e.stopPropagation();
    const video=videoRef.current;if(!video)return;
    if(video.paused){void video.play();setPaused(false)}else{video.pause();setPaused(true)}
  }
  function fullscreen(e:MouseEvent){e.stopPropagation();void boxRef.current?.requestFullscreen?.();}

  return <div ref={boxRef} className={`obs-feed${selected?' selected':''}`} onClick={onSelect}>
    {camera.streamUrl&&<video ref={videoEl} autoPlay muted playsInline preload="auto" loop={!live} onLoadedData={loaded} onPlaying={loaded} onError={fileError}/>}
    {status!=='playing'&&<div className="obs-no-signal obs-feed-status" role="status"><VideoOff size={34}/>
      <span>{status==='connecting'?'Conectando…':status==='retrying'?'Sin señal — reintentando':status==='denied'?'Sin acceso a esta cámara':'Sin señal'}</span>
      {status==='no-signal'&&<small>Video en vivo pendiente de conexión</small>}
    </div>}
    <div className="obs-feed-title"><Video size={16}/><div><b>{camera.name||`Cámara ${String(index+1).padStart(2,'0')}`}</b><span>{camera.location??camera.code}</span>{selected&&<em>Seleccionada para evidencia</em>}</div></div>
    <div className={`obs-feed-badge ${live&&status==='playing'?'live':''}`}><i/>{live?'EN VIVO':'GRABACIÓN'}</div>
    <div className="obs-feed-time">{formatDateTime(now)}</div>
    {status==='playing'&&<div className="obs-feed-controls">
      <button type="button" title="Usar para evidencia" aria-label="Usar para evidencia" onClick={e=>{e.stopPropagation();onSelect()}}><Crosshair size={15}/></button>
      <button type="button" title={paused?'Reproducir':'Pausar'} aria-label={paused?'Reproducir':'Pausar'} onClick={togglePlay}>{paused?<Play size={15}/>:<Pause size={15}/>}</button>
      <button type="button" title="Pantalla completa" aria-label="Pantalla completa" onClick={fullscreen}><Maximize2 size={15}/></button>
    </div>}
  </div>;
}
