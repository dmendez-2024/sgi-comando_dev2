import {useEffect,useMemo,useState} from 'react';
import {Download,Search,Upload,X} from 'lucide-react';
import {api,getUser} from '../api';

export type LibraryModule='patrols'|'consignments';
type Scope='COMPANY'|'COUNTRY';
type Entry={id:string;scope:Scope;code:string;name:string;description:string;versionNo:number;itemCount:number;detailCount:number;exportedBy:string;exportedAt:string;alreadyExported:boolean};
type Props={module:LibraryModule;mode:'import'|'export';postId:string;postLabel:string;companyName:string;source?:{id:string;code:string;name:string;versionNo:number};onClose:()=>void;onImported?:(protocol:any,entry:Entry)=>Promise<void>|void;onExported?:(entry:Entry)=>void};

function message(error:unknown){return error instanceof Error?error.message:'No se pudo completar la operación.'}

export default function ProtocolLibraryModal({module,mode,postId,postLabel,companyName,source,onClose,onImported,onExported}:Props){
  const [entries,setEntries]=useState<Entry[]>([]);
  const [selectedId,setSelectedId]=useState('');
  const [scope,setScope]=useState<'ALL'|Scope>('ALL');
  const [exportScope,setExportScope]=useState<Scope>('COMPANY');
  const [query,setQuery]=useState('');
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const canCountry=['presidente','ggeneral','dlatam','don','dnacional','dzonal'].includes(getUser());
  const itemName=module==='patrols'?'patrullas':'consignas';
  const detailName=module==='patrols'?'hitos':'evidencias';
  useEffect(()=>{let active=true;setLoading(true);setError('');
    const request=mode==='import'?api.protocolLibrary(module,postId):source?api.protocolLibraryExports(module,source.id):Promise.resolve([]);
    void request.then(rows=>{if(active)setEntries(rows as Entry[])}).catch(e=>{if(active)setError(message(e))}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[module,mode,postId,source?.id]);
  const filtered=useMemo(()=>entries.filter(entry=>(scope==='ALL'||entry.scope===scope)&&`${entry.code} ${entry.name} ${entry.description}`.toLocaleLowerCase('es').includes(query.trim().toLocaleLowerCase('es'))),[entries,scope,query]);
  const selected=entries.find(entry=>entry.id===selectedId);
  const submit=async()=>{setBusy(true);setError('');try{
    if(mode==='import'){
      if(!selected)return;
      const protocol=await api.importLibraryProtocol(module,selected.id,postId);
      onClose();await onImported?.(protocol,selected);
    }else{
      if(!source)return;
      const result=await api.exportLibraryProtocol(module,source.id,exportScope) as Entry;
      onClose();onExported?.(result);
    }
  }catch(e){setError(message(e))}finally{setBusy(false)}};
  return <div className="pat-modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget&&!busy)onClose()}}><section className="bit-export-modal bit-import-modal" role="dialog" aria-modal="true" aria-labelledby="protocol-library-title"><header><div><h3 id="protocol-library-title">{mode==='import'?'Importar protocolo':'Exportar protocolo'}</h3><span>{mode==='import'?`Puesto destino: ${postLabel} · ${companyName}`:`${source?.code} · ${source?.name} · v${source?.versionNo}`}</span></div><button type="button" aria-label="Cerrar" disabled={busy} onClick={onClose}><X size={18}/></button></header><div className="bit-export-body">{mode==='import'?<><p>Seleccione una versión publicada. Se creará un protocolo nuevo en borrador con sus {itemName}, reglas y fotos estándar.</p><div className="bit-import-filters"><select value={scope} onChange={e=>setScope(e.target.value as 'ALL'|Scope)} aria-label="Biblioteca"><option value="ALL">Todas las bibliotecas</option><option value="COMPANY">Modelos de Compañía</option><option value="COUNTRY">Estándares Cajamarca</option></select><label><Search size={14}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar protocolo…" aria-label="Buscar protocolo"/></label></div>{loading?<p>Cargando modelos…</p>:filtered.length?<div className="bit-import-models">{filtered.map(entry=><button type="button" key={entry.id} className={selectedId===entry.id?'selected':''} onClick={()=>setSelectedId(entry.id)}><strong>{entry.name}</strong><span>{entry.code} · v{entry.versionNo} · {entry.scope==='COMPANY'?'Modelo de Compañía':'Estándar Cajamarca'}</span><small>{entry.itemCount} {itemName} · {entry.detailCount} {detailName}</small></button>)}</div>:<p>No hay modelos publicados disponibles para este puesto y filtro.</p>}{selected&&<div className="bit-import-preview"><strong>Modelo seleccionado</strong><span>{selected.name} · v{selected.versionNo}</span>{selected.description&&<p>{selected.description}</p>}<small>El nuevo protocolo tendrá código propio. Al importar a otro punto, deberá configurar las ubicaciones. Las consignas por puesto se dirigirán al puesto destino.</small></div>}</>:<><p>Seleccione la biblioteca en la que quedará registrada esta versión publicada.</p><div className="bit-export-options"><label><input type="radio" name="protocol-library-scope" checked={exportScope==='COMPANY'} onChange={()=>setExportScope('COMPANY')}/><span><strong>Modelos de Compañía</strong><small>Disponible para puestos de {companyName}.</small>{entries.some(row=>row.scope==='COMPANY')&&<em>Ya exportada</em>}</span></label>{canCountry&&<label><input type="radio" name="protocol-library-scope" checked={exportScope==='COUNTRY'} onChange={()=>setExportScope('COUNTRY')}/><span><strong>Estándares Cajamarca</strong><small>Disponible para la instancia país.</small>{entries.some(row=>row.scope==='COUNTRY')&&<em>Ya exportada</em>}</span></label>}</div><small>La versión original conserva su contenido e historial. La biblioteca permite importarla como un borrador nuevo.</small></>}{error&&<p className="bit-import-error" role="alert">{error}</p>}</div><footer><button type="button" disabled={busy} onClick={onClose}>Cancelar</button><button type="button" className="primary" disabled={busy||loading||(mode==='import'?!selected:!source||entries.some(row=>row.scope===exportScope))} onClick={()=>void submit()}>{mode==='import'?<Download size={14}/>:<Upload size={14}/>} {busy?'Procesando…':mode==='import'?'Importar protocolo':'Exportar protocolo'}</button></footer></section></div>;
}
