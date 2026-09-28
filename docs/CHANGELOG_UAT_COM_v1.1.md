# SGI: Comando — COM v1.1 UAT

**Fecha:** 2026-09-19

## Objetivo
Migrar la identidad de Compañías a un catálogo administrado por CORE sin perder la configuración operacional de SGI.

## Cambios
- Eliminada la creación local de Compañías desde cero.
- Nueva acción **Activar desde CORE**.
- Nombre, Logo y Reseña histórica pasan a solo lectura / Fuente CORE.
- Estado, Motivo del cambio, Zona y Regiones operativas permanecen editables en SGI.
- Se conserva versionamiento e historial de cambios.
- Se incorpora `core_company_catalog_snapshot` como adaptador UAT de CORE.
- Se crea/asegura **Kaibil** como Compañía de Operaciones siempre Activa.
- Kaibil no puede desactivarse y no requiere Zona/Región propia.
- Se preserva la identidad visible existente de Galvarino al enlazarla con CORE.

## SoR
- Identidad de Compañía: **CORE**.
- Configuración operacional de Compañía dentro de SGI: **SGI: Comando / COM**.
