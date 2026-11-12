/** CSV oriented to Excel: UTF-8 BOM, explicit delimiter and Windows row endings. */
export function createExcelCsv(rows:readonly (readonly unknown[])[]):string {
  const content=rows.map(row=>row.map(value=>`"${String(value??'').replaceAll('"','""')}"`).join(',')).join('\r\n');
  return '\uFEFFsep=,\r\n'+content+'\r\n';
}

export function downloadCsv(filename:string,rows:readonly (readonly unknown[])[]):void {
  const blob=new Blob([createExcelCsv(rows)],{type:'text/csv;charset=utf-8;'});
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
