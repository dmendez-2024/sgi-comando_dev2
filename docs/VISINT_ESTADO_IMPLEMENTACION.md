# Fotos del agente + VISINT — estado de implementación

**Actualizado:** 2026-09-30 · **Rama:** `acordova` · **Alcance:** Hitos de Patrullas, evidencias de Consignas y campos de Bitácora.
Flujo: `SGI: Operador (agente) → SGI: Comando → VISINT → SGI: Comando (Operación)`. Impulsos queda **fuera de alcance** por ahora.

| Fase | Estado | Commit |
|---|---|---|
| 1 · Fotos del agente por FormData y almacenamiento en MinIO | Hecha | `b5aa37a` |
| 2 · VISINT para Hitos de patrulla + vista Operación | Hecha, **probada contra el VISINT real** | sin commit |
| 3 · Devolver el resultado al agente (Operador) + nueva captura tras "No cumple" | Hecha, probada contra el VISINT real | sin commit |
| 4 · Consignas y Bitácora (fotos estándar 1–5, foto del agente, VISINT, resultado, Operación) | Hecha, probada contra el VISINT real | sin commit |
| Pendiente | Revisión manual, alertas, retención/purga de fotos | — |

---

## 1. Cómo funciona hoy

```text
Coordinador (Comando)                      Agente (Operador / Simulador UAT)          VISINT (Joel)
─────────────────────                      ────────────────────────────────          ─────────────
Hito: requiere evidencia                   1. Toma UNA foto del Hito
      + 1..5 fotos estándar                2. POST /api/v1/operator/evidences (multipart)
      (VISINT siempre activo si requiere evidencia)    3. POST /api/v1/operator/executions
Publica el protocolo                          → ack inmediato: QUEUED_FOR_VISINT
                                                        │
                           Comando (worker, cada 2 s) ──┴──► POST /v1/evidence/validate (multipart)
                             image = foto del agente          referenceImages = fotos estándar (1..5)
                                                        ◄──── status / reasonCode / matchedReferenceId
Servicios → Punto → Operación: Cumple / No cumple / Error, foto del agente vs estándar que coincidió
```

- **VISINT está siempre activo** en los Hitos que requieren evidencia (la opción no se muestra en pantalla). Por eso, para publicarse, el Hito necesita al menos una foto estándar.
- **VISINT decide el veredicto** (basta coincidir con una foto estándar). Comando no usa puntajes ni reglas propias; solo guarda y muestra.
- **La confirmación del agente nunca espera a VISINT**: se encola y un worker hace la llamada en segundos.

## 2. Qué se construyó

### Fase 1 — fotos del agente (commit `b5aa37a`)
- Subida por `multipart/form-data` (`metadata` JSON + `files`), idempotente por `clientEvidenceId`, con validación de formato real (JPEG/PNG/WebP), sha256, 5 MB por foto.
- Fotos en **MinIO** (`sgi-evidence`), no en la base. Fotos estándar también en MinIO (`sgi-standard`); las antiguas en `bytea` se migran solas al arrancar.
- Alertas por foto: `GALLERY`, `OUT_OF_RANGE` (fuera del radio GPS del Hito), `SUSPECTED_REUSE`.
- Confirmación del Hito (`PATROL_CHECKPOINT_COMPLETED`) idempotente por `eventId`; un Hito se registra una vez por ronda.
- Simulador de Agente (UAT, **temporal** para demo).

### Fase 2 — VISINT (sin commit)
- **Hasta 5 fotos estándar por Hito** (galería en *Reglas por hito*: agregar, quitar, se reordenan). La foto estándar única anterior pasó a ser la n.º 1. Al crear una nueva versión del protocolo se copian.
- **Una sola foto del agente por Hito**: se eliminó la configuración de mínimo/máximo; una segunda foto se rechaza (`TOO_MANY_PHOTOS`).
- VISINT activado por defecto en todo Hito con evidencia; la casilla "Validar con VISINT" se retiró de la pantalla.
- Las alertas "Foto repetida" (`SUSPECTED_REUSE`) y "De galería" (`GALLERY`) se siguen registrando pero no se muestran en pantalla; solo se muestra "Fuera de GPS".
- **Revisión visual** (`visual_review`) por ejecución, con snapshot de las fotos estándar enviadas (`visual_review_standard`): si luego cambian las del Hito, Operación sigue mostrando las que se compararon.
- **Worker** con reintentos automáticos (30 s, 60 s, 120 s, 240 s; tope 10 min; 5 intentos) y **reintento manual** desde Operación.
- **Vista Operación** (Servicios → Punto → Operación): KPIs (Ejecuciones, Cumplen, No cumplen, En revisión, Error VISINT, Sin VISINT), tabla de ejecuciones y detalle con foto del agente vs foto estándar que coincidió ("Coincide"), galería de estándar enviadas, código de VISINT, versión del modelo y línea de tiempo.
- **VISINT simulado** solo para UAT (`SGI_VISINT_MODE=MOCK` + `SGI_UAT_FEATURES_ENABLED=true`); fuera de UAT se rechaza. `/api/features` oculta el Simulador de Agente si no hay bandera UAT.
- **Adaptador VISINT real** (llamada directa, no vía CORE).

### Fase 3 — resultado para el agente (sin commit)
- `GET /api/v1/operator/executions/{eventId}` y `?patrolRunId=`: estado de la validación, `outcome` (`NOT_REQUIRED` / `PENDING` / `VALIDATED` / `NOT_VALIDATED` / `TECHNICAL_ERROR`), mensaje para el agente y `canRetake`. La app consulta cada 3 s mientras está `PENDING`.
- **Nueva captura tras "No cumple"**: mismo Hito y misma ronda, `eventId` nuevo → `captureNo` 2, 3… Solo si la última captura "no cumple"; mientras se valida o si ya cumplió → 409. Un error técnico de VISINT no obliga a repetir (el Hito queda registrado). Sin tope de capturas (pendiente Gerencia).
- Simulador de Agente: muestra el resultado y el botón "Tomar nueva foto". Operación: "Captura N" en la tabla.
- Verificado con el VISINT real: extintor → "Evidencia no validada" → nueva foto 1b → "Captura 2 · Foto validada". Video: `evidencias_playwright/fase3_resultado_agente/`.

### Fase 4 — Consignas y Bitácora (sin commit)
- Fotos estándar 1–5 en `standard_reference_image` para los tres módulos (V42 migra las existentes); galería compartida en las tres configuraciones.
- Consignas: foto por evidencia tipo Foto de consigna **vigente**, una por turno, VISINT activo. Bitácora: foto por campo y **registro de visitante** (`groupId`); VISINT en campos DOCUMENTO (Cédula, Pasaporte, Credencial); **Rostro sin VISINT**.
- Agente: `consignmentTasks` / `logbookTasks` en el runtime y evento `TASK_EVIDENCE_SUBMITTED`. Simulador con tipo de tarea (Patrulla / Consigna / Bitácora) y "Nuevo visitante".
- Operación: columna Tarea con módulo (Patrulla / Consigna / Bitácora).
- Corregido de paso: activar un protocolo de Consignas cuando el Punto ya tenía otro activo fallaba con 500.
- Verificado con el VISINT real: consigna extintor → no cumple → nueva foto 1b → cumple; cédula 1b → cumple; rostro → guardado. Video: `evidencias_playwright/fase4_consignas_bitacora/`.

## 3. Contrato con VISINT (vigente)

`POST http://181.39.84.138:8010/v1/evidence/validate` · `multipart/form-data` (archivos, **no** base64) · cabecera `X-API-Key: <token>` · **HTTP/1.1** (con `Upgrade: h2c` el servidor pierde el cuerpo y responde 422).

| Campo | Valor que envía Comando |
|---|---|
| `requestId` | `<id de la revisión>-<n.º de intento>` |
| `source` | `SGI_COM` |
| `companyId`, `pointId`, `postId` | de la ejecución |
| `serviceType` | `PATRULLA` |
| `serviceId` / `activityId` | ronda de patrulla / Hito |
| `evidenceId`, `capturedAt`, `latitude`, `longitude` | de la foto del agente |
| `image` (archivo) | **foto del agente** |
| `referenceImages` (archivo, 1..5) + `referenceIds` | **fotos estándar** del Hito, en orden |

Respuesta: `{requestId, status, quality{valid,score}, match{compatible,score}, matchedReferenceId, reasonCode, processedAt, modelVersion}`. Los `score` se ignoran.

| `status` de VISINT | En Comando |
|---|---|
| `PASS` | **Cumple** (se marca la foto estándar `matchedReferenceId`) |
| `FAIL_QUALITY`, `FAIL_NO_MATCH`, `FAIL_INVALID_IMAGE` | **No cumple**, con el `reasonCode` (p. ej. `NO_REFERENCE_ABOVE_THRESHOLD`) |
| `ERROR_VISINT` (aunque venga con HTTP 500) | **VISINT no pudo evaluar** — falla técnica, no es incumplimiento; reintento manual |
| `TIMEOUT`, `PROCESSING` | Reintento automático |
| HTTP no 2xx sin `status`, sin conexión, foto ilegible | Reintento automático; el motivo de VISINT queda en el error |

Detalle completo: `docs/API_CONTRACTS.md` (Fase 2) y decisiones `SGI-VIS-DEC-001..011` en `docs/DECISIONS.md`.

## 4. Configuración

| Variable | Valor local (`.env`, no versionado) | Por defecto |
|---|---|---|
| `SGI_VISINT_MODE` | `HTTP` (real) | `HTTP` |
| `SGI_INTERCONNECTIONS_CORE_RESOLVER_URL` | `http://192.168.20.138:5173` (CORE) | vacío → se usa `SGI_VISINT_URL` |
| `SGI_INTERCONNECTIONS_ENVIRONMENT` | `LOCAL` | `UAT` |
| `SGI_INTERCONNECTIONS_INSTANCE_COUNTRY_ID` | `398233d2-293a-4709-ac30-b74c4269e22d` (temporal, hasta que IDENT entregue el id de la empresa) | vacío → se usa `SGI_VISINT_URL` |
| `SGI_VISINT_INTERCONNECTION_CODE` | — | `SGI_COM_VISINT_0001_v002` |
| `SGI_VISINT_URL` | `http://181.39.84.138:8010/v1/evidence/validate` (respaldo si CORE no resuelve) | vacío y sin CORE → "VISINT no configurado" |
| `SGI_VISINT_TOKEN` | token de VISINT (solo en `.env`) | vacío |
| `SGI_VISINT_AUTH_HEADER` / `SGI_VISINT_AUTH_SCHEME` | — | `X-API-Key` / sin esquema |
| `SGI_VISINT_TIMEOUT_SECONDS` | — | `60` |
| `SGI_UAT_FEATURES_ENABLED` | `true` | `false` |
| `SGI_OPERATOR_RELIEF_UAT_ENABLED` | `true` | `false` |

La URL de VISINT se pide primero a CORE: `GET {CORE}/api/v1/interconnections/SGI_COM_VISINT_0001_v002/resolve?instanceCountryId=…&environment=…` → `resolvedUrl` (en caché 5 min, se acepta la anterior hasta 1 h si CORE falla). Si CORE no está configurado, no responde o no tiene binding (404), se usa `SGI_VISINT_URL`. El log indica la fuente: `URL de VISINT desde CORE …` o `… desde SGI_VISINT_URL`. El token sigue saliendo de `SGI_VISINT_TOKEN` (CORE responde `credentialRef: null`); de CORE no se usan `timeoutMs` (5000) ni `retries`.

Para demo sin VISINT real: `SGI_VISINT_MODE=MOCK` y `docker compose up -d backend`.

Migraciones: `V36`–`V37` (fase 1), `V38` `visual_review`, `V39` respuesta de VISINT, `V40` fotos estándar múltiples, snapshot por revisión y eliminación de mínimo/máximo, `V41` número de captura (fase 3), `V42` fotos estándar comunes y grupo de ejecución, `V43` tipos de destino (fase 4).

## 5. Verificación

- **Backend:** 80/80 pruebas (`scripts/backend-test.ps1`), incluida una llamada multipart contra un VISINT falso local (campos, orden, token, sin `Upgrade`). **Frontend:** build OK.
- **E2E contra el VISINT real (2026-09-30):** PRO-PAT-0006 v5 · H01 con 2 fotos estándar, 3 ejecuciones del agente:

| Foto del agente | Esperado | VISINT | Operación |
|---|---|---|---|
| Idéntica a la estándar 1 (portón con candado) | Cumple | `PASS` · `OK` | Cumple — coincide estándar 1 |
| Otro elemento (extintor) | No cumple | `FAIL_NO_MATCH` · `NO_REFERENCE_ABOVE_THRESHOLD` | No cumple |
| Otro lugar (local cerrado) | No cumple | `FAIL_NO_MATCH` · `NO_REFERENCE_ABOVE_THRESHOLD` | No cumple |

  Respuesta en 2–5 s, modelo `visint-evidence-1/dinov2-large-v1/quality-1`. Script: `visint_real_e2e.mjs` (scratchpad de la sesión).

## 6. Pendientes

**Con VISINT (Joel)**
- Confirmar si los reintentos deben reusar el mismo `requestId` (hoy va uno nuevo por intento; VISINT rechaza reusar un `requestId` con contenido distinto: `REQUEST_ID_CONFLICT`).
- Lista cerrada de `reasonCode`.
- HTTPS y rotación del token antes de producción (hoy `http://`, token en claro).

**Comando**
- Decisión de Gerencia: qué pasa tras X intentos fallidos (bloquear, continuar con observación o aprobación del Supervisor).
- Probar con una foto *parecida* (mismo sitio, otro ángulo), no solo idéntica.
- API de Patrullas: los 400 no traen el motivo en el cuerpo (p. ej. 6.ª foto estándar).
- Regrabar los videos de Playwright (muestran el flujo anterior: varias fotos del agente, una estándar).
- Menores: la lista de Operación hace varias consultas por fila; el worker procesa las revisiones de a una.
- Quitar el Simulador de Agente (`SIMULATOR_USER`) después de la demo.

**Fuera de alcance:** Impulsos; reconocimiento facial / identificación del agente.

## 7. Archivos principales

- Backend: `visint/` (`HttpVisintAdapter`, `MockVisintAdapter`, `VisintClient`, `VisualReviewService`, `VisualReviewWorker`, `VisualReview`, `VisualReviewStandard`), `operation/OperationResource`, `patrols/PatrolCheckpointStandardImage` + `PatrolResource`, `operator/` (`OperatorEvidenceResource`, `PatrolExecutionService`, `OperatorPatrols`), `common/FeaturesResource`.
- Frontend: `pages/PatrolConfig.tsx` (galería de estándar + VISINT), `pages/OperationPage.tsx`, `pages/AgentSimulator.tsx` (temporal), `api.ts`.
- Planes: `docs/superpowers/plans/2026-09-29-fase1-fotos-agente-formdata.md`, `…-fase2-visint-patrullas.md` (el plan de fase 2 es el original; este documento refleja lo implementado).
