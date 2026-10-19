# CSL — persistencia de Notificaciones de Incidente (2026-10-08)

Actualización posterior: [CSL IMG RC1, validada el 2026-10-19](CSL_INCIDENT_IMAGES_2026-10-16.md) implementa imágenes persistentes. La limitación binaria descrita aquí corresponde al 08/10 y queda supersedida en esa RC; routing STC continúa pendiente.

Extensión posterior a la baseline CSL v0.2 congelada. No modifica el documento histórico `CSL_INCIDENT_NOTIFICATION_v0.2.md`, que describe el alcance UAT original.

- SGI: Comando conserva en PostgreSQL el registro operacional de origen de las notificaciones, en `incident_notification` (`V69`).
- `POST /api/incidents` crea un registro y asigna su código; `PUT /api/incidents/{id}` actualiza Borrador o Finalizado; `GET /api/incidents` recupera los incidentes visibles por alcance territorial o los borradores propios sin Compañía.
- El registro incluye el formulario, la ubicación, colaboradores, estado y la selección de cobertura/reasignación (`targetShiftId`, `replacementEmployeeId` y nombres de presentación). El servidor valida el Cliente/Punto/Puesto y, al finalizar, vuelve a comprobar el turno y el candidato.
- Guardar un incidente de inasistencia **no cambia** por sí solo la asignación operacional publicada. La aplicación de una reasignación al plan requiere un flujo transaccional aparte.
- Las imágenes del formulario conservan únicamente su metadata; el contenido binario todavía no se almacena. El enrutamiento a STC tampoco está activado.
