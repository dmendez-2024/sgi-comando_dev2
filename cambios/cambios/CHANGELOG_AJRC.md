# Changelog AJRC — SGI: Comando

Registro de cambios solicitados para SGI: Comando gestionados por AJRC.

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
