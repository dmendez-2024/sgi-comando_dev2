# CHANGELOG — CSL v0.1.1 — 2026-09-27

**Programa:** SGI_COM  
**Vertical:** CSL — Consola  
**Baseline:** `SGI_Comando_P0P1_RC_2026-09-27.zip`  
**Estado:** UAT_CANDIDATE  

## Cambios realizados
- `CSL-UX-001 | APROBADO` — área de búsqueda/filtros convertida en accordion/collapsible.
- `CSL-UX-002 | APROBADO` — estado inicial del accordion: cerrado.
- `CSL-UX-003 | APROBADO` — al expandir se muestra exactamente la lógica de filtros preexistente.
- `CSL-UX-004 | APROBADO` — colapsar no borra filtros ni modifica resultados.
- `CSL-UX-005 | PERMITIDO` — accesibilidad con `aria-expanded`, `aria-controls` y soporte `prefers-reduced-motion`.

## Archivos modificados
- `frontend/src/pages/ConsolaMonitor.tsx`
- `frontend/src/styles.css`
- `README.md`
- `docs/00_HANDOFF.md`
- `docs/CHANGELOG.md`
- `docs/VERSION_MATRIX.md`
- `RELEASE_MANIFEST.json`

## Archivos nuevos
- `README_CSL_v0.1.1.md`
- `docs/CHANGELOG_CSL_v0.1.1_2026-09-27.md`
- `CSL_RELEASE_MANIFEST.json`

## Backend / BD / arquitectura
- Backend: sin cambios.
- Flyway/BD: sin cambios; máximo continúa V34.
- Interconexiones: 0 nuevas, 0 modificadas, 0 retiradas.
- `sitc/SGI_Comando_CURRENT.sitcpack`: sin cambios.
- EVC/Eventos de Cumplimiento: no implementado.

## Validación
- TSX `ConsolaMonitor.tsx`: transpile/parsing PASS.
- CSS: llaves balanceadas PASS.
- Validación estructural del accordion: PASS.
- Build Vite completo: no ejecutado en esta fase por no existir `node_modules`/lockfile en el artefacto base.
