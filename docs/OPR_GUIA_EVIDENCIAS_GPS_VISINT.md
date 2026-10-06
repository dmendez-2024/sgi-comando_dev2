# Guía para SGI: Operador — fotos, ubicación GPS y VISINT

**Para:** equipo de la app SGI: Operador (agente) · **Desde:** SGI: Comando · **Fecha:** 2026-10-06 · **Versión del contrato:** `v1` (cambios aditivos)

Esta guía resume qué servicios de SGI: Comando debe usar la app del agente para las tareas con foto: **Hitos de patrulla, Consignas, Bitácora y Relevo**, con validación de ubicación GPS y validación visual VISINT. Cada servicio trae un request y un response de ejemplo. Los responses de los servicios 1 y 6 se tomaron del ambiente local de SGI: Comando, recortados donde se indica (`…`); los acuses de los servicios 3 a 5 siguen el formato que construye el código. Contrato detallado: `docs/API_CATALOG.md` → `OPR-EVIDENCE-LOCATION-001`.

## Lo más importante

1. **Nada bloquea al agente.** Ni la ubicación ni VISINT impiden registrar una tarea. Una foto lejos del punto o que VISINT marca "no cumple" se guarda igual; el supervisor ve el aviso en SGI: Comando (Servicios → Punto → Operación).
2. **Envía el GPS de cada foto** (`latitude`, `longitude`, `accuracyM`). Si el teléfono no da ubicación, envía la foto sin ellos: se registra igual y no se compara la distancia.
3. **No esperes a VISINT.** La confirmación responde al instante; el resultado llega en segundos y se consulta aparte. El agente puede pasar a la siguiente tarea mientras tanto.
4. **La app no envía umbrales ni radios.** El umbral de VISINT (`matchThreshold`) y el radio GPS los configura y aplica SGI: Comando.
5. **No hay servicios nuevos.** Son los mismos endpoints `v1` con campos adicionales opcionales: una app que todavía no los envíe sigue funcionando.

## Conexión

- Base: `/api/v1/operator`. La URL efectiva y las credenciales se resuelven mediante CORE (interconexiones `SGI_OPR_SGI_COM_0001_v001` y `SGI_OPR_SGI_COM_0002_v001`). No cambian autenticación, topología ni credenciales.
- Todas las llamadas van autenticadas con el usuario del agente (cabecera `Authorization`). El usuario debe estar vinculado a un empleado (`operator_employee_binding`) y tener una asignación vigente.
- Fechas en ISO-8601 UTC (`2026-10-06T14:15:45Z`). Identificadores en UUID; los `eventId`, `batchId`, `uploadBatchId`, `clientEvidenceId`, `patrolRunId` y `groupId` de Bitácora los **genera la app**.

## Servicios

| # | Servicio | Para qué |
|---|---|---|
| 1 | `GET /runtime` · `GET /runtime?assignmentId={id}` | Asignaciones del agente · tareas del turno con sus coordenadas de referencia y radio |
| 2 | `GET /standard-images/{imageId}?assignmentId={id}` | Foto estándar de guía (opcional) |
| 3 | `POST /evidences` (multipart) | Subir la foto de Patrulla, Consigna o Bitácora con su GPS |
| 4 | `POST /executions` | Confirmar la tarea (Hito, Consigna, Bitácora o Relevo) |
| 5 | `PUT /relief-evidence/{eventId}/{purpose}?assignmentId={id}` | Subir las fotos del relevo (JPEG) |
| 6 | `GET /executions/{eventId}` · `GET /executions?patrolRunId=` · `GET /executions?groupId=` | Consultar el resultado de VISINT |

Los servicios propios de cada módulo no cambian (`/patrol-executions` para iniciar y cerrar la ronda, `/consignment-compliances`, etc.).

### Flujo por tipo de tarea

```text
Patrulla / Consigna / Bitácora                     Relevo
──────────────────────────────                     ──────
1  GET  /runtime                                   1  GET  /runtime
2  GET  /standard-images/{id}   (opcional)         5  PUT  /relief-evidence/{eventId}/{purpose}   × cada foto
3  POST /evidences      (foto + GPS)               4  POST /executions   RELIEF_SUBMITTED (+ GPS)
4  POST /executions     (confirmar)                6  GET  /executions?groupId={eventId del relevo}
6  GET  /executions/{eventId}  cada 3 s mientras outcome = PENDING
```

## 1. Leer el turno — `GET /runtime`

### 1a. Asignaciones del agente

**Request**
```http
GET /api/v1/operator/runtime
Authorization: <credencial del agente>
```

**Response `200`**
```json
{
  "instanceCountryId": "398233d2-293a-4709-ac30-b74c4269e22d",
  "employeeId": "fcf488ea-9c43-3a1d-9494-a5c8a4145fa3",
  "assignments": [
    { "assignmentId": "fbe119dd-ba4a-44af-ad7c-414ffaf2b28f", "postName": "Control de Acceso Principal",
      "startsAt": "2026-10-06T11:00:00Z", "endsAt": "2026-10-06T23:00:00Z", "accessMode": "CURRENT_SHIFT" }
  ]
}
```

### 1b. Tareas del turno

**Request**
```http
GET /api/v1/operator/runtime?assignmentId=fbe119dd-ba4a-44af-ad7c-414ffaf2b28f
Authorization: <credencial del agente>
```

**Response `200`** (una tarea de cada tipo; los campos de ubicación y VISINT están en **negrita** en la tabla de abajo)
```json
{
  "instanceCountryId": "398233d2-293a-4709-ac30-b74c4269e22d",
  "employeeId": "fcf488ea-9c43-3a1d-9494-a5c8a4145fa3",
  "patrols": [{
    "protocolId": "83000000-0000-0000-0000-000000000001", "protocolCode": "PRO-PAT-0001", "protocolVersion": 1,
    "patrolId": "84000000-0000-0000-0000-000000000001", "code": "PAT-001", "name": "Perímetro general",
    "structureType": "CLOSED", "scheduleType": "PROGRAMMED", "sequenceType": "STRICT", "windowStart": "22:00", "windowEnd": "22:45",
    "checkpoints": [{
      "checkpointId": "85000000-0000-0000-0000-000000000001", "code": "H01", "name": "Acceso Principal",
      "description": "Punto de control del acceso principal.", "sortOrder": 1,
      "requiresEvidence": true, "maxPhotos": 1, "visintEnabled": true,
      "standardImageVersion": 1, "standardImageNotes": "Vista frontal completa del acceso.",
      "latitude": -2.154490, "longitude": -79.952253, "radiusM": 50,
      "hasStandardImage": true, "standardImages": [{ "id": "d718bc1b-fad8-4ae1-b5ff-d4c8278ab4ed", "position": 1 }]
    }]
  }],
  "consignmentTasks": [{
    "consignmentId": "5c068634-95ef-4874-bb1f-25987c958fc2", "code": "CON-000006", "title": "Portón perimetral cerrado",
    "instruction": "En cada ronda, verificar que el portón perimetral…", "protocolCode": "PRO-CON-0005", "protocolVersion": 2,
    "expectedLocationMode": "GPS", "latitude": -2.154490, "longitude": -79.952253, "radiusM": 50,
    "evidences": [{
      "evidenceId": "ccc7c958-98d2-4e4c-9f3f-095199842cd9", "name": "Foto del portón", "description": "Foto frontal del portón…",
      "required": true, "visintEnabled": true, "standardImageNotes": "Ambas hojas cerradas y alineadas…",
      "standardImages": [{ "id": "…", "position": 1 }, { "id": "…", "position": 2 }]
    }]
  }],
  "logbookTasks": [{
    "protocolId": "81000000-0000-0000-0000-000000000001", "code": "PRO-BA-0001", "name": "Ingreso de visitantes",
    "objectType": "PAX", "applicationType": "INGRESO", "protocolVersion": 2,
    "latitude": -2.154490, "longitude": -79.952253, "radiusM": 50,
    "fields": [{
      "fieldId": "82000000-0000-0000-0000-000000000001", "accreditationCode": "ACC-001", "section": "IDENTIFICACION",
      "name": "Cédula", "description": "Documento oficial de identidad del visitante…", "fieldType": "DOCUMENTO",
      "required": true, "visintEnabled": true, "standardImageNotes": "Documento completo, sin reflejos…",
      "standardImages": [{ "id": "…", "position": 1 }]
    }]
  }],
  "relief": {
    "assignmentId": "fbe119dd-ba4a-44af-ad7c-414ffaf2b28f", "shiftOccurrenceId": "9604d367-c998-4e5a-9836-8e682a1842e8",
    "postId": "50000000-0000-0000-0000-000000000001", "pointId": "40000000-0000-0000-0000-000000000001",
    "postName": "Control de Acceso Principal", "pointName": "Telco-City", "clientName": "Telconet",
    "incomingEmployeeId": "fcf488ea-9c43-3a1d-9494-a5c8a4145fa3", "incomingEmployeeName": "Alex Enrique Chiriboga Mafla",
    "expectedOutgoingEmployeeId": "…", "expectedOutgoingEmployeeName": "…", "expectedOutgoingShiftEndsAt": "2026-10-06T11:00:00Z",
    "plannedAt": "2026-10-06T11:00:00Z", "shiftStartsAt": "2026-10-06T11:00:00Z", "shiftEndsAt": "2026-10-06T23:00:00Z",
    "inventoryStatus": "PENDING_SOURCE", "noveltiesStatus": "PENDING_SOURCE", "validationStatus": "PENDING_REVIEW",
    "reliefAlreadyRegistered": false,
    "consignments": [{ "consignmentId": "5c068634-95ef-4874-bb1f-25987c958fc2", "code": "CON-000006",
                       "title": "Portón perimetral cerrado", "version": "2:2026-10-05T21:03:18.595071Z", "…": "…" }],
    "bitacoraProtocols": ["…"], "patrolProtocols": ["…"],
    "stationPhotos": ["Vista general del puesto", "…", "…"],
    "configurationVersion": "0c7a3a3c5a6635bf1e4246359b172b143a91ebd3…",
    "stationVisint": { "enabled": true, "standardImageIds": ["32c1e594-0511-41e5-893c-f0f2365702e5", "…", "…"] },
    "postLocation": { "latitude": -2.154490, "longitude": -79.952253, "radiusM": 50 }
  }
}
```

| Tarea | Campos de ubicación y VISINT | Se configura en Comando |
|---|---|---|
| Hito de patrulla | **`patrols[].checkpoints[].latitude`, `longitude`, `radiusM`**, `visintEnabled`, `standardImages[]` | Patrullas → Hito |
| Consigna | **`consignmentTasks[].latitude`, `longitude`, `radiusM`** (solo si `expectedLocationMode = GPS`); `evidences[].visintEnabled`, `standardImages[]` | Consignas → Aplicación → "Coordenadas GPS" |
| Bitácora | **`logbookTasks[].latitude`, `longitude`, `radiusM`**; `fields[].visintEnabled`, `standardImages[]` | Puestos → "Ubicación GPS del puesto" |
| Relevo | **`relief.postLocation`** y **`relief.stationVisint`** | Puestos → "Ubicación GPS del puesto" y "Validar las fotos del puesto del relevo" |

- `radiusM` llega **ya resuelto** (radio propio de la tarea → predeterminado del país → 50 m). Úsalo solo para orientar al agente (por ejemplo, "Estás a 120 m del punto"), nunca para impedirle continuar.
- `latitude`/`longitude` en `null` = la tarea no tiene referencia: no hay nada que comparar.
- `relief.postLocation` y `relief.stationVisint` van **fuera** de `configurationVersion`: cambiarlos en Comando no invalida un relevo en curso.

## 2. Foto de guía — `GET /standard-images/{imageId}?assignmentId={id}`

**Request**
```http
GET /api/v1/operator/standard-images/d718bc1b-fad8-4ae1-b5ff-d4c8278ab4ed?assignmentId=fbe119dd-ba4a-44af-ad7c-414ffaf2b28f
Authorization: <credencial del agente>
```

**Response `200`**: los bytes de la imagen, con `Content-Type: image/jpeg` (o `image/png`, `image/webp`). Sirve para cualquier `standardImages[].id` de una tarea del Puesto del agente. Opcional: solo para mostrarle al agente qué debe fotografiar.

## 3. Subir la foto — `POST /evidences`

**Request**: `multipart/form-data` con una parte `metadata` (JSON) y una parte `files` por foto. El nombre de cada archivo debe ser `{clientEvidenceId}.jpg` (o `.png`, `.webp`). JPEG, PNG o WebP, hasta 5 MB por foto, **una foto por tarea**.

```http
POST /api/v1/operator/evidences
Authorization: <credencial del agente>
Idempotency-Key: 7a0e2b8c-4c1f-4c55-9a51-2f7b1f0e6d10
Content-Type: multipart/form-data; boundary=----sgi

------sgi
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{
  "uploadBatchId": "7a0e2b8c-4c1f-4c55-9a51-2f7b1f0e6d10",
  "eventId": "0db6467d-be89-48a8-b636-ac7ab0520cbe",
  "assignmentId": "fbe119dd-ba4a-44af-ad7c-414ffaf2b28f",
  "targetType": "LOGBOOK_FIELD",
  "targetId": "82000000-0000-0000-0000-000000000001",
  "items": [{
    "clientEvidenceId": "3f6c0a52-1f2e-4b8e-9a7d-5c1d2e3f4a5b",
    "capturedAt": "2026-10-06T14:19:42Z",
    "latitude": -2.153590, "longitude": -79.952253, "accuracyM": 8,
    "source": "CAMERA",
    "sha256": "9b2f…e41c"
  }]
}
------sgi
Content-Disposition: form-data; name="files"; filename="3f6c0a52-1f2e-4b8e-9a7d-5c1d2e3f4a5b.jpg"
Content-Type: image/jpeg

<bytes de la foto>
------sgi--
```

| Campo | Obligatorio | Valor |
|---|---|---|
| `uploadBatchId` | sí | UUID del envío; el mismo va en `Idempotency-Key` |
| `eventId` | sí | UUID de la ejecución; el **mismo** irá en `POST /executions` |
| `assignmentId` | sí | De `/runtime` |
| `targetType` | sí | `PATROL_CHECKPOINT`, `CONSIGNMENT_EVIDENCE` o `LOGBOOK_FIELD` |
| `targetId` | sí | `checkpointId`, `evidences[].evidenceId` de la consigna o `fields[].fieldId` |
| `items[].clientEvidenceId` | sí | UUID de la foto (idempotencia) |
| `items[].capturedAt` | sí | Momento de la captura |
| `items[].latitude`, `longitude` | no | Grados WGS84, tomados **al capturar** |
| `items[].accuracyM` | no | Precisión del GPS en metros (hoy solo se guarda) |
| `items[].source` | sí | `CAMERA` o `GALLERY` (galería queda como marca informativa) |
| `items[].sha256` | sí | Hex del SHA-256 de los bytes enviados |

**Response `200`**
```json
{
  "results": [{
    "clientEvidenceId": "3f6c0a52-1f2e-4b8e-9a7d-5c1d2e3f4a5b",
    "evidenceId": "c1d7e0a4-8b55-4a2b-9e61-0b7f3c2d9a18",
    "status": "STORED",
    "reason": null,
    "flags": ["OUT_OF_RANGE"]
  }]
}
```

| `status` / `reason` | Significado |
|---|---|
| `STORED` | Aceptada. Guarda `evidenceId` para el servicio 4 |
| `ALREADY_STORED` | Reenvío de una foto ya recibida (mismo `clientEvidenceId` y bytes): mismo `evidenceId` |
| `flags: OUT_OF_RANGE` | Fuera del radio. **Solo es un aviso**: la foto sirve igual |
| `flags: GALLERY`, `SUSPECTED_REUSE` | Marcas informativas (foto de galería, foto ya enviada antes) |
| `REJECTED` · `FILE_TOO_LARGE` | Más de 5 MB |
| `REJECTED` · `UNSUPPORTED_FORMAT` | No es JPEG, PNG ni WebP |
| `REJECTED` · `CHECKSUM_MISMATCH` | El `sha256` no coincide con los bytes |
| `REJECTED` · `TOO_MANY_PHOTOS` | Ya hay una foto para esa ejecución |

Reenviar el mismo `clientEvidenceId` con los mismos bytes responde `ALREADY_STORED`; con otros bytes → `409`. La cantidad de partes `files` debe coincidir con `items[]` (el archivo se asocia por nombre).

## 4. Confirmar la tarea — `POST /executions`

Todas las confirmaciones usan el mismo sobre, con **un evento por lote**:

| Campo del sobre | Valor |
|---|---|
| `batchId`, `correlationId` | UUID generados por la app |
| `employeeId`, `instanceCountryId` | De `/runtime` |
| `deviceId` | Identificador del teléfono (máx. 120 caracteres) |
| `capturedAt` | Momento del envío |
| `events` | Lista con **un** evento |

### Hito de patrulla — `PATROL_CHECKPOINT_COMPLETED`

**Request**
```json
{
  "batchId": "5b8a1d7e-2c3f-4e9a-8b1c-6d2e7f3a4b5c",
  "correlationId": "e2f4a6b8-1c3d-4e5f-9a7b-8c9d0e1f2a3b",
  "employeeId": "fcf488ea-9c43-3a1d-9494-a5c8a4145fa3",
  "instanceCountryId": "398233d2-293a-4709-ac30-b74c4269e22d",
  "deviceId": "android-7f3a",
  "capturedAt": "2026-10-06T22:05:12Z",
  "events": [{
    "type": "PATROL_CHECKPOINT_COMPLETED",
    "eventId": "1964e6bd-8443-4d9c-861f-40dff4a2e8fa",
    "assignmentId": "fbe119dd-ba4a-44af-ad7c-414ffaf2b28f",
    "patrolRunId": "610e92fc-4349-4050-93d6-9522573e60eb",
    "patrolId": "84000000-0000-0000-0000-000000000001",
    "checkpointId": "85000000-0000-0000-0000-000000000001",
    "executedAt": "2026-10-06T22:05:10Z",
    "evidenceIds": ["c1d7e0a4-8b55-4a2b-9e61-0b7f3c2d9a18"],
    "observation": "Sin novedad",
    "latitude": -2.154490, "longitude": -79.952253, "accuracyM": 8
  }]
}
```
- `patrolRunId`: UUID de la ronda, generado por la app; el **mismo** para todos los Hitos de esa ronda.
- `evidenceIds`: el `evidenceId` devuelto por el servicio 3 (una foto; vacío si el Hito no requiere evidencia).
- `observation` (máx. 1000) y `latitude`/`longitude`/`accuracyM` son opcionales. La distancia se calcula con el GPS **de la foto**; el del evento es solo un dato.

### Consigna — `TASK_EVIDENCE_SUBMITTED`

**Request** (mismo sobre; solo cambia el evento)
```json
{
  "batchId": "…", "correlationId": "…", "employeeId": "…", "instanceCountryId": "…", "deviceId": "android-7f3a",
  "capturedAt": "2026-10-06T21:03:41Z",
  "events": [{
    "type": "TASK_EVIDENCE_SUBMITTED",
    "eventId": "1cab2fcb-b3aa-4d73-ad88-7c43dd264757",
    "assignmentId": "fbe119dd-ba4a-44af-ad7c-414ffaf2b28f",
    "targetType": "CONSIGNMENT_EVIDENCE",
    "targetId": "ccc7c958-98d2-4e4c-9f3f-095199842cd9",
    "executedAt": "2026-10-06T21:03:40Z",
    "evidenceIds": ["…"]
  }]
}
```
Una foto por evidencia de consigna y **turno**.

### Bitácora — `TASK_EVIDENCE_SUBMITTED` con `groupId`

**Request**
```json
{
  "batchId": "…", "correlationId": "…", "employeeId": "…", "instanceCountryId": "…", "deviceId": "android-7f3a",
  "capturedAt": "2026-10-06T14:19:44Z",
  "events": [{
    "type": "TASK_EVIDENCE_SUBMITTED",
    "eventId": "0db6467d-be89-48a8-b636-ac7ab0520cbe",
    "assignmentId": "fbe119dd-ba4a-44af-ad7c-414ffaf2b28f",
    "targetType": "LOGBOOK_FIELD",
    "targetId": "82000000-0000-0000-0000-000000000001",
    "groupId": "b1e5c3a0-7d2f-4f8e-a6b9-0c1d2e3f4a5b",
    "executedAt": "2026-10-06T14:19:43Z",
    "evidenceIds": ["…"]
  }]
}
```
`groupId` es **obligatorio**: un UUID por registro de visitante, generado por la app. Todos los campos con foto del mismo visitante (cédula, rostro…) usan el mismo `groupId`; otro visitante, otro `groupId`.

### Response `200` (Hito, Consigna y Bitácora)
```json
{
  "serverVersion": "operator-v1",
  "acknowledgedEventIds": ["0db6467d-be89-48a8-b636-ac7ab0520cbe"],
  "rejectedEvents": [],
  "results": [{
    "eventId": "0db6467d-be89-48a8-b636-ac7ab0520cbe",
    "status": "RECEIVED",
    "evidenceCount": 1,
    "validationStatus": "QUEUED_FOR_VISINT"
  }]
}
```
- `validationStatus`: `QUEUED_FOR_VISINT` (se validará) o `NOT_REQUESTED` (la tarea no usa VISINT). Si se reenvía el mismo evento, trae el estado actual de la revisión. Para el resultado, usa siempre el servicio 6.

### Relevo — `RELIEF_SUBMITTED`

Primero se suben las fotos (servicio 5) y después se confirma. Como esas fotos van sin metadatos, **el GPS va en el evento**.

**Request**
```json
{
  "batchId": "…", "correlationId": "…",
  "employeeId": "fcf488ea-9c43-3a1d-9494-a5c8a4145fa3",
  "instanceCountryId": "398233d2-293a-4709-ac30-b74c4269e22d",
  "deviceId": "android-7f3a",
  "capturedAt": "2026-10-06T11:02:30Z",
  "events": [{
    "type": "RELIEF_SUBMITTED",
    "eventId": "080db649-0913-48d9-8036-b33b48b241b9",
    "assignmentId": "fbe119dd-ba4a-44af-ad7c-414ffaf2b28f",
    "postId": "50000000-0000-0000-0000-000000000001",
    "shiftOccurrenceId": "9604d367-c998-4e5a-9836-8e682a1842e8",
    "incomingEmployeeId": "fcf488ea-9c43-3a1d-9494-a5c8a4145fa3",
    "outgoingEmployeeId": "<expectedOutgoingEmployeeId del runtime>",
    "unilateral": false,
    "executedAt": "2026-10-06T11:02:28Z",
    "inventoryStatus": "PENDING_SOURCE",
    "configurationVersion": "0c7a3a3c5a6635bf1e4246359b172b143a91ebd3…",
    "consignmentReadings": [
      { "consignmentId": "5c068634-95ef-4874-bb1f-25987c958fc2", "version": "2:2026-10-05T21:03:18.595071Z", "confirmedAt": "2026-10-06T11:01:50Z" }
    ],
    "evidence": [
      { "purpose": "entrant_face",  "evidenceId": "…" },
      { "purpose": "entrant_full",  "evidenceId": "…" },
      { "purpose": "outgoing_face", "evidenceId": "…" },
      { "purpose": "outgoing_full", "evidenceId": "…" },
      { "purpose": "station_0",     "evidenceId": "…" },
      { "purpose": "station_1",     "evidenceId": "…" },
      { "purpose": "station_2",     "evidenceId": "…" }
    ],
    "latitude": -2.154490, "longitude": -79.952253, "accuracyM": 8
  }]
}
```
- **Nuevo:** `latitude`, `longitude`, `accuracyM` (opcionales; se aplican a las fotos del puesto `station_0..2`).
- `unilateral: true` (sin saliente presente): no se envía `outgoingEmployeeId`, se agrega `unilateralReason` (máx. 1000) y no van las fotos `outgoing_*`.
- `consignmentReadings`: todas las `relief.consignments` vigentes con su `version`. `configurationVersion`: el del runtime (si cambió → `409`, hay que actualizar).
- `evidenceId` de cada foto: el devuelto por el servicio 5. El resto del contrato del relevo no cambia: `docs/RELIEF_OPERATOR_API_V1.md`.

**Response `200`**
```json
{
  "serverVersion": "relief-uat-v1",
  "acknowledgedEventIds": ["080db649-0913-48d9-8036-b33b48b241b9"],
  "rejectedEvents": [],
  "pendingMessages": ["Inventario y novedades pendientes de fuente; revisión visual pendiente"],
  "results": [{
    "eventId": "080db649-0913-48d9-8036-b33b48b241b9",
    "reliefId": "080db649-0913-48d9-8036-b33b48b241b9",
    "status": "PENDIENTE",
    "inventoryStatus": "PENDING_SOURCE",
    "validationStatus": "PENDING_REVIEW",
    "stationVisintStatus": "QUEUED_FOR_VISINT"
  }]
}
```
- **Nuevo:** `stationVisintStatus`: `QUEUED_FOR_VISINT` si el Puesto valida con VISINT, `NOT_REQUESTED` si no. `validationStatus` no cambia.

## 5. Fotos del relevo — `PUT /relief-evidence/{eventId}/{purpose}?assignmentId={id}`

**Request**: una llamada por foto, con el `eventId` que luego llevará el `RELIEF_SUBMITTED`.
```http
PUT /api/v1/operator/relief-evidence/080db649-0913-48d9-8036-b33b48b241b9/station_0?assignmentId=fbe119dd-ba4a-44af-ad7c-414ffaf2b28f
Authorization: <credencial del agente>
Content-Type: image/jpeg

<bytes JPEG, máx. 5 MB>
```
`purpose`: `entrant_face`, `entrant_full`, `outgoing_face`, `outgoing_full`, `station_0`, `station_1`, `station_2`.

**Response `200`**
```json
{ "evidenceId": "6e2a9c41-0d8b-4f7e-b3a5-1c9d8e7f6a5b", "sha256": "4c1e…9b07" }
```
- Repetir la misma foto devuelve la misma respuesta. Otra foto con el mismo `purpose` → `409` "La evidencia ya fue cargada con otro contenido". Después de recibido el relevo → `409` "El relevo ya fue recibido".
- Solo JPEG (`400` "Fotografía JPEG…" si no lo es o supera 5 MB).

## 6. Resultado de VISINT — `GET /executions…`

| Servicio | Devuelve |
|---|---|
| `GET /executions/{eventId}` | Una tarea (objeto) |
| `GET /executions?patrolRunId={id}` | Todas las capturas de la ronda (lista) |
| `GET /executions?groupId={id}` | Registro de visitante de Bitácora, turno de Consigna o relevo (lista; en el relevo `groupId` = `eventId` del relevo) |

Solo devuelve ejecuciones del propio agente (otra → `404`).

### Consigna validada

**Request**
```http
GET /api/v1/operator/executions/1cab2fcb-b3aa-4d73-ad88-7c43dd264757
Authorization: <credencial del agente>
```

**Response `200`**
```json
{
  "eventId": "1cab2fcb-b3aa-4d73-ad88-7c43dd264757",
  "targetType": "CONSIGNMENT_EVIDENCE",
  "targetId": "ccc7c958-98d2-4e4c-9f3f-095199842cd9",
  "patrolRunId": null,
  "groupId": "5e98a289-75c3-4e7f-8d06-da03a3ddd896",
  "checkpointId": "ccc7c958-98d2-4e4c-9f3f-095199842cd9",
  "captureNo": 1,
  "executedAt": "2026-10-05T21:03:40.682Z",
  "receivedAt": "2026-10-05T21:03:40.801874Z",
  "validation": {
    "status": "PASSED", "result": "PASS", "reasonCode": "OK", "reviewedAt": "2026-10-05T21:03:43.545355Z",
    "quality": { "valid": true, "score": 1.0 },
    "match": { "compatible": true, "score": 0.9697 }
  },
  "outcome": "VALIDATED",
  "message": "Foto validada.",
  "canRetake": false,
  "station": null
}
```

### Hito con "no cumple"

```json
{
  "eventId": "…", "targetType": "PATROL_CHECKPOINT", "targetId": "…", "patrolRunId": "…", "groupId": null, "checkpointId": "…",
  "captureNo": 1, "executedAt": "…", "receivedAt": "…",
  "validation": {
    "status": "FAILED", "result": "FAIL", "reasonCode": "NO_REFERENCE_ABOVE_THRESHOLD", "reviewedAt": "…",
    "quality": { "valid": true, "score": 1.0 }, "match": { "compatible": false, "score": 0.0107 }
  },
  "outcome": "NOT_VALIDATED",
  "message": "Evidencia no validada: no coincide con el lugar esperado. El Hito queda registrado; puede tomar una nueva foto.",
  "canRetake": true,
  "station": null
}
```

### Ronda de patrulla con error técnico de VISINT

**Request**
```http
GET /api/v1/operator/executions?patrolRunId=610e92fc-4349-4050-93d6-9522573e60eb
```

**Response `200`**
```json
[{
  "eventId": "1964e6bd-8443-4d9c-861f-40dff4a2e8fa",
  "targetType": "PATROL_CHECKPOINT",
  "targetId": "0d323790-5881-4b34-9250-bcbb88421033",
  "patrolRunId": "610e92fc-4349-4050-93d6-9522573e60eb",
  "groupId": null,
  "checkpointId": "0d323790-5881-4b34-9250-bcbb88421033",
  "captureNo": 1,
  "executedAt": "2026-10-05T22:44:03.995Z",
  "receivedAt": "2026-10-05T22:44:04.162267Z",
  "validation": { "status": "ERROR_FINAL", "result": null, "reasonCode": null, "reviewedAt": null, "quality": null, "match": null },
  "outcome": "TECHNICAL_ERROR",
  "message": "No se pudo validar la foto por un problema técnico. El Hito queda registrado.",
  "canRetake": false,
  "station": null
}]
```

### Fotos del puesto de un relevo

**Request**
```http
GET /api/v1/operator/executions?groupId=080db649-0913-48d9-8036-b33b48b241b9
```

**Response `200`** (una entrada por foto del puesto, ordenadas por `station`)
```json
[
  {
    "eventId": "0b6e997c-4868-4a89-b671-5eb2c6205f6d",
    "targetType": "POST_CONFIG",
    "targetId": "50000000-0000-0000-0000-000000000001",
    "patrolRunId": null,
    "groupId": "080db649-0913-48d9-8036-b33b48b241b9",
    "checkpointId": "50000000-0000-0000-0000-000000000001",
    "captureNo": 1,
    "executedAt": "2026-10-05T22:16:17.115Z",
    "receivedAt": "2026-10-05T22:16:17.526319Z",
    "validation": {
      "status": "PASSED", "result": "PASS", "reasonCode": "OK", "reviewedAt": "2026-10-05T22:16:18.816914Z",
      "quality": { "valid": true, "score": 1.0 }, "match": { "compatible": true, "score": 0.9033 }
    },
    "outcome": "VALIDATED",
    "message": "Foto validada.",
    "canRetake": false,
    "station": "station_0"
  },
  { "eventId": "11ad3fe4-713f-4270-807e-784231a3fee8", "…": "…", "outcome": "VALIDATED", "station": "station_1" },
  { "eventId": "79c217ae-7e24-4d2b-8bfb-2f5183bebe92", "…": "…", "station": "station_2" }
]
```

### Cómo leer el resultado

| `outcome` | `validation.status` | Qué significa | Qué hace la app |
|---|---|---|---|
| `PENDING` | `QUEUED_FOR_VISINT`, `ERROR_RETRYABLE` (reintento automático) | VISINT la está validando | Mostrar `message`; dejar continuar; volver a consultar cada 3 s |
| `VALIDATED` | `PASSED` | VISINT dice "cumple" | Mostrar `message` |
| `NOT_VALIDATED` | `FAILED` | VISINT dice "no cumple" | Mostrar `message`; ofrecer (no exigir) otra foto si `canRetake` |
| `TECHNICAL_ERROR` | `ERROR_FINAL` | VISINT no pudo evaluar | Mostrar `message`; la tarea queda registrada |
| `NOT_REQUIRED` | — (`validation` en `null`) | La tarea no usa VISINT | Nada más |

- `message` ya viene redactado para el agente y siempre confirma que el registro quedó guardado.
- `validation.quality` y `validation.match` son informativos (pueden venir `null`).
- `station` solo viene en el relevo. En el relevo `canRetake` siempre es `false`.
- `checkpointId` repite `targetId` por compatibilidad.

## Nueva foto y duplicados

- **Nueva foto:** se repiten los servicios 3 y 4 con un **`eventId` nuevo** y el mismo destino (misma `patrolRunId`, misma consigna en el turno o mismo `groupId` de visitante). Se acepta mientras la última captura esté pendiente, con error técnico o "no cumple". Queda como `captureNo` 2, 3…
- **`409` = ya registrada.** Responde así si la tarea ya fue validada o no usa VISINT. No es un fallo: la app debe tratarla como completada.
- **Reintentos de red:** reenviar el mismo `eventId` con el mismo contenido devuelve el acuse original. Con contenido distinto → `409` "Identificador reutilizado con datos diferentes".

## Errores

Los errores responden con el código HTTP y **el motivo en texto plano** (`Content-Type: text/plain`), listo para registrar o mostrar.

```http
HTTP/1.1 409 Conflict
Content-Type: text/plain

Este Hito ya fue registrado en la ronda
```

| HTTP | Cuándo | Ejemplos de motivo |
|---|---|---|
| `400` | Request mal formado o fuera de regla | "Campo requerido: batchId", "UUID inválido: eventId", "Se admite un evento por lote", "groupId (id del registro del visitante) es obligatorio en Bitácora", "Foto no autorizada o no cargada: …", "Fecha fuera de la ventana del turno", "patrolRunId o groupId es obligatorio" |
| `401` | Sin credencial o inválida | — |
| `403` | Usuario sin vínculo de empleado, `employeeId`/`instanceCountryId` que no son del agente | "Identidad o instancia incorrecta" |
| `404` | Ejecución o asignación inexistente, o de otro agente | "Ejecución no encontrada" |
| `409` | Duplicado o conflicto | "Este Hito ya fue registrado en la ronda", "Esta evidencia ya fue registrada en el turno", "Este campo ya fue registrado para este visitante", "La asignación ya tiene un relevo recibido", "Identificador reutilizado con datos diferentes", "El contexto cambió; actualiza y vuelve a confirmar las lecturas" |

Recomendación: ante `5xx` o falta de red, reintentar con los **mismos** identificadores (`uploadBatchId`, `clientEvidenceId`, `eventId`, `batchId`); los servicios son idempotentes.

## Qué ve el supervisor en SGI: Comando

Servicios → Punto → Operación:
- **Lista:** módulo (Patrulla, Consigna, Bitácora, Relevo), resultado VISINT (Cumple, No cumple, En revisión, Error) y alerta **"Fuera del radio GPS"**.
- **Detalle:** foto del agente frente a la foto estándar, "Coincidencia 0.96 de umbral 0.80" y "A 1.0 km del punto · radio 50 m".

## Checklist para la app

- [ ] Pedir permiso de ubicación y tomar `latitude`, `longitude` y `accuracyM` al capturar cada foto.
- [ ] Enviar el GPS en `metadata.items[]` (Patrulla, Consigna, Bitácora) y en el evento `RELIEF_SUBMITTED` (Relevo).
- [ ] Sin GPS: enviar la foto igual, sin los campos.
- [ ] Usar el mismo `eventId` en `POST /evidences` y `POST /executions`.
- [ ] Usar `radiusM` del runtime solo como orientación, nunca como bloqueo.
- [ ] No esperar a VISINT para pasar a la siguiente tarea; consultar el resultado cada 3 s mientras `outcome = PENDING`.
- [ ] Mostrar `message` tal como llega.
- [ ] Ofrecer otra foto (con `eventId` nuevo) cuando `canRetake = true`, sin obligar.
- [ ] Tratar `409` "ya registrado" como tarea completada.
- [ ] Reintentar con los mismos identificadores ante errores de red.
- [ ] No enviar `matchThreshold` ni radios.
- [ ] Bitácora: un `groupId` por visitante.

## Referencias

- `docs/API_CATALOG.md` → `OPR-EVIDENCE-LOCATION-001` (contrato detallado).
- `docs/API_CONTRACTS.md` → Fases 1–9 (fotos por multipart, VISINT, resultado al agente, Consignas y Bitácora, ubicación y radio).
- `docs/RELIEF_OPERATOR_API_V1.md` (contrato completo del relevo).
- `docs/VISINT_ESTADO_IMPLEMENTACION.md` (estado de implementación y videos de demostración).
