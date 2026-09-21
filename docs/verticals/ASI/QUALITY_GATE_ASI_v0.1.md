# Quality Gate — ASI v0.1

**Estado:** CANDIDATO UAT

## Scope

- Base: COM v1.0 FROZEN + TER v1.0 FROZEN.
- `diff -qr` confirma cambios únicamente en ASI, CSS acotado a `.assignments-asi-v01`, documentación/manifests y SITC acumulativo.
- Archivos funcionales de TER y COM no fueron modificados.

## Prechecks ejecutados

| Control | Estado | Nota |
|---|---|---|
| Raíz única `repo/` | PASS | empaquetado final controlado |
| Scope guard vs COM v1.0 FROZEN | PASS | ver `ASI_SCOPE_DIFF.txt` |
| TER v1.0 FROZEN | PASS | sin cambios funcionales |
| COM v1.0 FROZEN | PASS | sin cambios funcionales |
| TypeScript focal ASI | PASS PRECHECK | `tsc` no reporta errores locales; el contenedor no tiene dependencias React/Lucide instaladas |
| Java syntax focal ASI | PASS PRECHECK | `javac` parsea los archivos; fallan solo imports/dependencias Quarkus/Jakarta ausentes del contenedor |
| JSON / SITC | PASS | parseo JSON válido |
| Flyway | N/A | ASI v0.1 no agrega migraciones |
| npm/Vite build real | PENDIENTE LOCAL | requiere dependencias/toolchain del UAT local |
| Java 25/Quarkus package real | PENDIENTE LOCAL | requiere Maven/toolchain de Docker UAT |
| Docker Compose | PENDIENTE LOCAL | Docker no disponible en este entorno |

## Criterio de promoción

No congelar ASI hasta completar UAT funcional definida en `ASI_ACCEPTANCE_CRITERIA.md`.
