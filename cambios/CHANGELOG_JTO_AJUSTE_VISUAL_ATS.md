# CHANGELOG_JTO_AJUSTE_VISUAL_ATS

## Ajustes en Servicios → ATS

- Las siete tarjetas ATS permanecen en una sola fila en escritorio.
- El contenido de las tarjetas usa tamaños fluidos, límites de ancho y ajuste de texto para evitar desbordamientos.
- En anchos menores a 1180 px, las tarjetas utilizan una cuadrícula automática con un ancho mínimo adaptable.
- Los controles del visor del plano (`+`, `−`, `Ajustar` y `Capas`) permanecen en una sola fila horizontal; en anchos reducidos pueden desplazarse horizontalmente sin saltar de línea.
- Se fuerza la dirección horizontal del grupo de controles para evitar que las reglas responsive del encabezado los apilen verticalmente.
- La composición principal se reorganiza en dos columnas: el visor y el historial quedan en la columna izquierda; la vinculación operacional y la lista de verificación quedan en la columna derecha.
- El botón “Ver historial” desplaza suavemente la pantalla hasta la sección “Historial ATS” y enfoca el bloque para facilitar su navegación; permanece deshabilitado cuando no existen revisiones.
- “Validar vínculos” consulta las configuraciones de Puestos y los protocolos de Patrullas del Punto, comprueba la referencia al ATS vigente y las coordenadas de Puestos e Hitos, y actualiza estados, cobertura, conflictos y pendientes visibles.
- La sección “Vinculación operacional” ya no construye filas, rutas ni hitos simulados: al abrir un ATS y al ejecutar la validación consulta la información real del Punto; mientras la consulta está pendiente muestra un estado explícito y no presenta resultados ficticios.
