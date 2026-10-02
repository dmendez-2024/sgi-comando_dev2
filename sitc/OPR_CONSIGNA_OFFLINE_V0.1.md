# Delta SITC UAT — OPR Consigna offline v0.1

| Campo | Definición |
|---|---|
| Interconexión | `SGI_OPR_SGI_COM_0001_v001` |
| Interfaz | `IF12` — solicitud de consigna para revisión |
| Dirección | SGI Operador → SGI Comando |
| Método | `POST` |
| Idempotencia | `Idempotency-Key` UUID obligatorio; coincide con `requestId`; hash canónico persistido por SGI Comando |
| Reintentos | Outbox persistente local, ACK verificable, backoff exponencial acotado |
| Ownership | Usuario y empleado autenticados; asignación validada en SGI Comando |
| Datos persistidos OPR | requestId, usuario, asignación, payload, hash, estado, intentos, diagnóstico mínimo |
| Secretos | Prohibidos en la outbox y en el contrato |
| Estados | `PENDING`, `SYNCING`, `RETRY`, `ACKED`, `NEEDS_ATTENTION` |

Este delta es UAT y no habilita tráfico productivo hasta aprobación y empaquetado SITC formal.
