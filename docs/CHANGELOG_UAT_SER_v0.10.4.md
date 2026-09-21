# SGI: Comando — SER v0.10.4 UAT

## Bitácora — cambios funcionales

1. **Activación por Puesto**
   - La versión publicada del Protocolo conserva contenido inmutable.
   - La activación operativa se controla por Puesto desde `Definición`.
   - Marcar un Puesto activa el Protocolo en ese Puesto.
   - Desmarcar un Puesto lo inactiva únicamente en ese Puesto y muestra una advertencia antes de aplicar el cambio.
   - Un mismo Protocolo puede estar activo simultáneamente en varios Puestos.
   - Se elimina el botón global `Inactivar` de la cabecera.
   - Si no queda ningún Puesto activo, el estado global del Protocolo pasa a `Inactivo`; al activar al menos un Puesto, pasa a `Activo`.

2. **Publicación**
   - Un borrador publicado queda `Activo` si tiene uno o más Puestos marcados.
   - Queda `Inactivo` si se publica sin Puestos activos.

3. **Modales**
   - Bitácora deja de usar `window.confirm`/modales nativos del navegador para eliminaciones y advertencias.
   - Se introduce un modal SGI propio para confirmaciones destructivas o de cambio operativo.
   - Regla UX: no usar diálogos nativos del navegador en nuevas pantallas de SGI: Comando.

## Backend
- Nuevo endpoint `PUT /api/bitacora/protocols/{protocolId}/scope` para modificar activación por Puesto sin crear una nueva versión de contenido.
- No requiere nueva migración de base de datos; reutiliza `logbook_protocol_post_scope` de V22.
