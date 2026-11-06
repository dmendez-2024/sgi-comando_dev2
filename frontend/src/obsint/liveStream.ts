import {streamSrc} from './obsint';

/** Formato del video de una cámara según su URL: archivo (MP4), HLS (.m3u8) o WebRTC (WHEP, termina en /whep). */
export type StreamKind='file'|'hls'|'webrtc';
export type StreamStatus='connecting'|'playing'|'retrying'|'denied'|'failed';

export function streamKind(url:string):StreamKind{
  const path=url.split(/[?#]/)[0].toLowerCase();
  if(path.endsWith('.m3u8'))return 'hls';
  if(path.endsWith('/whep'))return 'webrtc';
  return 'file';
}

/** Pide a Comando una URL de video nueva (con un permiso vigente); null si ya no hay video para esa cámara. */
export type RenewUrl=()=>Promise<string|null>;

/**
 * Conecta el video de la cámara al <video> y devuelve la función que lo desconecta. El video siempre termina en el mismo
 * <video>, así que los controles y la captura de evidencia no cambian. renew: si el servidor de video rechaza el permiso
 * de la URL (401, por ejemplo porque venció), se pide una URL nueva y se reconecta solo.
 */
export function attachStream(video:HTMLVideoElement,url:string,onStatus:(status:StreamStatus)=>void,renew?:RenewUrl):()=>void{
  const src=streamSrc(url);
  switch(streamKind(url)){
    case 'hls':return attachHls(video,src,onStatus,renew);
    case 'webrtc':return attachWhep(video,src,onStatus);
    default:return attachFile(video,src,onStatus);
  }
}

function attachFile(video:HTMLVideoElement,src:string,onStatus:(status:StreamStatus)=>void){
  onStatus('connecting');
  video.src=src;
  return()=>{video.removeAttribute('src');video.load();};
}

/**
 * HLS con hls.js (se carga recién cuando hace falta). Los fallos de red y los congelamientos reinician la sesión;
 * un 403 detiene los reintentos. Un 401 (permiso de la URL vencido o inválido) pide una URL nueva una vez; si la nueva
 * también es rechazada antes de reproducir, se detiene. Safari usa HLS nativo con la misma vigilancia de progreso.
 */
function attachHls(video:HTMLVideoElement,initialSrc:string,onStatus:(status:StreamStatus)=>void,renew?:RenewUrl){
  let src=initialSrc;
  let renewing=false;
  let renewedWithoutPlay=false;
  const RETRY_MS=3000;
  const START_TIMEOUT_MS=12000;
  const STALL_TIMEOUT_MS=10000;
  let closed=false;
  let denied=false;
  let HlsClass:typeof import('hls.js').default|null=null;
  let hls:import('hls.js').default|null=null;
  let native=false;
  let retryTimer:ReturnType<typeof setTimeout>|null=null;
  let stallTimer:ReturnType<typeof setTimeout>|null=null;
  let attemptAt=Date.now();
  let lastProgress=attemptAt;
  let lastTime=video.currentTime;
  let hasPlayed=false;

  const clearStall=()=>{if(stallTimer){clearTimeout(stallTimer);stallTimer=null}};
  const detach=()=>{
    hls?.destroy();hls=null;
    video.removeAttribute('src');video.load();
  };
  const deny=()=>{
    if(closed||denied)return;
    denied=true;clearStall();
    if(retryTimer){clearTimeout(retryTimer);retryTimer=null}
    detach();onStatus('denied');
  };
  const retry=(delay=RETRY_MS)=>{
    if(closed||denied||retryTimer)return;
    clearStall();onStatus('retrying');
    retryTimer=setTimeout(()=>{retryTimer=null;start()},delay);
    detach();
  };
  /** 401: la URL perdió su permiso. Se pide otra a Comando; sin renovación posible, o si la nueva tampoco sirve, se detiene. */
  const unauthorized=()=>{
    if(closed||denied||renewing)return;
    if(!renew||renewedWithoutPlay){deny();return}
    renewing=true;clearStall();onStatus('retrying');detach();
    void renew().then(next=>{
      renewing=false;
      if(closed||denied)return;
      if(!next||streamKind(next)!=='hls'){deny();return}
      src=streamSrc(next);renewedWithoutPlay=true;start();
    }).catch(()=>{renewing=false;retry()});
  };
  const rejected=(status:number|undefined)=>{
    if(status===403){deny();return true}
    if(status===401){unauthorized();return true}
    return false;
  };
  const checkNativeAccess=()=>{
    void fetch(src,{method:'HEAD'}).then(res=>{if(!rejected(res.status))retry()}).catch(()=>retry());
  };
  const onPlaying=()=>{if(closed||denied)return;hasPlayed=true;renewedWithoutPlay=false;lastProgress=Date.now();lastTime=video.currentTime;clearStall();onStatus('playing')};
  const onTimeUpdate=()=>{if(Math.abs(video.currentTime-lastTime)>.05){lastTime=video.currentTime;lastProgress=Date.now()}};
  const onWaiting=()=>{if(closed||denied||!hasPlayed||video.paused||stallTimer)return;
    onStatus('retrying');stallTimer=setTimeout(()=>{stallTimer=null;retry(0)},RETRY_MS)};
  const onVideoError=()=>{if(closed||denied||renewing)return;if(native)checkNativeAccess();else retry()};
  video.addEventListener('playing',onPlaying);
  video.addEventListener('timeupdate',onTimeUpdate);
  video.addEventListener('waiting',onWaiting);
  video.addEventListener('stalled',onWaiting);
  video.addEventListener('error',onVideoError);
  const watchdog=setInterval(()=>{
    if(closed||denied||retryTimer||renewing)return;
    const now=Date.now();
    if(!hasPlayed&&now-attemptAt>START_TIMEOUT_MS)retry();
    else if(hasPlayed&&!video.paused&&now-lastProgress>STALL_TIMEOUT_MS)retry();
  },2000);

  function start(){
    if(closed||denied||renewing)return;
    attemptAt=Date.now();lastProgress=attemptAt;lastTime=video.currentTime;hasPlayed=false;
    if(!HlsClass){void import('hls.js').then(({default:Hls})=>{if(closed||denied)return;HlsClass=Hls;start()}).catch(()=>retry());return}
    if(!HlsClass.isSupported()){
      if(!video.canPlayType('application/vnd.apple.mpegurl')){onStatus('failed');return}
      native=true;video.src=src;video.load();void video.play().catch(()=>undefined);return;
    }
    native=false;
    const player=new HlsClass({lowLatencyMode:true,manifestLoadingMaxRetry:0,levelLoadingMaxRetry:0,fragLoadingMaxRetry:0});
    hls=player;
    player.on(HlsClass.Events.ERROR,(_event,data)=>{
      if(closed||denied||renewing)return;
      if(rejected(data.response?.code??data.networkDetails?.status))return;
      if(data.type===HlsClass!.ErrorTypes.NETWORK_ERROR||data.fatal)retry();
    });
    player.loadSource(src);player.attachMedia(video);
  }
  onStatus('connecting');start();
  return()=>{
    closed=true;clearStall();clearInterval(watchdog);
    if(retryTimer)clearTimeout(retryTimer);
    video.removeEventListener('playing',onPlaying);
    video.removeEventListener('timeupdate',onTimeUpdate);
    video.removeEventListener('waiting',onWaiting);
    video.removeEventListener('stalled',onWaiting);
    video.removeEventListener('error',onVideoError);
    detach();
  };
}

/**
 * WebRTC con WHEP (estándar de MediaMTX y otros servidores): una oferta SDP por POST y la respuesta del servidor.
 * Requiere H.264 sin B-frames (lo normal en cámaras IP). Si en 10 s no llega imagen, se da por fallido.
 */
function attachWhep(video:HTMLVideoElement,src:string,onStatus:(status:StreamStatus)=>void){
  const pc=new RTCPeerConnection();
  let closed=false;
  let session:string|null=null;
  let forbidden=false;
  onStatus('connecting');
  const fail=()=>{if(!closed){closed=true;onStatus(forbidden?'denied':'failed');}};
  const watchdog=setTimeout(()=>{if(video.readyState<2)fail();},10000);
  pc.addTransceiver('video',{direction:'recvonly'});
  pc.ontrack=event=>{video.srcObject=event.streams[0]??new MediaStream([event.track]);};
  pc.onconnectionstatechange=()=>{if(pc.connectionState==='failed'||pc.connectionState==='closed')fail();};
  void (async()=>{
    await pc.setLocalDescription(await pc.createOffer());
    await iceGathered(pc);
    const res=await fetch(src,{method:'POST',headers:{'Content-Type':'application/sdp'},body:pc.localDescription?.sdp});
    if(!res.ok){forbidden=res.status===403||res.status===401;throw new Error(`WHEP ${res.status}`)}
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
