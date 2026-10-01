# VALIDATION — CSL v0.2.4 — 2026-09-27

## Error observado en build real
`RrhhEmployeeSyncService.java` no compilaba porque importaba y construía `jakarta.ws.rs.ConflictException`, símbolo inexistente en la API Jakarta REST utilizada.

## Corrección
- Importa `jakarta.ws.rs.WebApplicationException`.
- Importa `jakarta.ws.rs.core.Response`.
- Lanza `new WebApplicationException(message, Response.Status.CONFLICT)`.
- Conserva HTTP 409 Conflict.

## Validaciones estáticas
- 0 referencias a `ConflictException` en backend Java.
- 1 uso intencional de `Response.Status.CONFLICT` en `RrhhEmployeeSyncService`.
- Database/Flyway y SITC sin cambios respecto de v0.2.3.
- Frontend funcional sin cambios respecto de v0.2.3; solo cambia etiqueta de versión.

## Limitación de entorno
No se ejecutó Maven completo en el contenedor de preparación porque no dispone de Maven/JDK 25. El build real del usuario ya había llegado a `javac` y reportó únicamente los dos errores del mismo símbolo corregido. La validación definitiva es `uat-start.ps1` en el entorno UAT Docker.
