# Entrega de hechos SGI Comando -> SMC

Primera entrega, 2026-10-27. Contrato de entrada SMC-002v0.5 del 2026-10-27.
Activación controlada por `SGI_SMC_ENABLED`. La publicación no adjudica Impulsos, calcula el ID ni modifica tablas operativas.

## Alcance

- `ROUTE_COMPLETION`: ejecución finalizada `COMPLETA`/`INCOMPLETA`, patrulla `CLOSED` y `PROGRAMMED`, al menos un hito configurado. Valor = hitos distintos `CUMPLIDO` / hitos configurados * 100, HALF_UP a dos decimales. Un 100 también se entrega como evidencia.
- `CONSIGNMENT_NOT_DONE`: registro explícito `consignment_compliance.result=NO_CUMPLIDA`, valor 1. La ausencia de registro, `CON_NOVEDAD`, `CUMPLIDA` y `NO_APLICA` no generan hechos.
- Cada origen se registra una vez en `smc_fact_delivery`; no se usa la bandeja SQLite del teléfono ni el outbox general de Comando.
- Alcance inicial: no incluía `NO_EJECUTADA` ni francos trabajados. La ampliación V141 incorpora cierres explícitos de patrulla y acreditaciones auditadas de franco trabajado; ver `SMC_ID_PATRULLAS_FRANCOS.md`. No incluye cierre automático de ocurrencias ni consulta del ID de SMC. No se alteran las rutas de ingreso de Operador.

### Hechos para Impulsos (SMC-002v0.5)

La fuente incremental `IMPULSE_EVALUATION` toma evaluaciones locales elegibles (`AWARDED` o `NO_AWARD`) de las reglas `REL_A_TIEMPO`, `REL_FOTO_CUERPO`, `BIT_CA_CORRECTO`, `PAT_CORRECTA` y `CON_CORRECTA`. Envía respectivamente `RELIEF_COMPLETED_ON_TIME`, `RELIEF_FULL_BODY_PHOTO`, `ACCESS_CONTROL_OK`, `PATROL_OK` y `CONSIGNMENT_OK`, cada uno con `value: 1`. La decisión aleatoria y el monto local no se envían; `NOT_ELIGIBLE` no se publica. Se mantienen las condiciones locales de elegibilidad y VISINT de la evaluación existente; validar con negocio su correspondencia con SMC.

El identificador estable se deriva del ID de la evaluación; las dos reglas del mismo relevo son hechos distintos. El cursor se inicializa como las demás fuentes: sin fecha inicial explícita, no carga evaluaciones anteriores a su inicialización. El contrato nuevo denomina `personId` al identificador de persona, mientras el payload existente usa `persona_id`; verificar compatibilidad con SMC antes de desplegar. Los otros cuatro tipos del contrato no están implementados.

Patrullas lee el cierre de `/api/v1/operator/patrol-executions` y sus resultados `patrol_checkpoint_execution`. También permite resolver la definición por `patrol_execution.patrol_definition_id` si existe; exige siempre cierre y resultado final. El registro individual en `task_execution` no es por sí solo una patrulla finalizada y no se transforma en un cierre artificial ni en demérito.

## Activación

El equipo confirmó el binding de CORE `SGI_COM_SMC__0001_v001` (dos guiones bajos antes de 0001). Se usa como valor predeterminado en configuración, no como constante Java. Configurar la interfaz registrada en CORE, método POST y ruta `/api/v1/operational-facts`, destino SMC y referencia de credencial de servicio. El catálogo local conserva su identificador canónico; la resolución de esta integración usa el ID configurado para el ambiente.

Aplicar Flyway hasta V93 en una base UAT antes de activar. V91 crea las entregas y V93 los cursores e índices de descubrimiento incremental:

```dotenv
SGI_SMC_ENABLED=false
SGI_SMC_INTERCONNECTION_ID=SGI_COM_SMC__0001_v001
SGI_SMC_INTERFACE_ID=SGI_COM_SMC_0001_IF01
SGI_SMC_PUBLISH_EVERY_SECONDS=3600
SGI_SMC_BATCH_SIZE=100
# No hace falta SGI_SMC_FACTS_SINCE para la operación normal.
```

Sin `SGI_SMC_FACTS_SINCE`, el primer ciclo activo inicializa automáticamente `smc_source_cursor` para cada instancia y fuente. No importa todo el historial anterior. Los siguientes ciclos continúan desde el cursor guardado, incluso después de reiniciar. No hace falta mantener una fecha fija ni cambiarla cada día/año. Con la bandera apagada no se inicializan cursores ni se encolan hechos.

`SGI_SMC_FACTS_SINCE` queda como opción para una carga histórica **al crear por primera vez el cursor**. Si se configura, debe ser ISO-8601 con zona; un formato inválido devuelve 503 con código `SMC_INVALID_FACTS_SINCE` y mensaje explícito. Una vez creado el cursor, cambiar esa variable no reinicia ni rebobina el progreso. Para solicitar otra carga histórica se necesita un procedimiento deliberado de reproceso, no cambiar silenciosamente el inicio en cada llamada.

Una vez confirmados la identidad de los agentes y el periodo abierto en SMC, activar `SGI_SMC_ENABLED=true` y recrear el backend con las variables. No hay URL/token propio de SMC en Comando: se usa `GenericInterconnectionExecutor` con CORE y su resolución de credenciales.

### Descubrimiento incremental

- Patrullas: se recorren rangos de `patrol_execution.updated_at` e ID. Así una patrulla iniciada antes de activar se descubre cuando se finaliza y actualiza.
- Consignas: se recorren rangos de `consignment_compliance.created_at` e ID. Son registros de confirmación insertados por Comando, no un reloj del teléfono.
- El cursor por fuente guarda fecha de cambio y último ID; el desempate por ID evita omitir filas cuando un lote tiene muchas con la misma fecha. La extracción tiene límite por lote y usa índices de rango de V93.
- Encolado y avance del cursor se confirman en la misma transacción. Si falla uno, ambos retroceden. El estado SENT/PENDING/REJECTED y los reintentos de red son independientes del cursor de descubrimiento.
- La fecha operacional `occurredAt` no cambia: sigue siendo `finished_at` o `confirmed_at`, vinculada al agente y su turno. Los registros recibidos tarde se detectan por su recepción/actualización local, aunque occurredAt sea antiguo.
- Se cierra cada rango antes de la transacción más antigua aún abierta de la misma cuenta PostgreSQL en esa base (`pg_stat_activity`). Evita avanzar por delante de escrituras que podrían confirmar después con timestamp de servidor anterior. No se bloquean filas operativas; una transacción muy larga puede demorar el descubrimiento, pero las entregas ya encoladas siguen reintentándose.
- La garantía anterior corresponde al ingreso normal de Comando, con timestamps de servidor y la misma cuenta DB. Importaciones administrativas que fuerzan `created_at`/`updated_at` hacia el pasado requieren reproceso acordado.

Una patrulla histórica del 21 de octubre no se publica automáticamente si la primera activación ocurre después. Para incluirla se acuerda la carga inicial explícita antes de crear el cursor. Las entregas ya encoladas antes de V93 no se eliminan y continúan su procesamiento.

`SGI_SMC_INTERFACE_ID` debe coincidir con la interfaz efectiva del binding; su valor predeterminado es `SGI_COM_SMC_0001_IF01` hasta que CORE confirme otro. Ambos identificadores se pueden cambiar por ambiente sin recompilar; requieren recrear/reiniciar el proceso con la nueva configuración. El ejecutor usa el ID configurado tanto en la consulta a CORE como en X-Interconnection-Id.

### OpenBao obligatorio para SMC

El adaptador exige que CORE entregue autenticación Bearer o `OAUTH2_CLIENT_CREDENTIALS` y un `credentialRef` que comience por `vault:`. Un binding anónimo, Basic o con referencia de variable de entorno se rechaza con `OPENBAO_CREDENTIAL_REQUIRED` antes de enviar el hecho. Esta política se activa solo en las llamadas SMC y no cambia los demás adaptadores.

- **BEARER**: OpenBao guarda el token de servicio que acepta SMC en el campo `value` de KV v2. Comando lee el secreto mediante su AppRole y lo usa como Bearer.
- **OAUTH2_CLIENT_CREDENTIALS**: OpenBao guarda el secreto del cliente M2M en `value`. Comando obtiene el access token de IDENT y lo envía a SMC. Configurar `SGI_M2M_CLIENT_SMC` con el client_id real registrado en IDENT; es un identificador no secreto. El destino debe ser `SMC`; también se reconoce el alias `SGI_COM_SMC__0001_v001` cuando CORE omite targetProgramId.

Ejemplo de configuración del binding (el marcador de ruta debe reemplazarse por la referencia real, no se crea automáticamente):

```json
{
  "authType": "BEARER",
  "credentialRef": "vault:kv/data/<ruta-real-del-secreto-SMC>"
}
```

Se reutilizan `SGI_VAULT_ADDR`, `SGI_VAULT_ROLE_ID_FILE`, `SGI_VAULT_SECRET_ID_FILE` y `SGI_VAULT_CA_CERT_FILE` y el montaje de archivos del AppRole de SGI_COM que ya está en Compose. La política de ese AppRole debe permitir lectura de la ruta real. No copiar secretos a `.env.openbaoSGI` ni al repositorio. El nombre de ese archivo no implica que sus variables se lean automáticamente desde OpenBao: contiene configuración no secreta del contenedor.

La bandera `SGI_SMC_ENABLED` y la carga histórica inicial opcional siguen siendo configuración independiente. Con `enabled=false` el publicador no resuelve el binding SMC ni lee su credencial de OpenBao; otras operaciones de Comando pueden seguir consultando CORE. Si OpenBao no está disponible o rechaza el acceso, la entrega queda PENDING para reintento. La configuración real en CORE/OpenBao debe comprobarse en DEV; no se modifica desde estas pruebas.

`SmcOpenBaoTest` comprueba sobre HTTP local el resolver CORE, login AppRole, lectura KV v2 y llamada a SMC usando los componentes productivos, en ambos modos. También comprueba referencias no vault, autenticación anónima y denegación de OpenBao, sin usar secretos reales.

Verificación 2026-10-27: `mvn -B -ntp -Dtest=SmcOpenBaoTest,IdentServiceTokensTest test`, 8 pruebas, 0 fallos, 0 errores. Compose validado con `config --no-interpolate --no-env-resolution --quiet`. No se verificaron ni modificaron el binding y los secretos reales de DEV.

El publicador usa `@Scheduled`: **con `QUARKUS_SCHEDULER_ENABLED=false` solo funciona la publicación manual**. No se crea un hilo que evada el interruptor global. Cada ciclo encola hasta el tamaño del lote por fuente y entrega hasta el tamaño del lote total; no significa que todo un histórico se entregue en 30 segundos.

## Contrato

`eventId = UUID.nameUUIDFromBytes(UTF8("SGI_COM:" + sourceType + ":" + sourceId))`, como indica la especificación SMC. Se conserva el payload serializado al encolar; los reintentos y el reproceso no lo recalculan.

```json
{
  "eventId": "<UUID estable del origen>",
  "instanceCountryId": "<instancia activa>",
  "employeeId": "<UUID de SGI conocido por SMC>",
  "persona_id": 123,
  "factType": "ROUTE_COMPLETION",
  "occurredAt": "2026-10-27T13:00:00Z",
  "value": 80.00,
  "pointId": "<punto>",
  "postId": "<puesto de la asignación o del plan>",
  "shiftId": "<ocurrencia de turno>",
  "metadata": {
    "source": "patrol_execution",
    "sourceId": "<ejecución>",
    "hitosCumplidos": 8,
    "hitosTotales": 10,
    "protocolVersion": 3
  },
  "correlationId": "<mismo eventId>"
}
```

Los marcadores son ilustrativos. `protocolVersion` corresponde al `version_no` de la definición de patrulla leída, no al puntaje/regla SMC. Puesto efectivo de consignas procede de la asignación, con fallback a la consigna. No se cambia el ID del empleado ni se infieren equivalencias; SMC debe tenerlo previamente sincronizado y con catálogo de cargo.

`persona_id` se agrega al nivel superior, junto a `employeeId`, por solicitud del equipo SGI. Procede de `employee_operational_snapshot.persona_id` para el mismo empleado e instancia. Es un número entero y es adicional al `employeeId` obligatorio; no se repite en metadata. Si no hay vínculo, se omite sin inventarlo ni descartar el hecho. Este campo adicional debe confirmarse con SMC porque no figura en su contrato original; usarlo para identificar al agente requiere acuerdo con el receptor. Los payloads ya encolados no se modifican.

Se conservan `occurredAt` operacional, `eventId`, contexto y correlación. Los encabezados Idempotency-Key y X-Correlation-Id son el eventId; el ejecutor agrega autenticación, X-Interconnection-Id y X-Contract-Version.

## Entrega y recuperación

| Respuesta | Estado |
|---|---|
| 200/201 con `accepted: true` booleano y `smcFactId` textual no vacío de hasta 64 caracteres | SENT |
| 400/404/409/422 y otros rechazos 4xx definitivos | REJECTED |
| 401/403/408/429, 5xx, fallo CORE/credencial/red o recibo inválido | PENDING |

Los intentos persistidos cuentan intentos del publicador. El ejecutor puede hacer reintentos HTTP adicionales según CORE. Espera entre intentos: 30s, 60s, 120s, hasta 1h. Se guardan HTTP, código/mensaje remoto y recibo; no se guardan detalles de excepción con posibles secretos ni se registran payloads en logs.

Se bloquea solo la fila de entrega con `FOR UPDATE SKIP LOCKED` durante la llamada. Las restricciones únicas evitan duplicados al encolar; una caída tras aceptación remota puede reenviar el mismo eventId, que SMC debe reconocer. El bloqueo local evita ciclos simultáneos en una réplica y el bloqueo SQL coordina réplicas. No se bloquean tablas operativas.

Administración: roles nacionales `PRESIDENTE`, `GERENCIA_GENERAL`, `DIRECTOR_OPERACIONES_LATAM`, `DIRECTOR_OPERACIONES_NACIONAL` o `DIRECTOR_NACIONAL`. Sigue aplicándose el filtro de autenticación IDENT existente. Ambos endpoints se limitan a la instancia activa del backend, validada contra la instancia del token cuando está presente.

```http
POST /api/v1/integrations/smc/publish
POST /api/v1/integrations/smc/publish?retryRejected=true
GET /api/v1/integrations/smc/deliveries?status=REJECTED
```

Publicación responde `{enabled,busy,enqueued,attempted,requeued}`. No debe interpretarse `attempted` como cantidad aceptada: consultar entregas. Listado: últimas 100 filas, sin el payload. `retryRejected=true` reencola hasta un lote de rechazados: usar después de resolver identidad, catálogo o periodo. No corrige datos ni cambia el eventId; un conflicto de contenido requiere un acuerdo de corrección con SMC. No activar reproceso periódico de rechazados.

## Limitaciones que deben acordarse en UAT

- Antes de la primera extracción, el denominador y versión se leen de la configuración existente. Si cambió después de ejecutar la patrulla, no hay un snapshot histórico garantizado para reconstruirla. Una vez encolado, el payload no cambia.
- `confirmed_at` de consignas es hora de registro en Comando; no necesariamente hora de ejecución offline. Se respeta la fecha solicitada por la especificación, pendiente de acuerdo con SMC.
- Si Operador únicamente envía `CUMPLIDA`, no habrá incumplimientos de consignas para exportar.
- La política de tardías, emergencias, correcciones y multiplicidad de rondas sigue pendiente. Esta entrega selecciona resultados finales normales y un hecho por ejecución.
- Sin CORE/credencial/empleados/periodos de SMC disponibles no puede afirmarse validación real de extremo a extremo.

## Validación reproducible

`SmcIncrementalDiscoveryTest`: PostgreSQL real, tablas de origen clonadas del esquema migrado, inicio automático sin carga histórica, continuidad con otra instancia del publicador, sincronización tardía, lotes con timestamps iguales, rollback de entregas y cursor, transacción escritora que confirma tarde, fecha opcional y bandera apagada. Usa esquemas aislados que elimina al terminar; ejecutar sobre una base de pruebas.

`SmcOpenBaoTest`: resolver/ejecutor productivos sobre HTTP local con dobles CORE/OpenBao/IDENT/SMC; AppRole, KV v2, Bearer y M2M, credencial no vault y denegación de OpenBao. No llama a servicios reales.

`IdentServiceTokensTest`: extracción del destino, incluido el alias SMC con doble guion bajo, y falta de configuración del cliente M2M.

No se cambian bases operativas ni se despliega/activa automáticamente esta entrega.

### Evidencia incremental del 2026-10-28

`mvn -B -ntp -Dtest=SmcIncrementalDiscoveryTest,SmcOpenBaoTest,IdentServiceTokensTest test`: **13 pruebas, 0 fallos, 0 errores** sobre PostgreSQL temporal y servicios HTTP locales. Flyway aplicó V93. Se validaron arranque sin fecha, continuidad con una instancia nueva del publicador, recepción tardía, desempate de lotes, rollback atómico y confirmación tardía de una transacción concurrente. Los esquemas de fixtures se clonan de las tablas migradas y se eliminan al terminar. No se desplegó ni se aplicó V93 en DEV.

Para el despliegue normal, quitar o dejar vacía `SGI_SMC_FACTS_SINCE` **antes** de la primera ejecución con V93 y recrear el backend con las variables del ambiente. El primer ciclo crea los cursores y puede responder `enqueued: 0`; las ejecuciones siguientes recogen novedades. Mantener el valor histórico anterior inicia un backfill deliberado al crear el cursor.

### Evidencia local del 2026-10-27

- Compilación con Maven 3.9.11 / JDK 25: correcta.
- `mvn -B -ntp -Dtest=SmcContractTest,SmcHttpTest,SmcDatabaseTest test`: **10 pruebas, 0 fallos, 0 errores**. Incluye rechazo 401 a anónimos y 403 a un Coordinador en la administración SMC.
- Suite completa con SMC: 214 pruebas, 54 fallos de aserción y 47 errores.
- Control sin cambios, exportado desde el commit `0ae61de`, con otra base temporal y el mismo ambiente: 204 pruebas, los mismos 54 fallos y 47 errores. Se compararon clase, método y tipo de cada fallo: sin diferencias.
- Los fallos existentes corresponden a pruebas con autenticación UAT que reciben 401 y fixtures faltantes (por ejemplo el puesto esperado por `OperatorFixtures`). No se modificaron esos módulos para hacer pasar la suite.
- PostgreSQL y MinIO temporales; CORE/SMC dobles HTTP locales. Para JDK 25 se usó `JAVA_TOOL_OPTIONS=--add-opens=java.base/java.lang=ALL-UNNAMED` en los contenedores de pruebas.
- Postman CLI no pudo iniciar por una política corporativa de Private Cloud; la prueba HTTP usa `HttpServer` local y el resolver/ejecutor reales de Comando, con credencial exclusivamente de prueba.
- Pendiente: validación contra CORE/IDENT/SMC reales en UAT, reconocimiento de empleados, periodos y efecto de valoración en SMC.


## Intervalo configurable del publicador (2026-11-11)

El intervalo predeterminado es una hora (3600 segundos). SGI_SMC_PUBLISH_EVERY_SECONDS
configura tanto la espera inicial tras arrancar como la frecuencia posterior. Aplica
a todas las fuentes del publicador SMC, incluidos los kilómetros. Ejemplos:
1800 = 30 minutos; 3600 = una hora; 7200 = dos horas. Al cambiar una variable del
contenedor se debe recrear el backend. Los reintentos HTTP internos de una llamada
y el backoff de las entregas no cambian; las entregas solo se revisan en cada ciclo
automático o mediante publicación manual.

## Novedades aprobadas y pendientes operativos (2026-11-11)

`NOVELTY_APPROVED` se obtiene directamente del flujo formal de Novedades. Requiere
estado `APPROVED` y una decisión auditada `APPROVE` con resultado `APPROVED` del
mismo tenant. Incluye incidentes, hallazgos y vulnerabilidades. `PENDING`,
`CONFIRMED`, `DISCARDED` y registros sin auditoría de aprobación no se publican.

El beneficiario es `operator_operational_report.employee_id`, nunca el aprobador.
El valor es 1 y `occurredAt` es la fecha de la primera aprobación auditada. No
exige fotografías adicionales, sorteo ni una adjudicación de la regla local
`NOV_APROBADA`. La aprobación conserva sus validaciones y transacción existentes.
Su tarea STC debe crearse correctamente para que la aprobación se confirme.

La fuente es `NOVELTY_APPROVAL`, con el ID de la novedad como `source_id`.
Repetir la aprobación o reintentar el envío conserva el mismo `eventId` y
`Idempotency-Key`. La bandeja `smc_fact_delivery` guarda el payload de entrega;
los ciclos usan el intervalo horario configurable existente. V140 amplía la
restricción del cursor y agrega un índice de decisiones; debe aplicarse después
de V139 antes de ejecutar el publicador actualizado. No reinicia cursores.

Como en las demás fuentes, sin `SGI_SMC_FACTS_SINCE` el primer ciclo inicializa
el cursor en el momento de arranque y no recupera aprobaciones anteriores.
Una fecha inicial explícita solo afecta cursores aún no creados; no cambia uno
existente. Una recuperación histórica debe coordinarse con el periodo abierto
de SMC; no se reinician cursores automáticamente.

`PATROL_KM` conserva precisión máxima de 25 m, separación máxima de 180 s,
captura cada 120 s y velocidad máxima de 5 km/h. Los límites de 25 m y 180 s
son valores iniciales aprobados para UAT; no se presentan como reglas originales
de SMC. V137 crea la medición y V139 admite su fuente en el cursor.

`POINT_RESPONSIBLE` sigue pendiente. El usuario definió contar únicamente agentes
con asistencia validada, excluyendo al responsable. La designación de
`point_responsibility` es planificada (ASI-DEC-059). El ingreso actual de relevo
guarda `relief_event.status='PENDIENTE'`; no hay un flujo implementado que confirme
asistencia/identidad. Las revisiones VISINT actuales son de fotos del puesto y
no acreditan esa asistencia. Por ello no se exporta un número de agentes
planificados como si fueran asistentes validados y no se crea una aprobación
manual nueva. Para completar este hecho falta disponer de la fuente real de
asistencia validada y acordar cómo cerrar el conteo ante validaciones tardías.

Las pruebas usan PostgreSQL temporal y HTTP local. No demuestran recepción ni
valoración en SMC real. La APK de Operador y la captura GPS con pantalla apagada
y reconexión siguen pendientes de compilación con JDK 17/Android SDK y UAT física.

### Evidencia de esta ampliación

23 pruebas aprobadas, 0 fallos y 0 errores: PatrolDistanceCalculatorTest (6),
PatrolDistanceDatabaseTest (4), SmcImpulseFactsDatabaseTest (3),
SmcApprovalFactsDatabaseTest (3), SmcImpulseHttpTest (1) y SmcOpenBaoTest (6).
Se verificaron límites GPS inclusivos de 25 m/180 s y configuración efectiva,
aprobación auditada, beneficiario original, exclusión de otros estados/tenants,
paginación con fechas iguales, deduplicación y HTTP local con idempotencia.
La cadena real V93 + V122 + V139 + V140 pasó la prueba SQL aislada, conservando
cursores y rechazando fuentes desconocidas. No se conectó a SMC real.

Una comprobación de solo lectura en PostgreSQL local encontró 121 relevos,
todos PENDIENTE, y ninguna tabla específica de asistencia validada. En esa base
no aparecían migraciones V137 o posteriores en flyway_schema_history al momento
de consultar. Esta sesión no aplica migraciones ni despliega el backend allí.

## Auditoría de datos de salida hacia SMC (2026-11-11)

Se revisó el recorrido SmcFactSources -> SmcFact -> smc_fact_delivery ->
SmcFactPublisher -> SmcAdapter -> GenericInterconnectionExecutor.

| Campo | Procedencia operacional |
|---|---|
| instanceCountryId | instance_country_context vigente, reconciliado con CORE |
| employeeId | employee_id del origen o del registro de evaluación; sin empleado de ejemplo de respaldo |
| persona_id | employee_operational_snapshot del mismo tenant; se omite si no existe |
| pointId, postId, shiftId | novedad, configuración operativa y asignación/turno vinculados al origen |
| occurredAt | aprobación auditada, finalización de patrulla, confirmación de consigna o fecha operacional de la evaluación |
| value de PATROL_KM | kilómetros del snapshot patrol_distance_measurement calculado desde muestras GPS recibidas |
| value de ROUTE_COMPLETION | porcentaje calculado con hitos cumplidos y totales de las tablas operativas |
| value de los hechos unitarios | 1 por cada ocurrencia elegible encontrada; no representa una nota ni el premio local |
| eventId e Idempotency-Key | identificador determinista derivado del tipo y el ID del registro origen |

No se sustituyen identidades, fechas ni mediciones faltantes por datos de ejemplo.
El UUID aleatorio de smc_fact_delivery es la clave interna de la bandeja, no un
agente ni una medición. La fecha actual se usa para cursores/backoff, no para
reemplazar occurredAt. Los códigos de hechos, estados y fuentes son vocabulario
del contrato. Los límites GPS configurados son reglas de cálculo. maxSpeedKmh
ahora usa la misma constante que el calculador, evitando duplicar el literal.
La URL se resuelve en CORE y la autenticación usa OpenBao/IDENT; los tokens de
prueba del harness HTTP no son fallback del transporte operativo.

Se agregó un filtro para no extraer impulsos o porcentajes de patrulla asociados
a visual_review.simulated=true. También se revisa esa condición antes de enviar
una entrega pendiente: se marca REJECTED con SIMULATED_OPERATIONAL_SOURCE y no
se invoca HTTP. Se excluye conservadoramente todo hecho vinculado a una revisión
marcada como simulada, aunque tenga otras capturas; los cursores no se reinician.
La consulta respeta tenant y relaciona la evidencia de consigna mediante el
cumplimiento y su consignment_id. No modifica ni borra los resultados de UAT.
Este control no afecta distancias GPS ni novedades aprobadas por el solo hecho
de existir una revisión simulada en un proceso independiente.

La inspección de solo lectura de la base local encontró la bandeja SMC vacía,
ninguna revisión marcada como simulada, ninguna revisión con modelo
visint-simulado sin esa marca y ninguna evaluación sin empleado en el snapshot.
No se enviaron datos a SMC real ni se cambió la base operativa.

Alcance: la validación acredita consultas y procedencia técnica. Un registro de
prueba insertado manualmente en tablas operativas sin ninguna marca de prueba
no puede distinguirse automáticamente de uno real. Esta auditoría no certifica
la veracidad física de una captura GPS ni cambia el catálogo de personal.

Validación final: 27 pruebas aprobadas, 0 fallos y 0 errores. Incluye los campos
extraídos de PostgreSQL hasta el request HTTP local, error 503 y reintento 201
con el mismo payload y la misma clave de idempotencia, cálculo de 50% desde un
hito cumplido de dos registrados, exclusión de evidencia simulada de Patrullas,
Bitácora y Consignas, aislamiento por tenant y rechazo de una entrega simulada
ya encolada antes de cualquier llamada HTTP. Los fixtures y tokens del harness
son de pruebas aisladas; no se usaron destinos ni credenciales reales de SMC.
La imagen del backend empaqueta quarkus-app y no incorpora los fixtures de
src/test al servicio operativo. Los cambios no se desplegaron en esta sesión.
