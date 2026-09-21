# SGI: Comando — SER v0.10.3 UAT

## Cambios principales
- **Bitácora — alcance por Punto/Puesto:** un Protocolo de Bitácora ahora puede aplicar a uno o varios Puestos del mismo Punto.
- **Bitácora — Definición:** debajo de Descripción se agregó selector de Puestos del Punto con checkbox para definir el alcance del Protocolo.
- **Bitácora — listado de protocolos:** la columna central ahora muestra **todos los Protocolos del Punto**. Si el Protocolo no aplica al Puesto seleccionado, se muestra contextualizado como **Inactivo** y con nota **No aplica al puesto**.
- **Bitácora — UX/UI:** ajuste de legibilidad (fuentes más grandes), mejor espaciado interno en el panel derecho y aviso contextual cuando el Protocolo no aplica al Puesto seleccionado.
- **Backend / datos:** nueva tabla `logbook_protocol_post_scope` y migración `V22__ser_bitacora_protocol_post_scope.sql`. Los Protocolos existentes heredan automáticamente su `post_id` como alcance inicial.

## Compatibilidad
- No rompe la estructura histórica de `logbook_protocol`.
- Los Protocolos existentes siguen visibles y editables.
- El alcance puede modificarse únicamente en borradores; para versiones publicadas se mantiene la regla de crear nueva versión.
