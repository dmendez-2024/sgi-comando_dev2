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
