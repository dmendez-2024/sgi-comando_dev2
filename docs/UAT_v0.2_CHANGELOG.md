# SGI: Comando — UAT v0.2

Base: UAT v0.1.5.1.

## Vertical implementada: SGI-06 Asignaciones

Esta entrega convierte Asignaciones en una vertical transaccional real, no un mockup.

### Incluye
- Planificación por `Compañía + semana`, filtro opcional por Punto.
- Turnos materializados desde templates `SIC: COM LOCAL`; la UI no asume cantidad fija de turnos.
- Personal paginado desde `SIC: RRHH LOCAL`: Agentes, Escoltas y Supervisores.
- Vacaciones y permisos médicos read-only desde RRHH.
- Drag & Drop de personal a Turno Requerido.
- Arrastrar una asignación existente para copiarla a otro turno.
- IC por Puesto; ID por SMC LOCAL y mínimo TIER.
- Warnings no bloqueantes por IC, rol, ID/TIER y Cambio Requerido.
- Bloqueos por Compañía, inactividad, indisponibilidad, solapamiento y auto-relevo.
- Estados `DRAFT / PUBLISHED / CLOSED` y publicación con vacantes.
- Snapshot inmutable al publicar.
- Reasignación explícita posterior a publicación.
- Auditoría `assignment_event` y Outbox de publicación.
- Cobertura semanal real alimenta Dashboard.

### Seed UAT
12 colaboradores de Galvarino, con habilidades e ID distintos. Edison Morales tiene Permiso Médico parcial en la semana UAT y Ana Lucía Vega Vacaciones parciales para verificar bloqueos temporales.

### Nota
Los datos de RRHH/COM/SMC son providers LOCAL con contratos preparados para sustituirse por integraciones reales.
