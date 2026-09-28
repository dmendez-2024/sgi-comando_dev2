# CHANGELOG — CSL v0.2.4 — 2026-09-27

- Backend build fix: reemplaza `jakarta.ws.rs.ConflictException` por `jakarta.ws.rs.WebApplicationException`.
- Mantiene respuesta HTTP 409 mediante `Response.Status.CONFLICT`.
- Sin cambios funcionales en Incidentes.
- Sin cambios en database/Flyway/SITC.
- `uat-start.ps1` y `uat-version.json` actualizados a 0.2.4.
