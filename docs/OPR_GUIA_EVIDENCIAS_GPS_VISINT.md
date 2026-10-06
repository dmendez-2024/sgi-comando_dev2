# Guía para SGI: Operador — fotos, ubicación GPS y VISINT

**Para:** equipo de la app SGI: Operador (agente) · **Desde:** SGI: Comando · **Fecha:** 2026-10-06 · **Versión del contrato:** `v1` (cambios aditivos)

Esta guía resume qué servicios de SGI: Comando debe usar la app del agente para las tareas con foto: **Hitos de patrulla, Consignas, Bitácora y Relevo**, con validación de ubicación GPS y validación visual VISINT. El contrato detallado está en `docs/API_CATALOG.md` → `OPR-EVIDENCE-LOCATION-001`.

## Lo más importante

1. **Nada bloquea al agente.** Ni la ubicación ni VISINT impiden registrar una tarea. Una foto lejos del punto o que VISINT marca "no cumple" se guarda igual; el supervisor ve el aviso en SGI: Comando (Servicios → Punto → Operación).
2. **Envía el GPS de cada foto** (`latitude`, `longitude`, `accuracyM`). Si el teléfono no da ubicación, envía la foto sin ellos: se registra igual y no se compara la distancia.
3. **No esperes a VISINT.** La confirmación responde al instante; el resultado llega en segundos y se consulta aparte. El agente puede pasar a la siguiente tarea mientras tanto.
4. **La app no envía umbrales ni radios.** El umbral de VISINT (`matchThreshold`) y el radio GPS los configura y aplica SGI: Comando.
5. **No hay servicios nuevos.** Son los mismos endpoints `v1` con campos adicionales opcionales: una app que todavía no los envíe sigue funcionando.

## Conexión

- Base: `/api/v1/operator`. La URL efectiva y las credenciales se resuelven mediante CORE (interconexiones `SGI_OPR_SGI_COM_0001_v001` y `SGI_OPR_SGI_COM_0002_v001`). No cambian autenticación, topología ni credenciales.
- El usuario autenticado debe estar vinculado a un empleado (`operator_employee_binding`) y tener una asignación vigente.

## Servicios

| # | Servicio | Para qué |
|---|---|---|
| 1 | `GET /runtime?assignmentId={id}` | Tareas del turno con sus coordenadas de referencia y radio |
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

## 1. Leer el turno — `GET /runtime?assignmentId={id}`

Además de las tareas, trae la **referencia de ubicación** de cada una:

| Tarea | Campos | Se configura en Comando |
|---|---|---|
| Hito de patrulla | `patrols[].checkpoints[].latitude`, `longitude`, `radiusM` | Patrullas → Hito |
| Consigna | `consignmentTasks[].latitude`, `longitude`, `radiusM` (solo si `expectedLocationMode = GPS`) | Consignas → Aplicación → "Coordenadas GPS" |
| Bitácora | `logbookTasks[].latitude`, `longitude`, `radiusM` | Puestos → "Ubicación GPS del puesto" |
| Relevo | `relief.postLocation.latitude`, `longitude`, `radiusM` | Puestos → "Ubicación GPS del puesto" |

- `radiusM` llega **ya resuelto** (radio propio de la tarea → predeterminado del país → 50 m). Úsalo solo para orientar al agente (por ejemplo, "Estás a 120 m del punto"), nunca para impedirle continuar.
- Si una tarea no trae coordenadas, no tiene referencia: no hay nada que comparar.
- `relief.stationVisint.enabled` indica si las fotos del puesto del relevo se validan con VISINT. Es informativo.
- `relief.postLocation` y `relief.stationVisint` van **fuera** de `configurationVersion`: cambiarlos en Comando no invalida un relevo en curso.
- Cada tarea trae `visintEnabled` y `standardImages[]` (ids para el servicio 2).

## 2. Foto de guía — `GET /standard-images/{imageId}?assignmentId={id}`

Devuelve la imagen estándar para mostrarle al agente qué debe fotografiar. Opcional.

## 3. Subir la foto — `POST /evidences`

`multipart/form-data` con la parte `metadata` (JSON) y una o más partes `files`. Cabecera `Idempotency-Key: {uploadBatchId}`. JPEG, PNG o WebP, hasta 5 MB por foto.

```json
{
  "uploadBatchId": "uuid", "eventId": "uuid", "assignmentId": "uuid",
  "targetType": "PATROL_CHECKPOINT | CONSIGNMENT_EVIDENCE | LOGBOOK_FIELD", "targetId": "uuid",
  "items": [{
    "clientEvidenceId": "uuid", "capturedAt": "2026-10-06T14:15:45Z",
    "latitude": -2.154490, "longitude": -79.952253, "accuracyM": 8,
    "source": "CAMERA", "sha256": "hex de la foto"
  }]
}
```

| Campo de `items[]` | Obligatorio | Nota |
|---|---|---|
| `latitude`, `longitude` | no | Grados WGS84, tomados **al capturar la foto** |
| `accuracyM` | no | Precisión del GPS en metros (hoy solo se guarda) |
| `capturedAt` | sí | ISO-8601 |
| `source` | sí | `CAMERA` o `GALLERY` (galería queda como marca informativa) |
| `sha256` | sí | De los bytes enviados |

Respuesta:
```json
{ "results": [{ "clientEvidenceId": "…", "evidenceId": "…", "status": "STORED", "reason": null, "flags": ["OUT_OF_RANGE"] }] }
```
- `STORED` = aceptada. `OUT_OF_RANGE` = la foto quedó fuera del radio: **solo es un aviso**, la foto sirve igual.
- `REJECTED` trae el motivo en `reason` (formato, tamaño, hash, etc.).
- Es idempotente por `clientEvidenceId`.

## 4. Confirmar la tarea — `POST /executions`

Sobre común: `{ batchId, correlationId, employeeId, instanceCountryId, deviceId, capturedAt, events: [ … ] }`. `employeeId` e `instanceCountryId` se toman del runtime.

| Tarea | Evento (`events[0]`) |
|---|---|
| Hito | `{"type":"PATROL_CHECKPOINT_COMPLETED","eventId","assignmentId","patrolRunId","patrolId","checkpointId","executedAt","evidenceIds":[…],"latitude","longitude","accuracyM"}` |
| Consigna | `{"type":"TASK_EVIDENCE_SUBMITTED","eventId","assignmentId","targetType":"CONSIGNMENT_EVIDENCE","targetId","executedAt","evidenceIds":[…]}` |
| Bitácora | igual que Consigna con `"targetType":"LOGBOOK_FIELD"` y **`groupId`** obligatorio (id del registro del visitante) |
| Relevo | `RELIEF_SUBMITTED` (ver abajo) |

- El `eventId` debe ser el mismo que se usó al subir la foto.
- En el Hito, `latitude`/`longitude`/`accuracyM` del evento son solo un dato; la distancia se calcula con el GPS **de la foto**.
- Acuse: `results[0].status = "RECEIVED"` y `validationStatus` = `QUEUED_FOR_VISINT` (se validará) o `NOT_REQUESTED` (la tarea no usa VISINT). Si se reenvía el mismo evento, trae el estado actual de la revisión. Para el resultado, usa siempre el servicio 6.

### Relevo

Las fotos del relevo se suben como JPEG sin metadatos (servicio 5), así que **el GPS va en el evento**:

```json
{ "type": "RELIEF_SUBMITTED", "eventId": "…", "assignmentId": "…", "…": "campos del contrato de relevo",
  "latitude": -2.154490, "longitude": -79.952253, "accuracyM": 8 }
```
- Los tres campos son opcionales y se aplican a las 3 fotos del puesto (`station_0..2`).
- El acuse añade `results[0].stationVisintStatus`: `QUEUED_FOR_VISINT` si el Puesto valida con VISINT, `NOT_REQUESTED` si no. El campo `validationStatus` (`PENDING_REVIEW`) no cambia.
- El resto de campos del relevo (puesto, turno, saliente, lecturas de consignas, `configurationVersion`, `inventoryStatus`, fotos) no cambia: ver `docs/RELIEF_OPERATOR_API_V1.md`.

## 5. Fotos del relevo — `PUT /relief-evidence/{eventId}/{purpose}?assignmentId={id}`

Cuerpo: JPEG en crudo (`Content-Type: image/jpeg`), hasta 5 MB. `purpose`: `entrant_face`, `entrant_full`, `outgoing_face`, `outgoing_full`, `station_0`, `station_1`, `station_2`. Respuesta `{ "evidenceId": "…", "sha256": "…" }`. Repetir la misma foto devuelve la misma respuesta; otra foto con el mismo `purpose` → `409`.

## 6. Resultado de VISINT

| Servicio | Devuelve |
|---|---|
| `GET /executions/{eventId}` | Una tarea |
| `GET /executions?patrolRunId={id}` | Todas las capturas de la ronda |
| `GET /executions?groupId={id}` | Registro de visitante (Bitácora), turno (Consigna) o relevo (`groupId` = `eventId` del relevo) |

```json
{ "eventId": "…", "targetType": "LOGBOOK_FIELD", "targetId": "…", "groupId": "…", "captureNo": 1,
  "executedAt": "…", "receivedAt": "…",
  "validation": { "status": "FAILED", "result": "FAIL", "reasonCode": "NO_REFERENCE_ABOVE_THRESHOLD", "reviewedAt": "…",
                  "quality": { "valid": true, "score": 1.0 }, "match": { "compatible": false, "score": 0.31 } },
  "outcome": "NOT_VALIDATED", "message": "…", "canRetake": true, "station": null }
```

| `outcome` | Qué significa | Qué hace la app |
|---|---|---|
| `PENDING` | VISINT la está validando | Mostrar `message`; dejar continuar; volver a consultar cada 3 s |
| `VALIDATED` | VISINT dice "cumple" | Mostrar `message` |
| `NOT_VALIDATED` | VISINT dice "no cumple" | Mostrar `message`; ofrecer (no exigir) otra foto si `canRetake` |
| `TECHNICAL_ERROR` | VISINT no pudo evaluar | Mostrar `message`; la tarea queda registrada |
| `NOT_REQUIRED` | La tarea no usa VISINT | Nada más |

- `message` ya viene redactado para el agente y siempre confirma que el registro quedó guardado.
- `validation.quality` y `validation.match` son informativos.
- `station` solo viene en el relevo (`station_0..2`). En el relevo `canRetake` siempre es `false`.

## Nueva foto y duplicados

- **Nueva foto:** con un **`eventId` nuevo** y el mismo destino (misma ronda, consigna o visitante) se acepta mientras la última captura esté pendiente, con error técnico o "no cumple". Queda como `captureNo` 2, 3…
- **`409` = ya registrada.** Responde así si la tarea ya fue validada o no usa VISINT. No es un fallo: la app debe tratarla como completada.
  - "Este Hito ya fue registrado en la ronda"
  - "Esta evidencia ya fue registrada en el turno"
  - "Este campo ya fue registrado para este visitante"
  - "La asignación ya tiene un relevo recibido" (un relevo por asignación)
- **Reintentos de red:** reenviar el mismo `eventId` con el mismo contenido devuelve el acuse original. Con contenido distinto responde `409`.

## Qué ve el supervisor en SGI: Comando

Servicios → Punto → Operación:
- **Lista:** módulo (Patrulla, Consigna, Bitácora, Relevo), resultado VISINT (Cumple, No cumple, En revisión, Error) y alerta **"Fuera del radio GPS"**.
- **Detalle:** foto del agente frente a la foto estándar, "Coincidencia 0.96 de umbral 0.80" y "A 1.0 km del punto · radio 50 m".

## Checklist para la app

- [ ] Pedir permiso de ubicación y tomar `latitude`, `longitude` y `accuracyM` al capturar cada foto.
- [ ] Enviar el GPS en `metadata.items[]` (Patrulla, Consigna, Bitácora) y en el evento `RELIEF_SUBMITTED` (Relevo).
- [ ] Sin GPS: enviar la foto igual, sin los campos.
- [ ] Usar `radiusM` del runtime solo como orientación, nunca como bloqueo.
- [ ] No esperar a VISINT para pasar a la siguiente tarea; consultar el resultado cada 3 s mientras `outcome = PENDING`.
- [ ] Mostrar `message` tal como llega.
- [ ] Ofrecer otra foto (con `eventId` nuevo) cuando `canRetake = true`, sin obligar.
- [ ] Tratar `409` "ya registrado" como tarea completada.
- [ ] No enviar `matchThreshold` ni radios.
- [ ] Bitácora: un `groupId` por visitante.

## Referencias

- `docs/API_CATALOG.md` → `OPR-EVIDENCE-LOCATION-001` (contrato detallado).
- `docs/API_CONTRACTS.md` → Fases 1–9 (fotos por multipart, VISINT, resultado al agente, Consignas y Bitácora, ubicación y radio).
- `docs/RELIEF_OPERATOR_API_V1.md` (contrato completo del relevo).
- `docs/VISINT_ESTADO_IMPLEMENTACION.md` (estado de implementación y videos de demostración).
