# SER v0.10.10 — Consignas reconstruidas contra Bitácora / Patrullas

Baseline: SER v0.10.8.

## Objetivo
Alinear la página de **Consignas** al estándar UX/UI ya aprobado para **Bitácora** y **Patrullas**, evitando la desviación visual y operativa que ocurrió en la entrega anterior de Patrullas.

## Cambios principales
- Nueva composición en 3 paneles:
  - **Puestos del Punto**
  - **Protocolos del Punto**
  - **Editor del Protocolo / Consigna**
- Encabezado homologado con:
  - código del Protocolo
  - versión
  - estado
  - acciones de Historial / Guardado / Publicación / Activación
- Banda de **Consignas (x/20)** con selector tipo chip.
- Navegación principal con **pasos numerados**:
  1. Definición
  2. Consigna
  3. Alcance
  4. Aplicación
  5. Evidencias
  6. Trazabilidad
- Reemplazo de confirmaciones nativas críticas por **modal SGI**.
- Validación backend: máximo **20 Consignas por Protocolo**.

## Alcance técnico
- Frontend:
  - `src/pages/ConsignasConfig.tsx`
  - `src/styles.css`
  - `src/components/Sidebar.tsx`
  - `frontend/package.json`
- Backend:
  - `src/main/java/com/cajamarca/sgi/comando/consignments/ConsignmentResource.java`
- Documentación / release:
  - `RELEASE_MANIFEST.json`
  - `README_SER_v0.10.9.md`

## Base de datos
No requiere nuevas migraciones Flyway.
