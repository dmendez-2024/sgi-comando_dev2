# SGI: Comando — SER v0.7.1 UAT FIX

Fecha: 2026-09-12  
Estado: UAT_CANDIDATE

## Motivo
SER v0.7 compilaba correctamente, pero Flyway V14 fallaba al iniciar porque el baseline histórico ya contiene tablas llamadas `patrol_definition` y `patrol_checkpoint`. El uso de `CREATE TABLE IF NOT EXISTS` omitía la creación y luego el migration intentaba indexar columnas nuevas (`protocol_id`) inexistentes en la tabla baseline.

## Corrección
- El modelo nuevo de **Configuración → Patrullas** queda físicamente separado del modelo baseline mediante:
  - `patrol_protocol`
  - `patrol_config_definition`
  - `patrol_config_checkpoint`
  - `patrol_config_checkpoint_rule`
- Las tablas baseline `patrol_definition`, `patrol_checkpoint`, `patrol_plan`, `patrol_occurrence` y tablas de ejecución permanecen intactas.
- Se actualizan los mapeos JPA de Configuración a las tablas `patrol_config_*`.
- Se conserva V14 porque el intento de v0.7 fue transaccional y Flyway reportó el esquema todavía en versión 13; V14 nunca quedó aplicado.

## Bootstrap UAT
`scripts/uat-start.ps1` ahora espera explícitamente `GET /q/health/ready` antes de devolver control. Si el backend no queda READY, imprime automáticamente los últimos 150 logs del backend y aborta.

## Diseño funcional
No cambia ninguna decisión funcional de SER v0.7:
- `Punto → Puesto → Protocolo → Patrulla → Reglas`.
- Cerrada/Abierta × Programada/No Programada.
- Hitos por Plano ATS, Campo o Mixto.
- Foto estándar preparada para VISINT.
- Versionado inmutable: publicado = snapshot; edición = nueva versión borrador.
