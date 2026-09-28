# SGI: Comando — SER v0.10.6 UAT

## Patrullas — homologación UX/UI al estándar de Bitácora

Esta versión ajusta la pantalla de **Patrullas** para alinearla mucho más al estándar visual y de experiencia de usuario ya aprobado en **Bitácora**, preservando las diferencias funcionales propias de la vertical.

### Cambios principales

1. **Listado de protocolos homologado al estándar del Punto**
   - La columna central ahora se presenta como **`Protocolos del Punto`**.
   - La grilla usa la estructura visual: **Código / Protocolo / Patrullas / Estado**.
   - Se añadió referencia del **Puesto asociado** dentro de cada fila.
   - Se mantiene la creación de protocolo y se deja preparado el botón visual de **Importar protocolo**.

2. **Cabecera de edición simplificada y más consistente**
   - La zona superior del editor usa ahora una banda resumen compacta similar a Bitácora.
   - Presenta: código + nombre del protocolo, modalidad principal visible, versión y estado.
   - Se mantienen las acciones clave: **Historial**, **Guardar borrador**, **Publicar versión**, **Crear nueva versión**, **Activar** e **Inactivar**.

3. **Selector de Patrullas alineado al mismo patrón de navegación**
   - Se muestra como una banda de selección horizontal con chips/botones consistentes.
   - Texto homologado: **`Patrullas (x/15)`**.
   - Se explicita la regla: **máximo 15 Patrullas por Protocolo**.
   - Se incluye acción visual directa: **Agregar patrulla**.

4. **Pestañas numeradas con círculo, igual lenguaje de Bitácora**
   - La navegación secundaria de Patrullas se remaquetó para usar el patrón de pasos enumerados con círculo.
   - Secuencia actual:
     1. Definición
     2. Patrulla
     3. Hitos
     4. Reglas por hito
     5. Evidencias
     6. Trazabilidad

5. **Pestaña inicial orientada a la Patrulla seleccionada**
   - Al seleccionar o crear una Patrulla, la vista cae ahora en la pestaña **`Patrulla`**.
   - Esto acerca el flujo al patrón de Bitácora, donde al seleccionar un elemento operativo se entra directamente en su configuración principal.

6. **Pestaña `Patrulla` enriquecida**
   - Ahora muestra en la misma vista los campos más importantes de configuración operativa:
     - Código
     - Nombre de la patrulla
     - Descripción
     - Modalidad espacial
     - Modalidad temporal
     - Ventana de ejecución / ejecuciones requeridas
     - Secuencia de hitos
   - También integra acciones de **Guardar patrulla** y **Eliminar**.

7. **Pestaña `Definición` reorientada como vista general**
   - Pasa a funcionar como overview del Protocolo y de su contexto:
     - Código del protocolo
     - Nombre del protocolo
     - Descripción del protocolo
     - Puesto asociado
     - Patrullas del protocolo (x/15)
   - Incluye nota guía para continuar con el flujo operativo.

8. **Bloque de versión publicada homologado**
   - Se muestra un aviso de bloqueo/inmutabilidad consistente con el lenguaje usado en Bitácora para versiones publicadas.

### Regla funcional nueva

9. **Límite backend: máximo 15 Patrullas por Protocolo**
   - Se agrega validación en backend para impedir la creación de una patrulla número 16.
   - Mensaje esperado: **`Un Protocolo admite máximo 15 Patrullas`**.

## Alcance técnico
- Cambios en **frontend** de `PatrolConfig.tsx` y estilos asociados en `styles.css`.
- Cambio puntual en **backend** dentro de `PatrolResource.java` para validar el máximo de 15 patrullas.
- **Sin nuevas migraciones Flyway**.
- Se preservan migraciones previas sin cambios de checksum.

## Referencias visuales usadas
- `repo/docs/assets/SER_v0.10.6_bitacora_reference.png`
- `repo/docs/assets/SER_v0.10.6_patrullas_target_reference.png`
