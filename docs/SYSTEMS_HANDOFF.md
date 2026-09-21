# SGI: Comando — Estrategia de Handoff a Sistemas

La meta de estos paquetes es que, al finalizar las verticales, Sistemas reciba un repositorio autocontenido con código, migraciones, documentación, contratos, historial de decisiones y arquitectura SITC.

## Artefactos obligatorios que se acumulan en cada versión

- Código fuente completo.
- Docker Compose / configuración de runtime.
- Migraciones Flyway inmutables.
- Documentación global.
- Documentación por vertical.
- Decision Logs.
- Criterios de aceptación y regresión.
- Manifest y hashes.
- `sitc/SGI_Comando_CURRENT.sitcpack`.
- Deltas SITC por vertical/version.
- Notas de producción y adapters transitorios a sustituir.

## Principio

Sistemas no debe reconstruir requisitos desde chats. El paquete debe contener el contexto suficiente para implementar/desplegar sin reinterpretar decisiones funcionales.

## Estado actual

- TER v1.0 FROZEN.
- COM v1.1.3 FROZEN.
- SER v0.10.10 FROZEN.
- COO v0.1 UAT_CANDIDATE.

Los adapters UAT/locales que no son productivos se documentan explícitamente como requisitos de hardening antes del go-live.

## SER v0.5 — ATS importado y plano real
- Endpoint actual ATS por Punto: `GET /api/points/{pointId}/ats`.
- Historial: `GET /api/points/{pointId}/ats/history`.
- Importación binaria: `POST /api/points/{pointId}/ats/upload?filename=...` con `Content-Type: application/octet-stream`.
- Plano materializado: `GET /api/points/{pointId}/ats/plan`.
- Descarga del paquete original: `GET /api/points/{pointId}/ats/download`.
- Persistencia nueva: `ats_point_package` (Flyway V11).
- Configuración de Puestos guarda `ats_package_id`, `ats_location_x`, `ats_location_y` en coordenadas normalizadas.
- El importador no ejecuta contenido del `.ats`; solo procesa ZIP/JSON y extrae el asset de plano declarado.


## SER v0.6 — Bitácora configurada
- Nueva página `Servicios → Configuración → Bitácora`.
- Persistencia: `logbook_protocol`, `logbook_protocol_field`.
- Fotos estándar se almacenan como activo binario UAT en PostgreSQL; producción podrá migrarlas a object storage sin cambiar el contrato funcional.
- En SER v0.6 la comparación VISINT aún no formaba parte de esa UAT. **Superseding 2026-09-20:** ya está definido el contrato runtime `SGI_OPR → SGI_COM → VISINT → SGI_COM` y el motor de Impulsos reside en SGI: Comando; ver `docs/SGI_OPR_VISINT_IMPULSOS.md`. La definición arquitectónica no implica que el adapter VISINT esté implementado en esta UAT.
- No mezclar esta configuración con registros ejecutados; la ejecución pertenece a Operación.


## SER v0.6.1 — Bitácora / Acreditaciones
- No elimine la capa Acreditación: `Protocolo → Acreditación → Reglas/Campos`.
- Tampoco elimine la capa **Protocolo**. Su función es de gobierno/versionado/vigencia: permite tener varias configuraciones completas preparadas (por ejemplo estándar, contingencia o evento especial) y activar/aplicar la que corresponda sin recrear Acreditaciones.
- Definición canónica para Sistemas/Operaciones: **Protocolo = paquete/versionado de operación y vigencia**; **Acreditación = forma específica de acreditar al objeto dentro del Protocolo**.
- Flujo conceptual de ejecución: determinar Protocolo vigente/aplicable → determinar Acreditación del objeto → ejecutar Identificación, Verificación, Autorización, Evidencias/Captura y Listas de esa Acreditación.
- Identificación, Verificación, Autorización, Evidencias, Captura y Listas son configuración de la Acreditación seleccionada.
- `logbook_protocol_field.accreditation_id` es obligatorio.
- Evidencias es un resumen derivado, no un editor duplicado.

## SER v0.7 — Patrullas + patrón de versionado transversal
- Regla obligatoria para Configuración: una versión publicada es **inmutable**. Para cambiarla, crear un nuevo BORRADOR por copia; publicar sustituye vigencia y conserva el snapshot anterior.
- Esta regla ya se aplica en **Bitácora** y **Patrullas** y debe reutilizarse al implementar las siguientes páginas de Configuración.
- Jerarquía de Patrullas: `Punto → Puesto → Protocolo → Patrulla → Reglas`.
- Matriz: Cerrada/Abierta × Programada/No Programada.
- Patrulla Cerrada: hasta 25 Hitos, secuencia Estricta/Flexible.
- Configuración de Hitos admite mezcla dentro de la misma Patrulla: **+ Agregar hito en plano** (ATS) y **+ Agregar hito en campo** (GPS móvil).
- Cada Hito conserva `ATS`, `FIELD` o `MIXED` y, cuando corresponda, referencia a package ATS, X/Y normalizado, GPS, precisión, fecha/hora y usuario.
- Nunca inferir WGS84 desde el plano ATS si el package `.ats` no provee calibración.
- Foto estándar del Hito es un activo versionado y será el patrón de futura comparación VISINT.
- Flyway v0.7: `V14__ser_versioned_configuration_and_patrols.sql`.


## SER v0.7.1 — corrección de persistencia UAT
- El baseline ya posee `patrol_definition` y `patrol_checkpoint`; NO deben reutilizarse para la nueva Configuración de Patrullas.
- Configuración usa `patrol_config_definition`, `patrol_config_checkpoint` y `patrol_config_checkpoint_rule`, bajo `patrol_protocol`.
- Esta separación evita alterar el modelo histórico de ejecución y conserva el patrón versionable aprobado.
- `uat-start.ps1` debe esperar `/q/health/ready` antes de considerar iniciado el UAT.

## SER v0.7.3.1 — Patrullas canónicas
Patrullas **NO** debe recrear tablas `patrol_config_*`. La estructura canónica es `patrol_protocol → patrol_definition → patrol_checkpoint → patrol_checkpoint_rule`. Las tablas baseline `patrol_definition` y `patrol_checkpoint` se evolucionan porque también serán referenciadas por la futura ejecución (`patrol_plan`, `patrol_occurrence`, `patrol_execution`, etc.). Toda versión publicada es inmutable; cualquier edición parte de un nuevo borrador versionado.


## SER v0.8 — Consignas
- Nueva vertical `Configuración → Consignas`.
- Modelo: `Punto → Protocolo de Consignas → Consigna → Alcance → Reglas/Evidencias`.
- Protocolos independientes de Bitácora/Patrullas; un solo Protocolo vigente de Consignas por Punto.
- Publicado = snapshot inmutable; editar crea una versión Borrador.
- Alcance de Consigna: Todo el Punto o 1..n Puestos.
- Foto estándar y ubicación esperada (ATS/GPS) preparan futura auditoría VISINT.
- La regla de alcance 1..n Puestos también está aprobada para Bitácora/Patrullas, pero no se modifica su UAT en esta entrega.


## COM v1.1 / ASI v0.7 — 2026-09-19

### COM — Catálogo CORE
- CORE es SoR de Nombre, Logo y Reseña histórica de Compañía.
- SGI ya no crea Compañías de cero: activa elementos del catálogo CORE.
- SGI edita únicamente Estado, Motivo del cambio, Zona y Regiones operativas.
- Kaibil es Compañía de Operaciones, `always_active=true`, sin Servicios/Clientes/Puntos por el momento.

### ASI — Transferencias entre Compañías
- SIC: RRHH continúa siendo SoR de la relación persona–Compañía.
- Origen envía; destino acepta/rechaza; no existe “pull”.
- Al enviar se liberan asignaciones futuras del origen. El turno actual y el histórico permanecen.
- Mientras está pendiente, origen puede anular. Después de aceptación, la transacción es irreversible.
- Si existe turno actual, la efectividad ocurre al cierre de ese turno.
- Motivo de catálogo y Observaciones son obligatorios.
- Personal disponible expone Entrantes/Salientes y la ficha individual contiene las acciones.

### Contratos nuevos
- `CORE → SGI_COM`: catálogo de Compañías e identidad.
- `SIC_RRHH → SGI_ASI`: membresía actual persona–Compañía.
- `SGI_ASI → SIC_RRHH`: solicitud/evento de cambio de Compañía tras aceptación.

Ver `docs/CHANGELOG_UAT_COM_v1.1.md`, `docs/CHANGELOG_UAT_ASI_v0.7.md`, `sitc/COM_v1.1_delta.sitcpack` y `sitc/ASI_v0.7_delta.sitcpack`.


## COM v1.1.1 / ASI v0.7.1 — corrección de migración V18
La migración de Catálogo CORE debe reconciliar Compañías preexistentes por `(instance_country_id, code)` y preservar sus UUID. No asumir IDs determinísticos para Compañías que pueden existir desde UAT previas. `company_region`, snapshots y referencias deben resolver siempre el `company.id` real. Esta corrección no cambia la lógica funcional de COM v1.1 / ASI v0.7.


## COM v1.1.2 / ASI v0.7.2 — corrección de aceptación de transferencia
- No cambia la regla funcional del flujo aprobado.
- Al hacer efectiva una transferencia, cerrar y hacer flush de la membresía PRIMARY activa antes de insertar la nueva membresía de destino; PostgreSQL mantiene `ux_membership_primary_active`.
- Confirmaciones de Aceptar/Rechazar/Anular se resuelven con modal SGI y muestran errores de backend dentro del mismo modal.

## COM v1.1.3 / ASI v0.7.3 — UX de liderazgo y confirmaciones
- En Asignaciones, Presidencia, Director Nacional, Director Zonal y Jefe Regional deben iniciar con **Kaibil** como Compañía seleccionada cuando esté disponible dentro de su scope.
- Para esos perfiles el selector de Compañía permanece visible incluso sobre Kaibil; el backend sigue limitando el catálogo por ámbito territorial.
- Enviar/Aceptar/Rechazar/Anular transferencias usa modal SGI; no usar confirmaciones nativas del navegador para estas acciones.
- No hay cambio de esquema; V18 continúa siendo la última migración.


## SER v0.9 — Servicios desde SIC: COM / asignación inicial
- SIC: COM entrega `Cliente + Punto + Puestos`; es SoR comercial.
- SIC: COM **no** asigna la Compañía operativa.
- Punto nuevo llega a SGI con Compañía nula y estado `PENDING`; la UI lo coloca en bandeja lógica **Kaibil**.
- Kaibil no es propietaria del Servicio y nunca es destino operacional de Servicios de clientes.
- Solo Presidencia/Director Nacional/Director Zonal/Jefe Regional pueden resolver la asignación inicial, respetando ámbito nacional/zonal/regional.
- Tras asignar, `point.company_id` toma la Compañía destino y se habilita Configuración.
- Migración V19 crea auditoría `service_company_assignment_event`.

### Contrato de personal entrante
SIC: RRHH debe entregar a SGI cada colaborador ya adscrito a **Seguridad Física (SF) + Compañía**. SIC: RRHH sigue siendo SoR de la relación laboral inicial; SGI no debe inferir esa Compañía.


## SER v0.9.1 — movimiento operacional de Servicios
- La Compañía operadora es una asignación de SER; la configuración operacional permanece ligada al Punto/Servicio.
- Flujo: `Compañía operadora → Kaibil (PENDING) → nueva Compañía`.
- En el retiro, eliminar de planificación activa únicamente asignaciones futuras; conservar filas históricas y turno en curso.
- `operational_transition_until` impide que la nueva Compañía duplique cobertura antes del cierre del turno heredado.
- No crear copias de ATS/Bitácora/Patrullas/Consignas al reasignar: se reutiliza el mismo `point_id`.
- Auditoría obligatoria en `service_company_assignment_event`.


## ASI v0.7.4 — transición heredada de Servicio
- ASI consume `point.operational_transition_until` definido por SER.
- Antes de esa fecha/hora no debe materializar cobertura nueva de la Compañía destino en el Servicio reasignado.
- La regla debe aplicarse coherentemente a week view, validación de asignación, cobertura y publicación.


## SER v0.10.1 — Protocolos / Bitácora / Patrullas
- Estados canónicos de Protocolo: `BORRADOR`, `INACTIVO`, `ACTIVO`.
- Publicar no activa: `BORRADOR -> INACTIVO`; Activar/Inactivar son transiciones operacionales independientes.
- Bitácora: máximo 10 Acreditaciones por Protocolo, validado tanto por frontend como por backend.
- Patrullas: UI homologado con Bitácora: `Puestos del Punto -> Protocolos del Puesto -> Detalle del Protocolo`, y dentro del detalle `Patrullas del protocolo -> Configuración de la Patrulla`.
- Consignas conserva estados internos de la **Consigna** (`PUBLICADO`/`VIGENTE`) para compatibilidad de consumo operacional; la estandarización Borrador/Inactivo/Activo aplica al **Protocolo**.
- Flyway V21 migra estados legacy sin crear modelos paralelos.
- UAT: backend `:8080`; frontend `:5173`. `uat-open.ps1` abre siempre `http://localhost:5173`.


## COO v0.1 — Coordinación
- Vertical nueva entre Servicios y Asignaciones.
- Entidades persistentes: `coordination_post`, `supervision_route`, `supervision_route_point`.
- COO consume Compañía/Punto/Puesto y `post_shift_template` en lectura; no modifica la vertical SER congelada.
- `CoordinationPost` es SoR de Puestos internos de Monitoreo/Supervisión, Formato, Rotación y horario.
- `SupervisionRoute` mantiene series versionadas: `DRAFT -> ACTIVE`, y la versión previa pasa a `REPLACED`.
- Cada parada es un Punto; la ejecución por turno determina los Puestos aplicables desde los horarios requeridos.
- Para Kaibil, COO v0.1 expone como universo de Ruta los Puntos operativos dentro del ámbito territorial del usuario.
- ASI aún no materializa estos Puestos de Coordinación; esa integración queda para la siguiente iteración.

## Handoff transversal — VISINT / Impulsos (2026-09-20)

Sistemas debe preservar la separación de responsabilidades:

- **SGI: Operador:** captura y presenta resultado.
- **SGI: Comando:** orquesta evidencia, solicita revisión, recibe respuesta, ejecuta reglas probabilísticas y mantiene ledger de Impulsos.
- **VISINT:** valida evidencia visual, sin lógica de gamificación.

La adjudicación debe ser idempotente. Un retry de VISINT o del bus de eventos no puede volver a ejecutar el sorteo ni acreditar Impulsos duplicados. Los mockups de Operador viajan como referencia en `docs/assets/impulsos_visint/`.

## Cierre final — 2026-09-20

SGI: Comando queda cerrado con todas las verticales funcionales congeladas. Para arquitectura vigente usar `sitc/SGI_Comando_FINAL_2026-09-20.sitcpack`. Para dependencias de CORE usar `docs/CORE_REQUIREMENTS_SGI_COMANDO.md` y `docs/SGI_Comando_Requerimientos_CORE.pdf`. PERF v0.1 representa hardening técnico y no una reapertura funcional/UI.
