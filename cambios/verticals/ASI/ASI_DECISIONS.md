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

---

# Apertura ASI v0.7 — Transferencias entre Compañías

**Fecha:** 2026-09-19  
**Estado:** UAT

## ASI-DEC-041 — SIC: RRHH conserva el SoR persona–Compañía
La relación laboral entre colaborador y Compañía pertenece a **SIC: RRHH**. SGI: Comando orquesta la solicitud operacional de transferencia y mantiene trazabilidad, pero no crea un maestro paralelo de relación laboral.

## ASI-DEC-042 — Origen inicia; destino acepta
Nadie puede “jalar” un colaborador desde otra Compañía. La Compañía de origen inicia la transferencia de una persona que actualmente le pertenece y la Compañía destino debe **Aceptar transferencia** o **Rechazar transferencia**.

## ASI-DEC-043 — Estados de transferencia
Estados canónicos:
1. `PENDING_ACCEPTANCE` — pendiente de aceptación.
2. `ACCEPTED_PENDING_EFFECTIVE` — aceptada pero existe un turno actual que debe terminar.
3. `EFFECTIVE` — transferencia efectiva.
4. `REJECTED` — rechazada por destino.
5. `CANCELLED` — anulada por origen mientras estaba pendiente.

Solo `PENDING_ACCEPTANCE` puede anularse por el origen. Una transferencia aceptada es una transacción irreversible/no editable; una transferencia futura nueva puede volver a mover al colaborador.

## ASI-DEC-044 — Asignaciones futuras se liberan al enviar
Al iniciar la transferencia se liberan/cancelan inmediatamente todas las asignaciones futuras del colaborador en la Compañía origen, aun antes de la aceptación del destino.

- Histórico ejecutado: no cambia.
- Turno actualmente en ejecución: no cambia.
- Transferencias pendientes bloquean nuevas asignaciones futuras.
- Si la transferencia es rechazada o anulada, las asignaciones liberadas **no se reconstruyen automáticamente**.

## ASI-DEC-045 — Momento efectivo
Si el destino acepta mientras existe un turno en ejecución, la transferencia se registra como aceptada pero se hace efectiva al finalizar dicho turno. Si no existe turno en ejecución, se hace efectiva inmediatamente.

## ASI-DEC-046 — Motivo y Observaciones
Toda transferencia requiere:
- Motivo obligatorio desde catálogo.
- Observaciones obligatorias, máximo 500 caracteres.

Catálogo inicial:
- Necesidad operativa.
- Reestructuración.
- Promoción / cambio de función.
- Solicitud del colaborador.
- Desempeño.
- Medida disciplinaria.
- Otro.

## ASI-DEC-047 — Visibilidad en Personal disponible
El filtro de estado de `Personal disponible` incorpora:
- Transferencias salientes.
- Transferencias entrantes.

Una persona con transferencia abierta deja de ser `Disponible`. En origen se marca `Saliente`; en destino se marca `Entrante`.

## ASI-DEC-048 — Acciones canónicas
Término funcional: **Transferencia**, no Asignación.

Acciones:
- `Transferir a otra Compañía`.
- `Aceptar transferencia`.
- `Rechazar transferencia`.
- `Anular transferencia`.

## ASI-DEC-049 — Permisos de Compañías normales
En una Compañía distinta de Kaibil, únicamente:
- Coordinador de Compañía.
- Asistente de Coordinación.

pueden iniciar transferencias de personal de su propia Compañía y aceptar/rechazar transferencias dirigidas a esa Compañía.

## ASI-DEC-050 — Permisos de Kaibil
Para personal de Kaibil:
- Presidencia: ámbito nacional.
- Director Nacional: ámbito nacional.
- Director Zonal: limitado a sus Zonas.
- Jefe Regional: limitado a sus Regiones.

Para transferencias hacia/desde Kaibil, Director Zonal y Jefe Regional se validan contra el territorio de la Compañía contraparte.

## ASI-DEC-051 — Una sola transferencia abierta
Un colaborador no puede tener dos transferencias simultáneas en estado abierto.

## ASI-DEC-052 — Trazabilidad e integración RRHH
Toda transferencia registra origen, destino, motivo, observaciones, iniciador, fecha, decisión, actor de decisión, fecha efectiva y número de asignaciones futuras liberadas. La efectivización genera un evento de integración para SIC: RRHH. En UAT el snapshot local se actualiza como adaptador de una confirmación simulada de RRHH.

## ASI-DEC-053 — Coordinación futura
Se reserva una futura página **Coordinación**, ubicada en navegación entre Servicios y Asignaciones, para crear Puestos de Coordinación (p. ej. ruta de supervisión, turno de monitoreo). Esta función NO forma parte de ASI v0.7.


## ASI-DEC-054 — Kaibil como contexto inicial de liderazgo
En Asignaciones, los perfiles **Presidencia, Director Nacional, Director Zonal y Jefe Regional** abren por defecto la Compañía **Kaibil** cuando esta se encuentra dentro de su catálogo visible. Kaibil es el contexto inicial, no una restricción de navegación.

## ASI-DEC-055 — Selector de Compañía persistente para liderazgo
Para Presidencia, Director Nacional, Director Zonal y Jefe Regional, el selector de Compañía debe permanecer visible en todo momento, incluso cuando la Compañía seleccionada sea Kaibil. Las Compañías visibles continúan limitadas por el ámbito territorial entregado por backend.

## ASI-DEC-056 — Confirmaciones de transferencia sin diálogos nativos
Las acciones de transferencia trabajadas en ASI deben usar componentes visuales propios de SGI: **Enviar, Aceptar, Rechazar y Anular transferencia**. No se usa `window.confirm` para estas acciones. El modal debe mostrar claramente impacto, origen/destino y errores de backend.


## ASI-DEC-057 — Alta operacional desde SIC: RRHH
Para que una persona ingrese a SGI: Comando desde **SIC: RRHH**, SIC: RRHH debe entregar explícitamente su adscripción a **Seguridad Física (SF) + Compañía**. SIC: RRHH continúa siendo System of Record de esta relación laboral inicial. SGI consume esa membresía; no infiere ni crea de cero la adscripción SF/Compañía. Las transferencias posteriores entre Compañías se orquestan desde SGI conforme ASI-DEC-041..056 y se sincronizan de vuelta a SIC: RRHH.

## ASI-DEC-058 — Guardia de transición cuando SER mueve un Servicio
Cuando SER retira un Servicio hacia Kaibil y conserva un turno actualmente en ejecución, el Punto puede definir `operational_transition_until`. Si el Servicio es reasignado a una nueva Compañía antes de ese corte, ASI no debe permitir cobertura nueva sobre turnos que comiencen antes de dicha fecha/hora. La misma regla debe aplicarse en vista semanal, asignación manual, cobertura y publicación para evitar doble cobertura mientras finaliza el turno heredado de la Compañía anterior.
