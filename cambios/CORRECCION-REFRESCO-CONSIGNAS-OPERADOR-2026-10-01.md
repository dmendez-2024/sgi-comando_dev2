# Corrección de refresco de consignas creadas desde SGI Operador

**Fecha:** 2026-10-01
**Componente:** `frontend/src/pages/ConsignasExecution.tsx`

## Diagnóstico verificado

- La cola persistente del dispositivo tenía dos solicitudes en estado `ACKED`, por lo que SGI Operador recibió confirmación de SGI Comando.
- El endpoint de Comando para solicitudes pendientes respondió correctamente y devolvió las dos consignas a una cuenta de coordinación de UAT.
- La pantalla de ejecuciones cargaba estas solicitudes solo al abrirse. El botón **Buscar** filtraba el conjunto ya cargado, sin consultar de nuevo al backend.

## Corrección

- Se centralizó la carga de solicitudes pendientes en `loadReviewRequests`.
- La pantalla consulta al abrirse y también cuando se presiona **Buscar**.
- La transformación a filas de consignas ad-hoc, el filtro por alcance y la idempotencia del envío se mantienen sin cambios.
