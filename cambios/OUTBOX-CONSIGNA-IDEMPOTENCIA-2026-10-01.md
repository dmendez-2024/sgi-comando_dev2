# CR — Outbox offline e idempotencia de consignas

**Fecha:** 2026-10-01  
**Vertical:** SGI Operador ↔ SGI Comando / Consignas  
**Estado:** UAT

## Decisión

`POST /api/v1/operator/consignment-review-requests` requiere `Idempotency-Key`; debe coincidir con el UUID `requestId` incluido en el cuerpo. SGI Comando persiste el hash SHA-256 del JSON canónico, el autor y el empleado junto a la solicitud.

## Resultado de reintentos

- Clave, autor y hash iguales: se devuelve el ACK original, sin crear otra solicitud.
- Clave existente con autor diferente: `403`.
- Clave existente con contenido diferente: `422`.
- Clave ausente, inválida o distinta de `requestId`: `400`.

La inserción usa conflicto por llave primaria para que carreras de reintentos no generen duplicados.

## Migración y rollback

`V36__operator_consignment_review_request_idempotency.sql` agrega `payload_hash`. No elimina ni modifica solicitudes existentes. Rollback aplicativo: deshabilitar el nuevo cliente; la columna e índice pueden permanecer sin impacto. No se deben borrar registros operativos.
