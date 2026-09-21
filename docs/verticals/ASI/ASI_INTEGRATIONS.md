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
