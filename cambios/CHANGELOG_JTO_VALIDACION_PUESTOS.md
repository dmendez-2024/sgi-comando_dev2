# CHANGELOG_JTO_VALIDACION_PUESTOS

## Mensajes de validación en Puestos

- Valida descripción y ubicación antes de guardar un borrador o la configuración del Puesto.
- Muestra simultáneamente los mensajes: “Ingresa la descripción operacional del Puesto.” y “Selecciona la ubicación del Puesto directamente en el plano ATS.” cuando faltan ambos datos.
- Resalta en rojo la descripción y el selector del plano mientras estén pendientes.
- Mantiene habilitados ambos botones de guardado para que la persona reciba el motivo concreto en lugar de un `400` silencioso.
- Devuelve desde `PUT /api/post-configurations/{postId}` las validaciones con cuerpo JSON `{\"message\":\"...\"}`.
- Normaliza en el frontend las respuestas de validación para mostrar únicamente el contenido de `message`, sin el prefijo del código HTTP ni el envoltorio JSON.
