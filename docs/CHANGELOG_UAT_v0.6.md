# SGI: Comando — CHANGELOG UAT v0.6

## Verticales
- SGI-00T Territorio v0.3
- SGI-06 Asignaciones v0.6

## Cambios desde UAT v0.5

### Asignaciones
- Reorganizada la estructura general de la pantalla para ganar espacio vertical útil y compactar la cabecera operativa.
- Eliminado el scroll vertical interno de la matriz de turnos/asignaciones; ahora el desplazamiento vertical lo realiza la página completa.
- Mantenido el scroll vertical independiente del panel de `Personal disponible`.
- Implementado `freeze pane` operativo: cabecera general sticky, panel principal sticky y cabecera de fechas sticky.
- Extendida la banda Cliente / Punto a lo largo de toda la línea de días del timeline.
- Limitado el scroll horizontal a una ventana fija de 120 días hacia atrás y 120 días hacia adelante desde hoy.
- Ajustado el formato del nombre en la tarjeta de asignación a `Inicial.Apellido` (ej. `C.Rojas`, `D.Zambrano`).
- Retirado el icono triangular de advertencia de la tarjeta compacta para liberar espacio.

## No cambia
- Drag & Drop queda operativo como base actual y continúa en stand by para futuros ajustes.
- Compatibilidad/ID/TIER siguen siendo warnings, no bloqueos.
- Solo Coordinador/Asistente editan y publican Asignaciones.
