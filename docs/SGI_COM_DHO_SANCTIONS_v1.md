# SGI_COM → SIC_DHO — registro de sanciones v1

## Alcance

Cuando un usuario finaliza un Incidente en Consola y selecciona **Sí** en `¿Genera sanción?`, SGI Comando envía la sanción a SIC:DHO dentro de la misma operación. Un borrador o un incidente finalizado sin sanción no genera llamadas.

El incidente solo queda finalizado cuando DHO confirma la recepción. Si DHO rechaza la solicitud o devuelve un error, la transacción local se revierte y la Consola presenta un mensaje funcional al usuario.

- Interconexión CORE: `SGI_COM_SIC_DHO_0001_v001`.
- Interfaz: `SGI_COM_SIC_DHO_0001_IF01`.
- Método y ruta: `POST /ops.api.dho/ws/sgi/empresa/sancion/registroComando`.
- Autenticación: `SERVICE_TOKEN` mediante `credentialRef=SGI_COM_SIC_DHO_0001_v001`.
- El secreto se resuelve en SGI Comando desde `SGI_CREDENTIAL_SGI_COM_SIC_DHO_0001_V001`; no se almacena en CORE ni en Git.
- No se envían cédula, evidencia, archivo, reglamento ni una decisión individual por empleado.

## Solicitud

```json
{
  "eventId": "c7d566f0-96ac-4be8-b588-cb53465854e8",
  "incidentId": "23c19824-dd85-497e-bd4a-fe2e30ace92e",
  "instanceCountryId": "793341c8-168c-4f29-b1b4-9ed638baa4b5",
  "emitterPersonaId": 98765,
  "infraccionFecha": "2026-11-09T15:30:00Z",
  "observacion": "Incumplimiento de las disposiciones asignadas.",
  "syncFecha": "2026-11-09T15:35:20Z",
  "employees": [
    {
      "employeeId": "6fef0130-693a-4781-8d2f-39fde927fbd4",
      "personaId": 12345,
      "punto": "Hospital Regional",
      "cliente": "Ministerio de Salud",
      "compania": "URRACA"
    },
    {
      "employeeId": "f28174f1-3021-4dd4-9828-a59379b89a03",
      "personaId": 12346,
      "punto": "Hospital Regional",
      "cliente": "Ministerio de Salud",
      "compania": "URRACA"
    }
  ]
}
```

`emitterPersonaId` sale del token IDENT del responsable que finaliza el incidente. Cada `personaId` sale de la instantánea de empleados sincronizada desde DHO. Si falta cualquiera de esos identificadores, SGI Comando no finaliza una sanción imposible de procesar.

## Idempotencia y entrega

`eventId` también viaja en `Idempotency-Key` y `X-Correlation-Id`. El mismo evento y contenido se conservan durante los reintentos de transporte configurados en CORE. DHO debe considerar repetida una solicitud con el mismo `eventId` sin crear otra sanción.

La respuesta HTTP `2xx` se considera entregada, salvo que el JSON devuelva `success=false`. Después de agotar los reintentos configurados en CORE, cualquier rechazo impide finalizar el incidente y se traduce a un mensaje entendible:

- Validación o `success=false`: DHO rechazó la información de la sanción.
- `401/403`: fallo de autenticación entre Sistemas.
- `404`: endpoint DHO ausente o no configurado.
- `409`: conflicto de idempotencia.
- `408/429/5xx` o transporte: DHO no pudo procesar la solicitud en ese momento.

SGI Comando expone en el incidente:

- `sanctionDeliveryStatus`: `NOT_REQUIRED` o `SENT` para operaciones confirmadas y persistidas.
- `sanctionEventId`.
- `sanctionDeliveryAttempts`.
- `sanctionSentAt` cuando DHO confirmó la recepción. Los errores no persisten un incidente finalizado.

La futura aprobación o rechazo de DHO requiere una interconexión inversa separada; no forma parte de este envío.
