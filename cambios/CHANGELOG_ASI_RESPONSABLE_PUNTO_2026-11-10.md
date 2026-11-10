# ASI — Responsable de Punto por turno/relevo

Regla confirmada: elección aleatoria con prioridad a quienes hayan tenido menos designaciones en el Punto. ID de SMC, IC y promedios quedan fuera de la selección. La duración acompaña al turno/relevo; no constituye un cargo nuevo en RRHH.

## Cambios

- Publicación: genera y conserva responsables planificados por intervalo del Punto e incluye el resultado en el snapshot.
- Reasignación: conserva al responsable si sigue elegible; sortea reemplazo si deja de estarlo. Transferencias e inactivaciones que liberan asignaciones futuras actualizan también la responsabilidad.
- Historial: eventos con candidatos, conteos anteriores, actor e intervalo; conteos persistentes entre semanas, por Punto e Instancia-País.
- Asignaciones: panel desplegable «Responsables de Punto por turno/relevo», disponible también en lectura, filtrado por los Puntos visibles.
- Base de datos: migración `V132__point_responsibility.sql`.
- Referencia funcional: `docs/verticals/ASI/ASI_DECISIONS.md`, ASI-DEC-059.

## Criterios de aceptación

1. Con cuatro agentes siempre elegibles, cada uno es elegido una vez antes de repetir y los conteos se mantienen equilibrados al continuar las rondas.
2. Los desempates son aleatorios; no favorecen al ID mayor.
3. Un agente no disponible no bloquea la elección; con un solo elegible puede repetirse y sin elegibles se informa expresamente.
4. Refrescar o consultar no vuelve a sortear; repetir una publicación tampoco.
5. Un turno nocturno conserva sus fechas/horas reales. Los horarios escalonados delimitan intervalos sin elegir agentes fuera de su asignación.
6. Una indisponibilidad/reasignación obliga a reemplazar a un elegido no elegible en la siguiente escritura; el historial y el snapshot publicado se conservan.
7. La vista identifica la designación como planificada. El Relevo mantiene la fuente de verdad de asistencia real y su validación independiente.

No se migran ni resortean retroactivamente planes publicados. Esta entrega no cambia el procesamiento de asistencia del Relevo.

## Validación realizada

- Frontend: `npm run build`, correcto.
- Backend: 13 pruebas seleccionadas, sin fallos ni errores (`PointResponsibilityServiceTest`, `PointResponsibilityPolicyTest`, `AssignmentCoverageCountTest`, `OperatorAssignmentWindowTest`).
- Integración en PostgreSQL de pruebas: migración V132, persistencia/idempotencia, reemplazo por indisponibilidad, continuidad entre semanas y liberación por inactivación.
- `git diff --check`, correcto.
