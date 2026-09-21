# SGI: Comando — Registro de Verticales Congeladas

| Vertical | Versión | Estado | Fecha | Dependencias clave |
|---|---:|---|---|---|
| TER — Territorio | 1.0 | FROZEN | 2026-09-08 | CORE, SIC: RRHH |
| COM — Compañías | 1.0 | FROZEN | 2026-09-08 | TER v1.0, SER, MinIO |
| ASI — Asignaciones | 0.6.5 | FROZEN | 2026-09-09 | SIC: COM, SIC: RRHH, SMC |

## Regla

No modificar una vertical `FROZEN` durante el desarrollo de otra vertical. Cualquier cambio posterior debe abrir explícitamente una nueva versión de esa vertical.
