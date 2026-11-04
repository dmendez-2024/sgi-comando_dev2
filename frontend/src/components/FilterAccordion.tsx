import {useState,type ReactNode} from 'react';
import {ChevronDown,Filter} from 'lucide-react';

type Props={
  id:string;
  children:ReactNode;
  defaultOpen?:boolean;
  title?:string;
};

export default function FilterAccordion({id,children,defaultOpen=true,title='Filtros de búsqueda'}:Props){
  const [open,setOpen]=useState(defaultOpen);

  return <div className={`csl-filter-accordion ${open?'open':''}`}>
    <button
      type="button"
      className="csl-filter-toggle"
      aria-expanded={open}
      aria-controls={id}
      onClick={()=>setOpen(value=>!value)}
    >
      <span><Filter size={18}/>{title}</span>
      <ChevronDown size={18} className="csl-filter-chevron" aria-hidden="true"/>
    </button>
    <div id={id} className="csl-filter-panel" aria-hidden={!open}>
      <div className="csl-filter-panel-inner">{children}</div>
    </div>
  </div>;
}
