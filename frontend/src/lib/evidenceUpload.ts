// Armado del FormData de evidencias del agente (mismo contrato que usará SGI: Operador).
export type PickedPhoto={clientEvidenceId:string;file:File;previewUrl:string;sha256:string;problem?:string};
const MAX_BYTES=5*1024*1024;

async function sniff(file:File):Promise<string|null>{
 const b=new Uint8Array(await file.slice(0,12).arrayBuffer());
 if(b[0]===0xFF&&b[1]===0xD8&&b[2]===0xFF)return 'image/jpeg';
 if(b[0]===0x89&&b[1]===0x50&&b[2]===0x4E&&b[3]===0x47)return 'image/png';
 if(String.fromCharCode(...b.slice(0,4))==='RIFF'&&String.fromCharCode(...b.slice(8,12))==='WEBP')return 'image/webp';
 return null;
}
async function sha256(file:File){const d=await crypto.subtle.digest('SHA-256',await file.arrayBuffer());return [...new Uint8Array(d)].map(x=>x.toString(16).padStart(2,'0')).join('')}
const extension=(type:string)=>type==='image/png'?'png':type==='image/webp'?'webp':'jpg';

/** Valida cada foto (tipo real y 5 MB) y respeta el máximo del Hito; las inválidas quedan marcadas con el motivo. */
export async function pickPhotos(files:FileList|File[],existing:number,max:number):Promise<PickedPhoto[]>{
 const out:PickedPhoto[]=[];
 for(const file of Array.from(files)){
  const p:PickedPhoto={clientEvidenceId:crypto.randomUUID(),file,previewUrl:URL.createObjectURL(file),sha256:''};
  if(existing+out.filter(x=>!x.problem).length>=max)p.problem=`Máximo ${max} fotos para este Hito`;
  else if(file.size>MAX_BYTES)p.problem='Supera 5 MB';
  else if(!(await sniff(file)))p.problem='Formato no permitido (use JPG, PNG o WebP)';
  else p.sha256=await sha256(file);
  out.push(p);
 }
 return out;
}

/** metadata va como texto (nunca Blob) y cada archivo se llama <clientEvidenceId>.<ext>. */
export function buildEvidenceForm(meta:{uploadBatchId:string;eventId:string;assignmentId:string;targetId:string},photos:PickedPhoto[],gps?:{latitude:number;longitude:number;accuracyM:number}):FormData{
 const now=new Date().toISOString();
 const items=photos.map(p=>({clientEvidenceId:p.clientEvidenceId,capturedAt:now,latitude:gps?.latitude??null,longitude:gps?.longitude??null,accuracyM:gps?.accuracyM??null,source:'GALLERY',sha256:p.sha256}));
 const fd=new FormData();
 fd.append('metadata',JSON.stringify({...meta,targetType:'PATROL_CHECKPOINT',items}));
 photos.forEach(p=>fd.append('files',p.file,`${p.clientEvidenceId}.${extension(p.file.type)}`));
 return fd;
}
