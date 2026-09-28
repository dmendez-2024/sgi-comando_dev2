# SGI-06 — Asignaciones v0.1 (En diseño)

## Propósito
Distribuir y programar los Agentes de Seguridad (AdS) de una Compañía entre los Puntos/Puestos de esa misma Compañía, respetando Formato, turnos, reglas de descanso/no auto-relevo, disponibilidad y trazabilidad.

## UI — estructura funcional congelada
La UI puede modernizarse visualmente, pero conserva la estructura general del diseño histórico de Asignaciones:
- pantalla base de planificación;
- panel/tarjetas de personal;
- filtros;
- matriz de Puestos/turnos;
- asignación por Drag & Drop;
- Hold & Drag & Copy para replicar patrones;
- visualización de permisos/indisponibilidades;
- trazabilidad visible de quién asignó o reasignó y cuándo.

## Separación de conceptos
- `CompanyMembership`: pertenencia primaria del colaborador a una Compañía.
- `OperationalAssignment`: planificación de un AdS a un Puesto/turno.
- `Reassignment`: Reasignación posterior a una asignación publicada, normalmente por falto/no presentación del AdS originalmente programado.
- `ReliefEvent`: Relevo ejecutado; determina quién realmente asistió/cubrió el Puesto.
- `PartialReliefCoverage`: extensión parcial del turno saliente para cubrir tardanza al inicio del siguiente turno.
- Un cambio de asignación no cambia la Compañía Primaria.
- Refuerzo Temporal entre Compañías queda como figura futura.

## Fuentes
- SIC: COM → Punto, Puesto, Formato, calendario/turnos, FHE.
- SIC: RRHH → AdS, estado laboral, habilidades, permisos/vacaciones/indisponibilidades.
- SGI-04 → perfil de 8 habilidades mínimas del Puesto, escala 0–5.
- SGI-01/03 → Compañía Primaria y Compañía responsable del Punto.
- SGI: Agente / SGI: Operador → ejecución del Relevo y evidencia de presencia.

## Reglas de elegibilidad para asignación
Estas reglas NO forman parte del Índice de Compatibilidad (IC):
1. El AdS debe pertenecer a la misma Compañía Primaria del Punto/Puesto, salvo futuro Refuerzo Temporal.
2. El AdS debe estar activo.
3. No puede existir una indisponibilidad que cubra el intervalo: permiso médico, vacaciones u otra ausencia formal.
4. No se permiten solapamientos temporales.
5. No se permite auto-relevo: el AdS no puede ser planificado para el turno inmediatamente consecutivo luego de completar el suyo, sea en el mismo Puesto, otro Puesto u otro Punto.
6. Las demás reglas de descanso aplicables deben respetarse.
7. `Cambio requerido` no bloquea la asignación mientras el AdS siga activo; permanece visible y afecta Dotación Humana Proyectada.

## Excepción operacional: cobertura parcial del siguiente turno
No es una asignación planificada normal.
- Solo puede ocurrir en el mismo Puesto.
- Se origina durante el Relevo cuando el AdS entrante llega tarde o la continuidad operacional exige esperar una Reasignación.
- Máximo: 3 horas adicionales.
- Se registra la hora real de salida del AdS saliente y la hora real de llegada/relevo.
- Genera eventos hacia SIC: RRHH para la bonificación correspondiente al AdS que extendió su turno y el descuento correspondiente al AdS tardío.
- No habilita cubrir el turno completo siguiente.

## Índice de Compatibilidad (IC)
El IC mide exclusivamente compatibilidad de habilidades:
- Perfil del AdS: 8 habilidades, escala 0–5.
- Perfil del Puesto: 8 habilidades mínimas, escala 0–5.
- Si el AdS cumple o supera todas las habilidades mínimas, `IC turno = 100%`.
- Si existe incumplimiento, `IC turno < 100%` en proporción al déficit de habilidades.
- Un IC menor a 100% NO bloquea la asignación.
- Permisos, vacaciones, pertenencia a otra Compañía, indisponibilidad o descanso NO reducen el IC: son reglas separadas de elegibilidad.

### IC semanal
`IC semanal = promedio aritmético de los IC de todos los turnos asignados durante la semana`.

La fórmula matemática queda congelada más abajo como `IC = 100 × Σ min(A_i,R_i) / Σ R_i`.

## Planificación y publicación
Vista predeterminada: semanal.

Estados:
- Borrador.
- Publicado.
- Cerrado.

`Publicar` es suficiente para convertir el plan en oficial y distribuirlo a las interfaces operativas autorizadas; no existe una segunda aprobación obligatoria.

## Reasignaciones
La nomenclatura oficial es `Reasignación`.

Caso típico:
1. AdS A estaba publicado para un turno.
2. AdS A no se presenta.
3. Operaciones asigna/cubre con AdS B.
4. SGI conserva la asignación original y crea el vínculo de Reasignación.
5. El Relevo determina quién realmente cubrió el Puesto.

Trazabilidad mínima:
- asignación original;
- AdS originalmente programado;
- AdS reasignado;
- motivo;
- usuario que ejecutó la Reasignación;
- fecha/hora;
- vínculo con el Relevo ejecutado.

## Relevo y asistencia real
La verdad operacional de quién asistió NO se determina solo desde la planificación.

El Relevo se ejecuta desde SGI: Operador / aplicación móvil del Agente:
- identificación del entrante y saliente;
- fotografía de rostro;
- validación biométrica mediante IA;
- fecha/hora;
- Puesto;
- evento de relevo confirmado.

SGI: Comando consume/registra este resultado para diferenciar:
- AdS planificado;
- AdS realmente presente;
- Reasignaciones;
- tardanzas;
- cobertura parcial adicional.

## Eventos hacia SIC: RRHH
### Falto / no presentación con Reasignación
SGI debe emitir dos efectos diferenciados:
1. solicitud/evento de descuento al AdS originalmente asignado que no asistió;
2. solicitud/evento de pago de día extra al AdS que efectivamente cubrió mediante Reasignación.

### Tardanza con cobertura parcial
SGI debe emitir:
1. solicitud/evento de descuento correspondiente al AdS que llegó tarde;
2. solicitud/evento de bonificación por cobertura parcial al AdS saliente que extendió su turno.

SIC: RRHH conserva la responsabilidad sobre el procesamiento laboral/nómina; SGI reporta el hecho operacional auditable.

## Dotación Humana visible
Por Compañía:
- FHE requerido.
- AdS Reales.
- Delta Actual.
- Estado Actual: Subdotación / Óptimo / Sobredotación.
- Cambio requerido.
- AdS Proyectados.
- Delta Proyectado.
- Estado Proyectado.
- AdS con/sin asignación en el período.

## Auditoría
Eventos conceptuales:
- `ASSIGNMENT_CREATED`
- `ASSIGNMENT_MOVED`
- `ASSIGNMENT_COPIED`
- `ASSIGNMENT_REMOVED`
- `ASSIGNMENT_PLAN_PUBLISHED`
- `ASSIGNMENT_PLAN_CLOSED`
- `REASSIGNMENT_CREATED`
- `RELIEF_CONFIRMED`
- `NO_SHOW_CONFIRMED`
- `LATE_ARRIVAL_CONFIRMED`
- `PARTIAL_RELIEF_COVERAGE_RECORDED`

Cada evento conserva contexto, Compañía, Punto, Puesto, AdS, intervalo, usuario/sistema, fecha/hora y before/after cuando aplique.

## Integraciones relacionadas
- SIC: COM → SGI: Punto, Puesto, Formato, turnos, FHE.
- SIC: RRHH → SGI: colaboradores, habilidades, estado e indisponibilidades.
- SGI: Comando → SGI: Agente / Supervisor / Operador: plan publicado.
- SGI: Agente / Operador → SGI: Comando: Relevo ejecutado + evidencia facial/validación IA.
- SGI: Comando → SIC: RRHH: faltos, tardanzas, descuentos solicitados, pago de día extra y bonificación por cobertura parcial.

## Distinción IC vs ID

### IC — Índice de Compatibilidad
Mide únicamente el acople entre las 8 habilidades del Agente y las 8 habilidades mínimas requeridas por el Puesto.

Para cada habilidad `i`:
- `A_i` = nivel del Agente (0–5).
- `R_i` = nivel mínimo requerido por el Puesto (0–5).

Fórmula congelada:

`IC = 100 × [Σ min(A_i, R_i)] / [Σ R_i]`

Reglas:
- Tope máximo: 100%.
- El sobrecumplimiento en una habilidad no compensa el déficit en otra.
- Si el Agente cumple/supera todas las habilidades, IC = 100%.
- IC < 100% no bloquea por sí solo la asignación.
- IC por turno se calcula con el Puesto efectivamente asignado.
- IC semanal planificado = promedio aritmético de IC de todos los turnos publicados de la semana.
- IC semanal ejecutado = promedio aritmético de IC de los turnos según el Agente que realmente cubrió cada turno, determinado por Relevo/Reasignación.

### ID — Índice de Desempeño
Es distinto al IC. Mide qué tan bien desempeña el Agente su trabajo mediante fórmulas propias, no su acople de habilidades a un Puesto.

El ID debe mostrarse junto al IC pero nunca fusionarse con él.

## TIER contractual e ID mínimo
La Orden de Servicio recibida desde SIC: COM incluye el TIER contratado por el cliente, además de Punto/Puesto, Formato, Rotación, Turnos, FHE y demás datos comerciales/operacionales vendidos.

Tabla congelada de ID mínimo:
- TIER I → ID mínimo 6.5
- TIER II → ID mínimo 7.5
- TIER III → ID mínimo 8.5
- TIER IV → ID mínimo 9.5

El TIER contractual determina el `ID mínimo requerido` para la operación. Este requisito es independiente del IC.

Ejemplo UI:
- ID del AdS: 8.7
- TIER: III
- ID mínimo requerido: 8.5
- Cumplimiento ID: Sí
- IC para Puesto seleccionado: 92%

Decisión congelada: no cumplir el ID mínimo del TIER genera alerta visual y NO bloquea la asignación. SMC es el System of Record del ID.

## ID mínimo por TIER — comportamiento de asignación
- El TIER pertenece al Puesto, no necesariamente a toda la Orden de Servicio.
- Un mismo Punto puede contener Puestos con TIER distintos.
- ID mínimo:
  - TIER I → 6.5
  - TIER II → 7.5
  - TIER III → 8.5
  - TIER IV → 9.5
- Si `ID Agente < ID mínimo del Puesto`, la asignación sigue permitida.
- La UI debe resaltarla con color diferenciado e icono de advertencia.
- El incumplimiento de ID no altera el IC y no es una regla de elegibilidad bloqueante.

## SMC — System of Record del ID
El ID es un KPI calculado por el SMC (Sistema de Métricas de Cajamarca), responsable de los KPIs de Cajamarca.

Flujo:
1. El cumplimiento de tareas se genera en SGI: Operador.
2. SGI: Operador envía el hecho/evento a SGI: Comando.
3. SGI: Comando normaliza/encamina la información hacia SMC.
4. SMC calcula/actualiza el ID y demás KPIs.
5. SMC devuelve el resultado vigente a SGI: Comando.
6. SGI: Comando usa el ID vigente en Asignaciones, Estado Operacional y reportería.

SGI: Comando no recalcula el ID.

## Planificación semanal — modelo operativo

### Unidad de planificación
La planificación se realiza por:
- `instance_country_id`
- `company_id`
- semana operacional

La vista predeterminada es semanal. Cada plan semanal conserva versión e historial.

### ShiftOccurrence / Turno requerido
SGI genera ocurrencias concretas de turno a partir de la Orden de Servicio vigente recibida desde SIC: COM:
- Punto
- Puesto
- Formato
- Rotación
- calendario
- hora inicio/fin
- FHE
- TIER
- versión comercial efectiva

Una `ShiftOccurrence` representa una necesidad temporal concreta de cobertura. La asignación vincula un AdS real a esa ocurrencia.

### Estados del plan
- Borrador
- Publicado
- Cerrado

Publicar convierte el plan en oficial y lo distribuye a SGI: Agente / Supervisor / Operador. No existe segunda aprobación obligatoria.

### UI — estructura general
Se conserva la estructura funcional del prototipo histórico:
- panel izquierdo: pool/tarjetas de AdS;
- filtros de disponibilidad, habilidades, ID, IC, Cambio requerido y situación;
- matriz central semanal por Punto → Puesto → turno;
- Drag & Drop;
- Hold & Drag & Copy / copiar patrones;
- panel contextual del slot seleccionado;
- trazabilidad de asignado/reasignado por usuario y fecha.

La modernización será visual y ergonómica, sin cambiar esta lógica general.

## Validación de asignación — orden lógico
1. Misma Compañía Primaria del Punto/Puesto, salvo futuro Refuerzo Temporal.
2. Colaborador activo.
3. Sin vacaciones, permiso médico u otra indisponibilidad aplicable.
4. Sin solapamiento horario.
5. Respeto de regla de no auto-relevo y descanso.
6. Mostrar ID vs mínimo por TIER del Puesto; incumplimiento genera advertencia visual, no bloqueo.
7. Calcular y mostrar IC para el Puesto; IC <100% no bloquea.

Elegibilidad, ID e IC son capas independientes.

## Indicadores de planificación
Por Compañía/semana:
- FHE requerido.
- AdS Reales.
- Cambio requerido.
- AdS Proyectados.
- Delta actual/proyectado y estado de Dotación Humana.
- Turnos requeridos.
- Turnos asignados.
- Turnos sin asignar.
- AdS con asignación.
- AdS sin asignación.
- IC Planificado semanal.
- porcentaje de asignaciones cuyo ID cumple el mínimo del TIER.
- cantidad de asignaciones bajo mínimo de ID.

## Reasignación
`Reasignación` es una modificación posterior a la asignación publicada, típicamente porque el AdS planificado no se presentó.

Se preservan:
- asignación original;
- AdS originalmente programado;
- nueva asignación;
- AdS que cubre;
- motivo;
- autor;
- fecha/hora;
- vínculo con Relevo/ejecución real.

## Ejecución real
La planificación no define la asistencia real. El Relevo es la fuente de verdad operacional.

SGI: Agente / SGI: Operador producen el Relevo con:
- identidad del entrante/saliente;
- Puesto;
- fecha/hora real;
- fotografía de rostro;
- resultado de validación IA.

SGI: Comando conserva la comparación:
- planificado;
- reasignado;
- ejecutado.

## IC Planificado e IC Ejecutado
- IC Planificado semanal: promedio aritmético del IC de todos los turnos publicados de la semana.
- IC Ejecutado semanal: promedio del IC asociado a la cobertura real confirmada por Relevo/Reasignación.
- Si un turno es cubierto por más de un AdS por una cobertura parcial, queda pendiente congelar el método de ponderación del IC ejecutado del turno.

## Alertas de Asignaciones — base
- turno requerido sin asignación;
- asignación a AdS con ID inferior al mínimo TIER;
- AdS con `Cambio requerido`;
- indisponibilidad superpuesta;
- intento de auto-relevo;
- turno publicado afectado por cambio comercial futuro;
- Reasignación pendiente;
- tardanza / cobertura parcial;
- falto confirmado.

Los colores/umbrales visuales específicos del IC quedan pendientes de definición.

## Cierre de decisiones de planificación
- Publicación con vacantes: permitida, con advertencia crítica.
- IC: valor numérico exacto; color por bandas configurables.
- IC Ejecutado con múltiples coberturas parciales: ponderación por minutos.
- Filtro de UI por Punto: permitido, incluyendo opción `Todos los Puntos`.
## Implementación UAT v0.2 — decisiones reforzadas
- SIC: RRHH es el System of Record del personal que pertenece a cada Compañía: Agentes de Seguridad, Escoltas de Seguridad y Supervisores de Seguridad.
- SIC: RRHH también es el System of Record de vacaciones, permisos médicos y demás indisponibilidades, incluyendo `desde` y `hasta`. SGI las consume como snapshots operacionales read-only.
- La matriz semanal muestra exactamente los Turnos definidos por SIC: COM; nunca asume únicamente Diurno/Nocturno. Un Puesto puede tener 1, 2, 3 o más turnos.
- Compatibilidad de rol, IC/habilidades y desempeño `ID vs TIER` NO bloquean una asignación. Solo generan advertencias visibles.
- Bloqueos reales: pertenencia válida a Compañía, estado laboral activo, indisponibilidad formal, solapamiento y no auto-relevo/descanso aplicable.
- Al publicar, el Plan Semanal conserva snapshot inmutable. Todo cambio posterior se registra como `Reasignación`, preservando original, nuevo Agente, motivo, autor y timestamps.
- UAT v0.2 implementa adapter `SIC: RRHH LOCAL`, adapter `SIC: COM LOCAL` para turnos y `SMC LOCAL` para ID con el mismo contrato previsto para integraciones reales.
## SGI-06 v0.2 — Cierre funcional de Publicación, Reasignación y Auditoría

### Estados del Plan
`BORRADOR → PUBLICADO → CERRADO`.

- `BORRADOR`: editable, con auditoría técnica.
- `PUBLICADO`: snapshot oficial inmutable; cambios posteriores son eventos explícitos.
- `CERRADO`: cierre automático al terminar la semana; planificación inmutable.

### Cobertura
Se conservan ambas métricas:
- **Cobertura Publicada**: calculada y almacenada al publicar.
- **Cobertura Actual**: recalculada sobre el estado vigente después de Reasignaciones y coberturas posteriores.

### Cambios posteriores
- Turno publicado vacío que luego se cubre → `POST_PUBLICATION_ASSIGNMENT` / UI **Asignación posterior a publicación**.
- Turno publicado con una persona y luego cambiado → `REASSIGNMENT_CREATED` / UI **Reasignación**.
- La cadena histórica nunca se sobrescribe.

### Snapshot de publicación
Incluye Turnos/version comercial y, por asignación: colaborador, rol, ID, IC, habilidades, indisponibilidades aplicables y versiones/fuentes disponibles.

### Aceptación v0.2
| Criterio | Estado de entrega |
|---|---|
| AC-ASG-01 Personal por Compañía desde RRHH LOCAL | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-02 Vacaciones y permiso médico bloquean por intervalo | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-03 IC/rol/ID-TIER/Cambio Requerido son warnings | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-04 Turnos exactos SIC: COM por Puesto | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-05 Drag & Drop + persistencia PostgreSQL | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-06 Publicación con vacantes permitida | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-07 Snapshot publicado inmutable | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-08 Asignación posterior a publicación diferenciada | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-09 Reasignación auditable | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-10 Cobertura publicada vs actual | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-11 Cierre automático semanal | IMPLEMENTADO / pendiente UAT local |
| AC-ASG-12 Copia múltiple parcial | DISEÑO CONGELADO / pendiente implementación UI completa |
| AC-ASG-13 Semáforo preventivo durante drag | DISEÑO CONGELADO / pendiente implementación UI completa |


## Incremento v0.4
- Guardado explícito de Borrador y checkpoint automático cada 15 min.
- Edición únicamente Coordinador/Asistente.
- Selección múltiple tipo hoja de cálculo: Copiar/Pegar/Eliminar.
- Clipboard conserva ciclo al cambiar de semana; cada Pegado revalida elegibilidad.
- Indicadores Vacaciones/Permiso Médico y rediseño de tarjeta de personal.
- TIER + horas/semana + IC/ID promedio por Puesto.
- Detalle IC con 8 habilidades y porcentaje de habilidades que cumplen.
- Ficha de Agente y ficha de Puesto.
- Nomenclatura temporal de turnos por mayoría horaria según 1–2 vs >2 turnos.
