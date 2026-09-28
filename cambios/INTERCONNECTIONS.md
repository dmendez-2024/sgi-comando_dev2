# SGI: Comando — Interconexiones canónicas (INT v0.1)

Baseline: SITC-NOM-001 v3.0 + `SITC-ECOSISTEMA-CM-SISTEMAS-20260921-SCENARIO_SNAPSHOT.sitcpack`.

**Regla de dirección:** `sourceProgramId` es el programa que inicia técnicamente la interacción. El sentido del dato puede ser inverso en una respuesta síncrona; por eso también se documenta `businessDataFlow`.

**Regla de versión:** la referencia estable es `SOURCE__TARGET__NNNNN`; la revisión actual es `__V0001`. `contractVersion=v1` evoluciona solo cuando existe ruptura incompatible.

| # | Interconnection ID | Origen técnico | Destino | Propósito | Estado | Lado destino |
|---:|---|---|---|---|---|---|
| 1 | `SGI_COM__CORE__00001__V0001` | SGI_COM | CORE | Resolve transversal Instance/Country context and master catalogs required by SGI: Comando. | UAT | BLOCKED |
| 2 | `SGI_COM__CORE__00002__V0001` | SGI_COM | CORE | Resolve the effective binding of any interconnection by interconnectionId + Instancia PE + environment. | UAT | BLOCKED |
| 3 | `SGI_COM__IDENT__00001__V0001` | SGI_COM | IDENT | Validate service/user identity and obtain authorization context for SGI: Comando. | DESIGN | BLOCKED |
| 4 | `SGI_COM__SIC_COM__00001__V0001` | SGI_COM | SIC_COM | Reconcile the commercial master required for physical-security operation. | DESIGN | MANUAL_PENDING |
| 5 | `SIC_COM__SGI_COM__00001__V0001` | SIC_COM | SGI_COM | Notify SGI: Comando of commercial Service/Point/Post lifecycle changes. | DESIGN | BLOCKED |
| 6 | `SGI_COM__SIC_RRHH__00001__V0001` | SGI_COM | SIC_RRHH | Read employee operational context for territory, assignments and supervision. | DESIGN | MANUAL_PENDING |
| 7 | `SGI_COM__SIC_RRHH__00002__V0001` | SGI_COM | SIC_RRHH | Submit auditable operational labor events produced by SGI. | DESIGN | MANUAL_PENDING |
| 8 | `SIC_RRHH__SGI_COM__00001__V0001` | SIC_RRHH | SGI_COM | Notify changes to employee/company/status/skills/unavailability used operationally by SGI. | DESIGN | BLOCKED |
| 9 | `SGI_COM__SIC_RRMM__00001__V0001` | SGI_COM | SIC_RRMM | Read expected material resources/inventory for Point/Post operational configuration and Relevo. | DESIGN | BLOCKED |
| 10 | `SIC_RRMM__SGI_COM__00001__V0001` | SIC_RRMM | SGI_COM | Provide SIC_RRMM with operational Company Point/Post catalog for MARE requirements. | UAT | BLOCKED |
| 11 | `ATS__SGI_COM__00001__V0001` | ATS | SGI_COM | Deliver a published .ats security architecture package to SGI: Comando/REGESEP. | UAT | READY |
| 12 | `SGI_COM__ATS__00001__V0001` | SGI_COM | ATS | Reconcile operational observations about security components/vulnerabilities with ATS design. | DESIGN | BLOCKED |
| 13 | `SGI_COM__SMC__00001__V0001` | SGI_COM | SMC | Publish normalized operational facts used by SMC to calculate ID/KPIs. | DESIGN | MANUAL_PENDING |
| 14 | `SGI_COM__SMC__00002__V0001` | SGI_COM | SMC | Read current employee ID/KPIs used by Assignments and operational views. | DESIGN | MANUAL_PENDING |
| 15 | `SGI_COM__STC__00001__V0001` | SGI_COM | STC | Create a work/case in STC for Incidents, Requirements and Activities while preserving SGI origin. | DESIGN | MANUAL_PENDING |
| 16 | `SGI_COM__STC__00002__V0001` | SGI_COM | STC | Read workflow status/progress/closure for an SGI-originated STC case. | DESIGN | MANUAL_PENDING |
| 17 | `SGI_COM__VISINT__00001__V0001` | SGI_COM | VISINT | Submit task evidence/photos for visual validation by VISINT. | DESIGN | MANUAL_PENDING |
| 18 | `SGI_COM__VISINT__00002__V0001` | SGI_COM | VISINT | Read VISINT review result for Impulse eligibility and audit. | DESIGN | MANUAL_PENDING |
| 19 | `SGI_OPR__SGI_COM__00001__V0001` | SGI_OPR | SGI_COM | Synchronize the mobile operator runtime context/configuration required for the active assignment. | DESIGN | BLOCKED |
| 20 | `SGI_OPR__SGI_COM__00002__V0001` | SGI_OPR | SGI_COM | Submit idempotent operational executions, novelties and evidence captured by SGI: Operador. | DESIGN | BLOCKED |
| 21 | `SGI_CLT__SGI_COM__00001__V0001` | SGI_CLT | SGI_COM | Read client-visible operational information after SGI moderation/authorization. | DESIGN | BLOCKED |
| 22 | `SGI_CLT__SGI_COM__00002__V0001` | SGI_CLT | SGI_COM | Submit client-originated Consigna proposals, incidents or requirements for SGI moderation/routing. | DESIGN | BLOCKED |
| 23 | `SGI_COM__CM_CON__00001__V0001` | SGI_COM | CM_CON | Request pre-shift attendance confirmation through Cajamarca Conmigo/IVR capability. | DESIGN | MANUAL_PENDING |
| 24 | `SGI_COM__CM_CON__00002__V0001` | SGI_COM | CM_CON | Read the result of a pre-shift attendance confirmation. | DESIGN | MANUAL_PENDING |

## Reglas transversales

- Ninguna integración puede leer la base de datos de otro programa.
- Hosts, puertos, endpoints efectivos y secretos no viven en lógica de negocio.
- El módulo genérico resuelve la configuración efectiva en CORE y usa caché.
- CORE no es proxy del tráfico funcional.
- `instance_country_id` y ambiente participan en la resolución.
- Todas las llamadas llevan `X-Correlation-Id`, ID de interconexión y versión contractual cuando aplique.
- Retries solo en errores transport/5xx y únicamente cuando la operación sea idempotente.
- Interconexiones legacy permanecen `MANUAL_PENDING` hasta que los DEVs implementen/proben el otro lado.
- Importar el `.sitcpack` en CORE **no** cambia automáticamente Instancias PE productivas.

## Pendientes explícitos

- `IDENT` está NOT_STARTED en el snapshot maestro; el contrato queda mapeado pero no operativo.
- SIC_COM, SIC_RRHH, SMC, STC, VISINT, SGI_CLT y CM_CON tienen lados legacy que requieren adecuación manual.
- SIC_RRMM y SGI_OPR deben incorporar su propio módulo genérico en sus respectivos desarrollos.
- Alarmas electrónicas de Consola no se registran todavía como interconexión porque el snapshot maestro no contiene un Program ID específico para el gateway/plataforma de alarmas. Crear el Program ID antes de definir ese contrato.
