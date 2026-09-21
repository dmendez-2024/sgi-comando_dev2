# SGI: Comando — CHANGELOG UAT v0.6.5

## Verticales
- SGI-00T Territorio v0.3
- SGI-06 Asignaciones v0.6.5

## Performance / interacción

### Objetivo
Reducir latencia perceptible en selección, Drag & Drop, Copiar/Pegar y navegación temporal sin cambiar la UI aprobada en v0.6.4.

### Cambios
- Virtualización horizontal del timeline: el rango lógico sigue siendo ±120 días, pero el DOM renderiza una ventana de 17 días con buffer.
- Carga de semanas limitada a la semana activa + semanas visibles/buffer, eliminando la carga simultánea de ~35 semanas.
- Evaluación de compatibilidad de una persona limitada a las semanas cargadas y cacheada por persona/plan.
- Inicio de Drag & Drop desacoplado de la evaluación: el payload de drag se prepara antes de disparar cálculos de compatibilidad.
- Eliminado el `mousedown` que seleccionaba/evaluaba persona antes de iniciar drag.
- Copiar interno queda listo inmediatamente; la escritura al clipboard del navegador se realiza sin bloquear la UI.
- Pegar/Drop actualizan localmente la asignación y refrescan en segundo plano únicamente las semanas afectadas.
- Botón `Hoy` ahora siempre reposiciona el timeline en la fecha de hoy, incluso si ya está seleccionada la semana actual.
- Navegación semanal aterriza el scroll horizontal directamente en el lunes de la semana seleccionada.

### Gate UAT sugerido
- Selección de celda: respuesta visual objetivo <100 ms.
- Inicio hold & drag: objetivo <100 ms.
- Copiar: objetivo <100 ms.
- Hoy: reposicionamiento visible inmediato.
- Pegar una celda: feedback visual sin esperar recarga completa del rango temporal.
