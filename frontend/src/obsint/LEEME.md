# OBSINT · Consola / OBSINT — v0.1

Estado: **UAT, no congelada**. Este archivo es la única documentación del módulo. Cubre lo construido, cómo funciona, cómo probarlo y lo que falta.

Fuentes:
- Mockup `OBSINT_mockup_real_v4.html`: la pantalla de evaluación del monitorista.
- Presentación "OBSINT Estructura" (17 diapositivas), en especial la diapositiva 11, *SGI: COMANDO → Consola / OBSINT*.
- Captura de la Consola actual con el botón OBSINT marcado junto al alcance.

---

## 1. Qué es OBSINT (según la presentación)

OBSINT (Observational Intelligence) es un **servicio comercial de auditoría por video**. Este es el flujo completo que describe la presentación:

1. **SIC: COM → SGI: Comando.** Llega una orden de servicio con:
   - Puesto de monitoreo con horario por día.
   - Cantidad de minutos por día.
   - Producto "Servicio de Auditoría OBSINT".
2. **Servicios.** La orden aparece en el listado. Los servicios OBSINT **se quedan en Kaibil**, la compañía de coordinación, y no se asignan a una compañía operadora.
3. **Configuración del punto (nueva).** El checklist se agrupa en Seguridad Física y **Seguridad Electrónica**. Esta última se divide en **Componentes de Seguridad** y **OBSINT**.
4. **Componentes de seguridad.** Hay familias: Perimetral, Control de Accesos, **Videovigilancia**, Intrusiones, Iluminación e Incendios. Una cámara tiene nombre, tipo, IP, puerto, credenciales y vista previa.
5. **Consignas OBSINT.** Cada consigna tiene código, categoría, descripción, frecuencia, horario por día, **tiempo máximo de ejecución**, instrucciones y **cámaras asociadas**.
   - La nota del orador dice que OBSINT es "un puesto más". Sus consignas le aparecen al **Monitorista OBSINT** en Consola / OBSINT.
6. **Consola / OBSINT.** Es la pantalla del mockup y **lo único implementado en esta versión**.
7. **Reportes OBSINT y "Informe Diario de Consignas" en PDF.** Solo pueden verlos **Presidente** y **Director Nacional**.

---

## 2. Qué se implementó (v0.1)

### Funcional

- **Botón OBSINT** en la cabecera de **Consola**, a la derecha del chip "Alcance". Abre la Consola OBSINT. "← Volver a Consola" regresa.
- **Inicio:** si el usuario no tiene un caso en curso, ve "No tienes un caso OBSINT en curso" con el botón **Iniciar caso**.
- **Caso** (`CAJ-OBS-001`, `CAJ-OBS-002`, …): una pasada por **todas las consignas OBSINT activas**, una tarea por consigna y en orden.
- **Cabecera de 6 tarjetas:**

  | Tarjeta | Contenido |
  |---|---|
  | Caso | Código del caso |
  | Grupo | Grupo N · K cámaras |
  | Tarea | N de M, con puntos de avance |
  | Tiempo restante | Cuenta regresiva |
  | Fecha y hora | Reloj en vivo |
  | Operador | Usuario con sesión iniciada |

- **Video:** grilla de 1 a 4 cámaras (con 3, la tercera ocupa todo el ancho). Cada cámara muestra:
  - Nombre y grupo.
  - La marca EN VIVO o GRABACIÓN.
  - Fecha y hora sobre el video.
  - Botones: usar para evidencia, pausar/reproducir y pantalla completa.
  - Con un clic, la cámara queda elegida para capturar (borde azul). Si un video no carga, la cámara muestra **Sin señal** y la consola sigue funcionando.
- **Consigna:** texto y código (OBS-001…).
- **¿Se cumple la consigna?:** **Sí / No / Incierto**.
- **Justificación:** obligatoria, máximo 500 caracteres, con contador.
- **Evidencia:** "Capturar evidencia" toma el fotograma actual de la cámara elegida y lo sube.
  - Cada miniatura indica la cámara y la hora.
  - Se puede quitar mientras la tarea está en curso.
- **Comunicación:** "Agente en sitio · Conectado · Canal unidireccional", lista de mensajes enviados y campo con el botón **Enviar**.
- **CONTINUAR →** guarda la respuesta y pasa a la siguiente tarea. En la última dice **FINALIZAR**.
- **Resumen final:** "Caso … completado", con la decisión de cada tarea, la marca "Fuera de tiempo" si corresponde y el botón **Iniciar nuevo caso**.
- **Avisos:** notificación abajo al centro, por ejemplo "Tiempo agotado…", "Respuesta registrada" o "Evidencia capturada desde Cámara 01".

### Diferencias con la diapositiva 11

- La diapositiva muestra 1 cámara. Aquí se muestran de 1 a 4 por consigna, como en el mockup.
- El botón "Reportes" de la diapositiva **no** está implementado (ver §8).
- Con los videos de prueba la marca dice **GRABACIÓN**. EN VIVO aparece cuando la cámara tiene `live=true`.

---

## 3. Lógica y reglas de negocio

### Caso y tareas

- Un usuario tiene **un solo caso en curso a la vez**. `POST /cases` devuelve el caso abierto si ya existe uno.
- Al abrir el caso, cada tarea **copia** de su consigna el código, la instrucción y el tiempo máximo. Si después se edita la consigna, los casos ya creados no cambian. Las cámaras sí se leen de la consigna.
- **Orden obligatorio:** solo se puede iniciar o responder la tarea actual, que es la primera sin responder. Fuera de orden → 409 "Las tareas se resuelven en orden…".
- Estados de una tarea: `PENDING → IN_PROGRESS → ANSWERED`. El caso pasa de `IN_PROGRESS` a `COMPLETED` al responder la última tarea.

### Tiempo máximo

- El tiempo **no arranca al abrir la tarea**, sino cuando todas sus cámaras cargaron o fallaron, con un máximo de 5 s de espera. Así el monitorista no pierde tiempo mientras cargan los videos.
- `POST /start` es **idempotente**: si la tarea ya está en curso, devuelve el mismo `deadline`. Al recargar la página el tiempo sigue donde iba y no se reinicia.
- La web calcula el tiempo restante con `deadline` y `serverNow`, que vienen del servidor. Así corrige la diferencia entre el reloj del equipo y el del servidor.
- Los instantes se truncan a **microsegundos**, que es la precisión de Postgres. Sin eso, el `deadline` cambiaba al releerlo de la base.
- Colores del contador según la fracción del tiempo máximo (los mismos cortes del mockup, 30/20/10 s sobre 60 s):

  | Tiempo restante | Color |
  |---|---|
  | Más de 1/2 | Verde |
  | Hasta 1/2 | Amarillo |
  | Hasta 1/3 | Naranja |
  | Hasta 1/6 | Rojo parpadeante |

- **Al vencer el tiempo:** aparece un único aviso y **se puede seguir respondiendo**. La tarea guarda `timed_out=true` y `seconds_used`. La presentación no define qué pasa con una tarea vencida, por eso no se bloquea.

### Respuesta

- La decisión es obligatoria.
- La justificación es obligatoria. Se le quitan los espacios de los extremos y admite hasta 500 caracteres.
- Mientras se responde, el borrador (decisión y justificación) se guarda en `sessionStorage` por tarea. Recargar la página no pierde lo escrito, y el borrador se borra al guardar.

### Evidencia

- Se captura en el navegador. Se dibuja el fotograma del `<video>` en un canvas y se obtiene un JPEG con calidad 0,85.
- El video **debe ser del mismo origen** que la web, o tener CORS. Si no, el navegador no permite leer el fotograma.
- Validaciones del servidor:
  - La tarea debe estar en curso.
  - La cámara debe pertenecer a la consigna.
  - Máximo **6** evidencias por tarea, de 1 byte a 5 MiB.
  - Solo JPEG, PNG o WebP, detectados por sus bytes y no por la extensión.
- Se guarda en MinIO, en el bucket de evidencias de Comando: `evidence/{instanceCountryId}/obsint/{taskId}/{evidenceId}.{ext}`.
- Se registran el SHA-256, el tamaño, el tipo, la cámara, el segundo del video y la hora de captura. Primero se sube el archivo y después se guarda la fila, para no dejar filas que apunten a nada.
- La imagen se sirve con `Cache-Control: private, no-store` y `nosniff`.

### Mensajes al agente

- El mensaje es obligatorio (máximo 500 caracteres) y solo se puede enviar con la tarea en curso.
- **Hoy solo se registra** (`delivery_status=SENT`) y se escribe en el log. Todavía no llega a ninguna app, porque no existe una interconexión aprobada con SGI Operador.

### Seguridad

- Roles con acceso: los mismos de Notificación de Incidentes.
  - PRESIDENTE, GERENCIA_GENERAL, DIRECTOR_OPERACIONES_LATAM, DIRECTOR_OPERACIONES_NACIONAL, DIRECTOR_NACIONAL, DIRECTOR_ZONAL, JEFE_REGIONAL, COORDINADOR_COMPANIA, ASISTENTE_COORDINACION y SUPERVISOR_SEGURIDAD.
  - Agente y cliente no tienen acceso.
- Cada usuario **solo ve y modifica sus propios casos**. Todo se filtra por `instance_country_id` y por el usuario. Un caso o una tarea de otro usuario responde 404.
- Una regla incumplida responde **409** con el mensaje en texto plano, y la web lo muestra tal cual.

### Catálogo DEMO

- Si `SGI_UAT_FEATURES_ENABLED=true`, al abrir el primer caso de un tenant sin consignas se crean **3 consignas de 60 s con 4 cámaras cada una**, las mismas del mockup:

  | Consigna | Texto |
  |---|---|
  | OBS-001 | "El usuario no puede ingresar con ninguna maleta, mochila, bolso, chompa, abrigo." |
  | OBS-002 | "Revisar cualquier movimiento sospechoso." |
  | OBS-003 | "No debe existir vehículo estacionado en ninguna de estas escenas." |

- Se crea al usarse y no en la migración, porque el `instanceCountryId` llega de CORE y no se conoce al migrar.
- Sin esa bandera no se crea nada. Al iniciar un caso, la consola muestra "No hay consignas OBSINT activas para evaluar".
- **Videos DEMO:** las cámaras apuntan a `obsint/videos/{grupo}-{cámara}.mp4` (de `1-1` a `3-4`). La ruta es relativa a la base de la web, así que funciona en `/` y en `/dev.comando/`.
  - Los archivos van en `frontend/public/obsint/videos/` y **no se versionan** (~300 MB, en `.gitignore`). Hay que copiarlos a mano antes de compilar la web.
  - Si faltan, cada cámara muestra "Sin señal".

---

## 4. Aislamiento del módulo

OBSINT está hecho para no romperse cuando cambia el resto de Comando, y para no romperlo a él.

**Backend:** paquete `com.cajamarca.sgi.comando.obsint`, endpoints `/api/obsint/**` y tablas `obsint_*`.
- No tiene llaves foráneas hacia tablas de otros módulos.
- **`ObsintPlatform` es la única clase que usa Comando:** `TenantContext`, `OperationalScopeService`, `StorageService`, `ImageSniffer`, `Digests` y la bandera `sgi.uat.features-enabled`. Si alguno de esos cambia, se ajusta solo ese archivo.
- Comparte con todos los módulos la clase base de entidades (`BaseEntity`) y los nombres de roles en `@RolesAllowed`.

**Frontend:** todo vive en `frontend/src/obsint/`.
- Tiene su propia API (`obsintApi.ts`). De `api.ts` solo usa el transporte (`request`, `binaryRequest`, `multipartRequest` y `ApiError`): token, renovación de IDENT y errores.
- Tiene su propio CSS (`obsint.css`). Todas las clases llevan el prefijo `obs-` y no usa clases de otras pantallas. Cambiar `styles.css` no altera la consola, y al revés tampoco.

**Cambios en archivos compartidos (12 líneas en total):**

| Archivo | Cambio |
|---|---|
| `frontend/src/pages/ConsolaMonitor.tsx` | 1 import, 1 estado, 1 `return` que abre la consola y el botón `ObsintButton` junto al alcance |
| `frontend/src/api.ts` | `export` en `request` y `binaryRequest` |
| `.gitignore` | Excluye `frontend/public/obsint/videos/` |

---

## 5. Archivos

**Backend** (`backend/src/main/java/com/cajamarca/sgi/comando/obsint/`):

| Archivo | Rol |
|---|---|
| `ObsintResource.java` | API REST y contratos JSON (records) |
| `ObsintService.java` | Casos de uso: abrir caso, iniciar, responder, evidencia, mensajes y control de pertenencia |
| `ObsintCase.java` | Caso: hace cumplir el orden de las tareas y cierra el caso |
| `ObsintCaseTask.java` | Tarea: tiempo máximo, validación de la respuesta y fuera de tiempo |
| `ObsintConsigna.java` | Consigna OBSINT y sus cámaras, en orden |
| `ObsintCamera.java` | Cámara (URL del video, en vivo o grabación) |
| `ObsintEvidence.java` | Metadatos de la evidencia; el archivo está en MinIO |
| `ObsintAgentMessage.java` | Mensaje al agente |
| `ObsintDemoCatalog.java` | Catálogo DEMO (solo UAT) |
| `ObsintPlatform.java` | Adaptador hacia los servicios de Comando |
| `ObsintRuleException.java` | Regla incumplida (409, texto plano) |

Base de datos: `backend/src/main/resources/db/migration/V91__obsint_console.sql`.

Pruebas: `backend/src/test/java/com/cajamarca/sgi/comando/obsint/` (`ObsintCaseTest`, `ObsintResourceTest`).

**Frontend** (`frontend/src/obsint/`):

| Archivo | Rol |
|---|---|
| `ObsintConsole.tsx` | Pantalla: carga del caso, cabecera, layout, inicio del tiempo, captura y resumen. Exporta `ObsintButton` |
| `ObsintCameraGrid.tsx` | Grilla de cámaras: selección, pausa, pantalla completa y aviso de "cámaras listas" |
| `ObsintTaskPanel.tsx` | Consigna, decisión, justificación, evidencia y continuar |
| `ObsintCommPanel.tsx` | Mensajes al agente |
| `obsintApi.ts` | Llamadas a `/api/obsint` y texto de errores |
| `obsint.ts` | Tipos (espejo de los records del backend), cuenta regresiva, reloj, borrador, captura de fotograma y formatos |
| `obsint.css` | Estilos del módulo |

---

## 6. Base de datos (V91)

Solo agrega tablas nuevas. Todas llevan `instance_country_id`, `created_at` y `updated_at`.

| Tabla | Contenido |
|---|---|
| `obsint_camera` | `code` (único por tenant), `name`, `location`, `stream_url`, `live`, `active` |
| `obsint_consigna` | `code` (único por tenant), `title`, `instruction`, `time_limit_seconds` (10–3600), `position`, `active` |
| `obsint_consigna_camera` | Cámaras de cada consigna, en orden (`position`) |
| `obsint_case` | `code` (`CAJ-OBS-NNN`, secuencia `obsint_case_code_seq`), `operator`, `status`, `started_at`, `completed_at` |
| `obsint_case_task` | Copia de la consigna (`consigna_code`, `instruction`, `time_limit_seconds`), `status`, `started_at`, `answered_at`, `decision`, `justification`, `seconds_used`, `timed_out` |
| `obsint_evidence` | `task_id`, `camera_id`, `video_time_seconds`, `captured_at`, `bucket`, `object_key`, `content_type`, `size_bytes`, `sha256` |
| `obsint_agent_message` | `task_id`, `operator`, `text`, `sent_at`, `delivery_status` |

Con estas tablas ya se puede calcular buena parte del reporte diario de la diapositiva 13: ejecutadas, cumplidas e incumplidas, tiempo de revisión e incumplimientos con foto, cámara y observación. Falta enlazar las tablas con Cliente, Punto y Servicio (ver §8).

---

## 7. API

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/obsint/cases/current` | Caso en curso del usuario (204 si no hay) |
| POST | `/api/obsint/cases` | Abre un caso con las consignas activas (o devuelve el que está en curso) |
| GET | `/api/obsint/cases/{id}` | Caso con el estado de sus tareas y `currentTaskId` |
| GET | `/api/obsint/tasks/{id}` | Tarea: consigna, cámaras, `deadline`, `serverNow`, respuesta |
| POST | `/api/obsint/tasks/{id}/start` | Inicia el tiempo (idempotente) |
| PUT | `/api/obsint/tasks/{id}/answer` | `{ "decision": "YES"\|"NO"\|"UNCERTAIN", "justification": "…" }`; devuelve el caso |
| GET | `/api/obsint/tasks/{id}/evidence` | Lista de evidencias |
| POST | `/api/obsint/tasks/{id}/evidence` | Multipart: `file`, `cameraId`, `videoTimeSeconds` |
| DELETE | `/api/obsint/tasks/{id}/evidence/{evidenceId}` | Quita evidencia (solo con la tarea en curso) |
| GET | `/api/obsint/tasks/{id}/evidence/{evidenceId}/content` | Imagen |
| GET | `/api/obsint/tasks/{id}/messages` | Mensajes al agente |
| POST | `/api/obsint/tasks/{id}/messages` | `{ "text": "…" }` |

---

## 8. Lo que falta

### De la presentación, fuera de esta versión

1. **Orden de servicio desde SIC: COM** (diapositiva 2). El evento ya trae puestos y turnos, pero **no trae "minutos por día" ni el tipo de producto OBSINT**. Hay que ampliar el contrato con SIC: COM.
2. **Servicios OBSINT en Kaibil** (diapositiva 4). Deben saltarse la asignación a compañías y no mostrar "Retirar a Kaibil".
3. **Configuración del punto reorganizada** (diapositiva 6): Seguridad Física, Seguridad Electrónica (Componentes y OBSINT) y Recursos Tecnológicos.
4. **Componentes de seguridad y cámaras** (diapositivas 7, 8, 16 y 17): ABM por familia; cámaras con IP, puerto y **credenciales cifradas**; vista previa.
5. **Consignas OBSINT por servicio** (diapositiva 10): ABM con categoría, frecuencia, horario por día, tiempo máximo, instrucciones y cámaras. Hoy reemplaza al catálogo DEMO.
6. **Alta "¿vincular a un punto existente?"** (diapositivas 14 y 15). Con "Sí" se configura solo Seguridad Electrónica y OBSINT; con "No" se configuran además ATS y Consignas.
7. **Reportes OBSINT** (diapositivas 11 y 12): botón "Reportes" en la consola, listado filtrable por cliente, punto y fechas, y descarga en PDF. **Solo Presidente y Director Nacional**.
8. **Informe Diario de Consignas en PDF** (diapositiva 13), generado a las 23:59: resumen, desempeño por consigna, incumplimientos con foto, cámara y observación, y observaciones generales.

### Técnico, para pasar a producción

- **Video en vivo real:** el navegador no reproduce RTSP. Hace falta un servidor que convierta el video a HLS o WebRTC, y que lo sirva en el mismo origen o con CORS para poder capturar evidencia.
- **Generar las tareas por horario y frecuencia** de cada consigna. Hoy un caso toma todas las consignas activas de una vez.
- **Enlazar las tablas con Punto, Puesto y Servicio** (nueva migración), y filtrar por el alcance del monitorista (Kaibil).
- **Envío real de mensajes** al agente en sitio, con una interconexión a SGI Operador (requiere SITC).
- **Rol "Monitorista OBSINT"** en IDENT, en lugar de reutilizar los roles de incidentes.
- Correr `ObsintResourceTest` en el entorno de pruebas de Comando y verificar la consola en el navegador (ver §9).

### Preguntas abiertas para el negocio

1. ¿Cómo cuenta **"Incierto"** en el reporte? El PDF solo distingue cumplidas e incumplidas.
2. ¿Qué pasa cuando **vence el tiempo máximo** sin respuesta? ¿Es un incumplimiento, una consigna no ejecutada o se reasigna?
3. ¿Qué pasa al agotar los **minutos por día**? ¿Se bloquea, solo avisa o se factura el exceso?
4. Con varios monitoristas, ¿hay una **cola común** de tareas o se asignan?
5. ¿A qué app llegan los **mensajes al agente en sitio**?
6. ¿Una o varias cámaras por consigna en la vista? La diapositiva 11 muestra 1 y el mockup hasta 4.

---

## 9. Cómo probarlo y qué está verificado

### Levantar en local

1. Copia los 12 videos a `frontend/public/obsint/videos/` (`1-1.mp4` … `3-4.mp4`).
2. Levanta el stack con la bandera UAT:

   ```bash
   SGI_UAT_FEATURES_ENABLED=true docker compose up -d --build backend frontend
   ```

3. Entra a **Consola → OBSINT → Iniciar caso** con un usuario de los roles de §3.

### Verificado

- `ObsintCaseTest`: **6/6 pruebas pasan** (orden, idempotencia del inicio, validaciones, fuera de tiempo y cierre del caso).
- El backend y las pruebas compilan (`mvn test-compile`).
- El frontend compila: `tsc -b` y `vite build` de producción sin errores.

### Pendiente de verificar

- **`ObsintResourceTest`** (flujo completo por API, aislamiento entre usuarios y 403 al cliente) **no se ha ejecutado**. Usa la base de `application.properties`, no una temporal.
- **La pantalla todavía no se ha revisado en el navegador.**
