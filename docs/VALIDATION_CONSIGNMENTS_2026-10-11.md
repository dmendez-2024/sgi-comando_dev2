# Validación integral SGI Operador ↔ SGI Comando — Consignas

Fecha: 2026-10-11

## Resultado

- La consulta operativa conserva SGI Comando como System of Record.
- El contexto entrega la zona horaria del snapshot Instancia–País de CORE para que SGI Operador evalúe calendarios sin una zona física codificada en la regla de negocio.
- Las solicitudes `PENDING` aplican alcance territorial en el backend.
- Para IDENT, el alcance se resuelve mediante `persona_id`; `scope_id` identifica la compañía, región o zona. No existe un UUID de coordinador en este contrato.
- Si la persona no tiene alcance configurado, el endpoint responde un mensaje funcional y auditable, evitando ocultar la incidencia como una colección vacía.
- El frontend consulta Consignas y Patrullas de forma independiente y dejó de mezclar el dataset demostrativo con registros reales.
- El cumplimiento de una consigna con GPS requerido valida latitud, longitud y precisión; el JSON completo permanece en el payload auditable del cumplimiento.

## Fuera de alcance por decisión del propietario

La aprobación o descarte de las consignas propuestas por agentes no se implementa. Permanece pendiente hasta una segunda orden documentada y un control de cambios que defina roles, transición, auditoría, publicación y versionado REGESEP.

## Verificación de ambiente

Antes de UAT debe comprobarse que cada coordinador autenticado por IDENT tenga `persona_id`, `instance_country_id`, `scope_type` y `scope_id` válidos en `user_operational_scope`.
