# ASI v0.5 — Integraciones

## COM v1.0 FROZEN → ASI
- Compañías visibles por alcance territorial.
- Relación Compañía–Región para autorización.

## TER v1.0 FROZEN → ASI
- Scope Zona / Región.

## SIC: COM → ASI
ASI consume, sin asumir cardinalidades fijas:
- Cliente;
- Punto;
- Puesto;
- Turnos del Puesto;
- hora inicio / fin;
- TIER;
- Formato;
- Rotación;
- `cycle_length_days`.

### Snapshot UAT heredado de v0.4
`post_planning_cycle_snapshot` conserva `post_id`, `rotation_code`, `cycle_length_days`, `source_system` y `source_version`. Es un snapshot read-only para trazabilidad y ejecución de Copiar Ciclo; SIC: COM permanece como SoR.

Referencias UAT explícitas:
- 6-2 → 8 días.
- 5-2 → 7 días.

## SIC: RRHH → ASI
- personal por Compañía;
- estado laboral;
- habilidades;
- vacaciones;
- permisos / indisponibilidades.

## SMC → ASI
- ID vigente.

## SoR
ASI es SoR de planificación operacional/asignación y su auditoría. No es SoR de Cliente, Puesto comercial, Turno contractual, Formato, Rotación, Personal, ID, TIER o geografía.

## Contrato heredado de v0.4 (sin cambios en v0.5)
Se agrega a la proyección `PostDto` de ASI:
- `rotationCode`
- `cycleLengthDays`
- `cycleSourceVersion`

No se modifica el modelo congelado de COM.


### Contrato de ingreso de personal desde SIC: RRHH — 2026-09-19
SIC: RRHH debe entregar cada colaborador visible en SGI con adscripción explícita a **Seguridad Física (SF) + Compañía**. SIC: RRHH es SoR de esa relación inicial. SGI no crea ni infiere la membresía laboral; las transferencias operacionales posteriores se orquestan en ASI y se sincronizan de vuelta a SIC: RRHH.

### Puente de identidad DHO → SGI: Comando — V29 observado en código

La migración V29 agrega `employee_operational_snapshot.persona_id bigint` y un índice único parcial por `instance_country_id` cuando el valor no es NULL. El adaptador acepta `personaId` y `canonicalEmployeeId` como extensiones; `employeeId` numérico se conserva como alias legacy de `personas.id` conforme a la bitácora DME.

- La columna no se backfillea en bloque; históricos mantienen `persona_id = NULL` hasta sincronización válida.
- SGI conserva su UUID operacional; el SoR de la relación persona–Compañía permanece en SIC: RRHH.
- La opción de UUID canónico depende de una migración controlada de todas las referencias y no debe activarse solo mediante configuración.
- La nota DME reporta redespliegue DHO pendiente; el estado end-to-end requiere UAT de ambas puntas.
- ASI-DEC-059 se cita como excepción de asignación inicial a Kaibil en otra nota, pero no se encontró en el Decision Log disponible. No tratar esa excepción como decisión aprobada hasta ubicarla.
