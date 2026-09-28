# SGI: Comando — resumen UAT

**Estado del documento:** consolidación inicial de evidencia existente; no certifica que una regresión se haya ejecutado hoy.

La aprobación UAT corresponde al usuario/Product Owner según el proceso. Los estados siguientes se copian de la documentación del proyecto y deben confirmarse contra la RC y la base exactas que se prueben.

| Vertical | Baseline/estado registrado | Evidencia a usar | Pendiente identificado |
|---|---|---|---|
| TER | v1.0 congelada; v1.0.1 aparece como UAT_CANDIDATE en las notas del 2026-09-22 | `docs/verticals/TER/TER_UAT.md`, `TER_ACCEPTANCE_CRITERIA.md`, `QUALITY_GATE_TER_v1.0_FROZEN.md` | Ejecutar/registrar los criterios 023–027 de TER v1.0.1 y formalizar su CR/RC. |
| COM | v1.1.3 FROZEN según cierre | `docs/verticals/COM/COM_UAT.md`, `COM_ACCEPTANCE_CRITERIA.md`, `COM_FREEZE_NOTE.md` | V28 agrega responsable operacional; completar UAT de la RC COM correspondiente y formalizar CR/reapertura. |
| ASI | v0.7.4 figura como baseline cerrada en el cierre final; otros registros conservan estados/versiones anteriores | `docs/verticals/ASI/ASI_UAT.md`, criterios y gates por versión | Alinear versión vigente; agregar evidencia para `persona_id`/RRHH y notas UX sin mezclar cambios. ASI-DEC-059 está citado, pero no se encontró en su Decision Log. |
| SER | v0.10.10 aparece congelada en el cierre final | `docs/verticals/SER/SER_UAT.md` y changelogs UAT por release | Varias mejoras visuales de septiembre tienen validación visual pendiente; identificar cuál cuenta como RC abierta. |
| COO | v0.1 figura en el cierre final | `docs/CHANGELOG_UAT_COO_v0.1.md` y documentación COO disponible | El cambio Ver/Editar de Puestos tiene prueba local pendiente en `cambios/CHANGELOG_AJRC.md`. |
| BIT, CNS, NOV, CSL | Versiones finales figuran en `docs/SGI_COMANDO_CIERRE_FINAL.md` | Planes/changelogs de la vertical correspondiente | Confirmar el propietario de las pantallas de ejecución etiquetadas SER en algunas notas; adjuntar UAT visual cuando aplique. |
| INT | INT v0.1; los estados end-to-end incluyen BLOCKED/MANUAL_PENDING | `docs/00_HANDOFF_INT_v0.1.md`, `docs/INTERCONNECTIONS.md` | Probar ambas puntas antes de declarar READY/ACTIVE; alinear la referencia normativa y snapshot con v4.1. |

## Gate por cambio

Para cada RC, registrar en esta ubicación o en el plan de su vertical: identificador de baseline/RC, criterios ejecutados, resultado, evidencia, defectos, integraciones probadas y aprobación del responsable. Los cambios de reglas/datos/API/integraciones requieren CR aprobado antes del gate de implementación.

No se marcaron criterios nuevos como PASS en esta consolidación.
