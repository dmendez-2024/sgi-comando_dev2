# Impulsos en SGI Comando — 2026-10-08

## SGI Comando

- Flyway `V70`: tablas `impulse_rule` (reglas versionadas por Instancia PE) e `impulse_evaluation` (evaluaciones = ledger), con las reglas iniciales de la hoja AdS. Novedades, km de patrulla, Liderazgo y QR de cliente quedan inactivas: no tienen productor.
- Motor `ImpulseEngine` (paquete `impulses`), configurable con `sgi.impulses.engine-enabled`, `sgi.impulses.settle-seconds` (60) y `sgi.impulses.visint-wait-hours` (24).
- `GET /api/v1/operator/runtime` añade el bloque `impulses` (saldo del agente).
- Endpoints nuevos: `GET /api/v1/operator/impulses` (sin uso por la app), `GET /api/impulses/rules`, `GET /api/impulses/evaluations` y `GET /api/impulses/employees/{employeeId}`.
- Integraciones / CORE: `0 nuevas / 0 modificadas / 0 retiradas`. `SGI_COM_CORE_0001_v001` sigue sin implementar.

## SGI: Operador

- Se retiran los valores de demostración y la suma local de Impulsos; Mi Perfil y el banner muestran solo lo que adjudica Comando.

## Pruebas (Comando local en Docker + app en dispositivo)

- Consigna registrada desde la app → `CON_CORRECTA` otorgó 0,2. Bitácoras de prueba (`username='prueba.impulsos'`) → `BIT_CA_CORRECTO`.
- Playwright contra el front local: 9/9 PASS (asignación visible, reglas, evaluaciones sin duplicados, saldo = suma de evaluaciones, saldo del agente, 403 para Agente en `/api/impulses/*`).
- Pendiente: relevo y patrulla de punta a punta en dispositivo; aprobación de valores y escala por Gerencia.
