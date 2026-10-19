# CR-SGI_COM-CSL-0001 — Persistencia de imágenes de incidentes

Fecha: 2026-10-16. Vertical: CSL, extensión de la baseline histórica v0.2.
Baseline de código: `79e5e5d67718e898442a57a7cc3445f4bf4df564`.
Solicitud y autorización de alcance: usuario de esta tarea, guardar las imágenes según documentación y SITC-NOM-001 v4.1. Registro previo a implementación; aprobación de producción pendiente del proceso de Sistemas.

## Decisión de implementación

Completar los adjuntos de descripción y resolución con el almacenamiento MinIO ya existente y el registro canónico `evidence_object`. SGI_COM conserva el SoR del incidente. No inventar una asignación ni un agente para la foto subida por el usuario de Consola: agregar vínculo opcional al incidente y restringir por CHECK las evidencias de Operador para mantener sus requisitos actuales.

Impacto controlado: UI, contrato HTTP interno y migración aditiva V85. Nueva API `/api/v1/incidents/{id}/evidences`; se conserva la API histórica de formulario. Máximo cinco imágenes por sección y 5 MiB por archivo, JPEG/PNG/WebP según el detector compartido. Reintentos por UUID de adjunto con verificación de hash; referencias y contenido accesibles solo dentro del ámbito del incidente. Las imágenes retiradas de la selección se conservan en almacenamiento para trazabilidad, sin aparecer en el detalle actual. No crear un maestro paralelo ni modificar migraciones anteriores.

El formulario se guarda primero como borrador, sube los archivos pendientes y solo confirma Finalizado cuando termina la carga y el guardado solicitado. Si falla almacenamiento, conservar borrador y archivos locales pendientes para reintento; no anunciar finalización exitosa. Metadata histórica sin binario se identifica como archivo no disponible y requiere volver a adjuntar.

## Norma e impacto

SITC-NOM-001 v4.1 §§4, 6.1, 7, 17 y 20: aislamiento, auditoría, migraciones, control documental y validación. La elección de MinIO proviene de IMPLEMENTATION_BASELINE §6, no de una obligación específica de proveedor en la norma.
Otros Program IDs afectados: ninguno. Interconexiones del ecosistema nuevas/modificadas/retiradas: 0/0/0. Se reutiliza StorageService/configuración existente, sin nuevos hosts ni secretos ni cambios de capacidad arquitectónica; sin cambios de interconexiones/SITC. El snapshot local se referencia en docs/SITCPACK.md; no se ha consultado CORE VIGENTE en esta tarea y no se declara promoción arquitectónica.

## Compatibilidad y reversión

V85 evoluciona evidencia existente; los registros de Operador preservan sus restricciones y los incidentes anteriores su formulario. La metadata sin archivo no se convierte en fotografía ficticia. Respaldar BD antes de migrar. Reversión de aplicación: volver a versión anterior, conservar V85 y los objetos para evitar pérdida; no ejecutar undo destructivo ni borrar fotos. MinIO no participa en la transacción SQL: una caída después de subir puede dejar un objeto huérfano; conservar y revisar contra metadata antes de cualquier limpieza.

## Validación prevista

Build frontend y pruebas backend de guardado, reapertura/finalización, hash/idempotencia, límites, formatos, aislamiento y archivos retirados. Ejecutar UAT de navegador y migración real antes de producción. Registrar resultados efectivos en docs/CSL_INCIDENT_IMAGES_2026-10-16.md.
