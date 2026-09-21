# Quality Gate — UAT v0.4

| Gate | Estado antes de entrega |
|---|---|
| Estructura raíz `repo/` | PASS |
| Parse TS/TSX | PASS |
| Type precheck con stubs de dependencias | PASS |
| Java syntax precheck (JDK 21 parser; dependencias Quarkus no disponibles localmente) | PASS |
| JSON / SITC parse | PASS al empaquetar |
| Scripts PowerShell ASCII-safe | PASS por herencia v0.3 |
| npm/Vite build real | PENDIENTE UAT LOCAL (registry npm no disponible en este entorno) |
| Java 25 + Quarkus Maven package real | PENDIENTE UAT LOCAL |
| Flyway V7 PostgreSQL | PENDIENTE UAT LOCAL |
| Docker Compose | PENDIENTE UAT LOCAL |
| Smoke test navegador/API | PENDIENTE UAT LOCAL |

Una versión no se declara UAT PASS hasta completar los gates locales pendientes.

## Hotfix v0.4.0.1
- Build local previo v0.4: FAIL por `ConflictException` inexistente.
- Defecto corregido en código fuente.
- Rebuild Java 25/Quarkus: PENDIENTE UAT LOCAL sobre este hotfix.
