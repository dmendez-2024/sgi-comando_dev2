# Validación — CSL v0.2 — 2026-09-27

## Resultado estático

- TypeScript/TSX syntax parse: PASS — 21 archivos (excluyendo `vite-env.d.ts`).
- CSS: PASS — llaves balanceadas.
- JSON: PASS — 30 archivos.
- Backend vs CSL v0.1.1: UNCHANGED.
- Database/Flyway vs CSL v0.1.1: UNCHANGED.
- Scripts vs CSL v0.1.1: UNCHANGED.
- SITC vs CSL v0.1.1: UNCHANGED.
- `sitc/SGI_Comando_CURRENT.sitcpack`: BYTE_IDENTICAL.
- Runtime EVC tables/classes (`OPERATIONAL_EVENT_RULE`, `EVENT_ACTION_CORRELATION`): no incorporadas.

## Cobertura funcional UAT

Implementado en frontend:
- `Notificar Incidente`.
- panel derecho editable;
- categorías, subcategorías y criticidad;
- Cliente/Punto/Puesto;
- colaboradores con lookback 14 días + deduplicación;
- 5 imágenes máximo en Descripción y 5 en Resolución;
- Sanción Sí/No;
- Borrador / Finalizado y reapertura;
- Inasistencia Programada: siguientes 2 turnos;
- Inasistencia Efectiva: turno actual;
- disponibilidad contra turno objetivo + turno previo;
- prelación mismo Puesto / mismo Punto / misma Compañía por distancia.

## No ejecutado

No se ejecutó build Vite completo ni Docker end-to-end porque la baseline portable no contiene `node_modules`/lockfile instalados en este entorno. La entrega debe pasar ese gate en UAT de SISTEMAS antes de producción.
