# TER — Acceptance Criteria v1.0 — CONGELADO

| ID | Criterio | Estado |
|---|---|---|
| TER-AC-001 | Layout: cabecera, resumen país/mapa y estructura territorial jerárquica. | PASS / APROBADO |
| TER-AC-002 | CORE determina si el país usa Provincias, Estados u otra denominación. | CONGELADO |
| TER-AC-003 | UI usa singular/plural de CORE; no usa `Provincia/Estado`. | CONGELADO |
| TER-AC-004 | SGI no crea subdivisiones oficiales; CORE es SoR. | CONGELADO |
| TER-AC-005 | Crear Zona genera Borrador y feedback visible. | PASS / APROBADO |
| TER-AC-006 | Editar Zona permite Responsable y subdivisiones; Activa no se elimina. | PASS / APROBADO |
| TER-AC-007 | Crear Región exige Zona padre. | PASS / APROBADO |
| TER-AC-008 | Región solo puede seleccionar subdivisiones incluidas en su Zona. | PASS / APROBADO |
| TER-AC-009 | Responsables provienen de SIC: RRHH. | CONGELADO |
| TER-AC-010 | Árbol expandir/contraer Zona y Región. | PASS / APROBADO |
| TER-AC-011 | Búsqueda y filtros territoriales. | PASS / APROBADO |
| TER-AC-012 | TER muestra Compañías por Región sin editar COM. | CONGELADO |
| TER-AC-013 | Historial/auditoría de Zona/Región. | PASS / APROBADO |
| TER-AC-014 | Director Nacional=País; Zonal=Zona; Jefe Regional=Región; Coordinador/Asistente=Compañía. | PASS PRELIMINAR UAT / CONGELADO |
| TER-AC-015 | Restricción territorial se aplica server-side. | CONGELADO |
| TER-AC-016 | Selector UAT contiene solo seis perfiles acordados. | PASS / APROBADO |
| TER-AC-017 | Tipografía/controles legibles y layout responsive razonable. | PASS / APROBADO |
| TER-AC-018 | CORE entrega polígonos/geometrías de cada subdivisión oficial. | DEPENDENCIA EXTERNA CORE / CONTRATO CONGELADO |
| TER-AC-019 | CORE entrega `datasetVersion` territorial. | DEPENDENCIA EXTERNA CORE / CONTRATO CONGELADO |
| TER-AC-020 | Mapa TER se pinta dinámicamente a partir de geometrías CORE + clasificación Zona/Región TER. | DISEÑO CONGELADO / IMPLEMENTACIÓN PRODUCTIVA DEPENDE DE CORE |
| TER-AC-021 | Cambio de Región/Zona de una subdivisión repinta mapa sin nueva imagen. | DISEÑO CONGELADO |
| TER-AC-022 | Ninguna otra vertical se modifica funcionalmente durante cierre TER. | PASS SCOPE GUARD |

## Cierre
TER v1.0 queda congelada. Los ítems dependientes de CORE no reabren TER: implementan un contrato ya congelado.
