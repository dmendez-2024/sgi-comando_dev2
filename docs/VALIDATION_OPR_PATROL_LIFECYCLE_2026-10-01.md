# Validación — ciclo de vida de Patrullas — 2026-10-01

## Regla funcional

- `NOT_STARTED`: muestra la duración configurada; no corre el tiempo y no habilita evidencias.
- `IN_PROGRESS`: existe `patrol_execution` con `started_at` oficial y `finished_at = null`; el tiempo corre desde esa hora.
- `COMPLETED`: existe `finished_at` y resultado; el tiempo se detiene y la ejecución aparece en SGI Comando.

## Contrato

- `GET /api/v1/operator/patrol-executions/current?assignmentId=...&patrolId=...`
- `POST /api/v1/operator/patrol-executions` con `action=START` o `action=FINISH`.
- Identidad, tenant, asignación, puesto y Patrulla activa se validan en SGI Comando.
- `START` reutiliza una ejecución abierta del mismo Agente, Patrulla y Puesto.
- `FINISH` solo acepta una ejecución abierta del Agente y contexto autorizados.

## Evidencia técnica

- Backend empaquetado correctamente dentro de la imagen reproducible de Docker (Quarkus/Maven): `BUILD SUCCESS`.
- Servicio UAT recreado y `/q/health`: `UP`, base de datos `UP`.
- Migración aditiva `V37`: vincula la ejecución con `operational_assignment` y evita más de una ejecución activa por asignación/plan. No modifica históricos ni crea tablas paralelas.

