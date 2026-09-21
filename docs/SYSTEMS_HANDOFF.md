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
- COM v1.0 FROZEN.

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
- VISINT es futuro: no conectar todavía un motor de comparación automático.
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


## SER v0.7.1 — corrección de persistencia UAT — SUPERSEDIDA
- **Esta decisión física fue supersedida por SER v0.7.3.1.** No debe usarse como arquitectura vigente.
- v0.7.1 introdujo temporalmente `patrol_config_*` para resolver una colisión UAT; posteriormente se consolidó el modelo canónico.
- La arquitectura vigente es `patrol_protocol → patrol_definition → patrol_checkpoint → patrol_checkpoint_rule`.
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

## SER v0.8.1 — baseline cerrada de handoff
- **SER v0.8.1 es la baseline cerrada para entrega a Sistemas a fecha 2026-09-12.**
- No equivale a finalización total de SER; identifica el punto de comparación obligatorio para cambios posteriores.
- Leer primero `docs/HANDOFF_SER_v0.8.1_CERRADO.md` y `docs/KNOWN_LIMITATIONS_SER_v0.8.1.md`.
- Cualquier modificación posterior debe versionarse, registrar cambios en `.md` y actualizar `.sitcpack` cuando afecte arquitectura/interconexiones.
