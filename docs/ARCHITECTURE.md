# SGI: Comando — Arquitectura

## Contexto raíz
`CORE → Instancia → Instancia–País → SGI: Comando`

`instance_country_id` es obligatorio directa o indirectamente para todo objeto SGI y se resuelve desde el contexto autenticado del backend.

## System of Record
| Dominio | System of Record | Uso en SGI |
|---|---|---|
| Instancia / País / locale / zona horaria / moneda / unidades / perfiles regulatorios | CORE | Consume |
| Cliente | SIC: COM | Consume |
| Servicio vendido | SIC: COM | Consume |
| Punto vendido | SIC: COM | Consume identidad; agrega perfil operacional |
| Puesto vendido | SIC: COM | Consume identidad, turnos y FHE; agrega perfil operacional |
| Personas / relación laboral / cargos / adscripción SF + Compañía | SIC: RRHH | Consume |
| Identidad de Compañía | CORE | Consume; SGI activa/configura capa operacional |
| Membresía laboral persona–Compañía | SIC: RRHH | Consume; SGI orquesta transferencias y sincroniza resultado |
| Activos / materiales | SIC: RRMM | Consume / asigna operacionalmente |
| Diseño técnico de seguridad | ATS | Importa `.ats` |
| REGESEP | SGI: Comando | SoR operacional/documental |
| Asignaciones, relevos, consignas, patrullas, bitácora | SGI | SoR operacional |

## Identidades estables y capas operacionales
SGI evita duplicar los objetos comerciales. Se recomiendan perfiles operacionales referenciando IDs externos estables:
- `ServiceOperationalProfile.service_id`
- `PointOperationalProfile.point_id`
- `PostOperationalProfile.post_id`

## Versionado temporal
Relaciones que pueden cambiar deben tener vigencia, no simples campos mutables:
- Persona ↔ Compañía (`CompanyMembership`).
- Punto ↔ Compañía (`PointCompanyAssignment`).
- Punto ↔ Servicio (historizado/versionado cuando el Punto cambia de Servicio).
- Versiones del Servicio/Punto/Puesto recibidas desde SIC: COM.
- Versiones ATS.
- REGESEP.

## Flujos principales
### Comercial a Operación
`CCS → SIC: COM → SGI: Comando → bandeja lógica Kaibil → Compañía operativa`

CCS calcula la estructura de cobertura; SIC: COM incorpora lo vendido y es fuente autoritativa de Cliente/Servicio/Punto/Puesto. SIC: COM no decide la Compañía operativa. SER recibe el Punto con `company_id = NULL`, lo presenta en la bandeja lógica Kaibil y Coordinación asigna una Compañía dentro de su ámbito. Kaibil no es propietaria ni operadora del Servicio.

### Personal
`SIC: RRHH (SF + Compañía) → SGI: Comando → Asignaciones`

El alta operacional en SGI requiere que SIC: RRHH entregue la adscripción a Seguridad Física (SF) y la Compañía laboral. SGI no infiere esa relación.

### Diseño de seguridad
`ATS → archivo .ats → SGI: Comando → REGESEP / configuración operacional`


## ATS diferido
SGI-05 no se implementa todavía. ATS será un módulo externo. Su paquete `.ats` se vinculará al Punto y contendrá planos, ubicación inicial/base de Puestos, UAP, amenazas, vulnerabilidades, riesgos y componentes de seguridad en plano. La ubicación de Puesto es base; las patrullas pueden desplazar al Agente durante la operación. El Índice de Riesgo se diseñará junto con ATS.

## UAT v0.3 — jerarquía territorial y autorización
La jerarquía operacional canónica se amplía a `Instancia–País → Zona → Región → Compañía → Punto → Puesto`.
`user_operational_scope` desacopla el rol funcional del territorio autorizado. Los endpoints operacionales resuelven el conjunto de Compañías accesibles server-side; el frontend no es un control de seguridad.

## VISINT e Impulsos — flujo transversal

Para tareas ejecutadas desde **SGI: Operador** con evidencia fotográfica, el flujo canónico es:

`SGI_OPR → SGI_COM → VISINT → SGI_COM → SGI_OPR`

- SGI: Operador captura la evidencia y la envía a SGI: Comando.
- SGI: Comando correlaciona ejecución/evidencia y solicita revisión a VISINT.
- VISINT devuelve el resultado de validación visual; no adjudica Impulsos.
- SGI: Comando es SoR de la lógica de Impulsos: resuelve regla vigente, probabilidad, cantidad, habilidad y ledger auditable.
- La probabilidad se evalúa una sola vez por ejecución/revisión/regla; los reintentos deben ser idempotentes y no pueden duplicar premios.
- Un resultado VISINT `PASS` habilita la evaluación de premio, pero no garantiza Impulsos.

Ver `docs/SGI_OPR_VISINT_IMPULSOS.md` y `sitc/IMP_v0.1_delta.sitcpack`.


## INT v0.1 — Módulo genérico de interconexiones (2026-09-21)
SGI: Comando incorpora un único módulo reusable para integraciones salientes conforme a SITC-NOM-001 v3.0. La lógica de negocio ya no debe crear clientes HTTP ad-hoc ni hardcodear hosts/puertos/credenciales.

Flujo técnico: `Business Adapter → GenericInterconnectionExecutor → CORE resolver/cache → auth/resilience/observability → programa destino`. CORE resuelve configuración por `interconnectionId + instance_country_id + ambiente`, pero no actúa como proxy del tráfico funcional.

Componentes: `CoreInterconnectionResolver`, `ResolutionCache`, `CredentialRefResolver`, `CircuitRegistry`, `GenericInterconnectionExecutor`, catálogo canónico e IDs. La URL bootstrap del resolver CORE se configura por ambiente; todos los demás bindings provienen de CORE.

Ver `docs/INTERCONNECTIONS.md`, `docs/API_CATALOG.md`, `docs/SITCPACK.md` y `sitc/SGI_Comando_CURRENT.sitcpack`.
