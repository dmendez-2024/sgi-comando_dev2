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
- `POST /checkpoints/{checkpointId}/standard-images` — agrega una Foto estándar (`multipart/form-data`, parte `file`; JPG/PNG/WebP, máx. 5 MB). Hasta **5** por Hito (la 6.ª → 400).
- `GET /checkpoints/{checkpointId}/standard-images/{imageId}` — recupera una Foto estándar.
- `DELETE /checkpoints/{checkpointId}/standard-images/{imageId}` — la retira del borrador y reordena las demás (1..n). Sin fotos estándar, VISINT se apaga.
- El Hito devuelve `standardImages:[{id,position,originalName,contentType}]` y `hasStandardImage`. Ya no existen `evidenceMinCount`/`evidenceMaxCount`: el agente envía **una** foto por Hito.

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
    "maxPhotos":1,"visintEnabled":true,"hasStandardImage":true,"standardImages":[{"id":"…","position":1}],"standardImageVersion":1,"standardImageNotes":"…",
    "latitude":-2.17,"longitude":-79.92,"radiusM":50}]}]
```

### `GET /api/v1/operator/checkpoints/{checkpointId}/standard-images/{imageId}?assignmentId={id}`
Una foto estándar del Hito como guía para el agente (binario de la imagen).

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
- `reason`: `FILE_TOO_LARGE` (> 5 MB), `UNSUPPORTED_FORMAT` (no es JPEG/PNG/WebP por contenido), `CHECKSUM_MISMATCH`, `TOO_MANY_PHOTOS` (el Hito ya tiene su foto: se admite **una** por `eventId`).
- `flags`: `GALLERY`, `OUT_OF_RANGE` (fuera de `radiusM` del Hito), `SUSPECTED_REUSE` (el mismo archivo ya se usó en otra ejecución).
- Idempotencia: reenviar el mismo `clientEvidenceId` con el mismo archivo → `ALREADY_STORED`; con otro archivo → `409`.
- Errores: `400` forma inválida o Hito no activo en el Puesto; `403` asignación ajena; `413` petición demasiado grande; `503` almacenamiento no disponible.
- Límites: 5 MB por foto, 5 fotos por petición, 30 MB por petición. Las fotos se guardan en MinIO (`sgi-evidence/evidence/{yyyy}/{MM}/{eventId}/{evidenceId}.{ext}`).

### `POST /api/v1/operator/executions` — evento `PATROL_CHECKPOINT_COMPLETED`
```json
{"batchId":"uuid","correlationId":"uuid","employeeId":"uuid","instanceCountryId":"uuid","deviceId":"…","capturedAt":"…",
 "events":[{"type":"PATROL_CHECKPOINT_COMPLETED","eventId":"uuid (el mismo de /evidences)","assignmentId":"uuid",
   "patrolRunId":"uuid de la ronda","patrolId":"uuid","checkpointId":"uuid","executedAt":"…",
   "latitude":-2.17,"longitude":-79.92,"accuracyM":8,"observation":"opcional (≤1000)","evidenceIds":["…"]}]}
```
Respuesta: `{"serverVersion":"operator-v1","acknowledgedEventIds":["…"],"rejectedEvents":[],"results":[{"eventId":"…","status":"RECEIVED","evidenceCount":1,"validationStatus":"NOT_REQUESTED"}]}`.
- Valida: fotos del mismo `eventId`, asignación, usuario y Hito; exactamente **1** foto si el Hito requiere evidencia (0 o 1 si no); fecha dentro del turno (±12 h).
- Idempotente por `eventId` (mismo contenido → mismo ack; distinto → `409`). Un Hito se registra una vez por `patrolRunId` (segunda vez → `409`).
- `RELIEF_SUBMITTED` sigue funcionando igual.

### Foto estándar (Patrullas, Consignas, Bitácora)
En Consignas y Bitácora, `POST …/standard-image` acepta `multipart/form-data` con la parte `file` (además del binario `application/octet-stream` anterior). La imagen se guarda en MinIO (`sgi-standard/standard/{módulo}/{sha256}.{ext}`); las fotos antiguas en `bytea` se migran solas al arrancar.


## Fase 2 · VISINT para Hitos de patrulla

### Configuración del Hito
`PUT /api/patrols/checkpoints/{id}` acepta `visintEnabled` (booleano; `null` conserva el valor). VISINT viene **activado por defecto** en los Hitos que requieren evidencia. La pantalla ya no muestra la opción: al guardar un Hito envía `visintEnabled = requiresEvidence` (el campo sigue en el API).
- Activarlo exige que el Hito requiera evidencia (400 si no).
- Publicar un protocolo con un Hito con VISINT activo y sin fotos estándar → 400 `"<código>: VISINT activo requiere al menos una foto estándar"`.
- Agregar o quitar fotos estándar no apaga VISINT; quitar la última sí.

### Ejecución del agente
El ack de `POST /api/v1/operator/executions` (`PATROL_CHECKPOINT_COMPLETED`) informa `validationStatus`: `QUEUED_FOR_VISINT` si el Hito tiene VISINT activo, `NOT_REQUESTED` si no. La confirmación del agente **no depende** de VISINT.

### Estados de la revisión visual (`visual_review.status`)
| Estado | Significado |
|---|---|
| `QUEUED_FOR_VISINT` | En cola; se envía a VISINT en segundos. |
| `PASSED` | VISINT respondió `PASS` (cumple). |
| `FAILED` | VISINT respondió `FAIL` (no cumple). |
| `ERROR_RETRYABLE` | VISINT no respondió; se reintenta (30 s, 60 s, 120 s, 240 s; tope 10 min). |
| `ERROR_FINAL` | VISINT respondió `ERROR` (no pudo evaluar) o se agotaron 5 intentos. Se puede reintentar manualmente. |

### Contrato con VISINT
Llamada **síncrona**, directa (no pasa por CORE): `POST {SGI_VISINT_URL}` (hoy `http://181.39.84.138:8010/v1/evidence/validate`), `multipart/form-data` (archivos, no base64). Una llamada por ejecución del Hito: VISINT compara la **foto del agente** con las **fotos estándar** (1 a 5) y basta que coincida con una.

Campos del formulario:
| Campo | Valor que envía Comando |
|---|---|
| `requestId` | `<id de la revisión>-<n.º de intento>` |
| `source` | `SGI_COM` |
| `companyId` / `pointId` / `postId` | Compañía, Punto y Puesto de la ejecución |
| `serviceType` | `PATRULLA` |
| `serviceId` / `activityId` | ronda de patrulla / Hito |
| `evidenceId` | id de la foto del agente |
| `capturedAt`, `latitude`, `longitude` | hora y GPS de la foto del agente |
| `image` (archivo) | **foto del agente** (una) |
| `referenceImages` (archivos, repetido 1..5) | **fotos estándar** del Hito, en orden (las guardadas en la revisión) |
| `referenceIds` (repetido) | id de cada foto estándar, en el mismo orden |

Cabeceras: `X-API-Key: <SGI_VISINT_TOKEN>` (sin esquema; configurable con `SGI_VISINT_AUTH_HEADER` / `SGI_VISINT_AUTH_SCHEME`), `X-Correlation-Id`. El token va solo en variables de entorno (`.env`, no versionado).

Response `200`:
```json
{"requestId":"…","status":"PASS","quality":{"valid":true,"score":0.88},"match":{"compatible":true,"score":0.76},
 "matchedReferenceId":"<id de la foto estándar que coincidió>","reasonCode":"OK","processedAt":"…","modelVersion":"visint-faces-1/…"}
```
Cómo lo interpreta Comando (los `score` se ignoran; VISINT decide):
| Respuesta | Resultado |
|---|---|
| `PASS` | `PASS` → **Cumple** (se marca la foto estándar `matchedReferenceId`) |
| `FAIL_QUALITY`, `FAIL_NO_MATCH`, `FAIL_INVALID_IMAGE` (u otro con `match.compatible=false`) | `FAIL` → **No cumple**, con el motivo |
| `TIMEOUT`, `PROCESSING` o `reasonCode` `…TIMEOUT` (p. ej. `QUEUE_TIMEOUT`) | VISINT no alcanzó a evaluar → reintento automático |
| `ERROR_VISINT` u otro | `ERROR` → **falla técnica**, no es incumplimiento (reintento manual) |
| sin `status`, JSON inválido, HTTP no 2xx, sin conexión, foto ilegible en MinIO | VISINT no disponible → reintento automático |

Estados según el plan de integración VISINT (lámina 9). Se guardan `reasonCode` (o el `status` si no viene), `matchedReferenceId` y `modelVersion`.

**Modo simulado (solo UAT):** con `SGI_VISINT_MODE=MOCK` **y** `SGI_UAT_FEATURES_ENABLED=true`, Comando no llama a VISINT: un simulador determinista responde `PASS` si la foto del agente es idéntica a alguna foto estándar (y la señala) y, si no, PASS/FAIL/ERROR según la huella de las fotos. Los hallazgos dicen “(VISINT simulado)”. Fuera de UAT el simulado se rechaza. Por defecto `SGI_VISINT_MODE=HTTP`.

### Vista Operación (`/api/operation`)
Roles de lectura: Presidente, Directores, Jefe Regional, Coordinador, Asistente, Supervisor (según alcance de Compañía). Reintento: los mismos sin Supervisor.
| Endpoint | Respuesta |
|---|---|
| `GET /api/operation/executions?pointId=&limit=` | Ejecuciones del Punto (Hito, patrulla, versión, agente, ids de fotos, alertas, `review {id,status,result,simulated}`). |
| `GET /api/operation/executions/{id}` | Detalle: fotos (`capturedAt`, GPS, origen, alertas), notas del estándar y `standards:[{id,position}]` (las enviadas a VISINT) y `review {status,result,findings,matchedStandardImageId,reasonCode,modelVersion,standardImageVersion,simulated,attempts,lastError,createdAt,requestedAt,reviewedAt}`. |
| `GET /api/operation/evidences/{id}/content` | Imagen de la foto del agente. |
| `GET /api/operation/executions/{id}/standards/{standardId}` | Una foto estándar **enviada a VISINT** (snapshot de la revisión; sin revisión, la actual del Hito). |
| `POST /api/operation/visual-reviews/{id}/retry` | Vuelve a la cola una revisión en `ERROR_RETRYABLE`/`ERROR_FINAL` (409 en otro estado). |

### Banderas (`GET /api/features`, autenticado)
`{"uatTools":true|false,"visintMode":"HTTP|MOCK","visintSimulated":true|false}`. El frontend oculta el Simulador de Agente si `uatTools=false`.


## Fase 3 · Resultado de VISINT para el agente

La confirmación del Hito responde al instante (`validationStatus: QUEUED_FOR_VISINT`); la app del agente consulta después el resultado **cada 3 s mientras `outcome = PENDING`** (VISINT suele responder en 2–5 s).

### `GET /api/v1/operator/executions/{eventId}`
Solo el agente que registró la ejecución (otro usuario → 404; sin rol de operador → 403).
```json
{"eventId":"…","patrolRunId":"…","checkpointId":"…","captureNo":1,"executedAt":"…","receivedAt":"…",
 "validation":{"status":"FAILED","result":"FAIL","reasonCode":"NO_REFERENCE_ABOVE_THRESHOLD","reviewedAt":"…"},
 "outcome":"NOT_VALIDATED","message":"Evidencia no validada: no coincide con el lugar esperado. Tome una nueva foto.","canRetake":true}
```

| `outcome` | Cuándo | Mensaje al agente | `canRetake` |
|---|---|---|---|
| `NOT_REQUIRED` | Hito sin VISINT | Hito registrado. | no |
| `PENDING` | En cola o reintentando | Validando la foto con VISINT… | no |
| `VALIDATED` | VISINT `PASS` | Foto validada. Hito cumplido. | no |
| `NOT_VALIDATED` | VISINT `FAIL_*` | Evidencia no validada: *motivo*. Tome una nueva foto. | sí, si es la última captura |
| `TECHNICAL_ERROR` | VISINT no pudo evaluar | No se pudo validar la foto por un problema técnico. El Hito queda registrado. | no |

Motivos: `FAIL_QUALITY` → "la foto no es clara"; `FAIL_INVALID_IMAGE` → "la imagen no se pudo procesar"; resto → "no coincide con el lugar esperado".

### `GET /api/v1/operator/executions?patrolRunId={id}`
Todas las capturas del agente en la ronda (mismo formato), por hora y número de captura.

### Nueva captura tras "No cumple"
Se envía como un Hito normal (`POST /evidences` + `POST /executions`) con un **`eventId` nuevo** y la **misma `patrolRunId`**; queda como `captureNo` 2, 3…
- Solo si la última captura del Hito en la ronda tiene VISINT `FAILED`.
- Si la anterior aún se valida → `409` "La foto anterior de este Hito aún se está validando".
- Si la anterior cumplió, no tiene VISINT o tuvo error técnico → `409` "Este Hito ya fue registrado en la ronda".
- Sin tope de capturas por ahora (pendiente decisión de Gerencia). Operación muestra "Captura N".
