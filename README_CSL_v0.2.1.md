# SGI: Comando — CSL v0.2.1 UAT

Baseline: `CSL v0.2 — Notificación de Incidentes`.

Objetivo: sincronizar el formulario de Incidentes de Consola con el Excel `Incidentes(1).xlsx` recibido el 2026-09-27.

Cambios:
- Catálogo exacto de 3 categorías, 20 subcategorías y 90 tipos de incidente.
- El formulario respeta `Categoría → Subcategoría → Incidente`.
- `Inasistencia programada` y `Inasistencia efectiva` quedan correctamente como tipos de incidente bajo `Asistencia y Puntualidad`.
- El flujo de cobertura/reasignación se activa por el tipo de incidente.
- Se conserva el resto de CSL v0.2.

No cambia backend, Flyway, BD, scripts, interconexiones ni SITC. No incorpora EVC.
