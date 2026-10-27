# OBSINT y Componentes de Seguridad — v0.2

Estado: **UAT, no congelada**. Este archivo es la única documentación de los módulos **OBSINT** y **Seguridad Electrónica (Componentes de Seguridad)**. Cubre lo construido, cómo funciona, cómo engancharlo desde Servicios, cómo probarlo y lo que falta.

Fuentes:
- Mockup `OBSINT_mockup_real_v4.html`: la pantalla de evaluación del monitorista.
- Presentación "OBSINT Estructura" (17 diapositivas):
  - 7, 8, 16 y 17: Componentes de Seguridad.
  - 10 y 14: Consignas OBSINT ("Vincular Consignas – Cámara – Horario").
  - 11: Consola / OBSINT.
- Captura de la Consola actual con el botón OBSINT marcado junto al alcance.

---

## 1. Qué es OBSINT (según la presentación)

OBSINT (Observational Intelligence) es un **servicio comercial de auditoría por video**. Este es el flujo completo que describe la presentación:

1. **SIC: COM → SGI: Comando.** Llega una orden de servicio con:
   - Puesto de monitoreo con horario por día.
   - Cantidad de minutos por día.
   - Producto "Servicio de Auditoría OBSINT".
2. **Servicios.** Los servicios OBSINT **se quedan en Kaibil**.
3. **Configuración del punto.** Grupo **Seguridad Electrónica**, con dos módulos:
   - **Componentes de Seguridad:** cámaras con IP, puerto y credenciales, más las otras familias.
   - **OBSINT:** consignas con cámaras, horario y tiempo máximo.
4. **Consola / OBSINT.** El monitorista evalúa las consignas mirando las cámaras.
5. **Reportes OBSINT e Informe Diario en PDF.** Solo Presidente y Director Nacional.

**Esta versión cubre la parte interna de los pasos 3 y 4.** El menú de Configuración del Punto (las filas y su navegación) lo hace otra persona; ver §4.

---

## 2. Qué se implementó

### 2.1 Componentes de Seguridad (`frontend/src/secelec/`, paquete `secelec`)

Pantalla de las diapositivas 7 y 8. Se abre desde Configuración del Punto.

- **Cabecera:** ruta de navegación "← Volver a Configuración / Componentes de Seguridad", contexto del punto (compañía, servicio, cliente y punto) e indicadores (total, activos, con observaciones).
- **Izquierda, las 6 familias:** Seguridad Perimetral, Control de Accesos, Videovigilancia, Detección de Intrusiones, Iluminación de Seguridad y Protección Contra Incendios. Cada una con su contador y un punto de estado (verde si todo está activo, naranja si algo está observado o inactivo).
- **Centro:** tabla de componentes de la familia elegida (código, nombre, tipo, estado), con "Agregar componente" y botones para editar y eliminar.
- **Derecha:** detalle del componente elegido, o su formulario.
  - Campos comunes: tipo (con sugerencias por familia), nombre, ubicación, marca/modelo, cobertura, estado (Activo / Con observación / Inactivo) y observaciones.
  - **Solo Videovigilancia** (como el formulario "Configuraciones · IP" de referencia):
    - Dirección IP, puerto y **canal** (1 a 256; 1 por defecto). En un NVR, cada canal es una cámara.
    - Usuario y **contraseña**, con un botón para ver lo que se está escribiendo. La contraseña guardada nunca se muestra.
    - **Latitud y longitud**, opcionales pero juntas, con 6 decimales.
    - **Streaming** activo o desactivado (activo por defecto) y su modo: **Copy** (el servidor de video reenvía el flujo tal cual, por defecto) o **Transcodificar** (lo recodifica, por ejemplo de H.265 a H.264). Se guarda ya, pero solo tendrá efecto cuando exista el servidor de video.
    - "URL de video (pruebas)".
    - **Probar conexión** (el botón "Test" de la referencia): prueba los datos escritos sin guardarlos.
      - Pide `DESCRIBE` del flujo RTSP del canal (`rtsp://IP:puerto/Streaming/Channels/{canal}01`), con autenticación Digest o Basic. Así valida red, usuario y contraseña, y que el canal exista, en una sola llamada.
      - Muestra el resultado: conexión correcta con el códec (H.264 / H.265), credenciales incorrectas, canal inexistente, sin conexión o sin respuesta, y el tiempo en ms.
      - Si la cámara entrega H.265 y el modo es Copy, avisa que el navegador no lo reproduce y sugiere Transcodificar.
      - Al editar, si no se escribe una contraseña nueva, usa la guardada (se descifra solo en el servidor).
      - Se usa RTSP y no ISAPI porque el puerto del formulario es el de RTSP (554). ISAPI va por HTTP (80) y queda para la vista previa.
- **Código automático por familia:** CCTV‑01, PER‑01, ACC‑01, INT‑01, ILU‑01, INC‑01…
- **Contraseña de la cámara:**
  - Se guarda **cifrada** con AES‑256‑GCM y un IV aleatorio por cifrado.
  - **Llave:** `SGI_SECELEC_SECRET_KEY` si el despliegue la define con un valor. Si no está o viene vacía, se usa la llave fija `sgi.secelec.default-secret-key` de `application.properties`, para que funcione en cualquier ambiente sin configurar nada. Como esa llave está en el repositorio, quien tenga acceso al código puede descifrar las contraseñas guardadas con ella; para más protección, definir `SGI_SECELEC_SECRET_KEY` en el servidor **antes** de registrar cámaras. **No cambiar la llave en uso:** las contraseñas guardadas solo se descifran con la misma.
  - **Nunca vuelve al navegador.** La API solo informa `hasPassword`.
  - Al editar, el campo aparece vacío con "Sin cambios"; dejarlo vacío conserva la contraseña actual.
- **La familia no se puede cambiar** después de crear el componente.
- **Eliminar** un componente que usa una consigna OBSINT responde 409, y la pantalla sugiere cambiarlo a Inactivo.
- **"URL de video (pruebas)"** existe porque todavía no hay integración automática con el servidor de video. Acepta una ruta relativa a la web (por ejemplo `obsint/videos/1-1.mp4`) o una URL http(s); cualquier otro esquema se rechaza. El reproductor de la consola (`liveStream.ts`) decide el formato por la URL:
  - termina en **`.m3u8`** → **HLS** en vivo, con hls.js (Safari en iPhone usa su HLS nativo). Ej.: `http://servidor:8888/live/ch1/index.m3u8`.
  - termina en **`/whep`** → **WebRTC** en vivo (WHEP, casi tiempo real). Requiere H.264 **sin B-frames**, lo normal en cámaras IP. Ej.: `http://servidor:8889/live/ch1/whep`.
  - cualquier otra → **archivo** (MP4 de pruebas), que se repite y se muestra como "GRABACIÓN".
  - HLS y WebRTC se muestran como "EN VIVO" y admiten captura de evidencia igual que el MP4. El servidor de video debe permitir CORS desde la web de Comando.

### 2.2 Consignas OBSINT (`frontend/src/obsint/config/`)

Pantalla de la diapositiva 10. Se abre desde Configuración del Punto.

- **Cabecera:** contexto del punto y versión del protocolo (`PRO-OBS-0001 v2`, fecha de actualización).
- **Barra del protocolo:**
  - Selector de versión (historial) y estado: **Borrador**, **Publicado · inactivo** o **Activo**.
  - Acciones según el estado:

    | Estado | Acciones |
    |---|---|
    | Borrador | Publicar versión, Descartar |
    | Publicado · inactivo | Activar, Nueva versión |
    | Activo | Inactivar, Nueva versión |

- **Izquierda:** categorías de tareas con contador. "Todas las tareas" y las 6 categorías: SSO, Producción industrial, Logística y almacenamiento, Orden y limpieza, Movilidad y tránsito interno, Atención al cliente.
- **Centro:** tabla de tareas (código OBS‑NNN, nombre, categoría, tiempo, cantidad de cámaras, estado), búsqueda y "Nueva tarea". Solo se puede editar en el borrador.
- **Derecha:** detalle de la tarea, o su formulario:
  - Nombre, categoría y tiempo máximo de ejecución (30 s, 1, 2, 3, 5 o 10 min).
  - Descripción, que es lo que ve el monitorista en la consola.
  - Tarea activa o inactiva.
  - **Horario de ejecución:** de lunes a domingo, cada día con casilla, hora de inicio y hora de fin.
  - **Cámaras asociadas:** casillas con las cámaras de Videovigilancia del punto que no están inactivas, máximo 4. Si el punto no tiene cámaras, la pantalla indica que se agreguen en Componentes de Seguridad.

### 2.3 Consola OBSINT (`frontend/src/obsint/`)

- Botón **OBSINT** en la cabecera de **Consola**, a la derecha del alcance. Es el único cambio en `ConsolaMonitor.tsx`.
- Inicio: "Iniciar caso". **El caso toma las consignas activas de los protocolos ACTIVOS cuyo horario cubre el momento actual** (zona America/Guayaquil). Si no hay ninguna, muestra "No hay consignas OBSINT activas en este horario".
- **Cabecera:**

  | Tarjeta | Contenido |
  |---|---|
  | Caso | Código del caso |
  | Grupo | Grupo N · K cámaras |
  | Tarea | N de M |
  | Tiempo restante | Cuenta regresiva |
  | Fecha y hora | Reloj en vivo |
  | Operador | Usuario con sesión iniciada |

- **Video:** de 1 a 4 cámaras, con clic para elegir la de evidencia, pausa y pantalla completa. Una cámara sin video configurado muestra **Sin señal · Video en vivo pendiente de conexión**.
- **Respuesta:** consigna, **Sí / No / Incierto**, justificación obligatoria (máximo 500 caracteres) y evidencia capturada de la cámara elegida.
- **Comunicación:** mensajes al agente en sitio.
- **Botones:** CONTINUAR, y FINALIZAR en la última tarea, que lleva al resumen del caso.

---

## 3. Lógica y reglas

### Protocolo OBSINT (versiones, como el módulo Consignas)

- **Un protocolo por punto.** Se edita en **BORRADOR** → al publicarlo pasa a **INACTIVO** y queda inmutable → al activarlo pasa a **ACTIVO**.
- La base de datos garantiza como máximo **un ACTIVO y un BORRADOR por punto** (índices únicos parciales). Al activar una versión, la que estaba activa pasa a INACTIVO.
- **Nueva versión:** crea un borrador con número de versión +1, copiando las consignas (con cámaras y horario) de la última versión. Si ya existe un borrador, se abre ese.
- **Publicar exige:**
  - Al menos una consigna activa.
  - Cada consigna activa con al menos una cámara, ninguna inactiva.
  - Cada consigna activa con horario en al menos un día.
- Editar o borrar consignas de una versión publicada → 409 "Cree una nueva versión".

### Consigna

- Código automático `OBS-NNN` por protocolo, que se conserva entre versiones.
- Nombre y descripción obligatorios.
- Categoría de la lista fija.
- Tiempo máximo entre 10 s y 60 min.
- Hasta 4 cámaras, todas de Videovigilancia, **del mismo punto** y no inactivas.
- Horario: un tramo por día, la hora de fin mayor que la de inicio. El inicio está incluido y el fin excluido.

### Caso y tareas (Consola)

- **Alcance del monitorista:** el caso solo toma consignas de los Puntos cuya **Compañía** está en el alcance del usuario. Es la misma regla que la configuración (`requirePoint`):
  - Roles de país (Presidente, Gerencia General, directores nacionales y LATAM): todas las Compañías.
  - Roles de Compañía (Coordinador, Asistente): solo las Compañías que tienen asignadas directamente.
  - Los demás: las Compañías de su zona, región o asignación directa (`user_operational_scope`).
  - **MONITOR:** además, las Compañías de los puestos de Monitoreo (Coordinación, `MON-xxx`) donde la persona tiene **turno en curso**. Se lee de Asignaciones con el mismo criterio que SGI Operador: asignación no eliminada, plan de la semana publicado o cerrado, cuenta quien cubrió el turno (`actual_employee_id`), y la persona se identifica por el `persona_id` de su token de IDENT. Fuera de su turno, o con el plan en borrador, no ve consignas.
  - Un Punto sin Compañía no entra en ningún caso. Si no queda ninguna consigna, responde 409: "No hay consignas OBSINT activas en este horario en las Compañías de su alcance".
  - No separa por cliente: dos clientes atendidos por la misma Compañía se ven juntos.
- Un usuario tiene un solo caso en curso. Las tareas se resuelven **en orden**.
- Cada tarea copia de la consigna el código, la descripción y el tiempo máximo, así que editar la configuración no altera casos ya creados.
- **Tiempo:**
  - Arranca cuando cargan las cámaras, o al instante si ninguna tiene video.
  - `start` es idempotente; la web corrige su reloj con `serverNow`.
  - Se puede responder fuera de tiempo, pero la tarea queda con `timed_out=true`.
- **Evidencia:**
  - Fotograma JPEG del video elegido.
  - Máximo 6 por tarea, de hasta 5 MiB, solo JPEG, PNG o WebP (detectados por sus bytes).
  - Se guarda en MinIO, en el bucket de evidencias de Comando, con SHA‑256.
- **Mensajes al agente (modo prueba):** solo se registran, con `deliveryStatus = TEST`: **no se envían al agente** y no deben salir a producción como reales. V94 agregó `TEST` y pasó a `TEST` los mensajes ya guardados como `SENT`, porque ninguno se envió. `SENT` y `FAILED` quedan para cuando exista el envío real a SGI Operador (SITC).
- Cada usuario ve solo sus propios casos.

### Seguridad

- **Roles:** los mismos de Notificación de Incidentes, en las tres APIs. Agente y cliente no tienen acceso.
  - PRESIDENTE, GERENCIA_GENERAL, DIRECTOR_OPERACIONES_LATAM, DIRECTOR_OPERACIONES_NACIONAL, DIRECTOR_NACIONAL, DIRECTOR_ZONAL, JEFE_REGIONAL, COORDINADOR_COMPANIA, ASISTENTE_COORDINACION y SUPERVISOR_SEGURIDAD.
  - **MONITOR** (`ObsintPlatform.MONITOR_ROLE`): solo la **consola** (`/api/obsint`), no la configuración ni Componentes de Seguridad. IDENT debe enviarlo para los cargos "Monitor" y "Agente de Consola de Monitoreo Senior" del programa SGI_COM (hoy no está en `cargo-defaults.json`).
- **Acceso al punto:** la configuración aplica la **misma regla que Consignas**. El punto debe existir en el tenant y el usuario debe poder gestionar su compañía (`requireCompanyForCompanyRoles`).
- **Errores:** cada rechazo responde `{"message": "…"}` con el código HTTP (400, 403, 404, 409, 503), y la pantalla muestra el mensaje tal cual.

---

## 4. Cómo engancharlo desde Servicios (para quien hace el menú)

Esta parte no está hecha a propósito: el menú de Configuración del Punto lo hace otra persona.

```tsx
import SecurityComponents from '../secelec/SecurityComponents';
import {secelecApi} from '../secelec/secelecApi';
import ObsintConfig from '../obsint/config/ObsintConfig';
import {obsintConfigApi} from '../obsint/obsintApi';

// Pantallas (mismo patrón que BitacoraConfig / ConsignasConfig):
<SecurityComponents point={{pointId, pointName, clientName, serviceName, companyName}} onBack={()=>setView('config-landing')}/>
<ObsintConfig       point={{pointId, pointName, clientName, serviceName, companyName}} onBack={()=>setView('config-landing')}/>

// Estado real de cada fila del checklist:
const sec = await secelecApi.summary(pointId);       // {status:'complete'|'warning'|'blocked', meta:'5 componentes · 4 activos', ...}
const obs = await obsintConfigApi.summary(pointId);  // {status, meta:'Protocolo v2 activo · 3 consignas', warnings:[...], ...}
```

- `status` corresponde a los estados de la lista de Configuración: `complete` = Completo, `warning` = Observación, `blocked` = Bloqueante.
- **Componentes de Seguridad:**
  - Bloqueante: no hay componentes activos ni con observación.
  - Observación: hay componentes con observación.
  - Completo: en otro caso.
- **OBSINT:**
  - Bloqueante: sin protocolo ACTIVO.
  - Observación: una consigna activa usa una cámara con observación o inactiva (los avisos van en `warnings`).
  - Completo: en otro caso.
- Ambas pantallas traen su propio CSS y no usan clases de `styles.css`.

---

## 5. Aislamiento

**Backend**
- Paquetes `obsint` y `secelec`, rutas `/api/obsint/**` y `/api/secelec/**`, tablas `obsint_*` y `secelec_*`.
- `point_id` no tiene llave foránea hacia `point`; el acceso se valida en la API.
- **`ObsintPlatform` y `SecElecPlatform` son las únicas clases que usan Comando:** tenant, usuario, acceso al punto (`PointEntity` + `OperationalScopeService`), MinIO y validación de imágenes.
- OBSINT solo depende de `secelec` a través de la entidad `SecElecComponent`, porque las cámaras de las consignas son componentes.

**Frontend**
- Carpetas `src/obsint/` y `src/secelec/`, cada una con su API y su CSS (prefijos `obs-`, `obs-cfg-` y `sec-`).
- De `api.ts` solo usan el transporte: `request`, `binaryRequest`, `multipartRequest` y `ApiError`.

**Cambios fuera de los módulos**

| Archivo | Cambio |
|---|---|
| `frontend/src/pages/ConsolaMonitor.tsx` | Botón OBSINT y apertura de la consola |
| `frontend/src/api.ts` | `export` en `request` y `binaryRequest` |
| `backend/src/main/resources/application.properties` | `sgi.secelec.secret-key` (con valores para dev y test) |
| `docker-compose.yml` | Variable `SGI_SECELEC_SECRET_KEY` del backend |
| `.gitignore` | Excluye los videos de prueba |

---

## 6. Archivos

**Backend `secelec`** (`backend/src/main/java/com/cajamarca/sgi/comando/secelec/`)

| Archivo | Rol |
|---|---|
| `SecElecResource.java` | API de componentes y resumen |
| `SecElecComponent.java` | Entidad (familia, estado, conexión) |
| `CameraSecretCipher.java` | Cifrado AES‑GCM de contraseñas. Si se adopta OpenBao, solo cambia esta clase |
| `RtspProbe.java` | "Probar conexión": `DESCRIBE` RTSP con Digest/Basic, sin dependencias. Conexión 3 s, lectura 5 s, respuestas de hasta 64 KB |
| `SecElecPlatform.java` | Adaptador hacia Comando |

**Backend `obsint`** (`backend/src/main/java/com/cajamarca/sgi/comando/obsint/`)

| Archivo | Rol |
|---|---|
| `ObsintConfigResource.java` | API de configuración: protocolo, versiones, consignas, cámaras disponibles y resumen |
| `ObsintResource.java` | API de la consola |
| `ObsintService.java` | Casos de uso de la consola: abrir caso por horario, responder, evidencia, mensajes |
| `ObsintProtocol.java` | Protocolo versionado |
| `ObsintConsigna.java` | Consigna, sus cámaras y su horario (`Slot`, `scheduledAt`) |
| `ObsintCase.java` | Caso: orden de las tareas y cierre |
| `ObsintCaseTask.java` | Tarea: tiempo máximo y validación de la respuesta |
| `ObsintEvidence.java` | Metadatos de la evidencia |
| `ObsintAgentMessage.java` | Mensaje al agente |
| `ObsintPlatform.java` | Adaptador hacia Comando |
| `ObsintRuleException.java` | Regla incumplida (409) |

**Base de datos:**
- `V91__obsint_console.sql`: la primera versión de la consola, que ya está en el repo. **No se modifica.**
- `V92__obsint_config_and_security_components.sql`: transforma V91 al modelo actual (ver §7).
- `V93__secelec_camera_channel_geo_streaming.sql`: canal, coordenadas y streaming de las cámaras (ver §7).
- `V94__obsint_agent_message_test_status.sql`: estado `TEST` (modo prueba) para los mensajes al agente (ver §3).

**Pruebas**

| Prueba | Qué cubre |
|---|---|
| `obsint/ObsintCaseTest` | Reglas del caso y horario (unitaria) |
| `obsint/ObsintResourceTest` | Configuración y consola de punta a punta (`@QuarkusTest`) |
| `secelec/SecElecResourceTest` | API de componentes (`@QuarkusTest`) |
| `secelec/CameraSecretCipherTest` | Cifrado (unitaria) |
| `secelec/RtspProbeTest` | Probar conexión contra una cámara RTSP simulada (`FakeRtspCamera`): OK con códec, contraseña incorrecta, canal inexistente, puerto cerrado y sin respuesta (unitaria) |

**Frontend**

| Archivo | Rol |
|---|---|
| `src/secelec/SecurityComponents.tsx` | Pantalla de componentes |
| `src/secelec/secelecApi.ts` | API, tipos y catálogo de familias |
| `src/secelec/secelec.css` | Estilos del módulo |
| `src/obsint/config/ObsintConfig.tsx` | Pantalla de Consignas OBSINT |
| `src/obsint/config/obsint-config.css` | Estilos de la configuración |
| `src/obsint/ObsintConsole.tsx` y componentes | Consola: `ObsintCameraGrid`, `ObsintTaskPanel`, `ObsintCommPanel` |
| `src/obsint/liveStream.ts` | Conecta el video de cada cámara al `<video>` según su URL: archivo (MP4), HLS (hls.js) o WebRTC (WHEP). Dependencia `hls.js`, que se carga solo cuando hay una cámara HLS |
| `src/obsint/obsintApi.ts` | **Contrato con el backend:** tipos (espejo de los records Java), `obsintApi` (consola), `obsintConfigApi` (configuración) y `errorText` |
| `src/obsint/obsint.ts` | Utilidades de pantalla: textos de categorías, días y estados, cuenta regresiva, reloj, borrador y captura de fotograma |
| `src/obsint/obsint.css` | Estilos de la consola |

### Responsables (división del trabajo)

**Persona A — solo frontend**
- Consola: `src/obsint/ObsintConsole.tsx`, `ObsintCameraGrid.tsx`, `ObsintTaskPanel.tsx`, `ObsintCommPanel.tsx`, `obsint.css` y `obsint.ts`.
- Consignas OBSINT: `src/obsint/config/ObsintConfig.tsx` y `obsint-config.css`.
- Integración con Comando: el botón OBSINT en `src/pages/ConsolaMonitor.tsx`, los `export` de `src/api.ts` y la coordinación con quien hace el menú de Servicios.

**Persona B — backend y algo de frontend**
- Backend completo: `secelec/*` y `obsint/*`, migraciones (V91 publicada y no se toca; V92 y siguientes), `sgi.secelec.secret-key` y todas las pruebas.
- Frontend de Componentes de Seguridad: `src/secelec/SecurityComponents.tsx` y `secelec.css`.
- Contrato de la API: `src/secelec/secelecApi.ts` y `src/obsint/obsintApi.ts` (tipos y llamadas).

**Reglas**
- **Contrato:** si B cambia un endpoint o un campo, actualiza `obsintApi.ts` o `secelecApi.ts` en el mismo cambio. Agrega campos sin quitar los existentes y avisa a A antes de integrar.
- **Errores:** el backend siempre responde `{"message": "…"}` y el frontend lo muestra con `errorText`.
- **Migraciones nuevas:** solo B.
- **Este LEEME:**
  - A edita §2.2 y §2.3 (pantallas de consignas y consola) y §4 (enganche con Servicios).
  - B edita §2.1 (componentes), §3 (lógica), §7 (base de datos) y §8 (API).
  - Quien integre último actualiza §9 y §10.

---

## 7. Base de datos (V91 + V92 + V93)

V91 (ya publicada) creó la consola con un catálogo DEMO global. **V92 la transforma**:
- **Borra los datos DEMO de V91** (casos, tareas, evidencias, mensajes y consignas). Las fotos de evidencia de esos casos quedan en MinIO, en `evidence/{tenant}/obsint/…`, y pueden borrarse a mano.
- Crea `secelec_component`, `obsint_protocol` y `obsint_consigna_schedule`.
- Agrega a `obsint_consigna` las columnas `protocol_id`, `point_id` y `category`. El código pasa a ser único por protocolo.
- Rehace `obsint_consigna_camera` para que apunte a `secelec_component`, y cambia la llave de `obsint_evidence.camera_id` a `secelec_component`.
- Elimina `obsint_camera`.

Probado sobre PostgreSQL 17 de dos formas: aplicando V91 y V92 sobre una base vacía, y aplicando V92 sobre una base que ya tenía datos DEMO de V91.

**V93** agrega a `secelec_component` las columnas `channel`, `latitude`, `longitude` (`numeric(9,6)`), `streaming_enabled` y `streaming_mode` (COPY / TRANSCODE), con sus CHECK de rango. Las cámaras existentes quedan con canal 1 y streaming activo en modo COPY. En las demás familias las cinco columnas quedan en null.

Esquema resultante (todas las tablas llevan `instance_country_id`, `created_at` y `updated_at`):

| Tabla | Contenido |
|---|---|
| `secelec_component` | `point_id`, `family`, `component_type`, `code` (único por punto), `name`, `location`, `coverage`, `brand_model`, `status`, `observations`, `ip`, `port`, `channel`, `latitude`, `longitude`, `streaming_enabled`, `streaming_mode`, `username`, `password_cipher`, `stream_url`, `updated_by` |
| `obsint_protocol` | `series_id`, `based_on_protocol_id`, `point_id`, `code`, `version_no`, `status` (BORRADOR / INACTIVO / ACTIVO), `published_at`, `activated_at`, `updated_by`. Índices únicos: un ACTIVO y un BORRADOR por punto |
| `obsint_consigna` | `protocol_id`, `point_id`, `code`, `title`, `instruction`, `category`, `time_limit_seconds`, `position`, `active` |
| `obsint_consigna_camera` | Consigna → `secelec_component`, con orden. La llave impide borrar una cámara en uso |
| `obsint_consigna_schedule` | `day_of_week` (1–7), `start_time`, `end_time` |
| `obsint_case` | Casos de la consola |
| `obsint_case_task` | Tareas de cada caso |
| `obsint_evidence` | Evidencias; `camera_id` apunta a `secelec_component` |
| `obsint_agent_message` | Mensajes al agente |

---

## 8. API

**Componentes de Seguridad** (`/api/secelec`)

| Método | Ruta | Uso |
|---|---|---|
| GET | `/points/{pointId}/components` | Componentes del punto |
| POST | `/points/{pointId}/components` | Crear: `family`, `componentType`, `name`, …; en VIDEO también `ip`, `port`, `channel`, `latitude`, `longitude`, `streamingEnabled`, `streamingMode`, `username`, `password` y `streamUrl` |
| PUT | `/components/{id}` | Editar (`password` vacía conserva la actual) |
| DELETE | `/components/{id}` | Eliminar (409 si está en uso) |
| POST | `/points/{pointId}/cameras/test` | Probar conexión con `ip`, `port`, `channel`, `username`, `password` (o `componentId` y contraseña vacía para usar la guardada). Responde 200 con `ok`, `outcome` (OK, AUTH_FAILED, NOT_FOUND, UNREACHABLE, TIMEOUT, PROTOCOL_ERROR), `message`, `codec`, `streamPath` y `elapsedMs` |
| GET | `/points/{pointId}/summary` | Estado para la fila del menú |

**Configuración OBSINT** (`/api/obsint/config`)

| Método | Ruta | Uso |
|---|---|---|
| GET | `/points/{pointId}/protocols` | Versiones del protocolo, con sus consignas |
| GET | `/points/{pointId}/cameras` | Cámaras que se pueden asociar |
| POST | `/points/{pointId}/protocols/draft` | Abre el borrador (existente, nueva versión o v1) |
| DELETE | `/protocols/{id}` | Descarta el borrador |
| POST | `/protocols/{id}/publish` | Publica (BORRADOR → INACTIVO) |
| POST | `/protocols/{id}/activate` | Activa (INACTIVO → ACTIVO) |
| POST | `/protocols/{id}/deactivate` | Inactiva (ACTIVO → INACTIVO) |
| POST | `/protocols/{id}/consignas` | Crea una consigna (solo en el borrador) |
| PUT | `/consignas/{id}` | Edita una consigna (solo en el borrador) |
| DELETE | `/consignas/{id}` | Borra una consigna (solo en el borrador) |
| GET | `/points/{pointId}/summary` | Estado para la fila del menú |

Cuerpo de una consigna:

```json
{"title":"…","instruction":"…","category":"SSO","timeLimitSeconds":120,"active":true,
 "cameraIds":["…"],"schedule":[{"dayOfWeek":1,"start":"08:00","end":"18:00"}]}
```

**Consola** (`/api/obsint`)

| Método | Ruta | Uso |
|---|---|---|
| GET | `/cases/current` | Caso en curso (204 si no hay) |
| POST | `/cases` | Abre un caso, o devuelve el que está en curso |
| GET | `/cases/{id}` | Caso con sus tareas |
| GET | `/tasks/{id}` | Tarea con consigna, cámaras y tiempo |
| POST | `/tasks/{id}/start` | Inicia el tiempo |
| PUT | `/tasks/{id}/answer` | Registra la respuesta |
| GET/POST | `/tasks/{id}/evidence` | Lista o sube evidencia |
| DELETE | `/tasks/{id}/evidence/{eid}` | Quita evidencia |
| GET | `/tasks/{id}/evidence/{eid}/content` | Imagen |
| GET/POST | `/tasks/{id}/messages` | Mensajes al agente |

---

## 9. Lo que falta

**De la presentación**
- **Menú de Servicios:** la fila Seguridad Electrónica con sus dos módulos y la navegación hacia ellos. Lo hace otra persona; ver §4.
- **Orden de servicio desde SIC: COM:** minutos por día y tipo de producto OBSINT.
- **Servicios OBSINT en Kaibil:** que muestren Configuración en lugar de Asignación.
- **Alta "¿vincular a un punto existente?"** (diapositivas 14 y 15).
- **Reportes OBSINT e Informe Diario en PDF** (diapositivas 12 y 13), solo para Presidente y Director Nacional.

**Técnico**
- **Video en vivo real:** la consola ya reproduce HLS y WebRTC (ver §2.1); falta de dónde sacar la URL de cada cámara. Opciones: la OpenAPI de **HikCentral** (vista en vivo por cámara) o un servidor propio que convierta RTSP a HLS/WebRTC (por ejemplo MediaMTX o go2rtc), más acceso de red a los sitios del cliente (VPN o equipo local). Con cámaras Hikvision la ruta es `rtsp://IP:554/Streaming/Channels/101` (102 es el substream), y el substream debe estar en H.264.
- **Hikvision ISAPI:** foto de vista previa (`/ISAPI/Streaming/channels/{canal}01/picture`) y datos del equipo (`/ISAPI/System/deviceInfo`). Necesitan el puerto HTTP (80), que hoy no está en el formulario. "Probar conexión" ya existe, por RTSP.
- **Frecuencia de las consignas** (cada turno, cada 2 o 4 horas) e instrucciones operativas.
- **Alcance del monitorista más fino:** hoy el caso se limita a los Puntos de las Compañías del usuario (§3). Si más adelante se requiere separar por cliente o por puesto de monitoreo (Kaibil), hace falta una asignación nueva; solo cambiaría `ObsintPlatform.visiblePoints`.
- **Envío real de mensajes** al agente en SGI Operador (requiere SITC). Mientras tanto se guardan como `TEST`.
- **Panel de mensajes en modo prueba (frontend):** con `deliveryStatus = TEST`, el panel debe decir que los mensajes no se envían (por ejemplo, "Modo prueba · los mensajes no se envían al agente") en lugar de "Agente en sitio · Conectado", y marcar cada mensaje como "No enviado (prueba)" en lugar del doble check.
- **Rol `MONITOR` en IDENT:** el backend ya lo acepta en la consola (§3). Falta que IDENT lo asigne a los cargos de monitoreo en SGI_COM, que el menú de Comando le muestre la Consola y el botón OBSINT (frontend), y que cada Compañía tenga su puesto de Monitoreo en Coordinación con los monitores asignados y el plan publicado.
- Cambios visuales de la consola: una cámara grande con miniaturas, Cliente · Punto en la cabecera.

**Preguntas abiertas para el negocio**
1. ¿Cómo cuenta "Incierto" en el reporte?
2. ¿Qué pasa con una tarea vencida sin respuesta?
3. ¿Qué pasa al superar los minutos por día?
4. ¿Hay una cola común entre monitoristas?
5. ¿A qué app llegan los mensajes al agente?
6. ¿Las cámaras se conectan directo o a través de un NVR?
7. ¿Qué vía de red se usa para llegar a los sitios?

---

## 10. Cómo probarlo y qué está verificado

### Levantar en local

1. Llave de cifrado: **no hace falta configurar nada**. Sin `SGI_SECELEC_SECRET_KEY` se usa la llave fija de `application.properties` (en el perfil `dev`, `dev-only-secelec-key`). Para usar una propia, defínela en `.env` antes de registrar cámaras:

   ```bash
   SGI_SECELEC_SECRET_KEY=una-llave-larga-y-secreta
   ```
2. (Opcional) Copia los videos de prueba a `frontend/public/obsint/videos/` (`1-1.mp4` … `3-4.mp4`; no se versionan, ~300 MB). En cada cámara, pon "URL de video (pruebas)" = `obsint/videos/1-1.mp4`.
3. Mientras no exista el menú, abre las pantallas montándolas a mano según §4.
4. **Flujo:**
   - Componentes de Seguridad → Videovigilancia → agregar cámaras.
   - Consignas OBSINT → Crear protocolo → Nueva tarea (cámaras y horario) → Publicar → Activar.
   - Consola → OBSINT → Iniciar caso.

### Verificado (2026-10-26)

- **18/18 pruebas pasan:**

  | Prueba | Tipo | Resultado |
  |---|---|---|
  | `ObsintCaseTest` | Unitaria | 7 |
  | `CameraSecretCipherTest` | Unitaria | 3 |
  | `ObsintResourceTest` | `@QuarkusTest` | 4 |
  | `SecElecResourceTest` | `@QuarkusTest` | 4 |

  Las `@QuarkusTest` corrieron contra un PostgreSQL 17 y un MinIO temporales, con todas las migraciones V1–V92 aplicadas.
  - Las migraciones **no crean** los datos UAT que usan las pruebas de Comando (usuarios `coord`, `dnacional`…, compañía y Punto `40000000-…0001`). Para correrlas en una base vacía se sembraron a mano: cliente, compañía, servicio, Punto, usuarios con la contraseña UAT de V6, y alcances.
  - En una base local ya poblada no hace falta sembrar nada.
- **Pantallas revisadas en el navegador** con el backend real, usando una página de vista previa provisional que ya se eliminó:
  - **Componentes de Seguridad:** listado por familia y detalle. Se creó la cámara CCTV‑09 desde el formulario, y la contraseña aparece solo como "Guardada (cifrada)".
  - **Consignas OBSINT:**
    - Se creó una nueva versión en borrador, que copió las consignas.
    - Se creó la tarea OBS‑004 con horario, cámara y categoría.
    - Se publicó y se activó.
  - **Consola:** el caso tomó solo las consignas vigentes a esa hora (2 de 4), y el video salió de la URL de pruebas de la cámara.
- El frontend compila (`tsc -b`) y el build de producción de Vite pasa.

### Pendiente de verificar

- Probar con el menú real de Servicios cuando esté hecho.
- Probar con IDENT encendido. La verificación se hizo con usuarios UAT y autenticación Basic.
