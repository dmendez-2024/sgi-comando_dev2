# SGI: Comando — INT v0.1.1 ARCH SYNC

Entrega de sincronización arquitectónica sobre INT v0.1.

## Cambio único de arquitectura vigente
`CORE.scope`: `COUNTRY_COMPANY` → `UNIVERSAL`.

Se adopta como baseline vigente:
`PKG-ECOSISTEMA-CM-CORE-UNIVERSAL-20260921-001`.

## Sin cambios funcionales
- UI: sin cambios.
- Backend funcional: sin cambios.
- Módulo genérico INT v0.1: sin cambios.
- 24 IDs de interconexión: sin cambios.
- 30 interfaces: sin cambios.
- Base de datos: sin migraciones.

## CURRENT
`sitc/SGI_Comando_CURRENT.sitcpack` ya contiene el snapshot acumulativo contra CORE UNIVERSAL.

## Artefacto recomendado para cargar en CORE
`sitc/v3/SITC-ECOSISTEMA-CM-CORE-UNIVERSAL-20260921-SGI_COM-INTERCONNECTIONS-SCENARIO_SNAPSHOT.sitcpack`

La carga es PREVIEW/MERGE y no modifica automáticamente Instancias PE productivas.
