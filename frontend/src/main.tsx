import React from 'react';import{createRoot}from'react-dom/client';import App from'./App';import'./styles.css';
import {ensureIdentLogin} from './security/identAuth';
// Con IDENT encendido en el backend, SGI solo se muestra despues del login de IDENT; si no, sigue el selector "Usuario UAT".
const root=createRoot(document.getElementById('root')!);
ensureIdentLogin().then(ready=>{if(ready)root.render(<React.StrictMode><App/></React.StrictMode>)})
 .catch(e=>root.render(<div style={{padding:32,fontFamily:'system-ui'}}><h2>No se pudo iniciar sesión</h2><p>{e instanceof Error?e.message:String(e)}</p><button onClick={()=>location.reload()}>Reintentar</button></div>));
