# SGI: Comando — ASI v0.7.4 UAT

## Cambio
Soporte controlado para la transición de Servicios entre Compañías iniciada por SER v0.9.1.

- `point.operational_transition_until` define hasta cuándo se conserva el turno heredado de la Compañía anterior.
- La semana de Asignaciones excluye turnos del Servicio que comienzan antes de ese corte.
- La API de asignación bloquea explícitamente una asignación que intente cubrir un turno anterior al corte.
- Cobertura y publicación usan la misma regla de elegibilidad.
- No modifica el histórico del turno retenido ni de las asignaciones liberadas.
