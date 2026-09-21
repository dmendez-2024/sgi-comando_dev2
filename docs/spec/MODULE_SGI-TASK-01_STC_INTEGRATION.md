# SGI-TASK-01 — Incidentes, Requerimientos y Actividades / Integración STC v0.1 (En diseño)

## Principio
Incidentes, Requerimientos y Actividades pueden originarse desde interfaces SGI, convergen en SGI: Comando y generan un objeto de trabajo en STC.

Separación de responsabilidades:
- SGI conserva el hecho/instrucción operacional de origen y su contexto.
- STC (Sistema de Tareas Cajamarca) es System of Record del workflow de resolución/ejecución.
- STC devuelve estado, progreso y cierre a SGI.
- SGI no duplica el motor de workflow de STC.

## Taxonomía
### Incidentes
Subtipos congelados:
- Servicio
- Seguridad
- Administrativo

Los Incidentes forman parte de `Novedades` y son visibles en SGI: Cliente conforme a permisos/contexto.

### Requerimientos
Peticiones de Cliente u otros actores que requieren evaluación/ejecución.
No forman parte de `Novedades`.

### Actividades
Trabajos internos delegados por jefaturas.
No forman parte de `Novedades`.

## Canales de origen
Pueden originar Incidentes, Requerimientos o Actividades:
- Cliente → SGI: Cliente.
- Agente → SGI: Agente.
- Supervisor → SGI: Supervisor.
- Coordinador → SGI: Comando.
- Asistente → SGI: Comando.
- Futuro Agente/Supervisor → SGI: Operador.

Todo converge en SGI: Comando antes del routing a STC.

## Registro operacional de origen en SGI
Entidad conceptual común:
`SgiWorkOrigin`
- `work_origin_id`
- `instance_country_id`
- `work_type`: INCIDENT | REQUIREMENT | ACTIVITY
- `subtype` cuando aplique
- `client_id`
- `service_id`
- `point_id`
- `post_id` nullable
- `company_id`
- `source_channel`
- `created_by`
- `created_at`
- `occurred_at` / `requested_at` según tipo
- `title`
- `description`
- `evidence_refs`
- `location` opcional
- `client_visibility`
- `routing_status`
- `stc_task_id` nullable
- `stc_status_snapshot` nullable
- `last_stc_sync_at`
- auditoría

El payload original sometido queda preservado; correcciones posteriores se registran como eventos/auditoría.

## Routing SGI → STC
Al crear un objeto:
1. SGI valida contexto y permisos.
2. SGI guarda el registro operacional de origen.
3. SGI genera solicitud idempotente de creación en STC.
4. STC crea el caso/tarea principal.
5. STC devuelve `stc_task_id`.
6. SGI guarda la correlación.
7. Cambios de estado/progreso/cierre retornan desde STC.

Relación recomendada:
- 1 registro SGI de origen → 1 caso/tarea principal STC.
- STC puede crear subtareas internas sin que SGI necesite replicarlas todas.

## Estados de routing en SGI
Estos NO son estados del workflow de resolución:
- `PENDIENTE_ENVIO`
- `ENVIADO`
- `ERROR_ENVIO`

Los estados operacionales de resolución pertenecen a STC y SGI solo mantiene un snapshot/mapeo de lectura.

## Resiliencia
Si STC no está disponible:
- SGI NO pierde el registro.
- queda `PENDIENTE_ENVIO` o `ERROR_ENVIO`;
- se reintenta mediante outbox/eventos;
- los envíos deben ser idempotentes para evitar duplicados.
- la UI de Comando muestra pendientes de sincronización.

## Incidentes
Campos específicos recomendados:
- `incident_subtype`: SERVICE | SECURITY | ADMINISTRATIVE
- fecha/hora del incidente
- ubicación/Punto/Puesto
- personas/activos relacionados opcionales
- evidencia
- criticidad/prioridad (pendiente de congelar escala)
- client_visible = true

En SGI: Cliente:
- aparece dentro de `Novedades`.
- muestra estado de resolución retornado desde STC.

## Requerimientos
Campos específicos recomendados:
- solicitante
- descripción
- fecha requerida opcional
- área/destino sugerido opcional
- evidencia/adjuntos
- client_visibility según origen/política

STC administra:
- responsable
- SLA/fecha objetivo
- progreso
- comentarios operativos
- resolución/cierre

## Actividades
Campos específicos recomendados:
- instrucción/trabajo
- creador/jefatura
- contexto operativo opcional
- responsable/área sugerida opcional
- fecha objetivo opcional

STC administra el ciclo real:
- asignación
- responsable
- vencimiento
- progreso
- cierre

## Feedback STC → SGI
Eventos/snapshots:
- TASK_CREATED
- TASK_ASSIGNED
- TASK_STATUS_CHANGED
- TASK_RESOLVED
- TASK_CLOSED
- TASK_CANCELLED (si STC lo contempla)

SGI conserva:
- `stc_task_id`
- estado resumido
- responsable visible cuando corresponda
- fechas clave
- resultado/cierre
- última sincronización

## SGI: Cliente
### Incidentes
Se muestran dentro de `Novedades`.

### Requerimientos
Recomendación pendiente de congelar:
- mostrarlos en una sección separada `Requerimientos` / `Mis Requerimientos`, no en Novedades.

### Actividades
Por defecto internas; no visibles en SGI: Cliente salvo regla futura explícita.

## Auditoría y correlación
Eventos SGI:
- WORK_ORIGIN_CREATED
- STC_CREATE_REQUESTED
- STC_TASK_LINKED
- STC_SYNC_FAILED
- STC_STATUS_RECEIVED
- STC_CLOSED_RECEIVED

Toda comunicación debe incluir:
- `instance_country_id`
- `work_origin_id`
- `stc_task_id` cuando exista
- `correlation_id`
- `event_id`
- timestamp
- version/sequence cuando aplique

## SITC
Interconexión principal:
SGI: Cliente / Agente / Supervisor / Comando / futuro Operador
→ SGI: Comando
→ STC
→ SGI: Comando
→ interfaces SGI correspondientes

SGI es dueño del contexto operacional de origen.
STC es dueño del workflow de trabajo.

## Decisiones congeladas — visibilidad y prioridad
- SGI: Cliente tendrá una sección separada `Mis Requerimientos`, fuera de Novedades, mostrando el estado devuelto por STC.
- Actividades son internas por defecto y no visibles al Cliente.
- Prioridad de Incidentes: Normal / Alta / Crítica.
- Un Incidente cerrado por STC permanece visible en SGI: Cliente; no expira automáticamente. El Cliente puede ocultarlo mediante filtros/cambio de vista.
- La taxonomía detallada de Incidentes suministrada en `Incidentes.xlsx` se conserva en `REFERENCE_INCIDENT_TAXONOMY.md`.
