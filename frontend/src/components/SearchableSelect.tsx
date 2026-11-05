import {useEffect,useId,useMemo,useRef,useState} from 'react';
import {Check,ChevronDown,Search} from 'lucide-react';

export type SearchableSelectOption={value:string;label:string;disabled?:boolean};

type Props={
  value:string;
  options:SearchableSelectOption[];
  onChange:(value:string)=>void;
  placeholder?:string;
  searchPlaceholder?:string;
  emptyMessage?:string;
  disabled?:boolean;
  className?:string;
  ariaLabel?:string;
  title?:string;
};

const normalize=(value:string)=>value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es');

export default function SearchableSelect({
  value,options,onChange,placeholder='Seleccione una opción',searchPlaceholder='Buscar…',
  emptyMessage='No hay opciones coincidentes.',disabled=false,className='',ariaLabel,title
}:Props){
  const [open,setOpen]=useState(false);
  const [query,setQuery]=useState('');
  const [alignRight,setAlignRight]=useState(false);
  const [openUp,setOpenUp]=useState(false);
  const rootRef=useRef<HTMLDivElement>(null);
  const searchRef=useRef<HTMLInputElement>(null);
  const listId=useId();
  const selected=options.find(option=>option.value===value);
  const filtered=useMemo(()=>{
    const term=normalize(query.trim());
    return term?options.filter(option=>normalize(option.label).includes(term)):options;
  },[options,query]);

  useEffect(()=>{
    if(!open)return;
    const rect=rootRef.current?.getBoundingClientRect();
    if(rect){
      setAlignRight(rect.left+Math.max(rect.width,260)>window.innerWidth-16);
      setOpenUp(rect.bottom+300>window.innerHeight&&rect.top>300);
    }
    searchRef.current?.focus();
    const close=(event:PointerEvent)=>{
      if(!rootRef.current?.contains(event.target as Node)){setOpen(false);setQuery('')}
    };
    window.addEventListener('pointerdown',close);
    return()=>window.removeEventListener('pointerdown',close);
  },[open]);

  useEffect(()=>{if(disabled){setOpen(false);setQuery('')}},[disabled]);

  const choose=(next:string)=>{onChange(next);setOpen(false);setQuery('')};
  const toggle=()=>{if(disabled)return;setOpen(current=>!current);setQuery('')};

  return <div className={`searchable-select ${open?'open':''} ${disabled?'disabled':''} ${className}`.trim()} ref={rootRef}>
    <button type="button" className="searchable-select-trigger" disabled={disabled} title={title??selected?.label??placeholder}
      role="combobox" aria-label={ariaLabel} aria-haspopup="listbox" aria-expanded={open} aria-controls={listId}
      onClick={toggle} onKeyDown={event=>{
        if(event.key==='ArrowDown'||event.key==='Enter'||event.key===' '){event.preventDefault();setOpen(true)}
        if(event.key==='Escape'){setOpen(false);setQuery('')}
      }}>
      <span className={!selected?'placeholder':''}>{selected?.label??placeholder}</span><ChevronDown size={15}/>
    </button>
    {open&&<div className={`searchable-select-menu ${alignRight?'align-right':''} ${openUp?'open-up':''}`}>
      <label className="searchable-select-search"><Search size={14}/><input ref={searchRef} value={query}
        onChange={event=>setQuery(event.target.value)} placeholder={searchPlaceholder}
        aria-label={ariaLabel?`Buscar en ${ariaLabel.toLocaleLowerCase('es')}`:'Buscar opción'}
        onKeyDown={event=>{
          if(event.key==='Escape'){event.preventDefault();setOpen(false);setQuery('')}
          if(event.key==='Enter'){
            const first=filtered.find(option=>!option.disabled);
            if(first){event.preventDefault();choose(first.value)}
          }
        }}/></label>
      <div className="searchable-select-options" id={listId} role="listbox" aria-label={ariaLabel}>
        {filtered.map(option=><button type="button" role="option" aria-selected={option.value===value}
          className={option.value===value?'selected':''} disabled={option.disabled} key={option.value}
          onClick={()=>choose(option.value)}><span>{option.label}</span>{option.value===value&&<Check size={14}/>}</button>)}
        {!filtered.length&&<div className="searchable-select-empty">{emptyMessage}</div>}
      </div>
    </div>}
  </div>;
}
