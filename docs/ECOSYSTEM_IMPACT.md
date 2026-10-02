# SGI: Comando — impacto al ecosistema P0/P1 2026-09-27

**Baseline:** `sgi-comando_dev.zip` / commit `8c528e8` / 2026-09-25.  
**Arquitectura vigente usada:** CORE `UNIVERSAL`; SITC-NOM-001 v4.1.  
**Alcance:** seguridad de migraciones, sincronización CURRENT/SITC/handoff, normalización de interconexiones y corrección de SoR de Impulsos. **Sin EVC y sin cambios de UI.**

## Impacto cuantificado

- Program IDs en CURRENT: **23**.
- ECOSYSTEM_INTERCONNECTION canónicas: **25**.
- Interfaces: **31**.
- Interconexiones semánticamente nuevas en este P0/P1: **1** — `SGI_COM_CORE_0001_v001` para reglas versionadas de Impulsos desde CORE.
- Interconexiones existentes normalizadas/reidentificadas a nomenclatura v4.1: **24**; sus IDs v3 permanecen como aliases de transición cuando aplica.
- Interconexiones retiradas funcionalmente: **0**.
- CORE permanece `UNIVERSAL`; SGI_COM permanece `PE_SPECIFIC`.

## Programas con impacto / siguiente coordinación

| Programa | Impacto | Acción de contraparte |
|---|---|---|
| CORE | SoR de reglas de Impulsos + resolver/bindings de interconexiones | Importar/validar CURRENT/delta como ESCENARIO; definir binding efectivo de `SGI_COM_CORE_0001_v001`; no activar productivo automáticamente. |
| SIC_COM | Inbound comercial de SISTEMAS normalizado al ID v4.1 `SIC_COM_SGI_COM_0001_v001` | Homologar/confirmar mismo ID, contractVersion y credential_ref del lado SIC_COM. |
| SIC_DHO | Inbound de empleados normalizado a `SIC_DHO_SGI_COM_0001_v001` y credencial por `credential_ref` | Homologar ID/contrato/idempotencia del lado DHO; completar pruebas bilaterales. |
| SGI_OPR | Runtime/relevo conserva semántica y adopta IDs v4.1 `SGI_OPR_SGI_COM_0001_v001` / `_0002_v001` | Usar los mismos IDs en su módulo genérico; no cambia este RC la UI/flujo de Operador. |
| VISINT | Contratos canónicos `SGI_COM_VISINT_0001_v001` / `_0002_v001` | Mantener validación visual; VISINT no decide ni almacena saldo de Impulsos. |
| Otros Program IDs | Catálogo normalizado v4.1; varios permanecen DESIGN/MANUAL_PENDING | Adecuación por sus chats/DEVs cuando corresponda. |

## Impulsos — separación autoritativa

`CORE → reglas versionadas → SGI_COM PE → cálculo/adjudicación/ledger`  
`SGI_OPR → hechos operativos → SGI_COM PE`  
`Otros sistemas → consulta de saldo/ledger → SGI_COM PE`

- **CORE = SoR de reglas de Impulsos.**
- **SGI_COM PE = SoR de la aplicación de regla, adjudicación/reverso y ledger/saldo por Operador.**
- SGI_OPR produce hechos; no es SoR del saldo.

## Base de datos

V30/V31 ya no ejecutan reset/seed UAT automáticamente. El SQL histórico exacto se preserva fuera del path Flyway y solo puede ejecutarse deliberadamente con el script UAT y `-Force`. Bases que ya aplicaron las versiones antiguas requieren reconciliación de checksum controlada antes de arrancar esta RC.

## Publicación CORE

El `.sitcpack` CURRENT/delta se carga a CORE como **ESCENARIO con preview/merge**. Importarlo no altera por sí mismo bindings productivos de Instancias PE. La promoción a VIGENTE y la materialización de valores efectivos siguen el proceso administrativo de CORE.
