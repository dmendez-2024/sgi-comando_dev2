# Changelog DM — SGI: Comando

## 2026-09-22 — Hotfix UI de navegación lateral
- Corrige el desbordamiento del menú lateral cuando las opciones y el ledger de versiones superan la altura visible por resolución o zoom del navegador.
- El sidebar conserva su fondo oscuro y dispone de desplazamiento vertical propio; ninguna opción queda fuera del contenedor ni depende del scroll de la página principal.
- El cambio está limitado a estilos del menú global; no modifica rutas, permisos, lógica funcional, base de datos, contratos, `ECOSYSTEM_INTERCONNECTION` ni `EXTERNAL_CONNECTION`.

## Revalidación TER v1.0.1 — UAT_CANDIDATE

| ID | Criterio | Estado |
|---|---|---|
| TER-AC-023 | El filtro `Tipo` sincroniza el listado para Zona, Región, subdivisión territorial y Compañía, combinado con búsqueda y Estado. | IMPLEMENTADO / PENDIENTE UAT |
| TER-AC-024 | Duplicar código o nombre de Zona produce HTTP 409 y feedback específico visible. | IMPLEMENTADO / PENDIENTE UAT |
| TER-AC-025 | Duplicar código o nombre de Región produce HTTP 409 y feedback específico visible. | IMPLEMENTADO / PENDIENTE UAT |
| TER-AC-026 | Código y Nombre de Zona/Región respetan máximos de 32 y 160 caracteres en UI y backend. | IMPLEMENTADO / PENDIENTE UAT |
| TER-AC-027 | Eliminar una Zona `DRAFT` con Regiones conserva la dependencia y muestra HTTP 409 con instrucción de eliminar primero las Regiones. | IMPLEMENTADO / PENDIENTE UAT |
## Cierre
TER v1.0 queda congelada. Los ítems dependientes de CORE no reabren TER: implementan un contrato ya congelado.

# TER — Changelog

## v1.0.1 — UAT_CANDIDATE — 2026-09-22
- Corrige el filtro `Tipo` para sincronizar el árbol visible con la entidad seleccionada: Zona, Región, subdivisión territorial o Compañía; la búsqueda y el filtro de Estado se aplican al tipo elegido.
- Valida duplicidad de código y nombre de Zonas y Regiones dentro de la Instancia–País y responde HTTP 409 con un mensaje legible mostrado por la UI, en lugar de exponer un error técnico genérico.
- Alinea los formularios de Zona y Región con el esquema persistente: Código admite hasta 32 caracteres y Nombre hasta 160, con `maxLength`, contador visible y validación equivalente en backend.
- Al intentar eliminar una Zona `DRAFT` que todavía contiene Regiones, conserva la regla existente y responde HTTP 409 con el mensaje: `No se puede eliminar la Zona porque tiene Regiones asociadas. Elimine primero las Regiones.`; ya no se presenta como error 404.
- Corrige el mapa operacional para que utilice exclusivamente asignaciones efectivas de Zonas y Regiones `ACTIVE`.
- Separa las asignaciones territoriales en borrador de las asignaciones efectivas mediante Flyway `V27__territory_draft_assignments.sql`.
- Crear, editar o eliminar una Zona/Región `DRAFT` ya no modifica la clasificación mostrada en el mapa.
- Al activar la configuración, las asignaciones pendientes pasan a ser efectivas y el mapa se repinta.
- Se mantiene el contrato CORE, la frontera de SoR y la exclusividad territorial congelada en TER v1.0.
- SITC: sin cambios de contratos, `ECOSYSTEM_INTERCONNECTION` ni `EXTERNAL_CONNECTION`.
- Validación técnica: build React/TypeScript y Java/Quarkus en Docker; Flyway V27 aplicado sobre PostgreSQL 17; prueba API de creación/eliminación de borradores sin cambio de asignación efectiva.
- Pendiente: revalidación funcional UAT del filtro `Tipo`, duplicidades, longitudes máximas y eliminación de Zona borrador con Regiones.

# TER — Decision Log v1.0 — CONGELADO

## TER-DEC-016 — Configuración borrador y mapa efectivo
Las asignaciones de subdivisiones realizadas en Zonas o Regiones `DRAFT` se conservan como configuración pendiente y no sustituyen la clasificación territorial efectiva.

El mapa usa únicamente la asignación efectiva cuando tanto la Zona como la Región están `ACTIVE`. Eliminar una entidad borrador descarta solo su configuración pendiente; activar la configuración promueve sus asignaciones y repinta el mapa.

## TER-DEC-017 — Semántica del filtro Tipo
El filtro `Tipo` determina qué clase de entidad sincroniza y muestra el árbol territorial: Zona, Región, subdivisión territorial o Compañía. La búsqueda y el filtro de Estado se evalúan sobre la entidad del tipo seleccionado, conservando únicamente los padres necesarios para representar su jerarquía.

## TER-DEC-018 — Validación de Código, Nombre y duplicidad
Zona y Región admiten Código de hasta 32 caracteres y Nombre de hasta 160, conforme a las columnas persistentes. La UI limita y contabiliza caracteres, y el backend repite la validación para evitar que una llamada directa exceda el esquema.

Dentro de una misma Instancia–País no puede repetirse el código ni el nombre de una Zona o Región. El backend responde HTTP 409 con un mensaje específico de código o nombre duplicado y la UI lo presenta como feedback legible.

## TER-DEC-019 — Eliminación de una Zona borrador con Regiones
Una Zona `DRAFT` que todavía tenga Regiones no se elimina. La API responde HTTP 409 con el mensaje `No se puede eliminar la Zona porque tiene Regiones asociadas. Elimine primero las Regiones.` y la UI muestra ese texto al usuario. Esta corrección conserva la regla de dependencia existente y no redefine las reglas de eliminación de Regiones.

# TER — Plan UAT v0.1

## Regresión TER v1.0.1
1. Registrar los colores y etiquetas actuales del mapa.
2. Crear una Zona `DRAFT` seleccionando Provincias que ya pertenezcan a una Zona/Región activa.
3. Confirmar que la Zona borrador conserva sus Provincias en el editor y que el mapa no cambia.
4. Crear una Región `DRAFT` dentro de la Zona borrador y confirmar nuevamente que el mapa no cambia.
5. Eliminar la Región y la Zona borrador; confirmar que el mapa y las asignaciones activas permanecen iguales.
6. Activar una nueva configuración completa y confirmar que el mapa se repinta solo después de quedar activas la Zona y la Región.
7. Seleccionar sucesivamente cada valor del filtro `Tipo`: Zona, Región, Provincia/denominación CORE y Compañía; confirmar que el listado muestra únicamente entidades del tipo elegido y sus padres de contexto.
8. Combinar cada `Tipo` con texto de búsqueda y Estado; confirmar que el listado, los conteos y el mensaje `Sin resultados` se actualizan inmediatamente.
9. Intentar crear una Zona con código repetido y luego con nombre repetido; confirmar HTTP 409 y un mensaje que identifica el valor duplicado, sin mostrar 404 ni un error técnico genérico.
10. Repetir la prueba anterior al crear una Región.
11. En formularios de Zona y Región, confirmar que Código muestra contador y no admite más de 32 caracteres, y Nombre no admite más de 160; comprobar que el backend rechaza también valores superiores con un mensaje legible.
12. Crear una Zona `DRAFT`, agregarle una Región y tratar de eliminar primero la Zona; confirmar HTTP 409 y el mensaje `No se puede eliminar la Zona porque tiene Regiones asociadas. Elimine primero las Regiones.`
13. Eliminar primero la Región y después la Zona borrador; confirmar éxito y ausencia de error 404.

# SGI: Comando — Matriz de verticales

| Vertical | Versión | Estado |
|---|---:|---|
| TER — Territorio | 1.0 | FROZEN |
| COM — Compañías | 1.1.3 | FROZEN |
| ASI — Asignaciones | 0.7.4 | UAT_CANDIDATE |
| SER — Servicios | 0.10.8 | UAT_CANDIDATE |

## 2026-09-22 — TER v1.0.1

Estado: **UAT_CANDIDATE**.

- Reapertura localizada de TER para corregir la separación entre configuración borrador y asignación territorial efectiva.
- El filtro `Tipo` sincroniza el listado por Zona, Región, subdivisión territorial o Compañía y combina búsqueda/Estado sobre el tipo seleccionado.
- Duplicidades de código o nombre de Zona/Región responden HTTP 409 con mensajes legibles.
- Código y Nombre de Zona/Región quedan limitados a 32 y 160 caracteres en UI y backend.
- Eliminar una Zona borrador con Regiones conserva el bloqueo existente y muestra la instrucción de eliminar primero sus Regiones, sin error 404.
- El mapa solo cambia cuando Zona y Región están `ACTIVE`.
- Flyway V27 agrega asignaciones pendientes sin modificar migraciones aplicadas.
- Sin cambios funcionales en COM, ASI, SER, COO, BIT, CNS, NOV o CSL.
- Sin cambios de contratos, `ECOSYSTEM_INTERCONNECTION` ni `EXTERNAL_CONNECTION`.
- Estado de estas regresiones: implementadas y pendientes de revalidación funcional UAT.

