# SGI: Comando — ASI v0.7 UAT

**Fecha:** 2026-09-19

## Objetivo
Agregar transferencias controladas de personal entre Compañías sin desplazar a SIC: RRHH como System of Record de la relación persona–Compañía.

## Flujo
`Origen inicia → futuras asignaciones del origen se liberan → Pendiente de aceptación → Destino acepta/rechaza → transferencia efectiva inmediata o al cierre del turno actual`.

## UI
- Filtros nuevos en Personal disponible: **Transferencias salientes** y **Transferencias entrantes**.
- Badges `Saliente` / `Entrante`.
- Ficha de persona con bloque **Movimiento entre Compañías**.
- Modal **Transferir a otra Compañía** con destino, Motivo y Observaciones.
- Modal **Revisar transferencia** con Aceptar/Rechazar.
- Historial de transferencias.
- Acción **Anular transferencia** disponible únicamente mientras sigue pendiente.

## Reglas
- Motivo + Observaciones obligatorios.
- Solo una transferencia abierta por persona.
- Asignaciones futuras se liberan al enviar, no al aceptar.
- Rechazo/anulación no reconstruye asignaciones canceladas.
- Turno actual se conserva.
- Después de aceptar, la transacción no puede anularse.
- Si existe turno en curso, la transferencia se hace efectiva al finalizarlo.

## SoR
**SIC: RRHH** sigue siendo SoR de persona–Compañía. El backend UAT usa snapshot local y Outbox para representar el contrato de integración.
