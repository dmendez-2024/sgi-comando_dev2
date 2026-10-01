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

- `SGI_COM_CORE_0002_IF01` — `GET` `/api/v1/instance-countries/{instanceCountryId}/context` — Instance/Country, locale, language, timezone, currency, units and formats
- `SGI_COM_CORE_0002_IF02` — `GET` `/api/v1/instance-countries/{instanceCountryId}/territorial-structure` — Official subdivision type/catalog/geometries/dataset version
- `SGI_COM_CORE_0002_IF03` — `GET` `/api/v1/instance-countries/{instanceCountryId}/companies` — Canonical Company catalog/identity/logo/status/source version
- `SGI_COM_CORE_0002_IF04` — `GET` `/api/v1/instance-countries/{instanceCountryId}/calendar-regulatory-profile` — Calendar/holidays and regulatory profile/version/effective dates
- aliases históricos: `SGI_COM__CORE__00001__V0001`, `SGI_COM__CORE__00001`

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

## `SIC_RRHH_SGI_COM_0001_v001` — SIC_RRHH → SGI_COM
Synchronize the ACTIVE/INACTIVE lifecycle of Seguridad Física personnel and their authoritative Persona–Compañía relationship into SGI: Comando.

- SoR de identidad laboral y Persona–Compañía: `SIC_RRHH`
- contractVersion: `v1`
- estado SGI_COM: `UAT`
- estado end-to-end: `UAT_PARTIAL` hasta homologar/probar contraparte

- `SIC_RRHH_SGI_COM_0001_IF01` — `POST` `/api/v1/inbound/sic-rrhh/employee-events` — evento maestro versionado de empleado.
  - Headers: `Authorization`, `X-Correlation-Id`, `X-Interconnection-Id`, `X-Contract-Version`, `Idempotency-Key`.
  - Campos: `employeeId?`, `personaId?`, `canonicalEmployeeId?`, `fullName`, `roleCode`, `employmentStatus`, `updatedFromSourceAt`, `companyCoreCatalogId?`, `companyCode?`.
  - Para un empleado **nuevo**, `companyCoreCatalogId` o `companyCode` es obligatorio. SGI deja de inventar Kaibil como compañía fuente.
  - Para un empleado ya existente, la omisión temporal de compañía conserva la compañía actual para compatibilidad de transición.
  - `Idempotency-Key` se persiste por Instancia PE: mismo key + mismo payload es retry seguro; mismo key + payload distinto responde conflicto.
- `SIC_RRHH_SGI_COM_0001_IF02` — `GET` `/api/v1/employees/by-persona/{personaId}` — consulta de empleado, cargo y Compañía operacional por el `personas.id` de DHO.
  - Headers: `Authorization`, `X-Correlation-Id`, `X-Interconnection-Id`, `X-Contract-Version`.
  - Campos: `personaId`, `employeeId`, `fullName`, `roleCode`, `employmentStatus`, `company.id`, `company.coreCatalogId`, `company.code`, `company.name`, `company.status`, `companyMembershipActive`.
- aliases históricos: `SIC_RRHH__SGI_COM__00001__V0001`, `SIC_RRHH__SGI_COM__00001`

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
