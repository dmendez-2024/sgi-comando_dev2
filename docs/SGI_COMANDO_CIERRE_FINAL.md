# SGI: Comando — Cierre Final de Definición

> **CIERRE HISTÓRICO SUPERSEDIDO / REABIERTO:** este documento registra el cierre del 2026-09-20. SISTEMAS devolvió una baseline posterior (`sgi-comando_dev.zip`, commit `8c528e8`, 2026-09-25) y el P0/P1 RC 2026-09-27 supersede la arquitectura/SITC y la definición de SoR de Impulsos aquí contenidas. Las verticales no modificadas conservan su historial; para estado vigente leer `docs/00_HANDOFF.md`, `docs/DECISIONS.md` y `sitc/SGI_Comando_CURRENT.sitcpack`.

**Fecha de cierre:** 2026-09-20  
**Estado:** CLOSED / FROZEN para todas las verticales funcionales definidas a esta fecha.  
**Propósito del paquete:** entrega consolidada a Sistemas con código UAT vigente, documentación acumulativa, contratos de integración y paquete SITC.

## Baselines funcionales congeladas

| Código | Vertical | Versión | Estado |
|---|---|---:|---|
| TER | Territorio | v1.0 | FROZEN |
| COM | Compañías | v1.1.3 | FROZEN |
| SER | Servicios | v0.10.10 | FROZEN |
| ASI | Asignaciones | v0.7.4 | FROZEN |
| COO | Coordinación | v0.1 | FROZEN |
| BIT | Bitácora global | v0.1 | FROZEN |
| CNS | Consignas operativas | v0.1.2 | FROZEN |
| NOV | Novedades | v0.1 | FROZEN |
| CSL | Consola de Monitoreo | v0.1 | FROZEN |

## Hardening técnico

`PERF v0.1` constituye un hardening técnico sobre la baseline congelada; no modifica la UI ni redefine las reglas funcionales. Incluye paginación real, reducción de consultas N+1, carga incremental, índices de rendimiento y optimizaciones para el objetivo de 2,000+ agentes.

## Arquitectura de integración consolidada

SGI: Comando actúa como hub/orquestador operacional. Las integraciones principales quedan conceptualizadas como:

- `CORE → SGI: Comando`: contexto país, parámetros transversales, calendario, regulación, geografía oficial y catálogo corporativo.
- `IDENT ↔ SGI: Comando`: autenticación, identidad y autorización.
- `SIC: RRHH ↔ SGI: Comando`: personal, compañía, habilidades, disponibilidades y transferencias.
- `SIC: RRMM ↔ SGI: Comando`: recursos materiales y dotaciones relevantes para la operación.
- `SIC: COM ↔ SGI: Comando`: cliente, servicio, punto y puesto comerciales.
- `SGI: Comando ↔ SGI: Operador`: configuración operativa y ejecución de campo.
- `SGI: Comando ↔ VISINT`: evaluación visual de evidencias; VISINT no adjudica Impulsos.
- `SGI: Comando ↔ SGI: Cliente`: publicación/consulta de información aprobada para cliente.

## Regla VISINT / Impulsos

Las fotos/evidencias levantadas desde SGI: Operador viajan a SGI: Comando; SGI: Comando las envía a VISINT. VISINT devuelve evaluación `PASS / FAIL / ERROR`. Si existe `PASS`, SGI: Comando evalúa la regla vigente de Impulsos y ejecuta la probabilidad una sola vez. Definición vigente superseding 2026-09-27: CORE es System of Record de las reglas versionadas de Impulsos; cada SGI: Comando PE aplica esas reglas y es System of Record de la evaluación/adjudicación y del ledger/saldo por Operador.

## CORE

La especificación consolidada de lo que SGI: Comando requiere de CORE se encuentra en:

- `docs/CORE_REQUIREMENTS_SGI_COMANDO.md`
- `docs/SGI_Comando_Requerimientos_CORE.pdf`

## SITC

Paquete acumulativo final:

- `sitc/SGI_Comando_FINAL_2026-09-20.sitcpack`
- `sitc/SGI_Comando_CURRENT.sitcpack` (actualizado para coincidir con el cierre)

## Regla de mantenimiento

Este paquete constituye el cierre de definición vigente. Cualquier cambio futuro debe reabrir explícitamente la vertical afectada y producir una nueva versión/delta sin alterar retroactivamente las baselines congeladas.
