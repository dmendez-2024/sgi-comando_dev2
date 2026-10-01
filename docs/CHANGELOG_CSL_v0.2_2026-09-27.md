# CHANGELOG — CSL v0.2 — Notificación de Incidentes

Fecha: 2026-09-27  
Baseline: `SGI_Comando_CSL_v0.1.1_UAT.zip`  
Estado: UAT_CANDIDATE  
Fuente funcional: `Pagina Consola.docx`.

## Cambios realizados

- CSL-INC-001 | APROBADO | Nuevo botón `Notificar Incidente` en Consola.
- CSL-INC-002 | APROBADO | Panel lateral derecho `Notificación de Incidente`.
- CSL-INC-003 | APROBADO | Categorías Servicio / Seguridad / Administrativo con iconografía de la especificación.
- CSL-INC-004 | APROBADO | Subcategorías basadas en `Incidentes.xlsx` ya transcrito en `docs/spec/REFERENCE_INCIDENT_TAXONOMY.md`.
- CSL-INC-005 | APROBADO | Criticidad Informativo / Menor / Moderado / Mayor / Crítico.
- CSL-INC-006 | APROBADO | Cliente y Punto obligatorios; Puesto opcional.
- CSL-INC-007 | APROBADO | Colaboradores limitados a quienes trabajaron en el Punto en las últimas 2 semanas; deduplicación por colaborador.
- CSL-INC-008 | APROBADO | Descripción + máximo 5 imágenes; Resolución + máximo 5 imágenes.
- CSL-INC-009 | APROBADO | Sanción Sí/No y descripción condicional.
- CSL-INC-010 | APROBADO | Estados Borrador / Finalizado y reapertura editable desde Casos operativos.
- CSL-INC-011 | APROBADO | Flujo especial de Inasistencia Programada/Efectiva y ranking de Agentes para reasignación.
- CSL-INC-012 | APROBADO | Detalle de caso pasa a panel derecho en la misma página para homologar la referencia visual de Consola.

## Archivos frontend

- `frontend/src/pages/ConsolaMonitor.tsx`
- `frontend/src/components/IncidentNotificationPanel.tsx`
- `frontend/src/styles.css`
- `frontend/public/assets/csl-incidents/incident.png`
- `frontend/public/assets/csl-incidents/service.png`
- `frontend/public/assets/csl-incidents/security.png`
- `frontend/public/assets/csl-incidents/administrative.png`

## Backend / BD / SITC

Sin cambios. No hay migración Flyway nueva. No hay interconexión nueva/modificada/retirada. `CURRENT` se conserva idéntico a la baseline.

## Limitación UAT

La fuente de colaboradores, historial de últimas 2 semanas, horarios/disponibilidad y coordenadas es un dataset DEMO/local para validar el flujo. Los adjuntos se validan como archivos de imagen y se conservan como metadata durante la sesión; no se almacenan todavía en repositorio binario/backend.

## EVC

Fuera de alcance. No se implementa ninguna capacidad de Eventos de Cumplimiento.
