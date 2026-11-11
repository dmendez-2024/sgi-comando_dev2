# Hechos de Impulsos publicados hacia SMC

Se conserva la implementación actual de sgiComandoDev: interconexión v003,
persona_id, bandeja durable y cursores incrementales por fuente. No se copia
el publicador antiguo del worktree del chat.

| Regla local | Fuente operativa | factType |
| --- | --- | --- |
| REL_A_TIEMPO | RELIEF | RELIEF_COMPLETED_ON_TIME |
| REL_FOTO_CUERPO | RELIEF | RELIEF_FULL_BODY_PHOTO |
| BIT_CA_CORRECTO | LOGBOOK | ACCESS_CONTROL_OK |
| PAT_CORRECTA | PATROL | PATROL_OK |
| CON_CORRECTA | CONSIGNMENT | CONSIGNMENT_OK |

Solo se publican evaluaciones AWARDED y NO_AWARD con la pareja de regla/fuente
esperada. NOT_ELIGIBLE, fuentes incompatibles y agentes ausentes se excluyen. La fecha del hecho es occurred_at; el cursor
usa evaluated_at para detectar evaluaciones tardías sin perderlas.

El valor es siempre 1: una ocurrencia elegible. No se transmite el monto del
sorteo ni se modifica la adjudicación local. El contexto procede de
asignación -> turno -> puesto -> punto cuando existe. Metadata conserva el origen
operativo y agrega evaluationId, ruleCode y ruleVersion.

La identidad usa source_type=IMPULSE_EVALUATION y source_id=evaluación.id.
Dos reglas del mismo relevo son dos hechos; cada reintento conserva eventId.
Las entregas ya encoladas no se reescriben al ampliar metadata.

## Pruebas

- SmcImpulseFactsDatabaseTest: cinco tipos, NO_AWARD, exclusiones, paginación por
  fecha/UUID, contexto opcional y deduplicación entre hechos de un mismo relevo.
  Usa PostgreSQL y tablas temporales sin datos operativos.
- SmcImpulseHttpTest: cinco tipos enviados dos veces mediante el adaptador,
  resolución CORE, OpenBao y OAuth2 contra servidores HTTP locales. Comprueba
  value, eventId, token de servicio y caché de credenciales.
- SmcOpenBaoTest: regresión de la autenticación existente.

Los servidores locales no demuestran la disponibilidad ni los permisos del SMC
real. Este cambio no activa el servicio, no despliega, no hace commit ni push.
