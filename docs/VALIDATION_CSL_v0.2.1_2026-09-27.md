# VALIDATION — CSL v0.2.1 — 2026-09-27

Baseline: `SGI_Comando_CSL_v0.2_Incidentes_UAT.zip`

## Fuente
- `docs/reference/Incidentes_2026-09-27.xlsx`
- Hoja: `Incidentes`
- Catálogo leído: 3 categorías, 20 subcategorías, 90 incidentes.

## Cambios de frontend
- `frontend/src/components/IncidentNotificationPanel.tsx`
- `frontend/src/pages/ConsolaMonitor.tsx`
- `frontend/src/data/incidentTaxonomy.ts` (nuevo)

## Validaciones
- Parse sintáctico TS/TSX: PASS (23 archivos, 0 errores).
- Taxonomía: PASS (3 / 20 / 90).
- Jerarquía: PASS (`Categoría → Subcategoría → Incidente`).
- Inasistencia programada/efectiva: PASS como incidentes bajo `Asistencia y Puntualidad`.
- Backend: byte-identical vs CSL v0.2.
- Database/Flyway: byte-identical vs CSL v0.2.
- Scripts: byte-identical vs CSL v0.2.
- SITC: byte-identical vs CSL v0.2.
- EVC: no implementado.
- Build Vite completo: no ejecutado en este entorno portable por ausencia de instalación `node_modules`.

## Nota
Esta UAT continúa usando datos frontend/locales para Incidentes y Reasignación; no introduce persistencia productiva.
