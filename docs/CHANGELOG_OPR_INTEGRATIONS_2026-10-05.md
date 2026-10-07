# Integraciones SGI Operador - SGI Comando

Fecha: 2026-10-05

## Cambios incorporados

- Recepción idempotente y consulta de registros reales de Bitácora.
- Persistencia aislada por instancia-país mediante la migración `V59__operator_logbook_records.sql`.
- Visualización de esos registros en Operaciones / Bitácora de SGI Comando.
- Entrega autenticada del plano ATS vigente y coordenadas ATS de los hitos de Patrulla.
- Lectura autenticada, asociada a asignación y sin caché, de evidencias fotográficas de relevo.

## Cumplimiento de integración

- SGI Comando conserva la autoridad sobre su información operativa.
- SGI Operador consume contratos autenticados y no accede directamente a la base de datos.
- Los registros de Bitácora incluyen identidad, tenant, correlación e idempotencia.
- Los contratos agregados son aditivos y conservan compatibilidad con los endpoints existentes.
