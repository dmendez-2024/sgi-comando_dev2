# ASI — Decision Log

**Vertical:** ASI — Asignaciones  
**Versión:** v0.5  
**Estado:** CANDIDATO UAT

## Decisiones heredadas
ASI-DEC-001 a ASI-DEC-022 continúan vigentes salvo la representación visual fragmentada del turno nocturno de ASI-DEC-017, que queda supersedida por ASI-DEC-023.

## ASI-DEC-023 — Una celda representa un Turno completo
Un `ShiftOccurrence` que cruza medianoche se muestra una sola vez en la fecha de inicio. La celda muestra hora inicio–fin y `+1`. No se divide en dos días visuales.

## ASI-DEC-024 — Timeline continuo, semana como navegación
La semana seleccionada define el plan/resumen central y posiciona el viewport; la matriz puede mostrar y operar un horizonte multi-semana.

## ASI-DEC-025 — Pasado visible pero no editable
Fechas anteriores al día operacional actual se mantienen disponibles como referencia, pero no admiten asignar, pegar, vaciar, rellenar ni editar.

## ASI-DEC-026 — Selección siempre activa
No existe un modo separado “Seleccionar celdas”. Click selecciona; Shift expande un rectángulo; Ctrl/Meta agrega o quita; arrastre expande la selección.

## ASI-DEC-027 — Operaciones masivas reversibles
En BORRADOR, copiar/pegar, vaciar y rellenar se registran como una operación lógica reversible mediante Undo/Redo.

## ASI-DEC-028 — Clipboard 2D e interoperabilidad TSV
La selección se copia como bloque filas × fechas. Se soporta TSV para interoperabilidad con Excel. Pegado externo resuelve nombres completos o IDs de colaborador.

## ASI-DEC-029 — Fill handle repite patrón literalmente
El handle de relleno usa el bloque seleccionado como patrón y lo repite por módulo de filas/columnas, sin inferir secuencias de negocio.

## ASI-DEC-030 — Copiar Día / Ciclo / Selección
El menú de copia reconoce tres unidades semánticas. Día replica la columna en el alcance visible; Selección replica el bloque rectangular; Ciclo replica el patrón operacional del Puesto.

## ASI-DEC-031 — Ciclo proviene de SIC: COM
`cycle_length_days` se consume del contrato Formato + Rotación de SIC: COM. ASI no puede asumir `7 días = ciclo`. Un ciclo 6-2 tiene 8 días y puede cruzar semanas calendario.

## ASI-DEC-032 — Vacíos forman parte del patrón
Copiar Día, Ciclo, Selección o Fill puede contener celdas vacías. Una celda vacía del origen vacía la correspondiente celda destino en BORRADOR; esto preserva descansos y estructura del patrón.

## ASI-DEC-033 — Snapshot local no cambia el SoR
`post_planning_cycle_snapshot` es una copia de lectura/auditoría del dato recibido de SIC: COM. SIC: COM sigue siendo SoR de Formato, Rotación y longitud de ciclo.

## ASI-DEC-034 — Validación temporal cruza AssignmentPlan
Overlap y auto-relevo deben considerar todas las asignaciones operacionales relevantes del colaborador dentro del tenant, porque un ciclo puede cruzar el límite domingo/lunes de dos planes semanales.

## ASI-DEC-035 — Turnos dinámicos
La grilla genera filas a partir de los `shiftCode` efectivamente recibidos para el Puesto. No se codifica Diurno/Nocturno ni una cardinalidad fija.

## ASI-DEC-036 — Protección de verticales congeladas
TER v1.0 FROZEN y COM v1.0 FROZEN no se modifican funcionalmente en ASI v0.4.

## ASI-DEC-037 — Resumen semanal v0.5
El resumen semanal presenta exactamente cinco métricas operacionales, en este orden: ID promedio, IC promedio, turnos asignados, turnos sin asignar y porcentaje de turnos asignados.

## ASI-DEC-038 — Semáforo de preview Drag & Drop
El preview de destino usa GREEN cuando cumple simultáneamente ID mínimo del TIER y compatibilidad 100%; AMBER cuando existe una brecha no bloqueante; RED cuando existe cualquier blocker de asignación. RED no ejecuta el drop.

## ASI-DEC-039 — CP en tarjeta
La tarjeta de asignación usa `CP` como abreviatura visible de **Compatibilidad del Puesto** y elimina fotografía, dial de IC y auditoría inline para reducir densidad visual.

## ASI-DEC-040 — Auditoría bajo demanda
La autoría de una asignación no ocupa espacio permanente en la grilla. Click y soltar sobre la tarjeta abre el detalle con resumen de persona, evolución ID, Compatibilidad del Puesto e historial de la asignación puntual.
