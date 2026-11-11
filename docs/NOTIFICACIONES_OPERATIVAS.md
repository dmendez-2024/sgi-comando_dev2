# Notificaciones operativas

La campana consulta `GET /api/notifications` con la identidad autenticada. El servidor aplica la Instancia-País vigente y `OperationalScopeService.allowedCompanyIdsForCompanyRoles()`: coordinador/asistente reciben solo sus compañías explícitas; jefaturas y directores territoriales reciben su alcance configurado; roles nacionales reciben las compañías de la instancia actual. Sin compañías autorizadas, la respuesta es vacía.

La fuente actual es `operator_operational_report` (incidentes, hallazgos y vulnerabilidades). Se incluyen estados REPORTED, PENDING, IN_PROGRESS, OVERDUE, NEW, CRITICAL, ESCALATED y ACKNOWLEDGED. Se excluyen borradores, finalizados y demás estados de cierre. Se priorizan críticos, vencidos/escalados, severidad alta y luego fecha descendente.

La respuesta contiene `total` (todos los casos elegibles) e `items` (hasta 50). El panel presenta los primeros 10 y acceso a Consola. El contador representa casos activos, no mensajes sin leer; no hay persistencia de lectura ni cambios de estado al abrir una notificación. Actualiza al ingresar, abrir la campana y cada 60 segundos mientras la pestaña está visible. Los errores se muestran y permiten reintentar.

`GET /api/operational-reports/{id}` consulta un caso específico con el mismo alcance, devolviendo 404 si no está autorizado o no existe. Permite abrir el detalle desde la campana incluso si el caso no está entre los 200 registros de la lista habitual o fuera del rango de fechas de Consola.

Los incidentes propios de Consola actualmente solo admiten DRAFT/FINALIZED, por lo que no generan notificaciones activas. Consignas y alarmas no se incorporan hasta disponer de una fuente de casos activos con detalle y estado consultables; no se generan avisos a partir de datos de ejemplo ni de contadores agregados.

No se requieren migraciones ni cambios de roles. La campana no modifica permisos ni registros operativos.
