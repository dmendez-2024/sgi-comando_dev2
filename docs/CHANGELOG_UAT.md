
## UAT v0.1.5
- Menú ajustado a estructura objetivo: Dashboard; Operaciones (Compañías, Servicios, Asignaciones, Bitácora, Consignas, Novedades, Requerimientos, Comunicación); Recurso Humano; Recurso Material; Reportes (Estadísticas, Reportería Ad-Hoc); Auditoría; Configuración.
- Dashboard renombrado visualmente a `Comando Operacional`.
- Dashboard expandido con KPIs, Alertas críticas, Cobertura por compañía, Mapa de cobertura, Resumen operativo hoy, Actividad del día y recomendaciones UAT.
- Logo SGI: Comando procesado para integrarse visualmente al fondo del sidebar.

## UAT v0.2 — Asignaciones end-to-end
- Primera vertical operacional completa sobre v0.1.5.1.
- Matriz semanal por Compañía → Punto → Puesto → Turnos exactos SIC: COM.
- Pool de Agentes/Escoltas/Supervisores desde SIC: RRHH LOCAL.
- Vacaciones y permisos médicos con intervalos read-only desde RRHH.
- Drag & Drop y Drag & Copy entre turnos.
- Persistencia PostgreSQL de Plan, Turnos Requeridos, Asignaciones y eventos de auditoría.
- IC calculado con fórmula congelada; `ID vs TIER`, rol, IC y Cambio Requerido solo generan alertas.
- Bloqueos por indisponibilidad, estado laboral, Compañía, solapamiento y no auto-relevo.
- Publicación con vacantes permitida y snapshot inmutable; cambios posteriores son Reasignaciones.
- Dashboard consume cobertura semanal real desde Asignaciones.
- Seed UAT con 12 colaboradores y casos de Vacaciones/Permiso Médico.

