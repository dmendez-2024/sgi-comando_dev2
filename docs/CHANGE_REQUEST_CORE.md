# CHANGE REQUEST — CORE

**CR-ID:** CR-CORE-0001  
**Origen:** SGI_COM P0/P1 RC 2026-09-27  
**Interconexión principal:** `SGI_COM_CORE_0001_v001`

## Cambio requerido

CORE debe mantener como **System of Record** las reglas versionadas de Impulsos y permitir que cada Instancia PE de SGI: Comando resuelva/consuma la regla efectiva aplicable a su contexto.

Contrato lógico inicial:
- `SGI_COM_CORE_0001_IF01`
- `GET /v1/impulses/rules/effective`
- contexto mínimo: Instancia PE / `instanceCountryId` y `asOf` cuando aplique.
- respuesta: `rulesetVersion`, vigencia y reglas versionadas (elegibilidad, habilidad, probabilidad, cantidad y condiciones).

## Separación de SoR

- CORE: reglas de Impulsos.
- SGI_COM PE: evaluación/aplicación de regla, adjudicación/reverso y ledger/saldo por Operador.
- SGI_OPR: hechos operativos; no es SoR de saldo.

## Importación arquitectura

Cargar `sitc/SGI_COM_P0P1_20260927_COMPONENT_DELTA.sitcpack` o CURRENT como **ESCENARIO PREVIEW/MERGE**. La importación no activa ni modifica automáticamente bindings productivos de Instancias PE.
