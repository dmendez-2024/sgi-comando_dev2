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
| Identidad de Compañía | CORE | Consume; SGI activa/configura capa operacional |
| Membresía laboral persona–Compañía | SIC: RRHH | Consume; SGI orquesta transferencias |
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


## Actualización 2026-09-19 — CORE / SIC: RRHH / SIC: COM / SER v0.9
- **CORE** es SoR de identidad de Compañía; COM v1.1.3 queda FROZEN.
- **SIC: RRHH** es SoR de la relación persona–Compañía. Para ingreso a SGI debe entregar personal adscrito a **Seguridad Física (SF) + Compañía**.
- **SIC: COM** es SoR de `Servicio = Cliente + Punto` y sus Puestos; no determina Compañía operativa.
- **SER v0.9** recibe Servicios nuevos con Compañía pendiente (`company_id = NULL`), los presenta en bandeja lógica **Kaibil** y Coordinación asigna la Compañía operativa según ámbito. Kaibil no opera Servicios de clientes.
- Tras la asignación inicial se habilita Configuración del Punto; mover posteriormente un Servicio ya asignado queda fuera de v0.9.


## SER v0.9.1 — retiro y reasignación de Servicios
- Coordinación autorizada puede retirar un Servicio asignado hacia la bandeja lógica Kaibil según alcance territorial.
- La configuración operacional permanece ligada al Punto y no se borra/copia al cambiar de Compañía.
- Solo las asignaciones futuras desaparecen de planificación activa; histórico y turno en curso se conservan.
- `operational_transition_until` protege el cierre del turno heredado y evita doble cobertura en la nueva Compañía.
- Desde Kaibil se reasigna directamente a otra Compañía autorizada sin aceptación del Coordinador destino.
