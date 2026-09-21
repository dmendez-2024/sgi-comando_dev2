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
| Personas / relación laboral / cargos | SIC: RRHH | Consume |
| Compañía operacional | SGI: Comando | SoR |
| Membresía de personal a Compañía | SGI: Comando | SoR operacional |
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
`CCS → SIC: COM → SGI: Comando`

CCS calcula la estructura de cobertura; SIC: COM incorpora lo vendido y es fuente autoritativa para SGI.

### Personal
`SIC: RRHH → Compañía SGI → Puntos de la Compañía → Puestos`

### Diseño de seguridad
`ATS → archivo .ats → SGI: Comando → REGESEP / configuración operacional`


## ATS diferido
SGI-05 no se implementa todavía. ATS será un módulo externo. Su paquete `.ats` se vinculará al Punto y contendrá planos, ubicación inicial/base de Puestos, UAP, amenazas, vulnerabilidades, riesgos y componentes de seguridad en plano. La ubicación de Puesto es base; las patrullas pueden desplazar al Agente durante la operación. El Índice de Riesgo se diseñará junto con ATS.

## UAT v0.3 — jerarquía territorial y autorización
La jerarquía operacional canónica se amplía a `Instancia–País → Zona → Región → Compañía → Punto → Puesto`.
`user_operational_scope` desacopla el rol funcional del territorio autorizado. Los endpoints operacionales resuelven el conjunto de Compañías accesibles server-side; el frontend no es un control de seguridad.
