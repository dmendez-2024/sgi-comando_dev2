# SGI: Comando — ECOSYSTEM_INTERCONNECTIONS canónicas

**Norma:** SITC-NOM-001 v4.1  
**RC:** P0/P1 2026-09-27  
**Baseline de código:** SISTEMAS commit `8c528e8` (2026-09-25).

## Nomenclatura

- Referencia estable: `ORIGEN_DESTINO_NNNN`
- ID versionado: `ORIGEN_DESTINO_NNNN_vNNN`
- Interface: `ORIGEN_DESTINO_NNNN_IFNN`
- Los IDs v3 con doble guion bajo/cinco dígitos/`V0001` se conservan únicamente en `legacyConnectionIds` para transición.
- El código nuevo, headers nuevos y bindings CORE deben utilizar IDs v4.1.

## Catálogo vigente

| # | ID versionado | Origen técnico | Destino | Propósito | Estado | Lado destino |
|---:|---|---|---|---|---|---|
| 1 | `SGI_COM_CORE_0001_v001` | SGI_COM | CORE | Consume the effective versioned Impulse rules applicable to the SGI: Comando Instancia PE. | DESIGN | DESIGN |
| 2 | `SGI_COM_CORE_0002_v001` | SGI_COM | CORE | Resolve transversal Instance/Country context and master catalogs required by SGI: Comando. | UAT | BLOCKED |
| 3 | `SGI_COM_CORE_0003_v001` | SGI_COM | CORE | Resolve the effective binding of any interconnection by interconnectionId + Instancia PE + environment. | UAT | BLOCKED |
| 4 | `SGI_COM_IDENT_0001_v001` | SGI_COM | IDENT | Validate service/user identity and obtain authorization context for SGI: Comando. | DESIGN | BLOCKED |
| 5 | `SGI_COM_SIC_COM_0001_v001` | SGI_COM | SIC_COM | Reconcile the commercial master required for physical-security operation. | DESIGN | MANUAL_PENDING |
| 6 | `SIC_COM_SGI_COM_0001_v001` | SIC_COM | SGI_COM | Notify SGI: Comando of commercial Service/Point/Post lifecycle changes. | UAT | READY |
| 7 | `SGI_COM_SIC_RRHH_0001_v001` | SGI_COM | SIC_RRHH | Read employee operational context for territory, assignments and supervision. | DESIGN | MANUAL_PENDING |
| 8 | `SGI_COM_SIC_RRHH_0002_v001` | SGI_COM | SIC_RRHH | Submit auditable operational labor events produced by SGI. | DESIGN | MANUAL_PENDING |
| 9 | `SIC_RRHH_SGI_COM_0001_v001` | SIC_RRHH | SGI_COM | Synchronize active Seguridad Física personnel and their authoritative Persona–Compañía relationship into SGI: Comando. | UAT | UAT_PARTIAL |
| 10 | `SGI_COM_SIC_RRMM_0001_v001` | SGI_COM | SIC_RRMM | Read expected material resources/inventory for Point/Post operational configuration and Relevo. | DESIGN | BLOCKED |
| 11 | `SIC_RRMM_SGI_COM_0001_v001` | SIC_RRMM | SGI_COM | Provide SIC_RRMM with operational Company Point/Post catalog for MARE requirements. | UAT | BLOCKED |
| 12 | `ATS_SGI_COM_0001_v001` | ATS | SGI_COM | Deliver a published .ats security architecture package to SGI: Comando/REGESEP. | UAT | READY |
| 13 | `SGI_COM_ATS_0001_v001` | SGI_COM | ATS | Reconcile operational observations about security components/vulnerabilities with ATS design. | DESIGN | BLOCKED |
| 14 | `SGI_COM_SMC_0001_v001` | SGI_COM | SMC | Publish normalized operational facts used by SMC to calculate ID/KPIs. | DESIGN | MANUAL_PENDING |
| 15 | `SGI_COM_SMC_0002_v001` | SGI_COM | SMC | Read current employee ID/KPIs used by Assignments and operational views. | DESIGN | MANUAL_PENDING |
| 16 | `SGI_COM_STC_0001_v001` | SGI_COM | STC | Create a work/case in STC for Incidents, Requirements and Activities while preserving SGI origin. | DESIGN | MANUAL_PENDING |
| 17 | `SGI_COM_STC_0002_v001` | SGI_COM | STC | Read workflow status/progress/closure for an SGI-originated STC case. | DESIGN | MANUAL_PENDING |
| 18 | `SGI_COM_VISINT_0001_v001` | SGI_COM | VISINT | Submit task evidence/photos for visual validation by VISINT. | DESIGN | MANUAL_PENDING |
| 19 | `SGI_COM_VISINT_0002_v001` | SGI_COM | VISINT | Read VISINT review result for Impulse eligibility and audit. | DESIGN | MANUAL_PENDING |
| 20 | `SGI_OPR_SGI_COM_0001_v001` | SGI_OPR | SGI_COM | Synchronize the mobile operator runtime context/configuration required for the active assignment. BIT-INT-001 RC1 exposes only active Bitácora configuration for the assigned post. | UAT_PARTIAL | READY |
| 21 | `SGI_OPR_SGI_COM_0002_v001` | SGI_OPR | SGI_COM | Submit idempotent operational executions, novelties and evidence captured by SGI: Operador. | UAT_PARTIAL | READY |
| 22 | `SGI_CLT_SGI_COM_0001_v001` | SGI_CLT | SGI_COM | Read client-visible operational information after SGI moderation/authorization. | DESIGN | BLOCKED |
| 23 | `SGI_CLT_SGI_COM_0002_v001` | SGI_CLT | SGI_COM | Submit client-originated Consigna proposals, incidents or requirements for SGI moderation/routing. | DESIGN | BLOCKED |
| 24 | `SGI_COM_CM_CON_0001_v001` | SGI_COM | CM_CON | Request pre-shift attendance confirmation through Cajamarca Conmigo/IVR capability. | DESIGN | MANUAL_PENDING |
| 25 | `SGI_COM_CM_CON_0002_v001` | SGI_COM | CM_CON | Read the result of a pre-shift attendance confirmation. | DESIGN | MANUAL_PENDING |

Total: **25 interconexiones / 31 interfaces**.

## Reglas de Impulsos

`SGI_COM_CORE_0001_v001` es la interconexión canónica para reglas de Impulsos.

- **CORE:** System of Record de reglas versionadas: elegibilidad, habilidad, condiciones, probabilidad, cantidad y vigencia.
- **SGI_COM PE:** aplica las reglas a los hechos recibidos desde SGI_OPR y es SoR del ledger/saldo de Impulsos por Operador.
- **SGI_OPR:** produce hechos operativos; no mantiene el saldo autoritativo.
- Otros sistemas consultan el saldo/ledger a SGI_COM PE.

## Inbound ya presentes en la baseline de SISTEMAS

- `SIC_COM_SGI_COM_0001_v001`: catálogo comercial, con recibo idempotente.
- `SIC_RRHH_SGI_COM_0001_v001`: sincronización de empleado con auth por `credential_ref`, idempotencia persistente y resolución de Compañía fuente. Para personal nuevo SGI exige `companyCoreCatalogId` o `companyCode`; la contraparte SIC:RRHH debe homologar esos campos antes de declarar el flujo ACTIVE.
- `SGI_OPR_SGI_COM_0002_v001`: relevo/evidencia UAT parcialmente implementado.

## Seguridad

- Ningún secreto efectivo se guarda en SITC, código portable o MD.
- `credential_ref` se resuelve en runtime.
- CORE gobierna/resuelve ECOSYSTEM_INTERCONNECTION por Instancia PE + ambiente, pero no es proxy obligatorio de tráfico.
- `X-Correlation-Id`, `X-Interconnection-Id` y `X-Contract-Version` deben acompañar contratos que los definan.
- Aliases v3 se aceptan solo durante transición; deben retirarse cuando ambos extremos estén homologados a v4.1.

## Sin EVC

Esta RC no crea ni modifica EVC/Eventos de Cumplimiento.
