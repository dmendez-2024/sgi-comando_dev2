# SER — Changelog

## Revisión de integración de catálogo comercial — migración V33 — 2026-09-25

- El código actual incluye receptor SIC:COM de eventos comerciales y réplica Cliente/Servicio/Puesto.
- Migración V33 crea `client`, `service.client_id`, `post.commercial_status` y recibos idempotentes.
- El SoR comercial continúa en SIC:COM según los contratos existentes.
- La especificación V3.1 define el catálogo validado sin enviar la orden interna; V2.0/V2.1 describen un flujo anterior. V3.1 sigue pendiente de CR, contrato bilateral, CORE y UAT end-to-end.
- Estado local: receptor implementado. Estado de interconexión end-to-end no READY; el registro mantiene el destino como BLOCKED y falta confirmar prueba del otro lado.
- Las colecciones Postman cubren ejemplos de crear, actualizar e inactivar, pero no tienen scripts de aserción ni acreditan ejecución. Faltan pruebas de reintento idempotente/conflicto, errores de autenticación/tenant/contrato y verificación persistida.
- La nota JTO en `cambios/` conserva el número V27; debe alinearse con V33.
- CR-ID, aprobación formal y release vertical que contiene este cambio deben confirmarse. No se declara freeze nuevo.
