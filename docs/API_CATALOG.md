# SGI: Comando — API / Interface Catalog v4.1

**RC:** P0/P1 2026-09-27.

## `SGI_COM_CORE_0001_v001` — SGI_COM → CORE
Consume the effective versioned Impulse rules applicable to the SGI: Comando Instancia PE.

- SoR de los datos principales: `CORE`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_CORE_0001_IF01` — `GET` `/v1/impulses/rules/effective` — Return the effective versioned Impulse rules for one Instancia PE/context.

## `SGI_COM_CORE_0002_v001` — SGI_COM → CORE
Resolve transversal Instance/Country context and master catalogs required by SGI: Comando.

- SoR de los datos principales: `CORE`
- contractVersion: `v1`
- estado: `UAT`

- `SGI_COM_CORE_0002_IF01` — `GET` `/api/v1/catalog/instance-countries?countryId={countryId}` — CORE Instance-Country record and country context
- `SGI_COM_CORE_0002_IF02` — `GET` `/api/v1/catalog/subdivisions?countryId={countryId}` and `/api/v1/catalog/subdivisions/geojson?countryId={countryId}` — Official subdivision catalog and GeoJSON geometries
- `SGI_COM_CORE_0002_IF03` — `GET` `/api/v1/catalog/companies` — Canonical Company catalog/identity/logo/status/source version
- `SGI_COM_CORE_0002_IF04` — `GET` `/api/v1/instance-countries/{instanceCountryId}/calendar-regulatory-profile` — Calendar/holidays and regulatory profile/version/effective dates
- aliases históricos: `SGI_COM__CORE__00001__V0001`, `SGI_COM__CORE__00001`

Campos mínimos de respuesta que SGI necesita en las interfaces implementadas:

- IF01 recibe el `countryId` de CORE y devuelve una lista de Instancias-País con `id`, `code`, `name`, `countryId`, `countryCode`, `countryName`, `locale`, `timezone` y `currency`. SGI selecciona el `code` configurado; el ID de instancia de CORE se conserva aparte del tenant local SGI.
- IF02 recibe `countryId` y devuelve `subdivisionType`, `subdivisionSingular`, `subdivisionPlural`, `datasetVersion` y `subdivisions[]` con `id`, `code`, `officialCode`, `name`, `type` y `typeLabel`. SGI consulta además el endpoint `geojson` con `Accept: application/geo+json`, asocia cada geometría por el ID exacto de provincia y verifica versión, cantidad e IDs antes de sincronizar. SGI considera ACTIVE las provincias devueltas y mantiene las asignaciones Zona/Región locales.
- IF03 devuelve el catálogo completo de compañías; SGI lo filtra por el `instanceCountryId` devuelto por IF01. Cada elemento contiene `id`, `code`, `name`, `description`, `logoUrl`, `companyType`, `instanceCountryId`, `sourceVersion` y `status`.

En UAT, el catálogo de CORE está publicado bajo `/api/v1/catalog` y responde a lecturas sin credencial de servicio en el ambiente observado. Las compañías se obtienen de `/companies` y SGI las filtra localmente por Instancia-País. La carga territorial consulta el catálogo de subdivisiones y su GeoJSON complementario; las demás interconexiones siguen el resolver genérico.

IF02 e IF03 deben devolver el catálogo completo de la Instancia–País en una sola respuesta. Los códigos oficiales/canónicos deben ser únicos y estables; si CORE cambia un UUID manteniendo el mismo código, SGI reata la referencia por ese código. En la primera reconciliación de datos UAT antiguos sin ID CORE, SGI también puede usar un nombre normalizado único cuando el código local todavía difiere del oficial. SGI conserva como inactivos los elementos omitidos y mantiene sus asignaciones Zona/Región y configuración operacional locales.

## `SGI_COM_CORE_0003_v001` — SGI_COM → CORE
Resolve the effective binding of any interconnection by interconnectionId + Instancia PE + environment.

- SoR de los datos principales: `CORE`
- contractVersion: `v1`
- estado: `UAT`

- `SGI_COM_CORE_0003_IF01` — `GET` `/api/v1/interconnections/{interconnectionId}/resolve` — Return target binding for one interface and context
- aliases históricos: `SGI_COM__CORE__00002__V0001`, `SGI_COM__CORE__00002`

## `SGI_COM_IDENT_0001_v001` — SGI_COM → IDENT
Validate service/user identity and obtain authorization context for SGI: Comando.

- SoR de los datos principales: `IDENT`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_IDENT_0001_IF01` — `POST` `/api/v1/identity/context` — Token introspection / identity and authorization context
- aliases históricos: `SGI_COM__IDENT__00001__V0001`, `SGI_COM__IDENT__00001`

## `SGI_COM_SIC_COM_0001_v001` — SGI_COM → SIC_COM
Reconcile the commercial master required for physical-security operation.

- SoR de los datos principales: `SIC_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_SIC_COM_0001_IF01` — `GET` `/api/v1/sgi-export/services` — Paged/versioned Client, Service, Point, Post, shifts, FHE and TIER snapshot
- aliases históricos: `SGI_COM__SIC_COM__00001__V0001`, `SGI_COM__SIC_COM__00001`

## `SIC_COM_SGI_COM_0001_v001` — SIC_COM → SGI_COM
Notify SGI: Comando of commercial Service/Point/Post lifecycle changes.

- SoR de los datos principales: `SIC_COM`
- contractVersion: `v1`
- estado: `UAT`

- `SIC_COM_SGI_COM_0001_IF01` — `POST` `/api/v1/inbound/sic-com/commercial-events` — Versioned commercial lifecycle event
- aliases históricos: `SIC_COM__SGI_COM__00001__V0001`, `SIC_COM__SGI_COM__00001`

## `SGI_COM_SIC_RRHH_0001_v001` — SGI_COM → SIC_RRHH
Read employee operational context for territory, assignments and supervision.

- SoR de los datos principales: `SIC_RRHH`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_SIC_RRHH_0001_IF01` — `GET` `/api/v1/employees` — Personnel by company/domain with employment state and pagination
- `SGI_COM_SIC_RRHH_0001_IF02` — `GET` `/api/v1/employees/{employeeId}/work-context` — Skills, certifications and unavailability intervals
- aliases históricos: `SGI_COM__SIC_RRHH__00001__V0001`, `SGI_COM__SIC_RRHH__00001`

## `SGI_COM_SIC_RRHH_0002_v001` — SGI_COM → SIC_RRHH
Submit auditable operational labor events produced by SGI.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_SIC_RRHH_0002_IF01` — `POST` `/api/v1/sgi-operational-events` — Idempotent labor consequence/request event
- aliases históricos: `SGI_COM__SIC_RRHH__00002__V0001`, `SGI_COM__SIC_RRHH__00002`

## `SIC_DHO_SGI_COM_0001_v001` — SIC_DHO → SGI_COM
Synchronize the ACTIVE/INACTIVE lifecycle of Seguridad Física personnel and their authoritative Persona–Compañía relationship into SGI: Comando.

- SoR de identidad laboral y Persona–Compañía: `SIC_DHO`
- contractVersion: `v1`
- estado SGI_COM: `UAT`
- estado end-to-end: `UAT_PARTIAL` hasta homologar/probar contraparte

- `SIC_DHO_SGI_COM_0001_IF01` — `POST` `/api/v1/inbound/sic-rrhh/employee-events` — evento maestro versionado de empleado.
  - Headers: `Authorization`, `X-Correlation-Id`, `X-Interconnection-Id`, `X-Contract-Version`, `Idempotency-Key`.
  - Campos: `employeeId?`, `personaId?`, `canonicalEmployeeId?`, `fullName`, `roleCode`, `employmentStatus`, `updatedFromSourceAt`, `companyCoreCatalogId?`, `companyCode?`.
  - Para un empleado **nuevo**, `companyCoreCatalogId` o `companyCode` es obligatorio. SGI deja de inventar Kaibil como compañía fuente.
  - Para un empleado ya existente, la omisión temporal de compañía conserva la compañía actual para compatibilidad de transición.
  - `Idempotency-Key` se persiste por Instancia PE: mismo key + mismo payload es retry seguro; mismo key + payload distinto responde conflicto.
- `SIC_DHO_SGI_COM_0001_IF02` — `GET` `/api/v1/employees?instanceCountryId={instanceCountryId}` — lista de empleados operacionales activos de la Empresa identificada por `instanceCountryId`, con su cargo y Compañía asignada.
  - Headers: `Authorization`, `X-Correlation-Id`, `X-Interconnection-Id`, `X-Contract-Version`.
  - Filtro obligatorio: `instanceCountryId`; identifica la Empresa y debe coincidir con el contexto vigente de SGI: Comando.
  - Respuesta: arreglo JSON; cada elemento contiene `personaId`, `employeeId`, `fullName`, `roleCode`, `employmentStatus`, `company.id`, `company.coreCatalogId`, `company.code`, `company.name`, `company.status`, `companyMembershipActive`.
  - Solo devuelve filas con `employee_operational_snapshot.employment_status = 'ACTIVE'`; sin coincidencias responde `200` con `[]`.
- aliases de transición: `SIC_RRHH_SGI_COM_0001_v001`, `SIC_RRHH__SGI_COM__00001__V0001`, `SIC_RRHH__SGI_COM__00001`

## `SGI_COM_SIC_RRMM_0001_v001` — SGI_COM → SIC_RRMM
Read expected material resources/inventory for Point/Post operational configuration and Relevo.

- SoR de los datos principales: `SIC_RRMM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_SIC_RRMM_0001_IF01` — `GET` `/api/v1/operational-assets/posts/{postId}/expected` — Expected assets/materials assigned to Post
- `SGI_COM_SIC_RRMM_0001_IF02` — `GET` `/api/v1/operational-assets/points/{pointId}` — Material resources visible for Point configuration
- aliases históricos: `SGI_COM__SIC_RRMM__00001__V0001`, `SGI_COM__SIC_RRMM__00001`

## `SIC_RRMM_SGI_COM_0001_v001` — SIC_RRMM → SGI_COM
Provide SIC_RRMM with operational Company Point/Post catalog for MARE requirements.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `UAT`

- `SIC_RRMM_SGI_COM_0001_IF01` — `GET` `/api/v1/integration/rrmm/companies/{companyId}/points` — Points currently operated by Company
- `SIC_RRMM_SGI_COM_0001_IF02` — `GET` `/api/v1/integration/rrmm/points/{pointId}/posts` — Posts under selected Point
- aliases históricos: `SIC_RRMM__SGI_COM__00001__V0001`, `SIC_RRMM__SGI_COM__00001`

## `ATS_SGI_COM_0001_v001` — ATS → SGI_COM
Deliver a published .ats security architecture package to SGI: Comando/REGESEP.

- SoR de los datos principales: `ATS`
- contractVersion: `v1`
- estado: `UAT`

- `ATS_SGI_COM_0001_IF01` — `POST` `/api/v1/inbound/ats/packages` — Upload versioned .ats package and manifest
- aliases históricos: `ATS__SGI_COM__00001__V0001`, `ATS__SGI_COM__00001`

## `SGI_COM_ATS_0001_v001` — SGI_COM → ATS
Reconcile operational observations about security components/vulnerabilities with ATS design.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_ATS_0001_IF01` — `POST` `/api/v1/sgi-operational-observations` — Operational observation linked to ATS component/location
- aliases históricos: `SGI_COM__ATS__00001__V0001`, `SGI_COM__ATS__00001`

## `SGI_COM_SMC_0001_v001` — SGI_COM → SMC
Publish normalized operational facts used by SMC to calculate ID/KPIs.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_SMC_0001_IF01` — `POST` `/api/v1/operational-facts` — Idempotent normalized KPI fact
- aliases históricos: `SGI_COM__SMC__00001__V0001`, `SGI_COM__SMC__00001`

## `SGI_COM_SMC_0002_v001` — SGI_COM → SMC
Read current employee ID/KPIs used by Assignments and operational views.

- SoR de los datos principales: `SMC`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_SMC_0002_IF01` — `GET` `/api/v1/employees/{employeeId}/kpis/current` — Current ID and consumable KPI snapshot
- aliases históricos: `SGI_COM__SMC__00002__V0001`, `SGI_COM__SMC__00002`

## `SGI_COM_STC_0001_v001` — SGI_COM → STC
Create a work/case in STC for Incidents, Requirements and Activities while preserving SGI origin.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_STC_0001_IF01` — `POST` `/api/v1/tasks` — Create idempotent STC work case from SGI origin
- aliases históricos: `SGI_COM__STC__00001__V0001`, `SGI_COM__STC__00001`

## `SGI_COM_STC_0002_v001` — SGI_COM → STC
Read workflow status/progress/closure for an SGI-originated STC case.

- SoR de los datos principales: `STC`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_STC_0002_IF01` — `GET` `/api/v1/tasks/{taskId}` — Current STC task status/result
- aliases históricos: `SGI_COM__STC__00002__V0001`, `SGI_COM__STC__00002`

## `SGI_COM_VISINT_0001_v001` — SGI_COM → VISINT
Submit task evidence/photos for visual validation by VISINT.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_VISINT_0001_IF01` — `POST` `/api/v1/visual-reviews` — Create VISINT review
- aliases históricos: `SGI_COM__VISINT__00001__V0001`, `SGI_COM__VISINT__00001`

## `SGI_COM_VISINT_0002_v001` — SGI_COM → VISINT
Read VISINT review result for Impulse eligibility and audit.

- SoR de los datos principales: `VISINT`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_VISINT_0002_IF01` — `GET` `/api/v1/visual-reviews/{reviewId}` — PASS/FAIL/ERROR result and technical metadata
- aliases históricos: `SGI_COM__VISINT__00002__V0001`, `SGI_COM__VISINT__00002`

## `SGI_OPR_SGI_COM_0001_v001` — SGI_OPR → SGI_COM
Synchronize the mobile operator runtime context/configuration required for the active assignment.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_OPR_SGI_COM_0001_IF01` — `GET` `/api/v1/operator/runtime` — Assignment, Point/Post, protocols, consignments, patrols, bitacora and pending messages
- `GET /api/v1/operator/patrol-executions` — consulta interna SGI Comando UI/backend del historial real de ejecuciones de Patrulla; aplica tenant, identidad del Operador y alcance RBAC por Compañía. No crea una nueva interconexión externa.
- aliases históricos: `SGI_OPR__SGI_COM__00001__V0001`, `SGI_OPR__SGI_COM__00001`

## `SGI_OPR_SGI_COM_0002_v001` — SGI_OPR → SGI_COM
Submit idempotent operational executions, novelties and evidence captured by SGI: Operador.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `UAT_PARTIAL`

- `SGI_OPR_SGI_COM_0002_IF01` — `POST` `/api/v1/operator/executions` — Batch/idempotent operational events and evidence references
- aliases históricos: `SGI_OPR__SGI_COM__00002__V0001`, `SGI_OPR__SGI_COM__00002`

## `SGI_CLT_SGI_COM_0001_v001` — SGI_CLT → SGI_COM
Read client-visible operational information after SGI moderation/authorization.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_CLT_SGI_COM_0001_IF01` — `GET` `/api/v1/client/operational-items` — Approved/publishable novelties and client-authorized operational read model
- aliases históricos: `SGI_CLT__SGI_COM__00001__V0001`, `SGI_CLT__SGI_COM__00001`

## `SGI_CLT_SGI_COM_0002_v001` — SGI_CLT → SGI_COM
Submit client-originated Consigna proposals, incidents or requirements for SGI moderation/routing.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_CLT_SGI_COM_0002_IF01` — `POST` `/api/v1/client/proposals` — Client-originated proposal/request
- aliases históricos: `SGI_CLT__SGI_COM__00002__V0001`, `SGI_CLT__SGI_COM__00002`

## `SGI_COM_CM_CON_0001_v001` — SGI_COM → CM_CON
Request pre-shift attendance confirmation through Cajamarca Conmigo/IVR capability.

- SoR de los datos principales: `SGI_COM`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_CM_CON_0001_IF01` — `POST` `/api/v1/shift-confirmations` — Create attendance confirmation request
- aliases históricos: `SGI_COM__CM_CON__00001__V0001`, `SGI_COM__CM_CON__00001`

## `SGI_COM_CM_CON_0002_v001` — SGI_COM → CM_CON
Read the result of a pre-shift attendance confirmation.

- SoR de los datos principales: `CM_CON`
- contractVersion: `v1`
- estado: `DESIGN`

- `SGI_COM_CM_CON_0002_IF01` — `GET` `/api/v1/shift-confirmations/{requestId}` — Confirmation result
- aliases históricos: `SGI_COM__CM_CON__00002__V0001`, `SGI_COM__CM_CON__00002`

# OPR-ASSIGNMENT-001 - Selección temporal de asignación

`GET /api/v1/operator/runtime`

- Sin `assignmentId`: devuelve en `assignments` únicamente el turno en curso o, si no existe, el próximo turno cuyo inicio esté a un máximo de 59 minutos.
- Con `assignmentId`: valida nuevamente la misma ventana antes de entregar `relief`.
- Cada opción añade `endsAt` y `accessMode` (`CURRENT_SHIFT` o `EARLY_ENTRY`) de forma compatible.
- Más de una opción significa una ambigüedad real de planificación; el consumidor debe bloquear y solicitar corrección.

# OPR-CONSIGNMENT-CONTEXT-001 - Contexto parametrizado de Consignas

`GET /api/v1/operator/runtime`

- `consignments[]` conserva el contrato existente y añade `code`, `protocolCode`, `protocolName`, `priority`, `applicationType`, `applicationDaysJson`, `applicationTimeFrom`, `applicationTimeTo`, `scopeType`, `acknowledgmentRequired`, `confirmationRequired`, `evidenceRequired`, `gpsRequired`, `observationRequired` y `expectedLocationMode`.
- La fuente oficial es el único Protocolo de Consignas `ACTIVO` de SGI Comando aplicable al Punto/Puesto de la asignación autorizada.
- La ampliación es aditiva y no introduce una nueva interconexión SITC ni cambia autenticación, topología o credenciales.

## Confirmación de cumplimiento

`POST /api/v1/operator/consignment-compliances`

- Registra el cumplimiento contra tenant, asignación autorizada, empleado autenticado, Protocolo y Consigna vigente.
- `executionId` es la clave idempotente: una repetición idéntica devuelve el acuse existente y una reutilización con contenido distinto responde `409`.
- Valida evidencia y observación cuando la parametrización las declara obligatorias.
- El resultado persistido se devuelve posteriormente en `lastComplianceResult`, `lastComplianceAt` y `lastComplianceUsername` dentro del runtime.


# OPR-EVIDENCE-LOCATION-001 - Ubicación GPS de las fotos y validación VISINT (no bloqueantes)

Cambio aditivo dentro de `SGI_OPR_SGI_COM_0001_v001` y `SGI_OPR_SGI_COM_0002_v001`: no hay interconexión nueva ni cambian autenticación, topología o credenciales.

**Regla general:** ni la ubicación ni VISINT bloquean al agente. Una foto fuera del radio o que VISINT marca "no cumple" se acepta igual. SGI Comando la registra y el supervisor la ve en Servicios → Punto → Operación.

## Qué envía SGI: Operador

Patrulla, Consigna y Bitácora: GPS en cada foto. `POST /api/v1/operator/evidences` (multipart), dentro de `metadata.items[]`:

| Campo | Tipo | Obligatorio | Uso |
|---|---|---|---|
| `latitude` | número (grados WGS84) | no | Se compara con la referencia de la tarea |
| `longitude` | número (grados WGS84) | no | Se compara con la referencia de la tarea |
| `accuracyM` | número (metros) | no | Precisión reportada por el teléfono; hoy solo se guarda |
| `capturedAt` | ISO-8601 | sí | Momento de la captura |
| `source` | `CAMERA` \| `GALLERY` | sí | `GALLERY` queda como marca informativa |

```json
{ "uploadBatchId": "…", "eventId": "…", "assignmentId": "…",
  "targetType": "PATROL_CHECKPOINT | CONSIGNMENT_EVIDENCE | LOGBOOK_FIELD", "targetId": "…",
  "items": [{ "clientEvidenceId": "…", "capturedAt": "2026-10-05T22:15:45Z",
              "latitude": -2.154490, "longitude": -79.952253, "accuracyM": 8, "source": "CAMERA", "sha256": "…" }] }
```

- La distancia se calcula con el GPS **de la foto**. `PATROL_CHECKPOINT_COMPLETED` también acepta `latitude`, `longitude` y `accuracyM`, pero solo como dato.
- Si la foto queda fuera del radio, la respuesta de la subida la acepta (`status: STORED`) e incluye `OUT_OF_RANGE` en `flags`.

Relevo: GPS en el evento. Las fotos del relevo (`PUT /api/v1/operator/relief-evidence/{eventId}/{purpose}`) son JPEG en crudo sin metadatos, así que el GPS va en `events[0]` del `RELIEF_SUBMITTED`:

```json
{ "type": "RELIEF_SUBMITTED", "eventId": "…", "assignmentId": "…", "…": "…",
  "latitude": -2.154490, "longitude": -79.952253, "accuracyM": 8 }
```

- Los tres campos son opcionales y se aplican a las 3 fotos del puesto (`station_0..2`).
- El acuse añade `stationVisintStatus`: `QUEUED_FOR_VISINT` si el Puesto valida con VISINT, `NOT_REQUESTED` si no. El campo `validationStatus` existente no cambia.

Sin GPS: si no se envían coordenadas, la tarea se registra igual y no se compara la ubicación.

## Referencia que entrega SGI Comando

En `GET /api/v1/operator/runtime?assignmentId=…`, para que la app pueda orientar al agente. El radio hoy es 50 m (`sgi.evidence.default-radius-m`), salvo que el Hito tenga uno propio.

| Tarea | Dónde viene | Origen en Comando |
|---|---|---|
| Hito de patrulla | `patrols[].checkpoints[].latitude`, `longitude`, `radiusM` | Patrullas → Hito → Latitud/Longitud o "Capturar GPS en campo" |
| Consigna | `consignmentTasks[].latitude`, `longitude`, `radiusM`, solo si `expectedLocationMode = GPS` | Consignas → Aplicación → Ubicación esperada "Coordenadas GPS" |
| Bitácora | `logbookTasks[].latitude`, `longitude`, `radiusM` | Puestos → "Ubicación GPS del puesto" |
| Relevo | `relief.postLocation.latitude`, `longitude`, `radiusM` | Puestos → "Ubicación GPS del puesto" |

- Si una tarea no trae coordenadas, no tiene referencia y no se compara.
- `relief.postLocation` y `relief.stationVisint` van fuera de `configurationVersion`: cambiarlos en Comando no invalida un relevo en curso.

## Resultado para el agente

`GET /api/v1/operator/executions/{eventId}` y `GET /api/v1/operator/executions?patrolRunId=…` o `?groupId=…` (en el relevo, `groupId` = `eventId` del relevo):

| Campo | Valores |
|---|---|
| `outcome` | `NOT_REQUIRED`, `PENDING`, `VALIDATED`, `NOT_VALIDATED`, `TECHNICAL_ERROR` |
| `message` | Texto listo para mostrar. Siempre indica que el registro quedó guardado, por ejemplo "Hito registrado. VISINT la está validando; puede continuar." |
| `canRetake` | `true` cuando VISINT dijo "no cumple" en la última captura. Es una sugerencia, no una obligación (en el relevo siempre `false`) |
| `validation` | `status`, `result`, `reasonCode` y puntajes `quality` y `match`, informativos |
| `station` | Solo en el relevo: `station_0`, `station_1` o `station_2` |

- La app no debe esperar a VISINT para seguir: puede pasar a la siguiente tarea con `outcome = PENDING`.
- `matchThreshold` (umbral de coincidencia de VISINT) lo configura SGI Comando junto a las fotos estándar de cada tarea, y lo envía Comando. **SGI: Operador no lo envía.**

## Nueva captura y duplicados

- Mientras la última captura de la tarea está en cola de VISINT, con error técnico o "no cumple", **se acepta una nueva captura** (`captureNo` + 1). VISINT nunca bloquea.
- Solo una tarea ya validada por VISINT, o que no usa VISINT, se considera registrada. Volver a enviarla responde `409`:
  - "Este Hito ya fue registrado en la ronda";
  - "Esta evidencia ya fue registrada en el turno";
  - "Este campo ya fue registrado para este visitante".
- El relevo se recibe una sola vez por asignación ("La asignación ya tiene un relevo recibido", `409`).
- Reintentar el mismo `eventId` con el mismo contenido devuelve el acuse existente (idempotencia).

## Lo que ve SGI Comando

Servicios → Punto → Operación:
- **Lista:** columna VISINT (Cumple, No cumple, En revisión, Error) y columna Alertas ("Fuera del radio GPS").
- **Detalle:** "Coincidencia 0.96 de umbral 0.80" y "A 1.0 km del punto · radio 50 m".
