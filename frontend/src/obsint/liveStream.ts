import {streamSrc} from './obsint';

/** Formato del video de una cámara según su URL: archivo (MP4), HLS (.m3u8) o WebRTC (WHEP, termina en /whep). */
export type StreamKind='file'|'hls'|'webrtc';

export function streamKind(url:string):StreamKind{
  const path=url.split(/[?#]/)[0].toLowerCase();
  if(path.endsWith('.m3u8'))return 'hls';
  if(path.endsWith('/whep'))return 'webrtc';
  return 'file';
}

/**
 * Conecta el video de la cámara al <video> y devuelve la función que lo desconecta. El video siempre termina en el mismo
 * <video>, así que los controles y la captura de evidencia no cambian. onError avisa que no se pudo reproducir.
 */
export function attachStream(video:HTMLVideoElement,url:string,onError:()=>void):()=>void{
  const src=streamSrc(url);
  switch(streamKind(url)){
    case 'hls':return attachHls(video,src,onError);
    case 'webrtc':return attachWhep(video,src,onError);
    default:return attachFile(video,src);
  }
}

function attachFile(video:HTMLVideoElement,src:string){
  video.src=src;
  return()=>{video.removeAttribute('src');video.load();};
}

/**
 * HLS con hls.js (se carga recién cuando hace falta): reintenta mientras el servidor prepara la lista de video. El HLS
 * nativo del navegador queda solo donde hls.js no funciona (Safari en iPhone), porque no reintenta.
 */
function attachHls(video:HTMLVideoElement,src:string,onError:()=>void){
  let closed=false;
  let hls:{destroy:()=>void}|null=null;
  let native:(()=>void)|null=null;
  void import('hls.js').then(({default:Hls})=>{
    if(closed)return;
    if(!Hls.isSupported()){
      if(video.canPlayType('application/vnd.apple.mpegurl'))native=attachFile(video,src);else onError();
      return;
    }
    const player=new Hls({lowLatencyMode:true,manifestLoadingMaxRetry:5,manifestLoadingRetryDelay:1000,levelLoadingMaxRetry:5});
    player.on(Hls.Events.ERROR,(_event,data)=>{if(data.fatal&&!closed)onError();});
    player.loadSource(src);
    player.attachMedia(video);
    hls=player;
  }).catch(()=>{if(!closed)onError();});
  return()=>{closed=true;hls?.destroy();native?.();};
}

/**
 * WebRTC con WHEP (estándar de MediaMTX y otros servidores): una oferta SDP por POST y la respuesta del servidor.
 * Requiere H.264 sin B-frames (lo normal en cámaras IP). Si en 10 s no llega imagen, se da por fallido.
 */
function attachWhep(video:HTMLVideoElement,src:string,onError:()=>void){
  const pc=new RTCPeerConnection();
  let closed=false;
  let session:string|null=null;
  const fail=()=>{if(!closed){closed=true;onError();}};
  const watchdog=setTimeout(()=>{if(video.readyState<2)fail();},10000);
  pc.addTransceiver('video',{direction:'recvonly'});
  pc.ontrack=event=>{video.srcObject=event.streams[0]??new MediaStream([event.track]);};
  pc.onconnectionstatechange=()=>{if(pc.connectionState==='failed'||pc.connectionState==='closed')fail();};
  void (async()=>{
    await pc.setLocalDescription(await pc.createOffer());
    await iceGathered(pc);
    const res=await fetch(src,{method:'POST',headers:{'Content-Type':'application/sdp'},body:pc.localDescription?.sdp});
    if(!res.ok)throw new Error(`WHEP ${res.status}`);
    const location=res.headers.get('Location');
    if(location)session=new URL(location,res.url).toString();
    await pc.setRemoteDescription({type:'answer',sdp:await res.text()});
  })().catch(fail);
  return()=>{
    closed=true;
    clearTimeout(watchdog);
    pc.close();
    video.srcObject=null;
    if(session)void fetch(session,{method:'DELETE'}).catch(()=>undefined);
  };
}

/** Espera a que el navegador junte sus candidatos de conexión (máximo 2 s) para enviarlos en la misma oferta. */
function iceGathered(pc:RTCPeerConnection){
  return new Promise<void>(resolve=>{
    if(pc.iceGatheringState==='complete')return resolve();
    const timer=setTimeout(resolve,2000);
    pc.addEventListener('icegatheringstatechange',()=>{if(pc.iceGatheringState==='complete'){clearTimeout(timer);resolve();}});
  });
}
