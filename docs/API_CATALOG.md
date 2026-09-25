# SGI: Comando — API / Interface Catalog (INT v0.1)

Todos los IDs siguen SITC-NOM-001 v3.0. Paths son contratos lógicos; host/basePath/auth efectivos los resuelve CORE por Instancia PE + ambiente.

## `SGI_COM__CORE__00001__V0001` — Resolve transversal Instance/Country context and master catalogs required by SGI: Comando.
- Origen técnico: `SGI_COM`
- Destino: `CORE`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `CORE`
- Estado: `UAT`; destino `BLOCKED`

### `SGI_COM__CORE__00001__IF01`
`GET /api/v1/instance-countries/{instanceCountryId}/context` — Instance/Country, locale, language, timezone, currency, units and formats

### `SGI_COM__CORE__00001__IF02`
`GET /api/v1/instance-countries/{instanceCountryId}/territorial-structure` — Official subdivision type/catalog/geometries/dataset version

### `SGI_COM__CORE__00001__IF03`
`GET /api/v1/instance-countries/{instanceCountryId}/companies` — Canonical Company catalog/identity/logo/status/source version

### `SGI_COM__CORE__00001__IF04`
`GET /api/v1/instance-countries/{instanceCountryId}/calendar-regulatory-profile` — Calendar/holidays and regulatory profile/version/effective dates

## `SGI_COM__CORE__00002__V0001` — Resolve the effective binding of any interconnection by interconnectionId + Instancia PE + environment.
- Origen técnico: `SGI_COM`
- Destino: `CORE`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `CORE`
- Estado: `UAT`; destino `BLOCKED`

### `SGI_COM__CORE__00002__IF01`
`GET /api/v1/interconnections/{interconnectionId}/resolve` — Return target binding for one interface and context

## `SGI_COM__IDENT__00001__V0001` — Validate service/user identity and obtain authorization context for SGI: Comando.
- Origen técnico: `SGI_COM`
- Destino: `IDENT`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `IDENT`
- Estado: `DESIGN`; destino `BLOCKED`

### `SGI_COM__IDENT__00001__IF01`
`POST /api/v1/identity/context` — Token introspection / identity and authorization context

## `SGI_COM__SIC_COM__00001__V0001` — Reconcile the commercial master required for physical-security operation.
- Origen técnico: `SGI_COM`
- Destino: `SIC_COM`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `SIC_COM`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__SIC_COM__00001__IF01`
`GET /api/v1/sgi-export/services` — Paged/versioned Client, Service, Point, Post, shifts, FHE and TIER snapshot

## `SIC_COM__SGI_COM__00001__V0001` — Apply SIC:COM commercial Client/Service/Point/Post lifecycle changes in SGI: Comando.
- Origen técnico: `SIC_COM`
- Destino: `SGI_COM`
- Tipo: `WEBHOOK / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SIC_COM`
- Estado: `DESIGN`; destino `BLOCKED`

### `SIC_COM__SGI_COM__00001__IF01`
`POST /api/v1/inbound/sic-com/commercial-events` — Idempotent, versioned commercial catalog lifecycle event. The body contains `client`, `service` and one or more `points` with their `posts`; it does not contain or persist SIC:COM orders. For every Post in a `COMMERCIAL_CATALOG_CREATED` or `COMMERCIAL_CATALOG_UPDATED` event, `rotation.code`, `rotation.cycleLengthDays` (1–366) and at least one `shifts[]` entry (`code`, `name`, `startTime`, `endTime`, `dayMask` and `active`) are mandatory. `COMMERCIAL_CATALOG_INACTIVATED` may omit planning data because it only applies logical inactivation. It requires `Authorization: Bearer <service-token>` plus `X-Correlation-Id`, `X-Interconnection-Id`, `X-Contract-Version` and `Idempotency-Key` (equal to `eventId`). The receiver resolves the effective token at runtime from `sgi.sic-com.inbound.credential-ref` through `CredentialRefResolver`; neither the credential reference's secret nor a fallback token is versioned.

## `SGI_COM__SIC_RRHH__00001__V0001` — Read employee operational context for territory, assignments and supervision.
- Origen técnico: `SGI_COM`
- Destino: `SIC_RRHH`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `SIC_RRHH`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__SIC_RRHH__00001__IF01`
`GET /api/v1/employees` — Personnel by company/domain with employment state and pagination

### `SGI_COM__SIC_RRHH__00001__IF02`
`GET /api/v1/employees/{employeeId}/work-context` — Skills, certifications and unavailability intervals

## `SGI_COM__SIC_RRHH__00002__V0001` — Submit auditable operational labor events produced by SGI.
- Origen técnico: `SGI_COM`
- Destino: `SIC_RRHH`
- Tipo: `REST_API / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__SIC_RRHH__00002__IF01`
`POST /api/v1/sgi-operational-events` — Idempotent labor consequence/request event

## `SIC_RRHH__SGI_COM__00001__V0001` — Notify changes to employee/company/status/skills/unavailability used operationally by SGI.
- Origen técnico: `SIC_RRHH`
- Destino: `SGI_COM`
- Tipo: `WEBHOOK / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SIC_RRHH`
- Estado: `DESIGN`; destino `BLOCKED`

### `SIC_RRHH__SGI_COM__00001__IF01`
`POST /api/v1/inbound/sic-rrhh/employee-events` — Employee operational master event

## `SGI_COM__SIC_RRMM__00001__V0001` — Read expected material resources/inventory for Point/Post operational configuration and Relevo.
- Origen técnico: `SGI_COM`
- Destino: `SIC_RRMM`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `SIC_RRMM`
- Estado: `DESIGN`; destino `BLOCKED`

### `SGI_COM__SIC_RRMM__00001__IF01`
`GET /api/v1/operational-assets/posts/{postId}/expected` — Expected assets/materials assigned to Post

### `SGI_COM__SIC_RRMM__00001__IF02`
`GET /api/v1/operational-assets/points/{pointId}` — Material resources visible for Point configuration

## `SIC_RRMM__SGI_COM__00001__V0001` — Provide SIC_RRMM with operational Company Point/Post catalog for MARE requirements.
- Origen técnico: `SIC_RRMM`
- Destino: `SGI_COM`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `UAT`; destino `BLOCKED`

### `SIC_RRMM__SGI_COM__00001__IF01`
`GET /api/v1/integration/rrmm/companies/{companyId}/points` — Points currently operated by Company

### `SIC_RRMM__SGI_COM__00001__IF02`
`GET /api/v1/integration/rrmm/points/{pointId}/posts` — Posts under selected Point

## `ATS__SGI_COM__00001__V0001` — Deliver a published .ats security architecture package to SGI: Comando/REGESEP.
- Origen técnico: `ATS`
- Destino: `SGI_COM`
- Tipo: `FILE / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `ATS`
- Estado: `UAT`; destino `READY`

### `ATS__SGI_COM__00001__IF01`
`POST /api/v1/inbound/ats/packages` — Upload versioned .ats package and manifest

## `SGI_COM__ATS__00001__V0001` — Reconcile operational observations about security components/vulnerabilities with ATS design.
- Origen técnico: `SGI_COM`
- Destino: `ATS`
- Tipo: `REST_API / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `BLOCKED`

### `SGI_COM__ATS__00001__IF01`
`POST /api/v1/sgi-operational-observations` — Operational observation linked to ATS component/location

## `SGI_COM__SMC__00001__V0001` — Publish normalized operational facts used by SMC to calculate ID/KPIs.
- Origen técnico: `SGI_COM`
- Destino: `SMC`
- Tipo: `EVENT / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__SMC__00001__IF01`
`POST /api/v1/operational-facts` — Idempotent normalized KPI fact

## `SGI_COM__SMC__00002__V0001` — Read current employee ID/KPIs used by Assignments and operational views.
- Origen técnico: `SGI_COM`
- Destino: `SMC`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `SMC`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__SMC__00002__IF01`
`GET /api/v1/employees/{employeeId}/kpis/current` — Current ID and consumable KPI snapshot

## `SGI_COM__STC__00001__V0001` — Create a work/case in STC for Incidents, Requirements and Activities while preserving SGI origin.
- Origen técnico: `SGI_COM`
- Destino: `STC`
- Tipo: `REST_API / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__STC__00001__IF01`
`POST /api/v1/tasks` — Create idempotent STC work case from SGI origin

## `SGI_COM__STC__00002__V0001` — Read workflow status/progress/closure for an SGI-originated STC case.
- Origen técnico: `SGI_COM`
- Destino: `STC`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `STC`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__STC__00002__IF01`
`GET /api/v1/tasks/{taskId}` — Current STC task status/result

## `SGI_COM__VISINT__00001__V0001` — Submit task evidence/photos for visual validation by VISINT.
- Origen técnico: `SGI_COM`
- Destino: `VISINT`
- Tipo: `REST_API / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__VISINT__00001__IF01`
`POST /api/v1/visual-reviews` — Create VISINT review

## `SGI_COM__VISINT__00002__V0001` — Read VISINT review result for Impulse eligibility and audit.
- Origen técnico: `SGI_COM`
- Destino: `VISINT`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `VISINT`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__VISINT__00002__IF01`
`GET /api/v1/visual-reviews/{reviewId}` — PASS/FAIL/ERROR result and technical metadata

## `SGI_OPR__SGI_COM__00001__V0001` — Synchronize the mobile operator runtime context/configuration required for the active assignment.
- Origen técnico: `SGI_OPR`
- Destino: `SGI_COM`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `BLOCKED`

### `SGI_OPR__SGI_COM__00001__IF01`
`GET /api/v1/operator/runtime` — Assignment, Point/Post, protocols, consignments, patrols, bitacora and pending messages

## `SGI_OPR__SGI_COM__00002__V0001` — Submit idempotent operational executions, novelties and evidence captured by SGI: Operador.
- Origen técnico: `SGI_OPR`
- Destino: `SGI_COM`
- Tipo: `REST_API / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `BLOCKED`

### `SGI_OPR__SGI_COM__00002__IF01`
`POST /api/v1/operator/executions` — Batch/idempotent operational events and evidence references

## `SGI_CLT__SGI_COM__00001__V0001` — Read client-visible operational information after SGI moderation/authorization.
- Origen técnico: `SGI_CLT`
- Destino: `SGI_COM`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `BLOCKED`

### `SGI_CLT__SGI_COM__00001__IF01`
`GET /api/v1/client/operational-items` — Approved/publishable novelties and client-authorized operational read model

## `SGI_CLT__SGI_COM__00002__V0001` — Submit client-originated Consigna proposals, incidents or requirements for SGI moderation/routing.
- Origen técnico: `SGI_CLT`
- Destino: `SGI_COM`
- Tipo: `REST_API / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `BLOCKED`

### `SGI_CLT__SGI_COM__00002__IF01`
`POST /api/v1/client/proposals` — Client-originated proposal/request

## `SGI_COM__CM_CON__00001__V0001` — Request pre-shift attendance confirmation through Cajamarca Conmigo/IVR capability.
- Origen técnico: `SGI_COM`
- Destino: `CM_CON`
- Tipo: `REST_API / ASYNC / HTTPS`
- Contrato: `v1`
- SoR: `SGI_COM`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__CM_CON__00001__IF01`
`POST /api/v1/shift-confirmations` — Create attendance confirmation request

## `SGI_COM__CM_CON__00002__V0001` — Read the result of a pre-shift attendance confirmation.
- Origen técnico: `SGI_COM`
- Destino: `CM_CON`
- Tipo: `REST_API / SYNC / HTTPS`
- Contrato: `v1`
- SoR: `CM_CON`
- Estado: `DESIGN`; destino `MANUAL_PENDING`

### `SGI_COM__CM_CON__00002__IF01`
`GET /api/v1/shift-confirmations/{requestId}` — Confirmation result
