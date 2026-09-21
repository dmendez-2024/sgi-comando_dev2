# Quality Gate — SGI: Comando UAT v0.3

La entrega solo marca como `PASS` las validaciones realmente ejecutadas en el entorno de generación. `BUILD VALIDADO` se obtiene únicamente cuando la UAT compila y levanta en Windows/Docker Desktop mediante `uat-start.ps1`.

| Gate | Estado | Evidencia / alcance |
|---|---|---|
| Estructura repo | PASS | Única raíz de entrega prevista: `repo/`. |
| JSON / `.sitcpack` | PASS | Parseo estructural previo a empaquetado. |
| Scripts PowerShell | PASS | Revisión ASCII-safe para Windows PowerShell 5.1. |
| Frontend TS/TSX syntax/type precheck | PASS | Precheck local con parser/compiler disponible; no equivale a `npm run build` con dependencias reales. |
| Java syntax precheck | PASS | Sin diagnósticos sintácticos detectados; no equivale a Maven/Quarkus Java 25. |
| Frontend `npm run build` real | PENDIENTE UAT LOCAL | El entorno de generación no dispone de dependencias npm instaladas de este repo. |
| Backend Java 25 / Maven / Quarkus package | PENDIENTE UAT LOCAL | El entorno de generación dispone de Java 21 y no Maven. |
| Flyway V6 sobre PostgreSQL 17 | PENDIENTE UAT LOCAL | Se valida al levantar Docker Compose. |
| Docker Compose | PENDIENTE UAT LOCAL | Docker no está disponible en el entorno de generación. |
| Smoke test API/UI | PENDIENTE UAT LOCAL | Se ejecuta después de levantar v0.3. |

## Base conocida

UAT v0.2.2.2 compiló y levantó correctamente en el ambiente local Windows/Docker Desktop del usuario. UAT v0.3 parte de esa base y agrega V6 + SGI-00T + cambios SGI-06; por tanto, debe volver a pasar el build real antes de considerarse validada.
