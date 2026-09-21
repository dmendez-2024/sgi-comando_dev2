# SGI: Comando — CHANGELOG UAT v0.5

## Verticales
- SGI-00T Territorio v0.3
- SGI-06 Asignaciones v0.5

## Correcciones desde UAT v0.4.0.1

### Territorio
- Sustituido recurso visual incorrecto por el mapa operacional de Ecuador proporcionado como referencia UAT.
- Catálogo de Provincias/Estados identificado explícitamente como `CORE LOCAL` en UAT; CORE queda como SoR del catálogo territorial.
- Aumentada legibilidad de títulos, formularios, responsables, Provincias/Estados y acciones.
- Retroalimentación de éxito/error con toast de 5 segundos.
- Ajustes responsive de mapa, árbol territorial y paneles de configuración.

### Asignaciones
- Restaurado/fortalecido Drag & Drop para roles editores (`Coordinador` y `Asistente`).
- Click en persona del pool: abre ficha de Agente y simultáneamente evalúa la matriz.
- Sustituidos iconos genéricos de Vacaciones/Permiso Médico por los recursos visuales aprobados en UAT.
- Rediseñada tarjeta persona-turno para evitar superposición entre IC, foto, nombre, ID y warning.
- Matriz alineada: la cabecera `Puesto / Turno` ahora cubre correctamente ancho de Puesto + etiqueta de Turno.
- Implementada semántica de día calendario: turnos que cruzan medianoche se dividen visualmente en segmento de Día 1 y continuación de Día 2.
- Los segmentos de un Turno partido comparten el mismo `shift_occurrence_id`; asignar/eliminar/seleccionar/copiar/pegar en cualquiera opera sobre el Turno completo.
- Estrategia responsive: a resoluciones menores se preserva legibilidad y se usa scroll horizontal interno de calendario.

## No cambia
- Compatibilidad/ID/TIER siguen siendo warnings, no bloqueos.
- Solo Coordinador/Asistente editan y publican Asignaciones.
- Plan publicado permanece snapshot inmutable; cambios posteriores son eventos auditables.
