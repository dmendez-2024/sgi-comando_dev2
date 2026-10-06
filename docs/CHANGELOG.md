# SGI: Comando — Changelog

## 2026-09-07 — Modelo Maestro v0.1
- Renombrado SGI: Administrador → SGI: Comando.
- Adoptado branding/logo SGI: Comando.
- Definido CORE → Instancia–País como raíz obligatoria.
- Definido SGI-01 Compañías: identidad, estructura, estados, capacidad, membresía primaria.
- Definido SGI-02 Servicios: fuente SIC: COM, turnos/FHE read-only, versionado y preparación de cambios.
- Definido SGI-03 Puntos: fuente SIC: COM, Compañía obligatoria, ATS/REGESEP preparados, Punto puede cambiar de Servicio conservando identidad.
- Definida Dotación Humana actual/proyectada y flujo `Cambio requerido`.
- Iniciado diseño SGI-04 Puestos.
- Registradas interconexiones CORE, CCS, SIC: COM, SIC: RRHH, SIC: RRMM y ATS para SITC.

- SGI-04 Puestos cerrado v0.1: FHE a dos decimales, nomenclatura Formato/Personalizado, tipos ACC/PAT/VIG/MIX, 8 habilidades 0–5, múltiples fotografías y regla de nueva identidad al cambiar de Punto.
- SGI-05 ATS diferido hasta construir ATS; reservado contrato `.ats` con planos, Puestos, UAP, amenazas, vulnerabilidades, riesgos, componentes de seguridad e Índice de Riesgo.
- SGI-06 Asignaciones iniciado: separación Membership/Asignación temporal, matriz visual por Puesto/turno, pool de Agentes, compatibilidad, drag & drop/copia, Borrador→Publicado→Cerrado y auditoría.

## v0.4 — SGI-06 Asignaciones
- UI mantiene estructura funcional histórica.
- IC redefinido como índice exclusivamente de habilidades.
- Incorporada regla de no auto-relevo.
- Incorporada cobertura parcial de relevo de hasta 3h.
- Nomenclatura `Reasignación`.
- Relevo pasa a ser fuente de verdad de asistencia real.
- Nuevos eventos SGI → SIC: RRHH para faltos, tardanzas, pago de día extra y bonificaciones.

## v0.5 — IC / ID / TIER
- Separados formalmente Índice de Compatibilidad (IC) e Índice de Desempeño (ID).
- Congelada fórmula del IC.
- Incorporados IC Planificado e IC Ejecutado semanales.
- Incorporado TIER contractual desde SIC: COM y tabla de ID mínimo por TIER.

## v0.6 — SMC / ID por Puesto
- TIER redefinido como atributo del Puesto.
- Permitida asignación con ID inferior al mínimo, con advertencia visual.
- SMC definido como System of Record de ID/KPIs.
- Registrado flujo SGI: Operador → SGI: Comando → SMC → SGI: Comando.

## v0.7 — planificación semanal
- Formalizada la unidad de planificación Compañía + semana.
- Introducido concepto de ocurrencia concreta de turno derivada de SIC: COM.
- Definida secuencia de validación: elegibilidad → ID/TIER → IC.
- Añadidos indicadores de planificación y alertas base.
- Conservados IC Planificado e IC Ejecutado.

## v0.8 — cierre SGI-06 / inicio SGI-07
- Permitida publicación con turnos vacantes y alerta crítica.
- IC exacto + bandas visuales configurables.
- IC Ejecutado ponderado por minutos cuando hay cobertura compartida.
- Filtro semanal por Punto.
- Iniciado diseño SGI-07 Relevos.

## v0.9 — confirmación y relevo integral
- Añadida confirmación previa T-90 por IVR/Cajamarca Conmigo.
- Separados estados de confirmación y ejecución.
- `Ejecutado` sustituye conceptualmente a `Confirmado` como evidencia de Relevo realizado.
- Default de tolerancia de tardanza: 0 min, configurable por Instancia–País.
- Relevo bilateral valida saliente y entrante.
- Añadido Relevo unilateral auditable.
- Relevo estructurado en Agentes + Puesto/Inventario + Consignas.
- Registrada integración de inventario con SIC: RRMM.
- Registrada evolución futura hacia SGI: Operador móvil.

## v1.0 — cierre SGI-07 Relevos
- Añadido `validation_method = MANUAL | AI` para uniforme.
- Inventario crítico faltante: continuidad con excepción autorizada y alerta crítica.
- Confirmación obligatoria de todas las Consignas vigentes en cada Relevo.
- Priorización visual de Consignas nuevas/modificadas.
- SGI-07 queda funcionalmente cerrado en v0.1.

## v1.1 — inicio SGI-08 Consignas
- Iniciado módulo SGI-08.
- Consignas versionadas y programables por vigencia/frecuencia tipo calendario.
- Integración formal con Relevos y confirmación de todas las Consignas vigentes.
- Registradas interconexiones de distribución/acuse para SITC.

## v1.2 — Consignas + Taxonomía Tareas/Novedades
- Eliminado `Recurrente` como tipo separado de Consigna.
- Separadas Vigencia y Aplicación por calendario.
- REGESEP pasa a ser reglamento vivo/versionado ante cambios de Consignas.
- Definidos autores/canales múltiples para creación de Consignas y Novedades.
- Incorporada taxonomía maestra TAREA: Consignas, Hallazgos, Vulnerabilidades, Incidentes, Requerimientos, Actividades.
- Hallazgos suma `Componentes de Seguridad`.
- Definido routing: Consignas/Hallazgos/Vulnerabilidades permanecen en SGI; Incidentes/Requerimientos/Actividades → STC.
- Registrada futura conciliación de Componentes de Seguridad/Vulnerabilidades con ATS.

## v1.3 — Novedades y aprobación de Consignas
- `Novedades` redefinido como Hallazgos + Vulnerabilidades + Incidentes para SGI: Cliente.
- Formalizado flujo Consigna Propuesta → aprobación/publicación.
- Confirmado modelo SGI origen + STC workflow para Incidentes/Requerimientos/Actividades.
- Hallazgos de Componentes de Seguridad pueden sugerir resolución de Vulnerabilidades sin cierre automático.

## v1.4 — Hallazgos y Vulnerabilidades
- Iniciado SGI-NOV-01.
- Formalizada vista Novedades = Hallazgos + Vulnerabilidades + Incidentes.
- Diseñados subtipos y relaciones Hallazgo↔Vulnerabilidad.
- Incorporado Hallazgo `Componentes de Seguridad`.
- Preparada conciliación futura con ATS.

## v1.5 — lifecycle Hallazgos/Vulnerabilidades
- Hallazgos simples reducidos a estado `Reportado` + ventana configurable de visibilidad.
- Eliminado routing STC para Hallazgos.
- Componentes de Seguridad incorporan confirmación por segundo Agente o por el mismo en turno posterior si es único.
- Vulnerabilidades incorporan confirmaciones acumulativas `xN`.
- Mitigada/Resuelta/Descartada quedan restringidas a Coordinador/Asistente.

## v1.6 — aprobación de estado Vulnerabilidades
- Corregido modelo de permisos: Agente/Supervisor/Cliente pueden proponer Mitigada/Resuelta/Descartada.
- Coordinador/Asistente deben aprobar para que el cambio surta efecto.
- Añadido límite de una confirmación por Agente por turno.
- Confirmada ventana global de Hallazgos simples con default 48h.

## v1.7 — Incidentes/Requerimientos/Actividades + STC
- Iniciado SGI-TASK-01.
- Formalizada separación SGI origen operacional vs STC workflow.
- Definido contrato de correlación, feedback y resiliencia.
- Incidentes permanecen en Novedades; Requerimientos/Actividades quedan fuera.

## v1.8 — cierre SGI-TASK-01 + taxonomía de Incidentes
- Cerradas reglas de visibilidad de Requerimientos/Actividades.
- Prioridad de Incidentes congelada en Normal / Alta / Crítica.
- Incidentes cerrados permanecen visibles en SGI: Cliente.
- Incorporada la taxonomía de `Incidentes.xlsx` como referencia: 3 categorías, 20 subcategorías y 90 tipos.

## v1.9 — inicio SGI-09 Bitácora
- Iniciado diseño de Bitácora por Puesto.
- Formalizados Protocolos de Acceso/Acreditaciones para PAX, VHL y CONT.
- Añadido alcance Ingreso/Egreso/Ambos.
- Definida Biblioteca de Protocolos e importación por snapshot versionado.
- Definido esquema generalizable de Identificación, Verificación y Autorización.

## v2.0 — Bitácora: estándares, Y/O y captura automática
- Definido alcance de Protocolos exportables por Compañía.
- Definido gobierno de Protocolos Estándar Cajamarca.
- Añadido privilegio exclusivo de rollout forzado para Director de Operaciones Nacional.
- Añadido motor lógico Y/O mediante grupos ALL/ANY.
- Añadida captura manual obligatoriamente disponible + autofill QR/Barcode.
- Confirmado versionado REGESEP por cambios efectivos de Protocolo.

## v2.1 — Bitácora multiobjeto
- Congelado `AMBOS` como mismas reglas en ambas direcciones.
- Congelados resultados Autorizado/Rechazado/Excepción Autorizada.
- Añadidos Protocolos multiobjeto con cardinalidad por PAX/VHL/CONT.
- Añadido `AccessMovement` como transacción contenedora de múltiples objetos relacionados.

## v2.2 — permanencia y listas
- Cerrado soporte multiobjeto generalizado.
- Añadido diseño de sesión de permanencia Ingreso→Egreso.
- Añadida vista derivada de objetos actualmente dentro.
- Añadidos Observaciones obligatorias y atribución operacional del Agente.
- Añadido diseño base de Listas Blancas/Negras.

## v2.3 — regularización de presencia duplicada
- Cerradas reglas de sesiones abiertas y White/Black Lists.
- Añadido flujo para detectar nuevo Ingreso de objeto ya presente.
- Añadido `Egreso Regularizado` con doble timestamp: salida declarada vs momento de corrección.
- Regularización aplica a PAX/VHL/CONT y conserva historial completo.

## v2.4 — UX de ejecución Bitácora
- Diseñado flujo operativo SGI: Agente / futuro SGI: Operador.
- Añadido scan-first, selección automática de Protocolo y ejecución visual ALL/ANY.
- Añadidos flujos de Ingreso, Egreso, Actualmente Dentro e Historial.
- Preparadas decisiones futuras de offline y generación contextual de Incidente/Hallazgo.

## v2.5 — offline, multi-Puesto y permanencia prolongada
- Eliminado acceso general al historial completo desde app móvil.
- Definida caché móvil 7 días y política de evidencias hasta sincronización.
- Diseñada presencia a nivel Punto con pasos internos entre Puestos.
- Añadido `CheckpointPassage` para controles interiores sin duplicar acreditación.
- Añadida salida superficial a las 24h como estado `Por regularizar`, sin inventar Egreso.
- Añadida creación contextual de Incidente/Hallazgo desde Bitácora.

## v2.6 — cierre SGI-09 Bitácora v0.1
- Congelada restricción de caché móvil al Punto actualmente atendido.
- Congelada inmutabilidad de datos reutilizados entre Puestos del mismo Punto.
- SGI-09 Bitácora v0.1 marcado como funcionalmente cerrado.

## v2.7 — inicio SGI-10 Patrullas
- Iniciado diseño funcional de Patrullas.
- Separados Ruta, Plan, Ocurrencia y Ejecución.
- Añadidos Hitos con validación GPS/QR/NFC/foto/manual.
- Añadidos offline, Novedades contextuales, métricas y relación con SMC/REGESEP.

## v2.8 — clasificación maestra de Patrullas
- Incorporados dos ejes independientes: Programada/No Programada y Cerrada/Abierta.
- Añadidas las cuatro combinaciones válidas.
- Patrulla Abierta crea/cierra Hitos dinámicos durante la ejecución.
- Secuencia Estricta/Flexible queda exclusiva de Patrulla Cerrada.
- Ajustado modelo con `PatrolDefinition` y `schedule_type`.

## v2.9 — código de Puestos y Novedades entre Hitos
- Incorporada nomenclatura oficial de códigos de Puesto.
- Añadido manejo de colisiones de base por discriminador de Punto.
- Confirmado que Patrullas Cerradas pueden generar Novedades durante todo el recorrido, incluso entre Hitos.
- Cerradas reglas de Patrulla Abierta mínima, ventana Programada e inicio libre No Programada.

## v3.0 — responsabilidad y trazado de Patrullas
- Congelada responsabilidad de Patrulla a nivel Puesto.
- Añadido breadcrumb GPS cada 2 minutos durante ejecución.
- Congelada regla de tardanza por finalización fuera de ventana.

## v3.1 — relevo y excepciones de Patrulla
- Eliminado traspaso de una misma Patrulla entre Agentes.
- Añadido cierre `Incompleta` previo al Relevo.
- Añadido estado de Hito `Exceptuado` y resultado `Ejecutada con Excepción`.
- Congelada regla de apertura de ventana para Patrullas Programadas.

## v3.2 — cierre automático y métricas de Patrullas
- Añadido cierre automático `No ejecutada` al terminar ventana sin inicio.
- Separadas métricas Programadas vs No Programadas.
- Eliminado resultado global `Interrumpida por Excepción`; interrupciones quedan `Incompletas`.

## v3.3 — simplificación de Hitos de Patrulla
- Eliminado `Hito Exceptuado`.
- Eliminado resultado `Ejecutada con Excepción`.
- Todos los Hitos de Patrulla Cerrada quedan obligatorios.
- Resultado por Hito simplificado a Cumplido/No cumplido.
- Patrulla con cualquier Hito no cumplido → Incompleta.
- Eliminados Hitos opcionales y duración objetivo de v0.1.

## v3.4 — cierre SGI-10 Patrullas v0.1
- Congelado bloqueo de secuencia estricta.
- Añadidos reintentos auditables por Hito.
- Marcado SGI-10 Patrullas v0.1 como funcionalmente cerrado.

## v3.5 — inicio SGI-11 REGESEP
- Iniciado diseño de REGESEP como reglamento estructurado y versionado por Punto.
- Formalizado ATS como fuente principal del diseño técnico de seguridad.
- Definida composición desde ATS/SIC/SGI en vez de edición manual.
- Definidos manifest de fuentes, disparadores de versionado y representación PDF.

## v3.6 — decisiones base REGESEP
- Congelado un REGESEP por Punto.
- Congelado versionado automático por nueva versión ATS efectiva.
- Definida separación Vulnerabilidad operacional SGI vs incorporación técnica ATS.
- Definido acceso Cliente y distribución por deltas para Agente/Operador.

## Implementación UAT v0.2 — SGI-06 Asignaciones
- Implementado adapter `SIC: RRHH LOCAL` con personal por Compañía, habilidades, vacaciones y permisos médicos.
- Implementado adapter `SIC: COM LOCAL` para templates de Turno y materialización semanal de `ShiftOccurrence`.
- Implementada matriz semanal, Drag & Drop, Drag & Copy, IC, warnings ID/TIER/rol/Cambio Requerido y bloqueos de elegibilidad.
- Publicación crea snapshot inmutable; cambios posteriores son Reasignaciones auditables.
- Dashboard consume cobertura semanal real de Asignaciones.
## UAT v0.2.2 — SGI-06 Asignaciones v0.2
- Normalizada documentación al estándar RRMM: MASTER/ARCHITECTURE/DECISIONS/CHANGELOG/INTEGRATIONS + modules + SITC packs.
- Diferenciada `Asignación posterior a publicación` de `Reasignación`.
- Añadidas Cobertura Publicada y Cobertura Actual.
- Snapshot de publicación enriquecido con contexto operacional RRHH/SMC.
- Añadido cierre automático semanal de Planes publicados.
- Añadidos scripts PowerShell estables `uat-start.ps1`, `uat-stop.ps1`, `uat-open.ps1`.


## UAT v0.2.2.1 — Hotfix de scripts PowerShell
- Corregidos `uat-start.ps1`, `uat-stop.ps1`, `uat-reset.ps1` y aliases para compatibilidad con Windows PowerShell 5.1.
- Los scripts ejecutables quedan ASCII-safe para evitar errores de parseo por codificación UTF-8 sin BOM en Windows PowerShell heredado.
- Añadidas validaciones explícitas de `$LASTEXITCODE` para `docker compose`.
- Sin cambios funcionales en SGI-06 Asignaciones; la vertical permanece en v0.2.

## UAT v0.2.2.2 — Hotfix compilación backend SGI-06

- Corrige 6 errores `javac` concentrados en 3 expresiones de `AssignmentResource.java`.
- Causa: inferencia genérica de los métodos estáticos Panache `list(...)` al encadenarlos directamente con `stream().collect(...)`; el compilador infería `PanacheEntityBase` en lugar de la entidad concreta.
- Corrección: materializar primero `List<PostEntity>`, `List<EmployeeOperationalSnapshot>` y `List<ShiftOccurrenceEntity>` tipadas, y luego construir los mapas.
- Sin cambios funcionales ni de Decision Log. SGI-06 Asignaciones permanece en v0.2.
- Scripts UAT permanecen ASCII-safe para Windows PowerShell 5.1.

## UAT v0.3
- Nueva vertical SGI-00T Territorio v0.1.
- Asignaciones sube a v0.3: selección de persona + semáforo de elegibilidad, retratos ficticios, tarjetas legibles, soporte UAT de 3 Turnos de 8 h.
- Alcance territorial server-side aplicado a Compañías, Operaciones y Asignaciones.
- Copia de ciclos futuros registrada como backlog inmediato, no incluida en esta entrega.

## UAT v0.4 — 2026-09-08
- SGI-00T Territorio v0.2: Zonas/Regiones configurables, responsables RRHH, Provincias/Estados, auditoría y mapa operacional UAT.
- SGI-01 Compañías v0.3: Región obligatoria visible en UI y creación con Región.
- SGI-06 Asignaciones v0.4: Guardar Borrador + autosave 15m, RBAC de edición Coordinador/Asistente, interacción multi-celda tipo Excel, detalle IC, fichas Agente/Puesto, TIER/horas/IC/ID y nomenclatura automática de turnos.
## UAT v0.4.0.1 — Hotfix compilación Territorio
- Corrige 4 errores `javac` en `TerritoryResource.java`.
- Causa: uso de `ConflictException`, clase que no existe en Jakarta REST.
- Corrección: respuestas HTTP 409 mediante `WebApplicationException(..., Response.Status.CONFLICT)`.
- Sin cambios funcionales: SGI-00T permanece v0.2 y SGI-06 permanece v0.4.



## INT v0.1 — 2026-09-21
- Módulo genérico de interconexiones conforme SITC-NOM-001 v3.0.
- 24 interconexiones canónicas y 30 interfaces registradas.
- `SGI_Comando_CURRENT.sitcpack` migra a snapshot v3 importable en CORE; el JSON histórico queda preservado como `SGI_Comando_CURRENT_LEGACY_v0.2.json`.
- Se agrega COMPONENT_DELTA versionado y SCENARIO_SNAPSHOT acumulativo para CORE.
- No se modifica UI ni lógica funcional de verticales congeladas.
- Sin migración de base de datos.

## SGI_COM — Documentación de cambios y tablas — revisión v4.1 — 2026-09-25

Baseline: código y migraciones presentes en el workspace; snapshot CORE v4.1 no disponible para comparación.
Responsable/aprobación: pendiente de confirmar.

### Cambios documentales

- `DOC-001 | PERMITIDO |` Se incorporó el handoff general, resumen UAT, limitaciones, impacto al ecosistema, siguientes acciones e impacto de base de datos, marcando los estados no confirmados como pendientes.
- `DOC-002 | PERMITIDO |` Se alinearon notas de las verticales COM, TER, ASI y SER con las migraciones/cambios observados; no se alteraron decisiones congeladas ni se declaró aprobación de RC.
- `DOC-003 | PERMITIDO |` Se documentaron las brechas de SITC v3.0→v4.1 y los CR/aprobaciones faltantes.

### Archivos / migraciones

- Nuevos docs: `docs/00_HANDOFF.md`, `docs/UAT.md`, `docs/KNOWN_LIMITATIONS.md`, `docs/ECOSYSTEM_IMPACT.md`, `docs/NEXT_ACTIONS.md`, `docs/DATABASE_IMPACT.md`.
- Vertical docs actualizados: COM, TER, ASI y SER bajo `docs/verticals/`.
- No se modificó ni ejecutó migración SQL; el inventario de V27–V33 se basa en lectura de archivos.
- Se observó que V30/V31 cargan/eliminan datos de empleados y `quarkus.flyway.migrate-at-start=true`; queda como gate de entorno.

### Integraciones / SITC

- Sin nuevo delta SITC generado. El snapshot vigente v4.1 de CORE no estuvo disponible para comparar; los IDs locales siguen pendientes de reconciliación.

### Validación

- Documentación contrastada contra el inventario de los 40 Markdown de `cambios/`, migraciones V27–V33 y rutas API citadas.
- No se ejecutaron build, tests, UAT, migraciones ni restauraciones de base de datos.
- Estado del resultado: revisión documental con acciones/CRs pendientes; no es cierre de RC ni gate de producción.


## 2026-09-27 — P0/P1 RC
Ver `docs/CHANGELOG_P0P1_2026-09-27.md`. Sin cambios de UI y sin EVC.


## 2026-09-27 — CSL v0.1.1 UAT — Filtros colapsables
- `Operaciones > Consola`: búsqueda y filtros pasan a accordion/collapsible.
- Cerrado por defecto; al expandir conserva todos los controles y reglas existentes.
- Sin cambios de backend, BD, interconexiones/SITC ni EVC.
- Ver `docs/CHANGELOG_CSL_v0.1.1_2026-09-27.md`.

## CSL v0.2 — Notificación de Incidentes — 2026-09-27
- Nuevo flujo `Notificar Incidente` en Consola con panel lateral.
- Categoría/subcategoría, criticidad, Cliente/Punto/Puesto, colaboradores, Descripción, Resolución, imágenes y Sanción.
- Borrador/Finalizado reeditables.
- Inasistencia Programada/Efectiva incorpora ranking UAT de Agentes para reasignación.
- Sin cambios backend/BD/SITC; EVC fuera de alcance.


## CSL v0.2.1 — 2026-09-27
- Taxonomía de Incidentes sincronizada con `Incidentes(1).xlsx`: 3 categorías, 20 subcategorías, 90 incidentes.
- Jerarquía corregida a Categoría → Subcategoría → Incidente.
- Inasistencia programada/efectiva quedan como incidentes de Asistencia y Puntualidad.
# 2026-09-29 — BIT-INT-001 RC1

- Extiende el runtime de operador con protocolos de Bitácora `ACTIVO` del puesto asignado.
- Añade acreditaciones y campos configurados sin migración de base de datos.
- Mantiene la persistencia operacional fuera de alcance hasta aprobar el contrato de escritura.
- Añade fixture UAT explícito e idempotente para `alex.chiriboga`, separado de Flyway y bloqueado si la identidad de empleado no es única.

# SGI_COM - OPR-ASSIGNMENT-001 - RC2 - 2026-09-30

Baseline: `SGI_COM_BIT_INT_001_RC1_2026-09-29`  
Responsable: Sistemas / solicitud funcional UAT

### Cambios realizados
- `OPR-ASSIGNMENT-001` | APROBADO | Selección temporal del turno vigente o del próximo turno desde 59 minutos antes.
- El turno vigente prevalece sobre el próximo; ambigüedades reales continúan bloqueadas.
- La fecha/hora se evalúa en SGI_COM, no en el dispositivo móvil.

### Archivos / migraciones
- `backend/.../operator/OperatorAssignmentWindow.java`
- `backend/.../operator/OperatorResource.java`
- `backend/.../operator/OperatorAssignmentWindowTest.java`
- Sin migración de base de datos.

### Integraciones / SITC
- Sin cambio topológico ni nueva interconexión; se mantiene `SGI_OPR -> SGI_COM`.
- No requiere nuevo `*.sitcpack` porque no cambia arquitectura ni identidad del contrato; se agrega respuesta compatible.

### Validación
- Pruebas focalizadas y de contrato: 12/12 aprobadas (`OperatorAssignmentWindowTest` y `ReliefContractTest`).
- Build del backend en contenedor: aprobado.
- Arranque UAT integrado: backend y conexión PostgreSQL en estado `UP`.
- Caso `alex.chiriboga`: la asignación vencida del 2026-09-29 queda excluida y la asignación vigente del 2026-09-30 queda seleccionada.
- Pendiente: confirmación funcional del inicio de sesión desde el dispositivo móvil.
- Observación de entorno: `HealthTest` aislado no inicia sin la credencial PostgreSQL ni los parámetros de apertura requeridos por el JDK local; la salud integrada en contenedor sí fue verificada.

# SGI_COM - OPR-CONSIGNMENT-VISIBILITY-001 - RC1 - 2026-10-01

Baseline: `SGI_COM_OPR_ASSIGNMENT_RC2_2026-09-30`  
Versión frontend: `0.11.1`

### Corrección
- Las solicitudes reales de Consignas recibidas desde SGI Operador ya no se vuelven a filtrar en el frontend mediante los códigos de compañía del dataset DEMO.
- El backend permanece como autoridad de alcance RBAC para `GET /api/v1/operator/consignment-review-requests`.
- El filtro local por `scopeByUser` se conserva exclusivamente para las filas estáticas de demostración.

### Impacto
- Frontend: `frontend/src/pages/ConsignasExecution.tsx`.
- Backend, base de datos y Flyway: sin cambios.
- SITC: sin cambio de contrato, topología, identidad, payload ni System of Record; no requiere nuevo `*.sitcpack`.

### Validación
- Evidencia y casos UAT: `docs/VALIDATION_OPR_CONSIGNMENT_VISIBILITY_2026-10-01.md`.

# SGI_COM - OPR-PATROL-HISTORY-001 - RC1 - 2026-10-01

Baseline: `OPR-CONSIGNMENT-VISIBILITY-001 RC1`  
Versión frontend: `0.11.2`

### Cambios
- Nuevo `GET /api/v1/operator/patrol-executions` de solo lectura para ejecuciones reales, hitos, contexto operacional y responsable.
- La consulta limita resultados por tenant, identidad del Agente y alcance RBAC de Compañía para perfiles de Comando.
- `Operaciones > Consignas > Patrullas` combina el historial real con las referencias DEMO existentes y permite inspeccionar su línea de tiempo.

### Impacto
- Backend y frontend modificados; sin migración de base de datos.
- Contrato interno UI/backend ampliado de forma aditiva.
- Sin nueva interconexión, cambio de topología, payload móvil, SoR o `*.sitcpack`.

### Validación
- `docs/VALIDATION_OPR_PATROL_HISTORY_2026-10-01.md`.

# SGI_COM - OPR-PATROL-LIFECYCLE-001 - RC1 - 2026-10-01

Baseline: `OPR-PATROL-HISTORY-001 RC1`

### Cambios
- El contrato de ejecución de Patrullas incorpora inicio explícito e idempotente (`START`) y cierre de la ejecución confirmada (`FINISH`).
- Nuevo `GET /api/v1/operator/patrol-executions/current` para recuperar el estado y la hora oficial al reabrir la app.
- Abrir o consultar una Patrulla ya no crea una ejecución ni inicia el cronómetro.

### Impacto SITC
- Cambio aditivo de interfaz en `SGI_OPR_SGI_COM_0001_v001`: IF10 ampliada e IF13 nueva.
- Sin nueva interconexión, credencial, topología ni tabla paralela; `V37` agrega la referencia de asignación y unicidad de ejecución activa al `patrol_execution` canónico.
- Evidencia: `docs/VALIDATION_OPR_PATROL_LIFECYCLE_2026-10-01.md`.

# SGI_COM - UAT-PATROL-PRESENTATION-001 - 2026-10-01

### Datos UAT versionados
- Fixture `database/uat-fixtures/PAT_02_protocolo_unico_tres_patrullas_presentacion.sql`.
- Puesto `PTO-001 / GGTT01`: un único protocolo activo, `PRO-PAT-0004 · Protocolo Integral de Prevención y Control`.
- Tres patrullas activas y programadas: Apertura Segura, Protección de Activos Críticos y Cierre Perimetral Preventivo.
- Cada patrulla contiene tres Hitos y cada Hito exige inspección visual, fotografía y confirmación.
- La carga es transaccional, idempotente y acotada al tenant/puesto UAT.

# SGI_COM - OPR-EVIDENCE-LOCATION-001 / VISINT fases 5–9 - 2026-10-06

Rama `acordova` · commits `5ac2f76`, `28b566c`, `bc0fafd`. Estado detallado: `docs/VISINT_ESTADO_IMPLEMENTACION.md`.

### Cambios
- **Relevo con VISINT opcional:** casilla por Puesto; las 3 fotos del puesto se validan contra sus fotos estándar y se ven en Operación (módulo Relevo). Nunca bloquea el relevo (`V55`).
- **Umbral de coincidencia `matchThreshold`:** VISINT lo exige; Comando lo configura por tarea junto a las fotos estándar (Hito, Consigna, Bitácora, Puesto) con un control 0–1 que marca como bajo hasta 0.40. Se guarda en cada revisión (`V56`).
- **Ubicación GPS de referencia:** latitud/longitud editables en Hito, Consigna (modo GPS) y Puesto (referencia de Bitácora y Relevo, `V57`). Operación muestra "Fuera del radio GPS" y la distancia.
- **No bloqueante:** se acepta una nueva captura mientras la anterior está pendiente, con error o "no cumple"; mensajes al agente actualizados.
- **Radio GPS configurable desde la base:** nuevo menú Configuración (`/api/settings/evidence-location`, tabla `operational_setting`) y radio propio por Hito, Consigna y Puesto; cada foto guarda su distancia y radio (`V58`).
- Correcciones: estilos del control de umbral en Puestos y Consignas; regresiones restauradas (`/executions`, tareas del runtime, galería de Consignas); el Simulador usa el `instanceCountryId` del runtime.
- Contrato para SGI: Operador documentado en `docs/API_CATALOG.md` → `OPR-EVIDENCE-LOCATION-001`.

### Impacto SITC
- Cambio aditivo en `SGI_OPR_SGI_COM_0001_v001` / `0002_v001` (campos opcionales `latitude`, `longitude`, `accuracyM`, `radiusM`, `postLocation`, `stationVisintStatus`, `station`). Sin interconexión, credencial ni topología nuevas.
- `SGI_COM_VISINT_0001_v002`: campo nuevo `matchThreshold` en el formulario (requerido por VISINT).
- Migraciones `V55`–`V58`. Variable nueva `SGI_VISINT_MATCH_THRESHOLD` (0.8).

### Validación
- Builds de backend y frontend en Docker; pruebas E2E por API y Playwright contra el VISINT real.
- Videos en `C:/Proyectos/sgi_comando/evidencias_playwright/`: `v6` (relevo), `v7` (GPS Hito, parcial), `v8` (umbral), `v9` (GPS consigna), `v10` (GPS Bitácora y Relevo), `v11` (radio configurable).
