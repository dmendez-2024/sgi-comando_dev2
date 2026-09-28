# ASI v0.5 — Guía UAT

## Arranque
Usar el flujo UAT estándar del repositorio. v0.5 no agrega migraciones ni cambios backend: debe levantar sobre el mismo esquema de ASI v0.4.

## UAT 1 — Resumen semanal
1. Abrir Asignaciones.
2. Confirmar exactamente este orden de métricas: **ID promedio → IC promedio → turnos asignados → turnos sin asignar → % de turnos asignados**.
3. Confirmar que ya no se usa el texto corto `sin asignar`.
4. Confirmar que la última tarjeta muestra el porcentaje y el texto `de turnos asignados`.
5. Cambiar filtro de Cliente y confirmar que ID/IC/promedios y conteos se recalculan sobre el alcance visible.

## UAT 2 — Preview Drag & Drop
1. Arrastrar un colaborador que cumpla el ID mínimo del TIER y tenga compatibilidad 100%: el borde del destino debe ser **verde**.
2. Arrastrar un colaborador con brecha de ID y/o compatibilidad, sin blocker: el borde debe ser **amarillo** y el drop debe seguir permitido.
3. Arrastrar un colaborador con permiso médico, vacaciones, solapamiento, auto-relevo u otro blocker: el borde debe ser **rojo**.
4. Intentar soltar sobre un destino rojo: la UI no debe ejecutar la asignación y el backend continúa siendo la segunda barrera de bloqueo.

## UAT 3 — Tarjetas compactas
1. Confirmar que la tarjeta ya no muestra fotografía.
2. Confirmar que ya no muestra círculo/dial de IC.
3. Confirmar que ya no muestra `Asignado por X` dentro de la tarjeta.
4. Confirmar formato compacto: indicador de estado + nombre + `ID x.x · CP yy%`.
5. Confirmar tarjeta verde cuando cumple ID + CP 100% y amarilla cuando existe brecha no bloqueante.
6. Confirmar que el botón de remover en BORRADOR sigue funcionando.

## UAT 4 — Click y soltar en tarjeta
1. Hacer click normal y soltar sobre una tarjeta, sin arrastrar.
2. Debe abrirse la ventana de detalle de asignación.
3. Confirmar cuatro bloques: **Resumen de la persona**, **Evolución del ID**, **Compatibilidad del Puesto**, **Historial de esta asignación**.
4. En Compatibilidad del Puesto confirmar CP y comparación de habilidades actual/requerido.
5. En Historial confirmar quién realizó la asignación y fecha; si existe reasignación, mostrar también autor, fecha y motivo.
6. Arrastrar la tarjeta a otro turno y confirmar que el gesto de drag no abre accidentalmente la ventana de detalle.

## Regresión ASI v0.4
- Turno nocturno completo en una sola celda con `+1`.
- Timeline multi-semana y freeze panes.
- Fechas pasadas read-only.
- Selección rectangular, Ctrl+C/V, TSV desde Excel, Delete, Undo/Redo.
- Fill handle.
- Copiar Día / Ciclo / Selección, incluyendo vacíos/descansos.
- Ciclos 6-2 = 8 días y 5-2 = 7 días.
- Validación cross-week.
- Turnos dinámicos por Puesto.
- Panel de Personal disponible compacto/colapsable.
- Publicación, filtros, permisos por rol, ficha de persona y detalle de Puesto.

## Puente de identidad SIC:RRHH/DHO — V29, pendiente de cierre end-to-end

- Evento legacy con `employeeId` numérico conserva compatibilidad y deriva el UUID operacional esperado.
- Evento con `personaId` registra `employee_operational_snapshot.persona_id` sin cambiar el UUID existente.
- Si `employeeId` y `personaId` discrepan, el evento se rechaza sin modificar referencias.
- Persona ya vinculada a otro UUID se rechaza hasta una migración controlada.
- Históricos permanecen con `persona_id=NULL` hasta sincronización válida; no se backfillean por defecto.
- Solo probar `canonicalEmployeeId` con autorización, migración de referencias y entorno de prueba identificados.

**Estado:** casos añadidos a partir de `cambios/CHANGELOG_DME_RRHH_IDENTIDAD_2026-09-24.md`; el registro indica que falta redesplegar DHO para probar el flujo desde la pantalla. No marcar end-to-end PASS todavía.
