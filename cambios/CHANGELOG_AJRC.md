# Changelog AJRC — SGI: Comando

Registro de cambios solicitados para SGI: Comando gestionados por AJRC.

## SER / Consola — detalle solo en panel emergente — 2026-09-25

- Se elimina el panel fijo de detalle a la derecha; la tabla ocupa el ancho disponible.
- Tanto el clic en una fila como **Ver** abren el mismo panel emergente de solo lectura.
- El panel lleva el título “Detalle del caso operativo” y el botón de pie “Ver detalle completo” sin acción asociada; se cierra con X o clic en el fondo.
- **Archivos:** `frontend/src/pages/ConsolaMonitor.tsx`, `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** build de producción correcto; frontend reiniciado y bundle servido por `localhost:5173` (HTTP 200). El título y botón aparecen en el bundle; verificación visual pendiente.

## SER / Consignas — panel emergente de consulta — 2026-09-25

- Al seleccionar una fila, usar **Ver** o activar **Ver detalle completo**, se abre el mismo panel lateral emergente con la ejecución seleccionada.
- El emergente muestra datos de operación, estado, prioridad, resultado, progreso y trazabilidad en modo solo lectura. Permite cerrar mediante botón o clic en el fondo.
- Ajuste aprobado: se elimina el panel fijo “Detalle de ejecución” de la derecha; toda la información de la ejecución se consulta exclusivamente en el emergente.
- El emergente incluye el título “Detalle de ejecución” y el botón de pie “Ver detalle completo”, presentado como elemento visual sin acción asociada.
- **Archivos:** `frontend/src/pages/ConsignasExecution.tsx`, `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** build de producción correcto; frontend reiniciado y bundle servido por `localhost:5173` (HTTP 200). El título y el botón aparecen en el bundle; verificación visual pendiente.

## SER / Novedades — título y tarjetas KPI — 2026-09-24

- Se fija el título principal de Novedades en 26 px.
- Las tarjetas KPI adoptan la estructura `coord-kpis` de Coordinación: `article`, icono en `span` con tono, y bloque de texto con `small`, `strong` y `em`. Se conservan las seis métricas y sus cálculos.
- **Archivos:** `frontend/src/pages/NovedadesExecution.tsx`, `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** pendiente de revisión visual; build no ejecutado por falta de `frontend/node_modules`.

## SER / Novedades — tarjetas de métricas — 2026-09-24

- Se compactan las tarjetas KPI, moderando altura, padding, radio y sombra, y se ajusta la jerarquía tipográfica para acercarlas al estilo de las tarjetas de Coordinación.
- Se conservan las seis métricas, sus datos, colores e iconos.
- **Archivo:** `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** pendiente de revisión visual; build no ejecutado por falta de `frontend/node_modules`.

## SER / Consignas — estilo del editor de Evidencias — 2026-09-24

- **Solicitud:** homologar los controles del editor de Evidencias de Consignas con el estilo de la página.
- Se uniforman etiquetas, campos de texto/selección, checkbox, tarjeta de foto estándar y acciones Guardar/Eliminar; en anchos estrechos, lista y editor se apilan y los controles no desbordan.
- El botón “Agregar evidencia” se presenta como acción primaria azul, con icono, alineación, dimensiones y estados de interacción coherentes con los botones de la página.
- **Archivo:** `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** pendiente de build y verificación visual.

## SER / Patrullas — estilo de controles de programación — 2026-09-24

- **Solicitud aprobada:** homologar los campos “Ejecuciones requeridas” y “Desde/Hasta” con los controles visuales de Patrulla.
- Se alinean etiquetas e inputs, se aplica borde, altura, tipografía y estado de foco del formulario; los campos horarios pasan a dos columnas y se apilan en pantallas muy estrechas.
- **Archivo:** `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** pendiente de build y verificación visual.

## SER / Bitácora — flujo de Acreditación en una sola fila — 2026-09-24

- **Aprobación:** aprobada por el usuario.
- Se conserva el flujo horizontal de cuatro pasos; en contenedores estrechos se habilita desplazamiento horizontal interno en lugar de apilar los pasos o recortarlos.
- El texto de las tarjetas puede envolverse dentro de cada paso para permanecer visible.
- **Archivo:** `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** pendiente de build y verificación visual.

## COO / separar Ver y Editar Puestos — 2026-09-23

- **Aprobación:** aprobada por el usuario.
- La acción **Ver** abre el Puesto en modo solo lectura; **Editar** abre el formulario editable.
- En modo Ver se ocultan las acciones de guardado del Puesto y de creación/edición/publicación/versionado de la Ruta; también se bloquean cambios a los datos, jornada y secuencia de Puntos.
- El enlace a la Ruta desde el listado abre el Puesto en modo solo lectura.
- **Archivos:** `frontend/src/pages/Coordination.tsx`; este registro en `cambios/CHANGELOG_AJRC.md`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** pendiente de recarga y prueba en la instancia local.

## SER / Bitácora — ajuste visual — 2026-09-22

- **Versión/RC:** pendiente de asignar.
- **Baseline:** pendiente de confirmar.
- **Responsable:** pendiente de especificar.
- **Clasificación:** PERMITIDO — responsive/UI polish.
- **Solicitud:** centrar visualmente el texto `No aplica al puesto` con la etiqueta `Inactivo` en la columna Estado de Protocolos del Punto.
- **Ajuste adicional:** alinear el carrusel de pasos con la franja de Acreditaciones, incluyendo sus márgenes laterales y la barra de desplazamiento horizontal.

### Archivos modificados

- `frontend/src/styles.css`
- `frontend/src/pages/BitacoraConfig.tsx`

### Base de datos y migraciones

- Sin cambios de base de datos.
- Sin migraciones Flyway nuevas.

### Integraciones y SITC

- Sin cambios de interconexiones/SITC.
- Sin impacto multi-sistema documentado.

### Validación

- `git diff --check`: PASS.
- Build frontend: pendiente de UAT local; `frontend/node_modules` no está disponible en el entorno actual.
- UAT Bitácora: pendiente.
- Regresión visual: pendiente de verificación en el navegador.

### Riesgos y pendientes

- Confirmar la baseline y la versión/RC antes de cerrar la entrega.
- Registrar la reapertura/versionado de SER si la corrección se incorpora a una vertical congelada.

## SER / Bitácora — panel de Puestos contraíble horizontalmente — 2026-09-22

- **Clasificación:** PERMITIDO — microinteracción UX / ajuste visual dentro de SER.
- **Solicitud aprobada:** contraer “Puestos del Punto” horizontalmente a una barra angosta con un botón para expandir; usar el espacio liberado para “Protocolos del Punto”.
- **Comportamiento:** se conserva la selección y la búsqueda; no modifica protocolos ni datos. En escritorio, el ancho liberado se asigna a “Protocolos del Punto” y se conserva el ancho del panel de detalle.
- **Archivos de código:** `frontend/src/pages/BitacoraConfig.tsx`, `frontend/src/styles.css`.
- **Documentación adicional:** no se modifica `docs/CHANGELOG.md` ni UAT; este registro queda en `cambios/CHANGELOG_AJRC.md`.
- **Validación:** `git diff --check` PASS; build/UAT visual pendiente porque no hay dependencias frontend instaladas en el entorno.

## SER / Bitácora — alineación del carrusel de pasos — 2026-09-22

- Reducido el margen horizontal de las pestañas y alineado el área de desplazamiento con el carrusel.
- Se conserva el tamaño del panel y el contenido de los pasos.
- **Archivo:** `frontend/src/styles.css`.
- **Validación:** `git diff --check` PASS; validación visual en navegador pendiente.

## SER / Bitácora — redistribución de encabezado y Acreditaciones — 2026-09-22

- Título y estado del Protocolo ocupan la parte superior; las acciones quedan en una fila independiente alineadas a la derecha.
- Acreditaciones se presenta en dos niveles: título/ayuda y fila desplazable de opciones con su scrollbar.
- Sin cambios de lógica, datos ni dimensiones fijas del panel; su altura natural aumenta para acomodar las filas propuestas.
- **Archivo:** `frontend/src/styles.css`.
- **Validación:** `git diff --check` PASS; build/UAT visual pendiente porque no hay dependencias frontend instaladas.
- **Corrección posterior:** se fuerza la fila de acciones en dirección horizontal para evitar que una regla compartida de encabezados SER apile verticalmente los botones; en pantallas angostas pueden envolver.

## SER / Bitácora — contención del desborde lateral — 2026-09-22

- Se limita el ancho mínimo de la vista de detalle, del cuerpo de pasos y de las columnas del formulario para que no empujen el contenido fuera de los bordes izquierdo y derecho.
- Los controles del formulario pueden reducirse al ancho disponible; en pantallas angostas la definición usa una sola columna.
- Se conservan los desplazamientos horizontales propios del carrusel de pasos y de Acreditaciones.
- **Archivo de código:** `frontend/src/styles.css`.
- **Validación:** `git diff --check` pendiente; build/UAT visual pendiente porque no hay dependencias frontend instaladas en el entorno.

### Ajuste del carrusel — visibilidad de pestañas en ambos extremos

- Se añade margen interior al carrusel y separación de seguridad a cada pestaña, evitando que el texto toque o quede recortado por los bordes.
- Al cambiar de paso, el carrusel desplaza la pestaña activa a una posición visible; se preserva el desplazamiento horizontal.
- **Archivos:** `frontend/src/pages/BitacoraConfig.tsx`, `frontend/src/styles.css`.
- **Corrección:** el ajuste automático calcula los límites reales de la pestaña respecto del viewport del carrusel y mantiene 12 px de separación visible para evitar recorte/pegado del texto en ambos extremos.
- **Corrección adicional:** el ajuste se ejecuta tras pintar el carrusel y también al cargar/cambiar el protocolo; antes podía correr cuando las pestañas aún no estaban montadas.
- **Alineación confirmada:** el fondo y el borde inferior ocupan todo el ancho; el viewport desplazable y su scrollbar quedan dentro de un rail con 16 px de margen a izquierda y derecha, alineados con Acreditaciones.

## SER / Patrullas — homologación visual de panel y carrusel — 2026-09-22

- **Reapertura autorizada:** se retoma SER v0.10.10, que consta como congelada, para esta continuación de cambios visuales. No se asigna aquí número de release; la documentación del proyecto consultada no especifica cuál corresponde.
- Se agrega contraer/expandir horizontalmente “Puestos del Punto”, conservando la selección, búsqueda y contenido.
- Se aplica al carrusel de pasos el rail a ancho completo y el viewport/scrollbar con inset horizontal de 16 px, alineado con “Patrullas”. La pestaña activa se mantiene visible al navegar.
- Sin cambios previstos en reglas operativas, API, datos ni esquema.
- **Archivos de código:** `frontend/src/pages/PatrolConfig.tsx`; estilos compartidos de Bitácora en `frontend/src/styles.css` ya cubren el rail y el comportamiento de panel contraído.
- **Validación:** pendiente de `git diff --check`, build y UAT visual; `frontend/node_modules` no está instalado en este entorno.
- **Ajuste visual de detalle:** se fija explícitamente en Patrullas el padding lateral de 16 px del rail del carrusel y se centra “No aplica al puesto” bajo el estado; fondo y borde permanecen a ancho completo.
- **Reglas del Hito:** se homologan jerarquía del encabezado, alineación de campos, controles de regla, coordenadas y bloque de Foto estándar al estilo visual de Patrullas. Es solo presentación: no cambia reglas, guardado, eliminación, carga de foto ni los estados deshabilitados de una versión publicada.
- **Corrección de desborde:** el texto de las opciones de Reglas del Hito reduce su tamaño a 11 px y puede ajustarse dentro de cada opción; el checkbox conserva su tamaño y posición.
- **Validación:** `git diff --check` PASS; build/UAT visual pendiente por falta de `frontend/node_modules`.

## SER / Consignas — homologación visual con Bitácora — 2026-09-22

- Se añade contraer/expandir horizontalmente el panel “Puestos del Punto”; la selección y la búsqueda permanecen en el estado de la pantalla.
- Se aplica al carrusel de pasos el fondo/borde a ancho completo y el viewport desplazable con scrollbar insetados 16 px; la pestaña activa se mantiene visible al navegar o cambiar de Protocolo/Consigna.
- Se contienen los anchos de la vista de detalle y sus formularios responsivos para reducir desbordes laterales.
- El encabezado de detalle y la franja de Consignas ya reutilizan los estilos compartidos de Bitácora; no se duplican reglas ni se cambia su lógica.
- “No aplica al puesto” no tiene un elemento equivalente en Consignas; no se traslada esa alineación.
- **Archivos:** `frontend/src/pages/ConsignasConfig.tsx`, `frontend/src/styles.css`.
- **Validación:** pendiente de `git diff --check`, build y revisión visual; `frontend/node_modules` no está instalado en este entorno.

### Consignas — pestaña Aplicación

- Se elimina el padding global heredado en las secciones del selector de Vigencia/Aplicación/Ubicación/Resumen, que comprimía los controles.
- Se distribuyen los controles principales en dos columnas y el resumen ocupa el ancho completo; en móvil estrecho se apilan.
- Los botones “Permanente/Temporal” y “Todo el tiempo/Calendario” tienen ancho disponible, alto cómodo y texto legible; los indicadores del resumen ya no se fragmentan por falta de espacio.
- Sin cambios en valores, reglas de aplicación ni guardado.
- **Archivo:** `frontend/src/styles.css`.
- Se homologan también los selectores, campos de fecha/hora y botones de días al estilo de formulario de la página: contorno azul al enfocar, bordes redondeados, tipografía y estados activos legibles.
# SER / Bitácora y Consola — estilo de Novedades y opción Ver — 2026-09-25

- Se homologa la jerarquía visual de ambas pantallas con Novedades: título de 26 px, KPIs con estructura `coord-kpis`, tarjetas de búsqueda/resultados y tablas.
- La acción **Ver** abre un panel completo en modo solo lectura en Bitácora y Consola. Se mantienen los paneles laterales existentes y sus funciones propias.
- **Archivos:** `frontend/src/pages/BitacoraGlobal.tsx`, `frontend/src/pages/ConsolaMonitor.tsx`, `frontend/src/styles.css`.
- **Base de datos/migraciones:** sin cambios.
- **Validación:** pendiente de build y verificación visual.
