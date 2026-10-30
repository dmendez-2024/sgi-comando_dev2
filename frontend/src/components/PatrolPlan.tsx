import {useEffect,useRef,useState} from 'react';
import './PatrolPlan.css';
import {Map as MapIcon,Smartphone} from 'lucide-react';

type Checkpoint={id:string;code:string;atsX?:number|null;atsY?:number|null};
type Props={planUrl:string;checkpoints:Checkpoint[];selectedId?:string;onChoose?:(id:string)=>void;zoom?:number;selection?:{x:number;y:number}|null;onSelect?:(x:number,y:number)=>void;fitToView?:boolean};

export default function PatrolPlan({planUrl,checkpoints,selectedId,onChoose,zoom=1,selection,onSelect,fitToView=false}:Props){
  const [loadedUrl,setLoadedUrl]=useState('');
  const loaded=loadedUrl===planUrl;
  const [failed,setFailed]=useState(false);
  const scrollRef=useRef<HTMLDivElement>(null);
  const [viewport,setViewport]=useState({width:0,height:0});
  const [imageSize,setImageSize]=useState({width:0,height:0});
  useEffect(()=>{
    if(!fitToView||!scrollRef.current)return;
    const scroll=scrollRef.current;
    const measure=()=>setViewport(current=>{
      const width=scroll.clientWidth,height=scroll.clientHeight;
      return current.width===width&&current.height===height?current:{width,height};
    });
    const observer=new ResizeObserver(measure);
    observer.observe(scroll);
    measure();
    return()=>observer.disconnect();
  },[fitToView]);
  const fitWidth=viewport.width&&viewport.height&&imageSize.width&&imageSize.height
    ?Math.min(viewport.width,viewport.height*imageSize.width/imageSize.height):0;
  useEffect(()=>{setFailed(false)},[planUrl]);
  if(!planUrl)return <p>Plano ATS no disponible. Importe un plano para ubicar los hitos.</p>;
  return <div ref={scrollRef} className="pat-plan-scroll">
    {failed&&<p role="alert">No se pudo cargar el plano ATS.</p>}
    <div className="pat-plan-surface" style={{width:fitToView&&fitWidth?`${fitWidth*zoom}px`:`${zoom*100}%`,marginInline:fitToView?"auto":undefined}} onClick={event=>{
      if(!onSelect||!loaded||failed)return;
      const image=event.currentTarget.querySelector('img');
      if(!image)return;
      const r=image.getBoundingClientRect();
      if(!r.width||!r.height)return;
      const x=(event.clientX-r.left)/r.width,y=(event.clientY-r.top)/r.height;
      if(x>=0&&x<=1&&y>=0&&y<=1)onSelect(x,y);
    }}>
      <img src={planUrl} alt="Plano ATS de la patrulla" draggable={false} onLoad={event=>{setLoadedUrl(planUrl);setImageSize({width:event.currentTarget.naturalWidth,height:event.currentTarget.naturalHeight})}} onError={()=>setFailed(true)}/>
      {loaded&&!failed&&checkpoints.filter(cp=>cp.atsX!=null&&cp.atsY!=null).map(cp=><button type="button" key={cp.id} className={`pat-marker ${cp.id===selectedId?'selected':''}`} style={{left:`${cp.atsX!*100}%`,top:`${cp.atsY!*100}%`}} onClick={event=>{event.stopPropagation();if(onSelect)onSelect(cp.atsX!,cp.atsY!);else onChoose?.(cp.id)}} title={cp.code}>{cp.code}</button>)}
      {loaded&&selection&&<span className="pat-plan-target" style={{left:`${selection.x*100}%`,top:`${selection.y*100}%`}} aria-label="Ubicación seleccionada"/>}
    </div>
  </div>;
}

export function PatrolPlanPicker({planUrl,checkpoints,selectedId,error,onCancel,onConfirm,readOnly=false,onAddPlan,onAddField,title}:{planUrl:string;checkpoints:Checkpoint[];selectedId?:string;error:string;onCancel:()=>void;onConfirm?:(x:number,y:number)=>Promise<void>;readOnly?:boolean;onAddPlan?:()=>void;onAddField?:()=>void;title?:string}){
  const dialog=useRef<HTMLDialogElement>(null);
  const cp=checkpoints.find(item=>item.id===selectedId);
  const [selection,setSelection]=useState<{x:number;y:number}|null>(cp?.atsX!=null&&cp.atsY!=null?{x:cp.atsX,y:cp.atsY}:null);
  const [zoom,setZoom]=useState(1);
  const [zoomInput,setZoomInput]=useState('100');
  const updateZoom=(percent:number)=>{
    const next=Math.max(25,Math.min(400,Math.round(percent)));
    setZoom(next/100);
    setZoomInput(String(next));
  };
  const commitZoom=()=>{
    const entered=Number(zoomInput);
    if(!Number.isFinite(entered)||!zoomInput.trim()){setZoomInput(String(Math.round(zoom*100)));return;}
    updateZoom(entered);
  };
  const [busy,setBusy]=useState(false);
  useEffect(()=>{const node=dialog.current;node?.showModal();return()=>node?.close()},[]);
  return <dialog ref={dialog} className="pat-plan-dialog" aria-labelledby="pat-plan-title" onCancel={event=>{event.preventDefault();if(!busy)onCancel()}}>
    <header><div><h3 id="pat-plan-title">{title??(readOnly?'Mapa de la patrulla':`${selectedId?'Reubicar hito':'Agregar hito'} en plano`)}</h3><p>{readOnly?'Amplíe el plano para consultar la ubicación de los hitos.':'Amplíe el plano y haga clic en la ubicación exacta. Confirme para guardarla.'}</p></div><button type="button" disabled={busy} onClick={onCancel} aria-label="Cerrar plano">×</button></header>
    <div className="pat-plan-toolbar">
    {readOnly&&(onAddPlan||onAddField)&&<div className="pat-plan-create-actions">
      {onAddPlan&&<button type="button" onClick={onAddPlan}><MapIcon size={14}/>Agregar hito en plano</button>}
      {onAddField&&<button type="button" className="primary" onClick={onAddField}><Smartphone size={14}/>Agregar hito en campo</button>}
    </div>}
    <nav aria-label="Zoom del plano"><button type="button" aria-label="Reducir zoom 10 %" disabled={zoom<=.25} onClick={()=>updateZoom(Math.round(zoom*100)-10)}>−</button><label className="pat-plan-zoom-input"><input type="number" inputMode="numeric" min={25} max={400} step={1} aria-label="Porcentaje de zoom" value={zoomInput} onChange={event=>setZoomInput(event.target.value)} onBlur={commitZoom} onKeyDown={event=>{if(event.key==='Enter'){commitZoom();event.currentTarget.blur()}}}/><span>%</span></label><button type="button" aria-label="Aumentar zoom 10 %" disabled={zoom>=4} onClick={()=>updateZoom(Math.round(zoom*100)+10)}>+</button><button type="button" onClick={()=>updateZoom(100)}>Ajustar</button></nav>
    </div>
    <PatrolPlan planUrl={planUrl} checkpoints={checkpoints} selectedId={selectedId} zoom={zoom} fitToView selection={readOnly?null:selection} onSelect={readOnly?undefined:(x,y)=>!busy&&setSelection({x,y})}/>
    {error&&<p role="alert">{error}</p>}
    <footer><span>{readOnly?'Vista de consulta':selection?`X ${(selection.x*100).toFixed(2)}% · Y ${(selection.y*100).toFixed(2)}%`:'Seleccione una ubicación sobre el plano'}</span><button type="button" disabled={busy} onClick={onCancel}>{readOnly?'Cerrar':'Cancelar'}</button>{!readOnly&&<button type="button" disabled={!selection||!planUrl||busy} onClick={async()=>{if(!selection)return;setBusy(true);try{await onConfirm?.(selection.x,selection.y)}finally{setBusy(false)}}}>{busy?'Guardando…':'Confirmar ubicación'}</button>}</footer>
  </dialog>;
}
