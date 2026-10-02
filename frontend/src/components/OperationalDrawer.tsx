import {useEffect,useId,type ReactNode} from 'react';
import {X} from 'lucide-react';

type OperationalDrawerProps={
  title:string;
  subtitle:string;
  onClose:()=>void;
  children:ReactNode;
  footer?:ReactNode;
  footerNote?:ReactNode;
  className?:string;
  bodyClassName?:string;
};

export function OperationalDrawer({title,subtitle,onClose,children,footer,footerNote,className='',bodyClassName=''}:OperationalDrawerProps){
  const titleId=useId();

  useEffect(()=>{
    function closeOnEscape(event:KeyboardEvent){if(event.key==='Escape')onClose()}
    window.addEventListener('keydown',closeOnEscape);
    return ()=>window.removeEventListener('keydown',closeOnEscape);
  },[onClose]);

  return <div className="nov-modal-backdrop operational-drawer-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)onClose()}}>
    <aside className={`nov-modal operational-drawer ${className}`} role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <header><div><h3 id={titleId}>{title}</h3><span>{subtitle}</span></div><button type="button" aria-label="Cerrar detalle" onClick={onClose}><X size={19}/></button></header>
      <div className={`nov-modal-body ${bodyClassName}`}>{children}</div>
      {footer&&<footer>{footer}</footer>}
      {footerNote}
    </aside>
  </div>
}
