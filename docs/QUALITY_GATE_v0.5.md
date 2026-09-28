# Quality Gate — UAT v0.5

| Gate | Estado | Nota |
|---|---|---|
| Raíz única `repo/` | PASS | validar de nuevo al comprimir |
| TS/TSX parser | PASS | todos los `.ts/.tsx` parsean con TypeScript compiler API |
| Type precheck local | PASS parcial | stubs locales; no sustituye dependencias reales de React/Vite |
| CSS estructura | PASS | balance de bloques validado |
| JSON / SITC | PASS | `RELEASE_MANIFEST` + baseline + delta v0.5 parsean |
| PowerShell ASCII-safe | PASS | todos los `.ps1` decodifican ASCII |
| Backend source | PASS heredado | código Java funcional no cambió desde v0.4.0.1, que levantó en UAT local; solo cambia versión de POM |
| npm/Vite build real | PENDIENTE UAT LOCAL | entorno de construcción actual no pudo resolver dependencias npm |
| Java 25 + Quarkus build real | PENDIENTE UAT LOCAL | el entorno actual no dispone del toolchain Java 25/Maven equivalente |
| Flyway | N/A | no hay nueva migración; se hereda V7 |
| Docker Compose | PENDIENTE UAT LOCAL | ejecutar con script estándar |
