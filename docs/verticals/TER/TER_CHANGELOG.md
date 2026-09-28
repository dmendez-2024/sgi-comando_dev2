# TER — Changelog

## v1.0 — CONGELADA — 2026-09-08
- Se congela la vertical TER.
- CORE pasa a ser SoR obligatorio del tipo de subdivisión territorial, catálogo oficial, geometrías y versión del dataset.
- Se elimina del diseño productivo el concepto de mapa estático como fuente maestra.
- El mapa de TER se define como render dinámico desde polígonos CORE + asignaciones Zona/Región TER.
- UI usa terminología dinámica de CORE (`Provincia`, `Estado`, etc.).
- Se congela exclusividad: una subdivisión oficial completa pertenece a una Zona y una Región a la vez.
- Se incorpora `CORE_TERRITORIAL_CONTRACT.md` como contrato interproyecto.
- Se congela frontera TER/COM: TER consulta Compañías por Región, COM las administra.
- Sin cambios funcionales en otras verticales.

## v0.1 — Candidato a congelar
- Reordenamiento completo de UI según referencia aprobada.
- Resumen de país con contadores y mapa operacional de referencia UAT.
- Árbol Zona → Región con búsqueda, filtros y expandir/contraer.
- Crear/Editar Zona y Región.
- Terminología territorial dinámica desde contexto CORE.
- Responsables desde SIC: RRHH LOCAL.
- Historial/auditoría consultable.
- Selector UAT reducido a seis perfiles acordados.

## v1.0.1 — UAT_CANDIDATE — 2026-09-22

La bitácora `cambios/CHANGELOG_DM.md` registra criterios adicionales de filtrado/validación y un cambio de persistencia para separar asignaciones territoriales en borrador de las efectivas. El código fuente contiene `V27__territory_draft_assignments.sql`, que agrega `draft_zone_id` y `draft_region_id` a `country_subdivision`.

- Estado reportado: implementado; revalidación funcional UAT pendiente.
- Esta entrada no cambia ni sustituye TER v1.0 congelada.
- El registro de trabajo afirma que el contrato CORE no cambió, pero el modelo de datos y el comportamiento local sí evolucionaron. Requiere CR/aprobación y RC documentados según SITC-NOM-001 v4.1 antes de considerarlo cerrado.
- Criterios nuevos: ver TER-AC-023..027 en `cambios/CHANGELOG_DM.md`; no se marcan PASS en esta documentación.
