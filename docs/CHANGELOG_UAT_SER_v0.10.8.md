# SGI: Comando — SER v0.10.8 UAT

## Patrullas — homologación estricta al estándar de Bitácora

Baseline: `SER v0.10.7`.

### UX/UI
- Patrullas usa ahora el mismo patrón estructural de Bitácora:
  - `Puestos del Punto`
  - `Protocolos del Punto`
  - panel derecho de detalle
- Eliminada la `Matriz de modalidades` de la columna izquierda.
- `Protocolos del Punto` usa exactamente las columnas:
  - Código
  - Protocolo
  - Patrullas
  - Estado
- Banda hija homologada a Acreditaciones:
  - `Patrullas (x/15)`
  - chips horizontales de Patrullas
  - `Nueva patrulla`
- Navegación secundaria homologada con círculos numerados:
  1. Definición
  2. Patrulla
  3. Hitos
  4. Reglas por hito
  5. Evidencias
  6. Trazabilidad
- Al entrar, cambiar Puesto o cambiar Protocolo: pestaña por defecto `Definición`.
- Al seleccionar o crear una Patrulla: pestaña `Patrulla`.
- Se reutilizan clases/componentes visuales de Bitácora para encabezados, tablas, strips, tabs, estados, spacing y definición.

### Definición / activación por Puesto
- Patrullas adopta el mismo modelo operativo que Bitácora:
  - un Protocolo puede estar activo en uno o varios Puestos del mismo Punto;
  - activar/inactivar un Puesto no modifica el contenido versionado;
  - versiones publicadas permiten ajustar únicamente activación por Puesto desde `Definición`;
  - activar o inactivar un Puesto publicado exige modal SGI de confirmación.
- Nueva tabla: `patrol_protocol_post_scope`.
- Nueva migración: `V24__ser_patrol_protocol_post_scope.sql`.
- Migraciones V22 y V23 permanecen byte-identical al baseline previo.

### Patrulla
- La pestaña `Patrulla` concentra:
  - Código
  - Nombre
  - Descripción
  - Modalidad espacial Cerrada / Abierta
  - Modalidad temporal Programada / No programada
  - Ventana horaria / ejecuciones requeridas
  - Secuencia Estricta / Flexible
- Se preserva la ventana máxima de 1 hora para Patrullas Programadas.

### Hitos / Reglas / Evidencias
- Se preserva máximo de 25 Hitos para Patrulla Cerrada.
- Se preservan ATS, GPS y origen ATS / FIELD / MIXED.
- Se preservan reglas de Hito, evidencia y Foto estándar.
- Evidencias funciona como resumen derivado de los Hitos.
- `Versionado` se presenta al usuario como `Trazabilidad`.

### Límites
- Máximo 15 Patrullas por Protocolo en frontend y backend.
- Códigos `PAT-xxx` avanzan desde el mayor código existente para evitar colisiones si se eliminan Patrullas intermedias.

### Modales
- No se usa `window.confirm()` ni `window.alert()` en Patrullas.
- Eliminar Patrulla, eliminar Hito, cambiar Cerrada → Abierta y cambios de activación publicados usan modal SGI.

## Quality gates ejecutados antes de empaquetar
- Parseo TypeScript/TSX con TypeScript 5.8.3: `15 archivos / 0 errores sintácticos`.
- `PatrolConfig.tsx`: parseo independiente `OK`.
- Java modificado: validado por `javac` hasta fase de resolución de dependencias; sin errores de parseo Java.
- V22/V23: SHA-256 comparado contra SER v0.10.7, sin cambios.
- No fue posible ejecutar `npm install` en el entorno de generación por ausencia de resolución DNS hacia npm; el build Docker UAT sigue ejecutando `npm install` + `npm run build` en el equipo UAT.
