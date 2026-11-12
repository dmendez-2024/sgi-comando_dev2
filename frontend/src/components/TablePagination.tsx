import {ChevronLeft,ChevronRight} from 'lucide-react';

export const PAGE_SIZE=10;
export function pageSlice<T>(rows:T[],page:number,size=PAGE_SIZE){
  const pages=Math.max(1,Math.ceil(rows.length/size));
  const current=Math.min(Math.max(0,page),pages-1);
  return {items:rows.slice(current*size,current*size+size),current,pages,from:rows.length?current*size+1:0,to:Math.min((current+1)*size,rows.length)};
}
export default function TablePagination({total,page,onChange,size=PAGE_SIZE}:{total:number;page:number;onChange:(page:number)=>void;size?:number}){
  const pages=Math.max(1,Math.ceil(total/size));
  const current=Math.min(Math.max(0,page),pages-1);
  const from=total?current*size+1:0,to=Math.min((current+1)*size,total);
  return <footer className="table-pagination" aria-label="Paginación de resultados">
    <span>{total?`Mostrando ${from} a ${to} de ${total} resultados`:'No hay resultados'}</span>
    <div><button type="button" aria-label="Página anterior" disabled={current===0} onClick={()=>onChange(current-1)}><ChevronLeft size={16}/></button><b>{current+1}</b><small>de {pages}</small><button type="button" aria-label="Página siguiente" disabled={current>=pages-1} onClick={()=>onChange(current+1)}><ChevronRight size={16}/></button></div>
  </footer>;
}
