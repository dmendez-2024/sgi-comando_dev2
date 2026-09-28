# ASI — Contexto funcional acumulado

Este documento consolida el contexto necesario para que la vertical ASI pueda continuar sin depender de releer el chat.

## Fuentes / SoR

- **SIC: COM:** Cliente, Punto, Puesto, Turnos, Formato, FHE, TIER y versión comercial.
- **SIC: RRHH:** pertenencia a Compañía, rol, estado laboral, habilidades, vacaciones, permisos médicos e indisponibilidades.
- **SMC:** System of Record del ID (Índice de Desempeño).
- **COM v1.0 FROZEN:** Compañías y su alcance territorial mono-zona/multirregión.
- **TER v1.0 FROZEN:** Zonas/Regiones y alcance territorial.

## IC

`IC = 100 × Σ min(A_i,R_i) / Σ R_i`, usando 8 habilidades. Tope 100%. IC < 100% genera alerta pero no bloqueo.

## ID mínimo por TIER

- TIER I: 6.5
- TIER II: 7.5
- TIER III: 8.5
- TIER IV: 9.5

ID bajo mínimo TIER genera alerta pero no bloqueo.

## Bloqueos reales

- colaborador fuera de la Compañía del plan;
- estado laboral no activo;
- vacaciones / permiso médico / indisponibilidad que solape el turno;
- solapamiento con otra asignación;
- auto-relevo / turno inmediatamente consecutivo.

## Plan

Estados: Borrador → Publicado → Cerrado. Solo Coordinador/Asistente modifican. El plan publicado es snapshot inmutable; cambios posteriores son eventos explícitos de reasignación/asignación posterior.

## Borrador

Existe botón Guardar borrador y checkpoint automático periódico ya definido.

## UI base

Panel izquierdo de personal + matriz semanal de Punto → Puesto → Turno. Drag & Drop, selección múltiple/copia y semáforo de elegibilidad forman parte del diseño acumulado, aunque cada incremento se valida de forma aislada.


### Contrato de ingreso de personal desde SIC: RRHH — 2026-09-19
SIC: RRHH debe entregar cada colaborador visible en SGI con adscripción explícita a **Seguridad Física (SF) + Compañía**. SIC: RRHH es SoR de esa relación inicial. SGI no crea ni infiere la membresía laboral; las transferencias operacionales posteriores se orquestan en ASI y se sincronizan de vuelta a SIC: RRHH.
