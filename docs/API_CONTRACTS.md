# Contratos API iniciales

Base: `/api`

## Contexto
- `GET /api/context`

## Compañías
- `GET /api/companies?page=0&size=50`
- `POST /api/companies`
- `PUT /api/companies/{id}`
- `GET /api/companies/responsibles` — candidatos para Responsable operacional (extensión observada, RC/CR por confirmar).
- `PUT /api/companies/{id}` admite `responsibleEmployeeId` UUID o `null`; respuesta reporta ID, nombre y código de rol del responsable (extensión observada, pendiente de contrato/versionado formal).

## Operación comercial local/mock
- `GET /api/services`
- `GET /api/services/{serviceId}/points`
- `GET /api/points/{pointId}/posts`

## Consignas
- `GET /api/consignments?pointId=...`
- `POST /api/consignments`
- `POST /api/consignments/{id}/publish`

## REGESEP
- `GET /api/points/{pointId}/regesep/current`

## Convenciones
- Todos los IDs técnicos son UUID.
- Todos los endpoints operan dentro del `instance_country_id` derivado de identidad/contexto, nunca recibido libremente desde el navegador en producción.
- Listados usan paginación.
- Escrituras que luego disparan otras acciones producen Outbox en la misma transacción.
- Integraciones futuras aceptarán `Idempotency-Key`/`event_id`.

## Asignaciones — UAT v0.2
- `GET /api/assignments/week?companyId=...&weekStart=YYYY-MM-DD&pointId=...` — plan semanal, Turnos Requeridos, Puestos y asignaciones; materializa ocurrencias desde SIC: COM LOCAL.
- `GET /api/assignments/personnel?companyId=...&weekStart=...&q=...&role=...&page=0&size=50` — personal de la Compañía desde SIC: RRHH LOCAL, paginado.
- `GET /api/assignments/employees/{employeeId}?weekStart=...` — detalle operativo, habilidades, indisponibilidades y asignaciones recientes.
- `POST /api/assignments/assign` — asignar en Borrador; si el Plan está publicado, distingue `Asignación posterior a publicación` cuando el Turno estaba vacío y `Reasignación` cuando reemplaza una asignación existente.
- `DELETE /api/assignments/{assignmentId}` — quitar asignación solo mientras el plan está en Borrador.
- `POST /api/assignments/plans/{planId}/publish` — publicación con snapshot inmutable y Outbox.
- `GET /api/assignments/coverage?weekStart=...` — cobertura actual semanal por Compañía y, cuando existe publicación, métricas de cobertura publicada para comparación histórica.

Regla crítica: IC, compatibilidad de rol y `ID vs TIER` son warnings no bloqueantes. Vacaciones/permisos, estado laboral, Compañía, solapamiento y no auto-relevo son bloqueos de elegibilidad.


## SER v0.7 — Patrullas / Configuración versionada

Base: `/api/patrols`

- `GET /protocols?pointId={uuid}` — lista la versión de trabajo por serie (BORRADOR si existe; en caso contrario VIGENTE/histórica más reciente) para los Puestos del Punto.
- `POST /protocols` — crea una nueva serie en BORRADOR v1.
- `PUT /protocols/{protocolId}` — modifica únicamente BORRADOR.
- `POST /protocols/{protocolId}/fork` — clona snapshot completo a la siguiente versión BORRADOR.
- `POST /protocols/{protocolId}/publish` — valida y publica el snapshot; la versión VIGENTE previa de la misma serie pasa a NO_VIGENTE.
- `GET /protocols/{protocolId}/history` — historial completo de snapshots de la serie.
- `POST /protocols/{protocolId}/patrols` — crea Patrulla en el borrador.
- `PUT /patrols/{patrolId}` / `DELETE /patrols/{patrolId}` — modifica/elimina Patrulla solo en borrador.
- `POST /patrols/{patrolId}/checkpoints` — crea Hito Cerrado ATS/FIELD/MIXED.
- `PUT /checkpoints/{checkpointId}` / `DELETE /checkpoints/{checkpointId}` — modifica/elimina Hito solo en borrador.
- `PUT /checkpoints/{checkpointId}/rules` — reemplaza reglas configuradas del Hito.
- `POST /checkpoints/{checkpointId}/standard-image?filename=...&contentType=...` — carga/reemplaza Foto estándar (octet-stream, JPG/PNG/WebP, máx. 5 MB).
- `GET /checkpoints/{checkpointId}/standard-image` — recupera Foto estándar.
- `DELETE /checkpoints/{checkpointId}/standard-image` — retira Foto estándar del borrador.

### Inmutabilidad
Todos los endpoints mutadores validan que el Protocolo padre esté en `BORRADOR`; intentar escribir sobre una versión publicada responde conflicto (HTTP 409).

## SER v0.8 — Consignas
- `GET /api/consignments/protocols?pointId={uuid}` — Protocolos de Consignas por Punto.
- `GET /api/consignments/protocols/current?pointId={uuid}` — Protocolo VIGENTE del Punto.
- `POST /api/consignments/protocols` — crear nueva serie de Protocolo en BORRADOR.
- `PUT /api/consignments/protocols/{id}` — editar metadata del BORRADOR.
- `POST /api/consignments/protocols/{id}/fork` — crear siguiente versión BORRADOR desde snapshot publicado.
- `POST /api/consignments/protocols/{id}/publish` — publicar snapshot; si es la serie vigente, reemplaza su versión activa; si es otra serie queda PUBLICADO/disponible.
- `POST /api/consignments/protocols/{id}/activate` — activar un Protocolo PUBLICADO; solo uno queda VIGENTE por Punto.
- `GET /api/consignments/protocols/{id}/history` — historial de versiones.
- `POST /api/consignments/protocols/{id}/items` — crear Consigna.
- `PUT /api/consignments/items/{id}` — guardar Definición, Alcance, Vigencia, Aplicación, Reglas y ubicación esperada.
- `DELETE /api/consignments/items/{id}` — eliminar Consigna del BORRADOR.
- `POST /api/consignments/items/{id}/evidences` — agregar Evidencia.
- `PUT /api/consignments/evidences/{id}` — editar Evidencia.
- `POST|GET|DELETE /api/consignments/evidences/{id}/standard-image` — gestionar Foto estándar.
- Compatibilidad: `GET /api/consignments?pointId={uuid}` permanece disponible.


## SER v0.9 — asignación inicial de Servicios
- `GET /api/services/overview` — incluye Servicios asignados dentro del scope y, para roles de Coordinación autorizados, Servicios `PENDING` recibidos desde SIC: COM.
- `GET /api/services/overview/assignment-destinations?pointId=...` — Compañías destino permitidas según rol/ámbito territorial.
- `POST /api/services/overview/points/{pointId}/assign-company` — asigna la Compañía operativa inicial; body `{ companyId, observations }`. Solo Presidencia, Director Nacional, Director Zonal y Jefe Regional.


## SER v0.9.1 — Movimiento operacional de Servicios
### `POST /api/services/overview/points/{pointId}/return-to-coordination`
Roles: `PRESIDENTE`, `DIRECTOR_NACIONAL`, `DIRECTOR_ZONAL`, `JEFE_REGIONAL` según alcance territorial del Punto.

Body:
```json
{ "observations": "opcional" }
```

Efecto: `company_id → NULL`, estado `PENDING`, preserva configuración del Punto, marca asignaciones futuras como `REMOVED`, conserva turnos activos y registra auditoría `RETURN_TO_COORDINATION`.

### `POST /api/services/overview/points/{pointId}/assign-company`
Cuando el Punto proviene de un retiro anterior, la misma operación registra `REASSIGNMENT_FROM_COORDINATION`; no requiere aceptación del Coordinador destino.


## SGI_OPR → SGI_COM · Evidencias del agente por multipart (v1, Fase 1)

Habilitado con `SGI_OPERATOR_RELIEF_UAT_ENABLED=true`. Autenticación: usuario con rol `AGENTE_SEGURIDAD` o `SUPERVISOR_SEGURIDAD` vinculado a un empleado (`operator_employee_binding`).
Flujo en dos pasos: (1) subir las fotos, (2) confirmar la ejecución referenciando los `evidenceId` devueltos. Los errores 400/403/409 de `/api/v1/operator` traen el motivo en texto plano.

### `GET /api/v1/operator/runtime?assignmentId={id}`
Además de `relief`, devuelve `patrols[]`: solo protocolos de Patrullas **publicados y activos** en el Puesto de la asignación.
```json
"patrols":[{"protocolId":"…","protocolCode":"PRO-PAT-0004","protocolVersion":1,"patrolId":"…","code":"PAT-001","name":"…",
  "structureType":"CLOSED","scheduleType":"PROGRAMMED","sequenceType":"STRICT","windowStart":"22:00","windowEnd":"22:45",
  "checkpoints":[{"checkpointId":"…","code":"H01","name":"Portón trasero","description":"…","sortOrder":1,"requiresEvidence":true,
    "evidenceMinCount":2,"evidenceMaxCount":3,"hasStandardImage":true,"standardImageVersion":1,"standardImageNotes":"…",
    "latitude":-2.17,"longitude":-79.92,"radiusM":50}]}]
```

### `GET /api/v1/operator/checkpoints/{checkpointId}/standard-image?assignmentId={id}`
Foto estándar del Hito como guía para el agente (binario de la imagen).

### `POST /api/v1/operator/evidences` — `multipart/form-data`
| Parte | Tipo | Contenido |
|---|---|---|
| `metadata` | texto (JSON) | Metadatos. Se envía como **texto**, nunca como `Blob` con nombre de archivo. |
| `files` | archivo, 1..5 | Cada archivo se llama `<clientEvidenceId>.<ext>`; se empareja con su ítem por nombre. |

Cabecera opcional `Idempotency-Key: <uploadBatchId>`. El cliente **no** fija `Content-Type` (lo pone el navegador/HTTP client con el `boundary`).

```json
{"uploadBatchId":"uuid","eventId":"uuid","assignmentId":"uuid","targetType":"PATROL_CHECKPOINT","targetId":"uuid-del-hito",
 "items":[{"clientEvidenceId":"uuid","capturedAt":"2026-09-29T22:14:03Z","latitude":-2.170998,"longitude":-79.922359,
   "accuracyM":8,"source":"CAMERA","sha256":"<64 hex minúsculas>"}]}
```
Respuesta `200` (un resultado por foto; puede haber éxito parcial):
```json
{"results":[{"clientEvidenceId":"…","evidenceId":"…","status":"STORED","reason":null,"flags":[]},
            {"clientEvidenceId":"…","evidenceId":"…","status":"ALREADY_STORED","reason":null,"flags":["GALLERY"]},
            {"clientEvidenceId":"…","evidenceId":null,"status":"REJECTED","reason":"UNSUPPORTED_FORMAT","flags":[]}]}
```
- `reason`: `FILE_TOO_LARGE` (> 5 MB), `UNSUPPORTED_FORMAT` (no es JPEG/PNG/WebP por contenido), `CHECKSUM_MISMATCH`, `TOO_MANY_PHOTOS` (supera `evidenceMaxCount` del Hito).
- `flags`: `GALLERY`, `OUT_OF_RANGE` (fuera de `radiusM` del Hito), `SUSPECTED_REUSE` (el mismo archivo ya se usó en otra ejecución).
- Idempotencia: reenviar el mismo `clientEvidenceId` con el mismo archivo → `ALREADY_STORED`; con otro archivo → `409`.
- Errores: `400` forma inválida o Hito no activo en el Puesto; `403` asignación ajena; `413` petición demasiado grande; `503` almacenamiento no disponible.
- Límites: 5 MB por foto, 5 fotos por petición, 30 MB por petición. Las fotos se guardan en MinIO (`sgi-evidence/evidence/{yyyy}/{MM}/{eventId}/{evidenceId}.{ext}`).

### `POST /api/v1/operator/executions` — evento `PATROL_CHECKPOINT_COMPLETED`
```json
{"batchId":"uuid","correlationId":"uuid","employeeId":"uuid","instanceCountryId":"uuid","deviceId":"…","capturedAt":"…",
 "events":[{"type":"PATROL_CHECKPOINT_COMPLETED","eventId":"uuid (el mismo de /evidences)","assignmentId":"uuid",
   "patrolRunId":"uuid de la ronda","patrolId":"uuid","checkpointId":"uuid","executedAt":"…",
   "latitude":-2.17,"longitude":-79.92,"accuracyM":8,"observation":"opcional (≤1000)","evidenceIds":["…","…"]}]}
```
Respuesta: `{"serverVersion":"operator-v1","acknowledgedEventIds":["…"],"rejectedEvents":[],"results":[{"eventId":"…","status":"RECEIVED","evidenceCount":2,"validationStatus":"NOT_REQUESTED"}]}`.
- Valida: fotos del mismo `eventId`, asignación, usuario y Hito; cantidad entre `evidenceMinCount` y `evidenceMaxCount`; fecha dentro del turno (±12 h).
- Idempotente por `eventId` (mismo contenido → mismo ack; distinto → `409`). Un Hito se registra una vez por `patrolRunId` (segunda vez → `409`).
- `RELIEF_SUBMITTED` sigue funcionando igual.

### Foto estándar (Patrullas, Consignas, Bitácora)
`POST …/standard-image` acepta ahora `multipart/form-data` con la parte `file` (además del binario `application/octet-stream` anterior). La imagen se guarda en MinIO (`sgi-standard/standard/{módulo}/{sha256}.{ext}`); las fotos antiguas en `bytea` se migran solas al arrancar.
