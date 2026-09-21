# ASI v0.5 — Criterios de aceptación

## Criterios prioritarios de la especificación UX
- [ ] **ASI-AC-034 / A1:** turno 18:00–06:00 aparece como una sola celda del día de inicio con `+1`.
- [ ] **ASI-AC-035 / A2:** Ctrl+C/V opera sobre rangos y Ctrl+Z revierte una operación masiva como un paso lógico.
- [ ] **ASI-AC-036 / A3:** fill handle replica una celda y repite literalmente un patrón multi-celda.
- [ ] **ASI-AC-037 / A4:** Copiar Día permite elegir origen visualmente y luego destino.
- [ ] **ASI-AC-038 / A5:** Copiar Ciclo usa la longitud recibida desde SIC: COM y no asume siete días.
- [ ] **ASI-AC-039 / A6:** un ciclo 6-2 de 8 días se copia aunque cruce dos semanas calendario.
- [ ] **ASI-AC-040 / A7:** Copiar Selección conserva dimensiones y posiciones relativas.
- [ ] **ASI-AC-041 / A8:** existe scroll horizontal multi-semana y Puesto/Turno + fechas permanecen congelados por eje.
- [ ] **ASI-AC-042 / A9:** fechas pasadas son visibles pero no editables.
- [ ] **ASI-AC-043 / A10:** Personal disponible puede colapsarse y usa formato compacto.
- [ ] **ASI-AC-044 / A11:** una operación masiva devuelve resultado agregado válidas / revisar / no permitidas.
- [ ] **ASI-AC-045 / A12:** Puestos con 1, 2, 3 o más Turnos generan sus filas dinámicamente.

## Criterios adicionales v0.4
- [ ] **ASI-AC-046:** copiar Día/Ciclo preserva celdas vacías, vaciando el destino correspondiente cuando el origen representa descanso.
- [ ] **ASI-AC-047:** pegado TSV desde Excel reconoce nombre completo o employeeId y rechaza valores no resolubles sin aplicar parcialmente el bloque.
- [ ] **ASI-AC-048:** overlap/auto-relevo se detecta aunque las dos asignaciones estén en planes semanales distintos.
- [ ] **ASI-AC-049:** Sidebar muestra ASI v0.4 UAT, TER v1.0 FROZEN y COM v1.0 FROZEN.
- [ ] **ASI-AC-050:** TER/COM no presentan regresiones funcionales.

## Criterios adicionales v0.5
- [ ] **ASI-AC-051:** Resumen semanal muestra, en orden, ID promedio, IC promedio, turnos asignados, turnos sin asignar y porcentaje de turnos asignados.
- [ ] **ASI-AC-052:** Preview Drag & Drop muestra borde verde para ID+compatibilidad cumplidos, amarillo para brecha no bloqueante y rojo ante blocker; rojo impide el drop.
- [ ] **ASI-AC-053:** Tarjeta compacta no muestra foto, dial de IC ni texto `Asignado por`; muestra nombre, ID y CP.
- [ ] **ASI-AC-054:** Click y soltar en tarjeta abre detalle con resumen de persona, evolución ID, Compatibilidad del Puesto e historial de autoría; drag no dispara el modal.
- [ ] **ASI-AC-055:** ASI v0.5 no introduce cambios de backend ni base de datos.
