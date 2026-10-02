# Integración de consignas pendientes desde SGI Operador

**Fecha:** 2026-10-01  
**Vertical:** Comando / Operador / Consignas

## Alcance

Se incorpora la recepción de solicitudes de consignas ad-hoc creadas por un operador. Las solicitudes se almacenan en `operator_consignment_review_request` con estado `PENDING` y quedan separadas de `consignment` y de los protocolos activos.

## Contrato

- `POST /api/v1/operator/consignment-review-requests`: valida que la asignación pertenezca al operador autenticado y registra la solicitud.
- `GET /api/v1/operator/consignment-review-requests?status=PENDING`: devuelve las solicitudes pendientes dentro del alcance territorial del usuario de Comando.

## Reglas y seguridad

- El puesto, punto, empleado y usuario se toman de la asignación autorizada; no se aceptan desde el cliente como fuente de verdad.
- Las consignas creadas por el operador no se publican ni pasan a `VIGENTE` automáticamente.
- El listado limita la visibilidad al operador originador o al alcance territorial de Comando.

## UAT

Crear una consigna desde Operador, filtrar **Pendiente** en Consignas de Comando y verificar que se muestre como ad-hoc con punto, puesto, responsable y prioridad correctos.
