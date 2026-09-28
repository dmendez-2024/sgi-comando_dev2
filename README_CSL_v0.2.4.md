# SGI: Comando — CSL v0.2.4 UAT

Baseline: CSL v0.2.3 UAT_CANDIDATE.

## Cambio técnico
Se corrige un error de compilación preexistente en backend: `jakarta.ws.rs.ConflictException` no forma parte de la API Jakarta REST usada por Quarkus 3.28.1. La semántica HTTP 409 se conserva con `WebApplicationException(..., Response.Status.CONFLICT)`.

No cambia la lógica funcional de CSL/Incidentes, la taxonomía 3/20/90, la base de datos, Flyway ni SITC.

## UAT
Requiere `.env` local a partir de `.env.example`. Ejecutar `scripts/uat-start.ps1`, luego `scripts/uat-open.ps1`.
