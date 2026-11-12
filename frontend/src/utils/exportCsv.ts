/** CSV oriented to desktop Excel: UTF-16 LE BOM, explicit delimiter and Windows row endings. */
export function createExcelCsv(rows:readonly (readonly unknown[])[]):string {
  const content=rows.map(row=>row.map(value=>`"${String(value??'').replaceAll('"','""')}"`).join(',')).join('\r\n');
  return 'sep=,\r\n'+content+'\r\n';
}

function utf16LeWithBom(content:string):Uint8Array {
  const bytes=new Uint8Array(2+content.length*2);
  bytes[0]=0xff;bytes[1]=0xfe;
  for(let index=0;index<content.length;index++){
    const code=content.charCodeAt(index);
    bytes[2+index*2]=code&0xff;
    bytes[3+index*2]=code>>8;
  }
  return bytes;
}

export function downloadCsv(filename:string,rows:readonly (readonly unknown[])[]):void {
  const blob=new Blob([utf16LeWithBom(createExcelCsv(rows))],{type:'text/csv;charset=utf-16le;'});
  const url=URL.createObjectURL(blob);
  const link=document.createElement('a');
  link.href=url;
  link.download=filename;
  document.body.appendChild(link);
  try { link.click(); }
  finally {
    link.remove();
    window.setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
}
