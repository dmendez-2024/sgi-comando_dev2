# Socket de Mensajes OBSINT: implementación REST con cm-socket

Actualizado: 9 de noviembre de 2026. Repositorio: `sgi-comando_dev`.

Este documento sustituye la descripción provisional basada en un cliente WebSocket saliente. El ajuste se implementó a partir de `AJUSTE_SOCKET_SGI_COMANDO.md`, proporcionado por el usuario. Comando publica por HTTP; la aplicación Operador o Postman recibe por WebSocket.

## Estado de la implementación

- Publicación REST directa a cm-socket, sin CORE ni Interconexiones.
- Una publicación por agente en `persona.<persona_id>` de DHO/IDENT.
- Selección de todos los agentes del Punto o de agentes específicos de su turno actual.
- Entrega y lectura por agente, incluyendo hora y vía de confirmación.
- Cola persistente, reintentos, vencimiento y sincronización REST de la app.
- Socket apagado por defecto: los mensajes siguen siendo TEST.
- La publicación contra el gateway real y el reenvío de ACK desde infraestructura requieren una verificación integrada posterior. Las pruebas automáticas utilizan un gateway HTTP local.

## Flujo

```text
Consola OBSINT
  POST /api/obsint/tasks/{taskId}/messages
  {text, target: POINT | AGENTS, employeeIds?}
        |
        v
Comando guarda mensaje y destinatarios en una transacción
        |
        | después del commit / ciclo periódico
        v
POST https://apps.cajamarca.ec/dev.socket/api/v1/publish
  {id: recipientId, source: sgi-comando,
   channel: persona.<persona_id>, event: obsint.mensaje, payload: {...}}
        |
        v
cm-socket -- WebSocket --> SGI Operador / Postman
        |
        | reenvío de ack de la aplicación
        v
POST /api/internal/socket/acks --> recibo SOCKET_ACK

SGI Operador con IDENT
  GET /api/v1/operator/messages
  GET /api/v1/operator/messages/rooms
  POST /api/v1/operator/messages/{recipientId}/receipt
        |
        v
recibo OPERATOR_API --> lectura confirmada a nombre del agente

Consola consulta mensajes cada 10 s mientras haya destinatarios pendientes
```

No se crean canales de grupo por Punto. Un mensaje a varios agentes produce N destinatarios y N publicaciones independientes. El UUID de cada destinatario se usa como identificador estable en todos los reintentos.

## Contrato y canales

La base del gateway es `https://apps.cajamarca.ec/dev.socket`, sin `/ws` ni parámetros. Comando agrega `/api/v1/publish` para publicar, `/api/v1/presence/{canal}` para presencia y `/q/health/ready` para diagnóstico.

Ejemplo ilustrativo de publicación; los UUID y el texto son de muestra:

```json
{
  "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "source": "sgi-comando",
  "channel": "persona.9428",
  "event": "obsint.mensaje",
  "payload": {
    "messageId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
    "recipientId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    "taskId": "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
    "pointId": "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
    "postId": "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
    "from": "Monitor Consola",
    "text": "Mensaje de prueba",
    "sentAt": "2026-11-09T17:00:00Z",
    "replyAllowed": false,
    "pointName": "Telco",
    "postName": "Garita uno",
    "roomId": "ffffffff-ffff-4fff-8fff-ffffffffffff"
  }
}
```

`from` es el nombre de la sala del mensaje: en los de la Consola OBSINT, `Monitor Consola`. Si el mensaje no tuviera sala, sale el cargo del monitorista (`obsint_agent_message.sender_role`, tomado de `employee_operational_snapshot.role_code`), o `Monitor Consola` si no se conoce. El cargo queda guardado en Comando para auditoría; al agente no le llega ni el nombre, ni el usuario, ni identificadores del monitorista. Lo mismo devuelve `GET /api/v1/operator/messages` (0033) y el recibo (0034).

El canal utiliza `employee_operational_snapshot.persona_id`, no el UUID `employee_id`, ni la identidad del Monitor. No se fija el canal 9428 en el código: cada agente de turno aporta su persona.

El contrato proporcionado indica que el gateway no autentica las publicaciones ni almacena mensajes. El código no envía Authorization Bearer; envía `source=sgi-comando`. Ese source debe estar habilitado en el gateway.

| Respuesta de publicación | Acción |
|---|---|
| 2xx, normalmente 202 | SENT; guardar `localRecipients` si existe. |
| 400 | FAILED con `SOCKET_400: <motivo recortado>`. |
| Otros HTTP, timeout o error de red | Mantener PENDING, sumar intento y programar reintento. |
| 202 con `localRecipients=0` | SENT; no reintentar. La app podrá obtenerlo mediante sincronización REST. |

La URL WebSocket reportada por el usuario en Postman es:

```text
wss://apps.cajamarca.ec/dev.socket/ws?channels=persona.9428
```

Esa URL corresponde al receptor. No debe utilizarse como `SGI_SOCKET_URL` en Comando. La configuración del publicador solo admite bases HTTP/HTTPS sin credenciales, query ni fragmento.

## Salas de chat

Cada mensaje al agente pertenece a una sala de Comunicación (`communication_room`, migración V125). Todos los mensajes de la Consola OBSINT van a la sala predeterminada **Monitor Consola** (`code = MONITOR_CONSOLA`, `source = OBSINT_CONSOLE`): la migración la crea para la instancia y le asigna los mensajes anteriores; si falta, `CommunicationRooms.monitorConsole` la crea al enviar. El mensaje lleva `roomId` en 0033, 0034 y `obsint.mensaje`, y la 0037 (`SGI_OPR_SGI_COM_0037`, por registrar en CORE) lista las salas. Por ahora las salas de la Consola OBSINT (Monitor Consola) solo se listan al Agente de Seguridad (`AGENTE_SEGURIDAD`); el Supervisor de Seguridad no las recibe en la 0037. La web de SGI: Comando no cambia. SGI Operador todavía muestra un solo chat; la lista de salas en la app queda pendiente.

## Push FCM (app en segundo plano o cerrada)

El socket solo llega con la app en pantalla. Para avisar con la app en segundo plano o cerrada, Comando envía además un push de Firebase Cloud Messaging (HTTP v1, proyecto `sgi-operador-bae31`) con `push/PushPublisher`:

- **Cuándo:** una vez por destinatario, en `ObsintMessageDispatcher.deliverOne`, cuando el gateway acepta la publicación (SENT). Requiere `SGI_SOCKET_ENABLED=true` y `SGI_PUSH_ENABLED=true`; con el socket apagado los mensajes quedan TEST y no sale push.
- **A quién:** al topic `persona.<persona_id>`, el mismo nombre del canal del socket. La app se suscribe al iniciar sesión el Agente y se desuscribe al cerrar o expirar la sesión.
- **Qué:** mensaje de solo datos (sin bloque `notification`), `android.priority=HIGH` y `ttl` = `SGI_SOCKET_MESSAGE_TTL_MINUTES`. En `data`: `event` (`obsint.mensaje`), `channel`, `recipientId`, `roomId` y `payload` (el mismo payload del socket, como JSON). La app no duplica el mensaje (por `recipientId`) y no muestra el aviso si está en pantalla.
- **Mejor esfuerzo:** si FCM falla, el destinatario conserva su estado y el mensaje sigue llegando por el socket y por la 0033. Queda auditado en `obsint_agent_message_recipient.push_sent_at` / `push_error` (migración V126). Los logs no incluyen el contenido.
- **Autenticación:** token OAuth de la cuenta de servicio de Firebase (`google-auth-library-oauth2-http`, alcance `firebase.messaging`), cargada al primer envío y renovada al vencer.
- **Seguridad:** el topic no tiene autenticación; quien conozca un `persona_id` podría suscribirse desde la app. Si se requiere más, registrar el token del dispositivo en Comando con una interconexión nueva.

## Selección del agente

La resolución cruza asignación operativa, turno y Puesto del Punto de la consigna. Usa el empleado efectivo (`actual_employee_id` o, en su ausencia, `employee_id`), descarta REMOVED y exige `starts_at <= ahora < ends_at`, en la misma instancia-país.

Se deduplica por empleado. Si hubiera varios snapshots del empleado para una asignación, se toma el más reciente y se registra una advertencia sin contenido del mensaje. Nombre y Puesto se guardan en el destinatario al enviarlo.

- POINT, también por defecto cuando no se indica destino: todos los agentes actuales del Punto.
- AGENTS: solo los UUID indicados, sin duplicados y con máximo 50. La lista vacía o un agente fuera del turno actual producen 400 antes de persistir el mensaje.
- Sin agentes en POINT: FAILED / NO_AGENT_ON_SHIFT.
- Agente sin `persona_id`: destinatario FAILED / NO_PERSONA_LINK. Los demás agentes siguen su envío.
- Socket apagado: TEST, sin filas destinatarias ni publicación, conservando el destino solicitado. Las selecciones AGENTS se validan igualmente.

## Persistencia y despacho

Se conserva V119 sin modificaciones. La nueva migración es:

```text
backend/src/main/resources/db/migration/V120__obsint_agent_message_persona_and_receipts.sql
```

Agrega `target` a `obsint_agent_message` y, al destinatario: `persona_id`, `local_recipients`, `delivered_via`, `read_via`, `app_rejected_at`, `full_name` y `post_name`. `persona_id` permite null para filas antiguas; si una fila antigua PENDING no lo tiene al despachar, falla con NO_PERSONA_LINK.

El destinatario persistido sigue siendo la cola: no se modifica `outbox_event`. El evento CDI despierta el despacho solo después de confirmar la transacción. Una tarea programada también revisa la cola cada cinco segundos, después de un retraso inicial de veinte segundos. Un indicador evita ejecutar ambas vías simultáneamente en la misma instancia.

Cada destinatario se obtiene con `FOR UPDATE SKIP LOCKED` y se procesa en una transacción nueva. También se bloquea su mensaje padre al recalcular el agregado. Los reintentos de errores transitorios empiezan en 5, 10, 20 segundos y crecen hasta un máximo de cinco minutos. Al no existir conexión persistente, cada intento HTTP fallido sí suma un intento.

El TTL predeterminado de treinta minutos solo vence PENDING. SENT no vence por falta de recibo. Ante pérdida de la respuesta HTTP puede haber publicación repetida; el receptor debe deduplicar por `recipientId`. Los mensajes TEST anteriores no se convierten ni se reenvían al encender el socket.

## Estados y recibos

| Estado | Significado |
|---|---|
| TEST | Guardado con envío apagado. |
| PENDING | Pendiente de aceptación por el gateway. |
| SENT | Gateway aceptó la publicación; no prueba recepción ni lectura. |
| DELIVERED | Se recibió confirmación de entrega. |
| READ | Se recibió confirmación de lectura. |
| FAILED | No hay agente, no hay vínculo de persona, rechazo de publicación o vencimiento. |

El agregado ahora toma el **menor avance entre los no fallidos**. Todos fallidos produce FAILED. Un READ y un SENT producen SENT; un READ, un DELIVERED y un FAILED producen DELIVERED. READ agregado significa que todos los destinatarios no fallidos llegaron a READ; el detalle y los contadores permiten identificar a los fallidos.

Los contadores son `total`, `sent`, `delivered`, `read` y `failed`. `sent` incluye DELIVERED y READ; `delivered` incluye READ.

`ObsintMessageReceipts` concentra la lógica compartida:

- Los recibos no retroceden estados y son idempotentes.
- READ completa también la entrega si faltaba.
- Un PENDING o un FAILED por EXPIRED puede confirmarse después, por ejemplo al sincronizar la app.
- Un fallo por NO_PERSONA_LINK no se recupera mediante recibo.
- REJECTED del socket deja `appRejectedAt` y `lastError=APP_REJECTED`; no cambia el estado.
- Una fecha inválida o más de cinco minutos futura se sustituye por la hora del servidor.
- Si la lectura llegó primero como SOCKET_ACK y luego por OPERATOR_API, se conserva la primera hora y se mejora la vía a OPERATOR_API.
- Se registra auditoría con mensaje, destinatario, empleado, estado y vía; sin texto del mensaje.

SOCKET_ACK identifica la confirmación recibida desde el gateway, cuyo contrato no autentica a la persona. OPERATOR_API utiliza la identidad autenticada existente del agente y es la vía que permite mostrar “Leído (confirmado en la app)”.

## APIs

| Ruta | Uso |
|---|---|
| `GET /api/obsint/tasks/{taskId}/agents-on-shift` | Agentes actuales, nombre, Puesto, reachable y online. Requiere propiedad de la tarea igual que Mensajes. |
| `POST /api/obsint/tasks/{taskId}/messages` | Body `{text,target,employeeIds}`; mantiene compatibilidad con solo `{text}`. |
| `GET /api/obsint/tasks/{taskId}/messages` | Mensajes, destino, estado, contadores y detalle por agente. |
| `GET /api/v1/operator/messages?since=<ISO-8601>` | Mensajes propios dentro de la ventana de sincronización, incluidos PENDING y FAILED por EXPIRED. |
| `GET /api/v1/operator/messages/rooms` (0037) | Salas de chat de Comunicación de la instancia, con el último mensaje propio (`lastMessageAt`, `lastMessageFrom` = nombre de la sala, `lastMessageText`) y `unreadCount`. La predeterminada, Monitor Consola, va primero aunque esté vacía. Por ahora Monitor Consola solo la ve el Agente de Seguridad: al Supervisor de Seguridad no se le lista (recibe `[]` mientras no haya otras salas). |
| `POST /api/v1/operator/messages/{recipientId}/receipt` | DELIVERED o READ, con la identidad de SGI Operador. REJECTED devuelve 400; otro propietario devuelve 404. |
| `POST /api/internal/socket/acks` | Callback del gateway, disponible únicamente si el socket está habilitado. |

Callback del gateway:

```json
{
  "messageId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "channel": "persona.9428",
  "status": "READ",
  "connectionId": "conexion-del-gateway",
  "at": "2026-11-09T17:01:00Z"
}
```

`messageId` aquí es el UUID del destinatario, no el UUID del mensaje padre. El canal debe coincidir exactamente con la persona del destinatario.

Controles del callback:

- Socket apagado: 404.
- Token configurado: exige `X-Socket-Ack-Token` mediante comparación en tiempo constante; de lo contrario 401.
- IPs/CIDR configurados: comprueba la IP remota de la conexión, sin confiar en `X-Forwarded-For`; de lo contrario 403.
- Destinatario inexistente o UUID inválido: 404.
- Canal incorrecto: 409.
- Estado distinto de DELIVERED, READ o REJECTED: 400.
- Recibo válido, incluso repetido: 204.

Sin restricciones de origen configuradas, el callback acepta cualquier origen mientras el socket esté encendido y lo advierte una vez al arrancar. En servidores se deben configurar las IPs del gateway y, si este lo soporta, el token. Detrás de un proxy, la IP observada será la de la conexión del proxy; por eso se recomienda una ruta interna directa del gateway al backend.

## Configuración

| Variable | Predeterminado |
|---|---|
| SGI_SOCKET_ENABLED | false |
| SGI_SOCKET_URL | https://apps.cajamarca.ec/dev.socket |
| SGI_SOCKET_SOURCE | sgi-comando |
| SGI_SOCKET_EVENT | obsint.mensaje |
| SGI_SOCKET_CHANNEL_PREFIX | persona. |
| SGI_SOCKET_HTTP_TIMEOUT_SECONDS | 5 |
| SGI_SOCKET_DISPATCH_EVERY | 5s |
| SGI_SOCKET_DISPATCH_BATCH_SIZE | 50 |
| SGI_SOCKET_MESSAGE_TTL_MINUTES | 30 |
| SGI_SOCKET_OPERATOR_SYNC_HOURS | 24 |
| SGI_SOCKET_PRESENCE_ENABLED | true |
| SGI_SOCKET_ACK_ALLOWED_IPS | Vacío |
| SGI_SOCKET_ACK_TOKEN | Vacío |
| SGI_PUSH_ENABLED | false |
| SGI_PUSH_CREDENTIALS_PATH | Vacío (ruta al JSON de la cuenta de servicio, montado como secreto) |
| SGI_PUSH_CREDENTIALS_JSON | Vacío (alternativa: el JSON completo) |
| SGI_PUSH_PROJECT_ID | Vacío (usa el `project_id` de la cuenta de servicio) |
| SGI_PUSH_FCM_URL | https://fcm.googleapis.com |
| SGI_PUSH_HTTP_TIMEOUT_SECONDS | 5 |

Docker Compose declara activación, URL y controles del ACK. El resto se lee desde el entorno del backend. Se quitaron el token Bearer de publicación, los tiempos de reconexión/ack WebSocket y la dependencia `quarkus-websockets-next`.

La presencia se consulta con timeout de dos segundos y caché de quince segundos por canal. Si está apagada o falla, online es null. La salud del gateway usa timeout de dos segundos y caché de treinta segundos.

Diagnóstico:

```powershell
Invoke-RestMethod http://localhost:8080/q/health/group/socket
Invoke-RestMethod http://localhost:8080/q/health/ready
```

El grupo `socket` expone `enabled`, `reachable`, `url` y `lastPublishOk`. Ya no hay `connected`: no existe conexión permanente desde Comando. Apagado es UP; encendido usa la salud HTTP del gateway. Este grupo no participa en readiness, aunque el endpoint de salud global puede reflejar el DOWN del grupo.

Métricas: `sgi_socket_publish_total{result=accepted|rejected|unavailable}`, `sgi_socket_last_publish_ok` y `sgi_push_send_total{result=sent|failed}`. Los logs no incluyen el texto ni el payload. El registro por agente se consulta en los destinatarios persistidos y en la respuesta de Mensajes.

## Componentes del cambio

Bajo `backend/src/main/java/com/cajamarca/sgi/comando/`:

- `socket/SocketConfig`, `SocketPublisher`, `SocketPresence`, `SocketHealthCheck`, `SocketAckResource`.
- `obsint/ObsintAgentMessage`, `ObsintAgentMessageRecipient`, `ObsintAgentRecipients`, `ObsintMessageDispatcher`, `ObsintMessageQueued`, `ObsintMessageReceipts`, `ObsintService`, `ObsintResource`.
- `operator/OperatorMessageResource`.
- `push/PushConfig`, `PushCredentials`, `PushPublisher` (push FCM).

Se eliminaron `SocketClient`, `SocketProtocol` y la excepción de indisponibilidad del cliente anterior. Las respuestas del publicador REST clasifican el resultado sin lanzar errores de red al despachador.

Frontend: `frontend/src/obsint/obsintApi.ts` y `ObsintCommPanel.tsx`. El panel permite elegir destino y agentes, muestra presencia como indicador informativo y despliega estado y horas por agente. Refresca cada diez segundos mientras cualquier destinatario esté PENDING, SENT o DELIVERED. TEST conserva la etiqueta de prueba sin resumen de destinatarios.

## Pruebas y verificación

La primera ejecución específica del ajuste REST pasó: 13 pruebas, cero fallos y cero errores. Incluyó publicador HTTP 202/400/503 y timeout, selección de destinatarios, canales por persona, presencia, reintento con identificador estable, envío con cero conexiones, recibos individuales y controles de origen. Se ampliaron después las aserciones de selección inválida, callback apagado y recuperación por sincronización de destinatarios vencidos.

El backend y frontend compilaron correctamente. La suite completa posterior ejecutó **330 pruebas, con 88 fallos y 71 errores**. Se compararon los nombres de los métodos fallidos y su tipo (failure/error) con los 159 fallos/errores guardados antes del ajuste: **ninguno agregado, eliminado ni cambiado de tipo**. Las 13 pruebas específicas pasan también dentro de la suite completa, incluidas las aserciones ampliadas. La suite general no está en verde: conserva los problemas previos de autenticación UAT, fixtures y almacenamiento.

Las pruebas usan bases separadas y un gateway HTTP simulado bajo `src/test`. No publican mensajes al gateway real ni a agentes reales. El recurso de pruebas del operador emplea identidad controlada para validar propiedad y recibos; la integración con IDENT y Android real requiere prueba aparte.

Después de reconstruir y recrear los contenedores locales, se comprobó:

- V119 sigue aplicada y V120 se aplicó correctamente, sin modificar la migración anterior.
- Frontend HTTP 200 y `/q/health/ready` HTTP 200 / UP.
- Monitor conserva `/api/obsint/access` HTTP 200 con `enabled=true`.
- Grupo socket HTTP 200 / UP con `enabled=false`, `reachable=false`, base HTTPS correcta y `lastPublishOk=false`. Este resultado no acredita conectividad al gateway: conserva el apagado solicitado por defecto.

Se comparó además el acceso de red mediante consultas GET de salud, sin publicaciones:

- Con la IP interna fijada en Compose (`apps.cajamarca.ec:172.18.1.10`), la consulta desde la red Docker local agotó diez segundos sin respuesta HTTP.
- Con la resolución DNS normal del mismo dominio, desde esa red Docker, el gateway devolvió HTTP 200 y estado UP. El dominio resolvió a `181.198.110.146` durante la comprobación; esa IP es evidencia puntual, no un valor que deba fijarse en el código.
- No se modificó `extra_hosts`, siguiendo el ajuste proporcionado. Antes de habilitar envíos en esta máquina hay que resolver la accesibilidad de la ruta interna, o acordar un override exclusivamente local que utilice DNS normal manteniendo el nombre HTTPS y la validación TLS. La conexión de Postman no acredita que la ruta fijada en el backend sea accesible.

## Infraestructura y aplicación Operador pendientes

El equipo de cm-socket debe configurar `CM_SOCKET_SERVICE_SGI_COMANDO_ACK_URL` hacia `/api/internal/socket/acks` de Comando y permitir `sgi-comando` como source. Si se configura token en Comando, el gateway debe enviar `X-Socket-Ack-Token`. Según el contrato entregado, el gateway no reintenta el callback; el REST de Operador cubre la pérdida de esa confirmación.

La app debe suscribirse a `persona.<su persona_id>`, reconocer `obsint.mensaje`, deduplicar por id, emitir DELIVERED y READ con `ref=recipientId`, y confirmar READ también por REST con IDENT. Debe sincronizar al abrirse, reconectar o volver de segundo plano. No se implementaron esos cambios Android en este repositorio.

Para el push FCM: generar en la consola de Firebase (proyecto `sgi-operador-bae31` → Configuración → Cuentas de servicio) una clave privada, montarla como secreto en el backend (`SGI_PUSH_CREDENTIALS_PATH`) y permitir salida HTTPS a `fcm.googleapis.com` y `oauth2.googleapis.com`. Registrar el canal SGI_COM → FCM → SGI_OPR donde corresponda (CORE/SITC).

## Preparación local y repositorio

Ver [OBSINT_PRUEBAS_LOCALES.md](OBSINT_PRUEBAS_LOCALES.md) para Monitor UAT, Telco-City y los fixtures manuales. Esos datos no son parte de las migraciones ni deben cargarse en servidores. No se crearon agentes de turno reales ni se fijó una persona concreta para enviar mensajes.

Al integrar al repositorio:

1. Conservar V119 aplicada y agregar V120; comprobar que el número siga libre en las ramas concurrentes.
2. Mantener el envío apagado por defecto y excluir `.env`, tokens, bases de datos y videos de prueba.
3. Revisar separadamente los cambios preexistentes de `docker-compose.dev-local.yml` y los fixtures locales.
4. Configurar los controles de ACK en el entorno del servidor; no reemplazar IDENT con usuarios UAT.
5. Verificar con infraestructura el publish REST, presencia y callback antes de validar lectura en la app real.

Este trabajo no incluye commit, push, despliegue al servidor ni mensajería desde pantallas ajenas a una tarea OBSINT.

### Nombres actuales de Punto y Puesto en mensajes de Operador

El listado de Operador, la respuesta del recibo y el payload publicado incluyen `pointName` y `postName`. En la respuesta REST se agregan al final del registro, conservando los once campos originales. Los nombres se consultan en `post` y `point` de la misma instancia del destinatario; no se usan los nombres históricos almacenados en el destinatario. Si falta el Puesto, ambos nombres son null; si falta únicamente el Punto, su nombre es null.

El listado usa una sola proyección SQL con joins a mensaje, tarea, consigna, Puesto y Punto. No carga entidades por destinatario ni dispara consultas adicionales por cada mensaje. Se ordena por la fecha de envío del mensaje y el identificador del destinatario. El recibo reutiliza esa proyección para el mensaje actualizado. El despacho consulta los nombres una vez por destinatario y permite null en el payload. No se agrega una migración ni otros datos del Punto.

Verificación de este cambio: backend compilado; 14 pruebas de mensajería y socket ejecutadas, sin fallos ni errores. La prueba nueva comprueba nombres actuales frente a un nombre histórico diferente, dos Puestos del mismo Punto, Puesto inexistente, Puesto de otra instancia, nombres en la respuesta del recibo y contenido publicado al gateway simulado. No se publicaron pruebas al gateway real.

### Activación local solicitada posteriormente

El 9 de noviembre de 2026 el usuario solicitó activar el envío local. Se habilitó en `.env.socket-local` (ignorado por Git), con la cola de destinatarios vacía verificada antes de recrear el backend. El diagnóstico posterior devolvió `enabled=true` y `reachable=true`. No se publicaron mensajes de prueba; `lastPublishOk` permanece false hasta el primer envío aceptado.

Para alcanzar el gateway se ajustó únicamente el override `docker-compose.dev-local.yml`: conserva la entrada interna de `apps.seminter.com.ec` y permite resolver `apps.cajamarca.ec` por DNS normal, pues la IP interna fija no respondía. Los archivos compartidos y de servidor conservan sus valores predeterminados y su mapeo original. Ver los detalles y limitaciones de ACK en [OBSINT_PRUEBAS_LOCALES.md](OBSINT_PRUEBAS_LOCALES.md).
