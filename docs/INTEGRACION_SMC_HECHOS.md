# Entrega de hechos SGI Comando -> SMC

Primera entrega, 2026-10-27. Contrato de entrada SMC-002v0.5 del 2026-10-27.
Activación controlada por `SGI_SMC_ENABLED`. No adjudica Impulsos, calcula el ID ni modifica tablas operativas.

## Alcance

- `ROUTE_COMPLETION`: ejecución finalizada `COMPLETA`/`INCOMPLETA`, patrulla `CLOSED` y `PROGRAMMED`, al menos un hito configurado. Valor = hitos distintos `CUMPLIDO` / hitos configurados * 100, HALF_UP a dos decimales. Un 100 también se entrega como evidencia.
- `CONSIGNMENT_NOT_DONE`: registro explícito `consignment_compliance.result=NO_CUMPLIDA`, valor 1. La ausencia de registro, `CON_NOVEDAD`, `CUMPLIDA` y `NO_APLICA` no generan hechos.
- Cada origen se registra una vez en `smc_fact_delivery`; no se usa la bandeja SQLite del teléfono ni el outbox general de Comando.
- No incluye `NO_EJECUTADA`, francos trabajados, Impulsos, cierre de ocurrencias ni consulta del ID de SMC. No se alteran las rutas de ingreso de Operador.

Patrullas lee el cierre de `/api/v1/operator/patrol-executions` y sus resultados `patrol_checkpoint_execution`. También permite resolver la definición por `patrol_execution.patrol_definition_id` si existe; exige siempre cierre y resultado final. El registro individual en `task_execution` no es por sí solo una patrulla finalizada y no se transforma en un cierre artificial ni en demérito.

## Activación

El equipo confirmó el binding de CORE `SGI_COM_SMC__0001_v001` (dos guiones bajos antes de 0001). Se usa como valor predeterminado en configuración, no como constante Java. Configurar la interfaz registrada en CORE, método POST y ruta `/api/v1/operational-facts`, destino SMC y referencia de credencial de servicio. El catálogo local conserva su identificador canónico; la resolución de esta integración usa el ID configurado para el ambiente.

Aplicar Flyway V91 en una base UAT antes de activar:

```dotenv
SGI_SMC_ENABLED=false
SGI_SMC_INTERCONNECTION_ID=SGI_COM_SMC__0001_v001
SGI_SMC_INTERFACE_ID=SGI_COM_SMC_0001_IF01
SGI_SMC_PUBLISH_EVERY_SECONDS=30
SGI_SMC_BATCH_SIZE=100
# Acordar el corte con SMC. Ejemplo ilustrativo, no activar sin validarlo:
SGI_SMC_FACTS_SINCE=2026-10-27T00:00:00-05:00
```

`facts-since` es obligatorio para publicar con la bandera activa. Se compara con `finished_at` en patrullas y `confirmed_at` en consignas; una fecha anterior habilita carga histórica. No hay backfill implícito. La ausencia/fecha inválida produce 503 en publicación manual y no encola ni envía. Con la bandera apagada la publicación es una operación sin efectos.

Una vez acordado el corte, identidad de los agentes y periodo abierto en SMC, activar `SGI_SMC_ENABLED=true` y recrear el backend con las variables. No hay URL/token propio de SMC en Comando: se usa `GenericInterconnectionExecutor` con CORE y su resolución de credenciales.

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

La bandera `SGI_SMC_ENABLED` y el corte `SGI_SMC_FACTS_SINCE` siguen siendo configuración independiente. Con `enabled=false` el publicador no resuelve el binding SMC ni lee su credencial de OpenBao; otras operaciones de Comando pueden seguir consultando CORE. Si OpenBao no está disponible o rechaza el acceso, la entrega queda PENDING para reintento. La configuración real en CORE/OpenBao debe comprobarse en DEV; no se modifica desde estas pruebas.

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

`SmcContractTest`: porcentajes, identidad estable, validación de recibos, clasificación de errores, backoff y bandera apagada/corte obligatorio.

`SmcHttpTest`: adaptador y ejecutor/resolver productivos sobre HTTP real local; dobles de CORE/SMC, encabezados, credencial de prueba, duplicado, 404, 503 y servidor desconectado. No llama a SMC real.

`SmcDatabaseTest`: Quarkus/PostgreSQL, fuentes y bandeja temporal con estructura de V91, exclusiones, aislamiento por instancia, deduplicación, payload inmutable, estados, fallo de transporte y reproceso. Cada prueba usa tablas temporales eliminadas al cerrar la transacción; se debe ejecutar sobre una base de pruebas.

No se cambian bases operativas ni se despliega/activa automáticamente esta entrega.

### Evidencia local del 2026-10-27

- Compilación con Maven 3.9.11 / JDK 25: correcta.
- `mvn -B -ntp -Dtest=SmcContractTest,SmcHttpTest,SmcDatabaseTest test`: **10 pruebas, 0 fallos, 0 errores**. Incluye rechazo 401 a anónimos y 403 a un Coordinador en la administración SMC.
- Suite completa con SMC: 214 pruebas, 54 fallos de aserción y 47 errores.
- Control sin cambios, exportado desde el commit `0ae61de`, con otra base temporal y el mismo ambiente: 204 pruebas, los mismos 54 fallos y 47 errores. Se compararon clase, método y tipo de cada fallo: sin diferencias.
- Los fallos existentes corresponden a pruebas con autenticación UAT que reciben 401 y fixtures faltantes (por ejemplo el puesto esperado por `OperatorFixtures`). No se modificaron esos módulos para hacer pasar la suite.
- PostgreSQL y MinIO temporales; CORE/SMC dobles HTTP locales. Para JDK 25 se usó `JAVA_TOOL_OPTIONS=--add-opens=java.base/java.lang=ALL-UNNAMED` en los contenedores de pruebas.
- Postman CLI no pudo iniciar por una política corporativa de Private Cloud; la prueba HTTP usa `HttpServer` local y el resolver/ejecutor reales de Comando, con credencial exclusivamente de prueba.
- Pendiente: validación contra CORE/IDENT/SMC reales en UAT, reconocimiento de empleados, periodos y efecto de valoración en SMC.
