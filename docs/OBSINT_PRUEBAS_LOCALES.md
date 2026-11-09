# OBSINT: acceso de Monitor y pruebas locales

El selector UAT del frontend utiliza la cuenta `monitor`. Elegir ese perfil no crea el usuario en la base de datos. Si falta, las peticiones devuelven HTTP 401 y el botón OBSINT no aparece.

## Preparación manual

El fixture `scripts/obsint-monitor-local.sql` solo admite la base `sgi_comando_codex_data` del proyecto Docker local `sgi-comando-uat`. Crea el usuario Monitor con el hash de la cuenta UAT `coord`, su empleado sintético y vínculo, y un turno de siete días en el puesto de Monitoreo MON-001 de Galvarino. También activa la bandera OBSINT del servicio del punto de muestra PTO-001 (Telco-City).

Ejemplo desde la raíz del repositorio, con el usuario PostgreSQL local `sgi`:

```powershell
Get-Content -Raw -Encoding UTF8 scripts/obsint-monitor-local.sql |
  docker exec -i sgi-comando-uat-postgres-1 psql -U sgi -d sgi_comando_codex_data -v ON_ERROR_STOP=1
```

La ejecución repetida conserva la cuenta y el turno vigentes. Si el turno venció, crea uno nuevo. No cambia automáticamente un plan existente en borrador.

Para disponer de un caso de prueba, ejecutar manualmente `scripts/obsint-persona-a-local.sql` de la misma forma y contra la misma base. Este segundo fixture también verifica el nombre de la base: crea el protocolo activo OBS-PRUEBA-A, cuatro consignas con horario diario completo y sus cámaras sintéticas en Telco-City. No sustituye otro protocolo activo existente. Las primeras tres cámaras requieren los videos locales `frontend/public/obsint/videos/1-1.mp4` a `1-3.mp4`; la cuarta permite probar Sin señal. Estos videos no se versionan. Al pulsar Iniciar caso, el backend crea el caso con las consignas pendientes; no se necesita un caso precargado.

Después, recargar la web y elegir Monitor. Comprobaciones con sus credenciales UAT: `GET /api/obsint/access` debe devolver HTTP 200 con `enabled=true`; `GET /api/obsint/cases/current` devuelve 204 si todavía no tiene un caso.

## Al subir al repositorio o desplegar

- Este fixture es soporte manual de desarrollo; no incorporarlo a Flyway, al arranque ni a scripts automáticos de despliegue. Nunca ejecutarlo en servidores compartidos o productivos.
- Versionar el código del socket, su migración y las pruebas con el socket apagado por defecto. El fixture y esta documentación pueden acompañarlos como herramientas explícitas para desarrollo.
- No publicar `.env`, credenciales reales, copias de base de datos ni datos de usuarios. Este fixture copia un hash dentro de la base local y no contiene una contraseña ni un hash concreto.
- Revisar por separado los cambios locales preexistentes de `docker-compose.dev-local.yml` y los otros fixtures OBSINT; no incluirlos automáticamente en el commit del socket.
- Para servidores, mantener IDENT y las asignaciones reales. No crear allí la cuenta UAT Monitor ni habilitar servicios de muestra.
- El acceso de Monitor no activa el socket. Con `SGI_SOCKET_ENABLED=false`, Mensajes conserva el estado TEST. Los estados SENT/DELIVERED/READ requieren un socket disponible y los recibos de SGI Operador.

## Verificación local del 2026-11-09

- `monitor` activo con rol MONITOR: confirmado.
- `GET /api/obsint/access`: HTTP 200, `{"enabled":true}`.
- Inicialmente `GET /api/obsint/cases/current`: HTTP 204, sin caso en curso.
- Fixture ejecutado dos veces: se conserva un solo turno vigente, hasta el 16 de noviembre de 2026 a las 10:54 (America/Guayaquil, misma hora de Colombia).
- Después de cargar el segundo fixture, `POST /api/obsint/cases`: HTTP 200, caso CAJ-OBS-001 en IN_PROGRESS con cuatro tareas. Se dejó creado para continuar desde la pantalla; las tareas no se iniciaron por API.
- Los tres videos locales responden HTTP 200 con tipo video/mp4.
- Las consignas usan su frecuencia normal: al responderlas, no volverán a estar pendientes inmediatamente. No se deshabilita esa regla global para las pruebas.

## Activación local posterior del socket (2026-11-09)

A solicitud del usuario se activó `SGI_SOCKET_ENABLED=true` en `.env.socket-local` (ignorado por Git), con base `https://apps.cajamarca.ec/dev.socket`. Los Compose compartidos y la propiedad de la aplicación conservan el valor predeterminado false.

Para recrear el backend conservando esta activación local, usar ambos archivos de variables:

```powershell
docker compose --env-file .env --env-file .env.socket-local -p sgi-comando-uat -f docker-compose.yml -f docker-compose.dev-local.yml up -d --build backend
```

Recrear sin `--env-file .env.socket-local` vuelve al valor predeterminado apagado, salvo otra variable del entorno que lo habilite. El `.env` principal ya estaba versionado; se retiraron de él únicamente las dos variables de activación agregadas en esta tarea, quedando sin diferencias respecto de su estado previo. Esta operación no retira del historial los datos que ya estuvieran versionados.

Como `172.18.1.10` no respondió desde esta máquina, `docker-compose.dev-local.yml` usa `extra_hosts: !override` para conservar únicamente la entrada de `apps.seminter.com.ec`; `apps.cajamarca.ec` se resuelve mediante DNS normal. No se cambió el nombre HTTPS ni se deshabilitó TLS. Este ajuste de red es exclusivamente local y debe revisarse por separado antes de un commit o despliegue.

Antes de activar se confirmó que no había destinatarios ni mensajes PENDING; los tres mensajes existentes eran TEST. No se publicaron mensajes de prueba. Después de recrear el backend, el grupo socket devolvió HTTP 200 con `enabled=true`, `reachable=true`, `lastPublishOk=false`. Este último dato sigue siendo falso hasta una publicación aceptada.

Para probar, enviar un mensaje nuevo desde la consola con un agente de turno del Punto que tenga `persona_id`. Los TEST anteriores no se reenvían. La confirmación por socket requiere que el gateway tenga configurada una URL de ACK accesible hacia este backend; su lectura también puede confirmarse por la API de Operador con IDENT. En esta activación no se configuraron restricciones de IP/token para el callback; el backend emite la advertencia correspondiente. No trasladar esa configuración abierta a un servidor compartido.
