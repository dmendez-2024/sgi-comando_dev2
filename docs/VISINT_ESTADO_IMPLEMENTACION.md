# Fotos del agente + VISINT — estado de implementación

**Actualizado:** 2026-10-06 · **Rama:** `acordova` · **Alcance:** Hitos de Patrullas, evidencias de Consignas, campos de Bitácora y fotos del puesto en el Relevo.
Flujo: `SGI: Operador (agente) → SGI: Comando → VISINT → SGI: Comando (Operación)`. Impulsos queda **fuera de alcance** por ahora.

**Regla general (desde la fase 8):** ni VISINT ni la ubicación GPS bloquean al agente. Todo se registra y el supervisor lo ve en Servicios → Punto → Operación.

| Fase | Estado | Commit |
|---|---|---|
| 1 · Fotos del agente por FormData y almacenamiento en MinIO | Hecha | `b5aa37a` |
| 2 · VISINT para Hitos de patrulla + vista Operación | Hecha, **probada contra el VISINT real** | `cfe20ec` |
| 3 · Devolver el resultado al agente (Operador) + nueva captura tras "No cumple" | Hecha, probada contra el VISINT real | `cfe20ec` |
| 4 · Consignas y Bitácora (fotos estándar 1–5, foto del agente, VISINT, resultado, Operación) | Hecha, probada contra el VISINT real | `ec9a1fb` |
| 5 · VISINT **opcional** en las fotos del puesto del Relevo | Hecha, probada contra el VISINT real | `5ac2f76` |
| 6 · Umbral de coincidencia (`matchThreshold`) configurable por tarea | Hecha, probada contra el VISINT real | `5ac2f76` |
| 7 · Ubicación GPS de referencia (Hito, Consigna, Puesto → Bitácora y Relevo) y aviso "Fuera del radio GPS" | Hecha | `5ac2f76`, `28b566c` |
| 8 · VISINT y ubicación **no bloqueantes** para el agente | Hecha | `28b566c` |
| 9 · Radio GPS configurable desde la base (predeterminado por país + radio propio) | Hecha | `28b566c` (backend), `bc0fafd` (pantallas) |
| Pendiente | Revisión manual, alertas, retención/purga de fotos, alerta "Sin GPS" | — |

---|---|---|
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
                             matchThreshold = umbral de la tarea (0.00–1.00)
                                                        ◄──── status / reasonCode / matchedReferenceId
Servicios → Punto → Operación: Cumple / No cumple / Error, foto del agente vs estándar que coincidió
```

- **VISINT está siempre activo** en los Hitos que requieren evidencia (la opción no se muestra en pantalla). Por eso, para publicarse, el Hito necesita al menos una foto estándar.
- **VISINT decide el veredicto** (basta coincidir con una foto estándar) con el **umbral que envía Comando** (`matchThreshold`, configurado por tarea). Comando no aplica reglas propias sobre los puntajes; solo los guarda y muestra.
- **Ubicación:** si la tarea tiene coordenadas de referencia, cada foto se compara con ellas; fuera del radio queda la marca `OUT_OF_RANGE` ("Fuera del radio GPS" en Operación). Es solo un aviso.
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

### Fase 5 — VISINT opcional en el Relevo (commit `5ac2f76`, migración `V55`)
- Puestos → casilla **"Validar las fotos del puesto del relevo"** (`post_operational_config.station_visint_enabled`, apagada por defecto). Si está activa, las 3 fotos del puesto del relevo (`station_0..2`) se comparan con las fotos estándar del Puesto.
- Cada foto del puesto queda como una ejecución `RELIEF_STATION_CAPTURED` (`task_execution.relief_evidence_id`, `target_type = POST_CONFIG`) con su revisión visual; a VISINT va con `serviceType = RELEVO`.
- **Nunca bloquea el relevo**: el acuse del `RELIEF_SUBMITTED` añade `stationVisintStatus` (`QUEUED_FOR_VISINT` / `NOT_REQUESTED`) y no cambia `validationStatus`. En el resultado, `canRetake` siempre es `false` y cada captura trae `station`.
- Operación muestra el módulo **Relevo** junto a Patrulla, Consigna y Bitácora. Simulador de Agente: modo **Relevo** (`AgentReliefSim.tsx`).
- Fotos de prueba: `fotos_estandar_ejemplo/relevo_v1` (DanTD, CC0). Video: `evidencias_playwright/v6_video_relevo_visint/`.

### Fase 6 — Umbral de coincidencia `matchThreshold` (commit `5ac2f76`, migración `V56`)
- VISINT pasó a **exigir** `matchThreshold` (texto con dos decimales entre `0.00` y `1.00`). Lo envía **Comando**; SGI: Operador no lo envía.
- Se configura en la web **junto a las fotos estándar** de cada tarea con el control `MatchThresholdField`: barra 0–1, campo de valor exacto con punto decimal, zona **baja ≤ 0.40** marcada en rojo con advertencia y la etiqueta "predeterminado" cuando no se cambió.
- Columnas: `patrol_checkpoint.match_threshold`, `consignment_evidence.match_threshold`, `logbook_protocol_field.match_threshold`, `post_operational_config.station_match_threshold`; `NULL` = predeterminado de SGI (`SGI_VISINT_MATCH_THRESHOLD`, 0.80).
- Cada revisión guarda el umbral con que se envió (`visual_review.match_threshold`): cambiarlo después no altera lo evaluado. Operación muestra "Coincidencia 0.96 de umbral 0.80".
- Código: `visint/MatchThreshold` (`normalize`, `format`, `LOW = 0.40`), `VisualReviewService.thresholdOf`, `VisintPort.ReviewRequest.matchThreshold`, `HttpVisintAdapter`.
- Video: `evidencias_playwright/v8_video_umbral_visint/`.

### Fase 7 — Ubicación GPS de referencia (commits `5ac2f76` y `28b566c`, migración `V57`)
- **Hito:** latitud y longitud editables en *Reglas por hito* (o "Capturar GPS en campo").
- **Consigna:** Aplicación → Ubicación esperada "Coordenadas GPS" (latitud/longitud).
- **Puesto:** bloque "Ubicación GPS del puesto" (`post_operational_config.latitude/longitude`, botón "Capturar GPS"). Es la referencia de **Bitácora** y **Relevo**.
- La app recibe la referencia en el runtime (`patrols[].checkpoints[]`, `consignmentTasks[]`, `logbookTasks[]`, `relief.postLocation`) y envía el GPS de cada foto en `metadata.items[]` (`latitude`, `longitude`, `accuracyM`); en el relevo va en el evento `RELIEF_SUBMITTED`.
- Operación: alerta **"Fuera del radio GPS"** en la lista y, en el detalle, "A 1.0 km del punto · radio 50 m".
- Contrato para el equipo del Operador: `docs/API_CATALOG.md` → `OPR-EVIDENCE-LOCATION-001`.
- Videos: `v7_video_gps_hito/` (parcial: lo cortó el cambio de VISINT a `matchThreshold` obligatorio), `v9_video_gps_consigna/`, `v10_video_gps_bitacora_relevo/`.

### Fase 8 — No bloqueante para el agente (commit `28b566c`)
- Se acepta una nueva captura mientras la anterior esté **en cola de VISINT, con error técnico o "no cumple"** (`captureNo` + 1). Solo una captura `PASSED` o sin VISINT (`NOT_REQUESTED`) cuenta como registrada (`409`).
- Mensajes al agente: siempre dicen que el registro quedó guardado ("… VISINT la está validando; puede continuar.", "… queda registrado; puede tomar una nueva foto."). `canRetake` es una sugerencia.
- Simulador: "Siguiente tarea" siempre habilitado.

### Fase 9 — Radio GPS configurable desde la base (commits `28b566c` y `bc0fafd`, migración `V58`)
- **Predeterminado por país**: tabla `operational_setting` (`instance_country_id`, `setting_key = 'evidence.default_radius_m'`, `setting_value`, `updated_by_username`, `updated_at`). Se edita en el menú **Configuración** (`SettingsPage.tsx`, `GET/PUT /api/settings/evidence-location`), entre 5 y 5000 m. Solo Presidencia, las Direcciones de Operaciones (LATAM y Nacional) y la Dirección Nacional pueden cambiarlo; el resto lo ve en solo lectura.
- **Radio propio** (tiene prioridad; campo "Radio (m)", `RadiusField.tsx`): `patrol_checkpoint.radius_m`, `consignment.expected_radius_m`, `post_operational_config.radius_m`. Vacío = usa el predeterminado.
- Orden: radio propio → predeterminado del país → 50 m (`sgi.evidence.default-radius-m`). Código: `settings/EvidenceLocationSettings` (`radiusFor`, `postReference`).
- **Snapshot por foto**: `evidence_object.reference_radius_m` y `reference_distance_m`. Cambiar el radio no altera los avisos ya registrados.
- Video: `evidencias_playwright/v11_video_radio_configurable/` (predeterminado 120 m → foto a 100 m sin aviso; radio propio 80 m → aviso; la foto anterior conserva "radio 120 m").

## 3. Contrato con VISINT (vigente)

`POST http://181.39.84.138:8010/v1/evidence/validate` · `multipart/form-data` (archivos, **no** base64) · cabecera `X-API-Key: <token>` · **HTTP/1.1** (con `Upgrade: h2c` el servidor pierde el cuerpo y responde 422).

| Campo | Valor que envía Comando |
|---|---|
| `requestId` | `<id de la revisión>-<n.º de intento>` |
| `source` | `SGI_COM` |
| `companyId`, `pointId`, `postId` | de la ejecución |
| `serviceType` | `PATRULLA` / `CONSIGNA` / `BITACORA` / `RELEVO` |
| `serviceId` / `activityId` | ronda / consigna / registro del visitante / relevo · Hito / evidencia / campo / Puesto |
| `evidenceId`, `capturedAt`, `latitude`, `longitude` | de la foto del agente |
| `matchThreshold` | umbral de la tarea con dos decimales (`"0.80"`); VISINT lo exige |
| `image` (archivo) | **foto del agente** |
| `referenceImages` (archivo, 1..5) + `referenceIds` | **fotos estándar** de la tarea, en orden |

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
| `SGI_INTERCONNECTIONS_CORE_RESOLVER_URL` | `https://apps.cajamarca.ec/dev.core` (CORE) | vacío → se usa `SGI_VISINT_URL` |
| `SGI_INTERCONNECTIONS_ENVIRONMENT` | `LOCAL` | `UAT` |
| `SGI_INTERCONNECTIONS_INSTANCE_COUNTRY_ID` | `398233d2-293a-4709-ac30-b74c4269e22d` (temporal, hasta que IDENT entregue el id de la empresa) | vacío → se usa `SGI_VISINT_URL` |
| `SGI_VISINT_INTERCONNECTION_CODE` | — | `SGI_COM_VISINT_0001_v002` |
| `SGI_VISINT_URL` | `http://181.39.84.138:8010/v1/evidence/validate` (respaldo si CORE no resuelve) | vacío y sin CORE → "VISINT no configurado" |
| `SGI_VISINT_TOKEN` | token de VISINT (solo en `.env`) | vacío |
| `SGI_VISINT_AUTH_HEADER` / `SGI_VISINT_AUTH_SCHEME` | — | `X-API-Key` / sin esquema |
| `SGI_VISINT_TIMEOUT_SECONDS` | — | `60` |
| `SGI_VISINT_MATCH_THRESHOLD` | `0.8` | `0.8` (umbral cuando la tarea no tiene uno propio) |
| `SGI_UAT_FEATURES_ENABLED` | `false` (en `true` solo para grabar con el Simulador) | `false` |
| `SGI_OPERATOR_RELIEF_UAT_ENABLED` | `true` | `false` |

La URL de VISINT se pide primero a CORE: `GET {CORE}/api/v1/interconnections/SGI_COM_VISINT_0001_v002/resolve?instanceCountryId=…&environment=…` → `resolvedUrl` (en caché 5 min, se acepta la anterior hasta 1 h si CORE falla). Si CORE no está configurado, no responde o no tiene binding (404), se usa `SGI_VISINT_URL`. El log indica la fuente: `URL de VISINT desde CORE …` o `… desde SGI_VISINT_URL`. El token sigue saliendo de `SGI_VISINT_TOKEN` (CORE responde `credentialRef: null`); de CORE no se usan `timeoutMs` (5000) ni `retries`.

Para demo sin VISINT real: `SGI_VISINT_MODE=MOCK` y `docker compose up -d backend`.

Radio GPS: no es variable de entorno. El predeterminado del país se guarda en `operational_setting` (Configuración); si no hay fila rige `sgi.evidence.default-radius-m=50` (`application.properties`).

Migraciones: `V36`–`V37` (fase 1), `V38` `visual_review`, `V39` respuesta de VISINT, `V40` fotos estándar múltiples, snapshot por revisión y eliminación de mínimo/máximo, `V41` número de captura (fase 3), `V42` fotos estándar comunes y grupo de ejecución, `V43` tipos de destino (fase 4), `V55` VISINT en el relevo (fase 5), `V56` `match_threshold` (fase 6), `V57` ubicación del Puesto (fase 7), `V58` `operational_setting`, radios propios y snapshot de distancia/radio (fase 9).

## 5. Verificación

- **Backend:** 80/80 pruebas (`scripts/backend-test.ps1`), incluida una llamada multipart contra un VISINT falso local (campos, orden, token, sin `Upgrade`). **Frontend:** build OK.
- **E2E contra el VISINT real (2026-09-30):** PRO-PAT-0006 v5 · H01 con 2 fotos estándar, 3 ejecuciones del agente:

| Foto del agente | Esperado | VISINT | Operación |
|---|---|---|---|
| Idéntica a la estándar 1 (portón con candado) | Cumple | `PASS` · `OK` | Cumple — coincide estándar 1 |
| Otro elemento (extintor) | No cumple | `FAIL_NO_MATCH` · `NO_REFERENCE_ABOVE_THRESHOLD` | No cumple |
| Otro lugar (local cerrado) | No cumple | `FAIL_NO_MATCH` · `NO_REFERENCE_ABOVE_THRESHOLD` | No cumple |

  Respuesta en 2–5 s, modelo `visint-evidence-1/dinov2-large-v1/quality-1`. Script: `visint_real_e2e.mjs` (scratchpad de la sesión).

- **Fases 5–9 (2026-10-05/06):** compila el backend en Docker (`docker compose build backend`; las pruebas compilan pero no se ejecutaron) y el frontend. Pruebas E2E por API y con Playwright. Radio configurable: el Coordinador recibe 403 al cambiarlo; 2 m → 400 con el motivo; Hito a 100 m con predeterminado 120 m → sin aviso; Bitácora a 100 m con radio propio 80 m → `OUT_OF_RANGE`; tras volver a 50 m las fotos anteriores conservan su radio.

| Video (`C:/Proyectos/sgi_comando/evidencias_playwright/`) | Qué muestra |
|---|---|
| `v6_video_relevo_visint/` | Relevo con VISINT en las fotos del puesto (Simulador) |
| `v7_video_gps_hito/` | Latitud/longitud del Hito (parcial) |
| `v8_video_umbral_visint/` | Configurar el umbral y su efecto, con el Simulador |
| `v9_video_gps_consigna/` | Consigna con coordenadas GPS: foto a ~1 km → aviso, sin bloquear |
| `v10_video_gps_bitacora_relevo/` | Ubicación del Puesto en Bitácora y Relevo |
| `v11_video_radio_configurable/` | Radio predeterminado en Configuración + radio propio del Puesto |

## 6. Pendientes

**Con VISINT (Joel)**
- Confirmar si los reintentos deben reusar el mismo `requestId` (hoy va uno nuevo por intento; VISINT rechaza reusar un `requestId` con contenido distinto: `REQUEST_ID_CONFLICT`).
- Lista cerrada de `reasonCode`.
- HTTPS y rotación del token antes de producción (hoy `http://`, token en claro).

**Comando**
- Decisión de Gerencia: qué pasa tras X intentos fallidos (bloquear, continuar con observación o aprobación del Supervisor).
- Probar con una foto *parecida* (mismo sitio, otro ángulo), no solo idéntica.
- API de Patrullas: los 400 no traen el motivo en el cuerpo (p. ej. 6.ª foto estándar). Mitigado con validación en la pantalla; la API de Configuración sí devuelve `{"message":…}`.
- Alerta "Sin GPS" cuando la tarea tiene referencia y la foto llega sin coordenadas (hoy no se compara ni se avisa).
- `accuracyM` solo se guarda; falta decidir si una precisión mala debe atenuar el aviso.
- Los campos de latitud/longitud son `type=number` y con la configuración regional es-EC se ven con coma.
- Regrabar los videos de Playwright (muestran el flujo anterior: varias fotos del agente, una estándar).
- Menores: la lista de Operación hace varias consultas por fila; el worker procesa las revisiones de a una.
- Quitar el Simulador de Agente (`SIMULATOR_USER`) después de la demo.

**Fuera de alcance:** Impulsos; reconocimiento facial / identificación del agente.

## 7. Archivos principales

- Backend: `visint/` (`HttpVisintAdapter`, `MockVisintAdapter`, `VisintClient`, `VisualReviewService`, `VisualReviewWorker`, `VisualReview`, `VisualReviewStandard`, `MatchThreshold`), `operation/OperationResource`, `patrols/PatrolCheckpointStandardImage` + `PatrolResource`, `operator/` (`OperatorEvidenceResource`, `PatrolExecutionService`, `TaskEvidenceService`, `OperatorPatrols`, `OperatorTasks`, `OperatorResource`, `OperatorExecutionResource`, `ReliefStationReviews`), `settings/` (`EvidenceLocationSettings`, `SettingsResource`), `common/FeaturesResource`, `ConsignmentResource`, `PostConfigurationResource`.
- Frontend: `pages/PatrolConfig.tsx`, `pages/ConsignasConfig.tsx`, `pages/BitacoraConfig.tsx`, `pages/Services.tsx` (Puestos: GPS, radio, VISINT del relevo y umbral), `pages/SettingsPage.tsx` (Configuración), `pages/OperationPage.tsx`, `components/MatchThresholdField.tsx`, `components/RadiusField.tsx`, `pages/AgentSimulator.tsx` + `AgentReliefSim.tsx` (temporales), `api.ts`.
- Planes: `docs/superpowers/plans/2026-09-29-fase1-fotos-agente-formdata.md`, `…-fase2-visint-patrullas.md` (el plan de fase 2 es el original; este documento refleja lo implementado).
