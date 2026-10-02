# SGI: Comando — SITCpack CURRENT

**Norma:** SITC-NOM-001 v4.1  
**Fecha:** 2026-09-27  
**Código baseline:** SISTEMAS commit `8c528e8`.

## CURRENT

`repo/sitc/SGI_Comando_CURRENT.sitcpack`

Contenido:
- `manifest.json`
- `architecture.json`
- `core-normalized.json`
- `scenario.json`
- `README.md`

CURRENT contiene **23 Program IDs**, **25 interconexiones** y **31 interfaces**.

## Cambios frente al CURRENT v3 anterior

- `CORE.scope`: `UNIVERSAL`.
- Programas PE usan nomenclatura v4.1 `PE_SPECIFIC`.
- IDs de interconexión: `ORIGEN_DESTINO_NNNN_vNNN`.
- IDs v3 se conservan solo en `legacyConnectionIds`.
- Nueva definición `SGI_COM_CORE_0001_v001` para reglas versionadas de Impulsos.
- CORE = SoR reglas de Impulsos.
- SGI_COM PE = SoR evaluación/adjudicación/ledger/saldo.
- SIC:RRHH inbound usa `credential_ref`, idempotencia persistente (V34) y exige relación Persona–Compañía autoritativa para altas nuevas.

## Delta de esta RC

`sitc/SGI_COM_P0P1_20260927_COMPONENT_DELTA.sitcpack`

## Delta específico Impulsos

`sitc/IMP_v0.2_delta.sitcpack`

`IMP_v0.1_delta.sitcpack` se conserva como histórico y su decisión de SoR queda superseded.

## Importación CORE

- `PREVIEW_MERGE`.
- Merge Programas por `programId`.
- Merge interconexiones por `referenceCode`.
- Merge interfaces por `interfaceId`.
- Mostrar `CREATE / UPDATE / NO_CHANGE / CONFLICT / INVALID`.
- No importar secretos.
- No activar ni modificar bindings productivos automáticamente.

## CSL v0.2 — 2026-09-27
Sin cambios de arquitectura, Program IDs o interconexiones. `sitc/SGI_Comando_CURRENT.sitcpack` se preserva byte-for-byte respecto de CSL v0.1.1 / P0P1. La Notificación de Incidentes usa capacidades internas de SGI: Comando en esta UAT y no activa nuevos contratos externos.

# BIT-INT-001 RC1 (2026-09-29)

Se genera `sitc/SGI_COM-BIT-INT-001-RC1-COMPONENT_DELTA.sitcpack`. Es un delta aditivo del contrato existente, sin cambio de topología ni base de datos. Requiere PREVIEW/MERGE y reconciliación con CORE antes de promoción.

