# TER v0.1 — Quality Gate

## Scope
Vertical única: **TER — Territorio**.  
Base técnica: SGI: Comando UAT v0.5.

## Resultado previo al ZIP
- Estructura `repo/`: **PASS**
- Scope guard vs base v0.5: **PASS**
- No cambios funcionales en COM/SER/PTO/PUE/ASG/REL/CON/NOV/BIT/PAT/REG: **PASS por diff**
- TS/TSX syntax parse (todo `frontend/src`): **PASS**
- Type precheck focal TER/Header/API con stubs locales: **PASS**
- CSS balance/precheck: **PASS**
- JSON + `.sitcpack`: **PASS**
- Java parse-phase syntax (archivos modificados): **PASS**
- Full `npm/Vite build`: **PENDIENTE UAT LOCAL** (dependencias npm no disponibles en este contenedor)
- Full Java 25/Quarkus package: **PENDIENTE UAT LOCAL** (toolchain Java 25/Maven no disponible aquí)
- Flyway: **N/A** — TER v0.1 no agrega migración
- Docker Compose: **PENDIENTE UAT LOCAL**

## Archivos funcionales modificados
- `frontend/src/pages/Territory.tsx`
- `frontend/src/styles.css` (solo clases prefijadas `ter-*`)
- `frontend/src/components/Header.tsx` (solo lista/labels de usuarios UAT visibles)
- `frontend/src/api.ts` (solo endpoint de auditoría TER)
- `backend/.../context/ContextResource.java` (metadata territorial CORE LOCAL para UI)
- `backend/.../territory/TerritoryResource.java` (consulta de historial TER)
- asset de mapa TER

Ver `TER_SCOPE_DIFF.txt` para comparación completa contra base v0.5.
