# SGI: Comando — Interconexiones para SITC

Este archivo registra interconexiones que deben representarse posteriormente en SITC.

## CORE → SGI: Comando
**Dirección:** CORE → SGI  
**Tipo lógico:** API/evento de contexto/configuración  
**Datos:** `instance_id`, `instance_country_id`, país, locale, idioma, zona horaria, moneda, sistema de unidades, formatos, calendario/perfiles regulatorios, estado del contexto.  
**Uso:** aislamiento de datos y convenciones transversales.

## CCS → SIC: COM
**Dirección:** CCS → SIC: COM  
**Datos:** estructura de cobertura/turnos y FHE calculado.  
**Nota:** SGI no consume CCS directamente.

## SIC: COM → SGI: Comando
**Dirección:** SIC: COM → SGI  
**Tipo lógico:** eventos + reconciliación/API  
**Datos:** Cliente, logo del Cliente, Servicio y versiones, estado comercial, vigencias, Punto, Puesto, calendarios/turnos, FHE.  
**Eventos conceptuales:** `SERVICE_CREATED`, `SERVICE_UPDATED`, `SERVICE_SUSPENDED`, `SERVICE_TERMINATED`, y equivalentes para Punto/Puesto.  
**Reglas:** idempotencia; FHE/turnos read-only; cambios futuros pasan por Revisado/Preparado en SGI.

## SIC: RRHH → SGI: Comando
**Dirección:** SIC: RRHH → SGI  
**Datos mínimos:** `employee_id`, identidad operativa, cargo, estado laboral, `instance_country_id`; posteriormente habilidades/certificaciones/permisos relevantes.  
**Uso:** membresía primaria a Compañía, compatibilidad con Puestos, disponibilidad operacional.  
**Regla:** SGI no crea personas ni modifica relación laboral.

## SGI: Comando → SIC: RRHH (futuro)
**Dirección:** SGI → SIC: RRHH  
**Objeto:** necesidad operacional `Cambio requerido` / demanda de reemplazo.  
**Regla:** la señal no constituye sanción ni baja; RRHH gestiona la acción laboral y reemplazo.

## SIC: RRMM → SGI: Comando
**Dirección:** SIC: RRMM → SGI  
**Datos:** activos/materiales, estado, identificadores, ubicación/tenencia relevante.  
**Uso:** asignación operacional de recursos a Compañías/Puntos/Puestos/personas, sin cambiar propiedad patrimonial.

## ATS → SGI: Comando
**Estado:** integración reservada; ATS aún no construido.  
**Dirección:** ATS → SGI mediante paquete `.ats`  
**Datos previstos:** planos del Punto, ubicación inicial/base de Puestos, UAP/UTA, amenazas, riesgos, vulnerabilidades, equipos/componentes de seguridad ubicados en plano (p. ej. CCTV, cercos eléctricos), Índice de Riesgo y metadatos de versión/contexto.  
**Uso:** base técnica para configuración operacional y REGESEP.  
**Regla:** ATS sigue siendo SoR del diseño; SGI conserva historial de versiones importadas.

## SGI interno → interfaces operativas
**Dirección:** SGI: Comando → SGI Agente / Supervisor / Operador / Cliente según rol  
**Datos:** configuración ejecutable, asignaciones, consignas, patrullas, relevos, bitácora y estado operacional.  
**Detalle:** se definirá por vertical.

## SGI-06 — interconexiones adicionales para SITC
### SGI: Comando → SGI: Agente / SGI: Supervisor / SGI: Operador
- Tipo: eventos/API de planificación.
- Datos: asignaciones publicadas, Puesto, Punto, turno, horario, AdS.

### SGI: Agente / SGI: Operador → SGI: Comando
- Tipo: eventos/API de ejecución operacional.
- Datos: Relevo, identidad entrante/saliente, fecha/hora, Puesto, evidencia facial, resultado de validación IA, tardanza/no presentación, cobertura real.

### SGI: Comando → SIC: RRHH
- Tipo: eventos laborales derivados de hechos operacionales.
- Datos/eventos:
  - falto confirmado / solicitud de descuento;
  - Reasignación ejecutada / solicitud de pago de día extra;
  - tardanza confirmada / solicitud de descuento;
  - cobertura parcial adicional / solicitud de bonificación.
- Regla: SGI reporta el hecho operacional; SIC: RRHH procesa la consecuencia laboral/nómina.

### SIC: COM → SGI: Comando — TIER contractual
- Objeto fuente: Orden de Servicio.
- Datos relevantes para Asignaciones: Servicio, Punto, Puesto, Formato, Rotación, Turnos, FHE, TIER contratado.
- Uso SGI: derivar/validar el ID mínimo requerido por TIER.
- Mapping vigente: TIER I=6.5; TIER II=7.5; TIER III=8.5; TIER IV=9.5.
- TIER/ID requerido es independiente del IC de habilidades.

### SGI: Operador → SGI: Comando → SMC → SGI: Comando
#### SGI: Operador → SGI: Comando
- Tipo: eventos operacionales.
- Datos: cumplimiento/incumplimiento de tareas, colaborador, Puesto, turno, fecha/hora, evidencia/contexto aplicable.

#### SGI: Comando → SMC
- Tipo: eventos/API de métricas.
- Datos: hechos operacionales normalizados necesarios para KPIs.

#### SMC → SGI: Comando
- Tipo: API/eventos de resultados.
- Datos: ID vigente del colaborador y demás KPIs consumibles por SGI.
- Responsabilidad: SMC calcula y mantiene el KPI; SGI: Comando lo visualiza y utiliza.

### SIC: COM → SGI: Comando — TIER por Puesto
- TIER es atributo del Puesto recibido desde SIC: COM.
- Cada Puesto puede tener su propio TIER.
- SGI deriva de ese TIER el ID mínimo requerido para la asignación.

### SIC: COM → SGI: Comando — materialización de turnos
- La Orden de Servicio/versiones efectivas suministran Punto, Puesto, Formato, Rotación, calendario, turnos, FHE y TIER.
- SGI: Comando materializa las ocurrencias temporales requeridas para la planificación semanal.
- Cada ocurrencia conserva referencia a la versión comercial que la originó.

### SGI: Comando → interfaces operativas — plan semanal publicado
- Datos: Compañía, Punto, Puesto, turno, AdS planificado, horario y versión del plan.
- Consumidores: SGI: Agente, SGI: Supervisor, SGI: Operador.

### SGI-07 — Relevos
- SGI: Agente / SGI: Operador → SGI: Comando.
- Tipo: eventos de ejecución operacional.
- Datos: relevo, identidad real, hora real, Puesto, evidencia facial, validación IA, tardanza, falto, reasignación, cobertura parcial.
- SGI: Comando → SIC: RRHH: consecuencias operacionales auditablemente confirmadas.
- SGI: Comando → SMC: hechos operacionales relevantes para KPIs.

### Confirmación previa de turno
#### SGI: Comando → IVR / Cajamarca Conmigo
- Tipo: solicitud de confirmación previa.
- Momento objetivo: aproximadamente T-90 min antes del turno.
- Datos: AdS, turno, Punto, Puesto, hora de inicio, canal.

#### IVR / Cajamarca Conmigo → SGI: Comando
- Tipo: evento de respuesta.
- Datos: confirmado / no asistirá / sin respuesta, timestamp, canal, motivo cuando aplique.
- Regla: confirmación expresa intención; no sustituye Relevo.

### SIC: RRMM → SGI: Comando / Relevo
- Tipo: API/consulta de inventario asignado al Puesto.
- Datos: activos/materiales esperados, identificadores, estado aplicable y titularidad/fuente.
- Uso: comparación durante Relevo de Puesto.

### SGI: Comando → SGI: Agente / Supervisor / futuro SGI: Operador
- Debe mantenerse contrato de Relevos desacoplado del canal.
- Futuro objetivo: SGI: Operador móvil unifica funciones actualmente separadas.

### SGI-07 — validación uniforme e inventario
#### SGI: Agente / futuro SGI: Operador → SGI: Comando
- Datos adicionales: fotografía de uniforme, método de validación (`MANUAL|AI`), resultado y evidencia.

#### SGI: Comando ↔ servicio IA de validación visual
- Integración futura/opcional para validación automática de uniforme.
- La arquitectura debe soportar sustitución de revisión manual por IA sin cambiar el contrato de Relevo.

#### SIC: RRMM → SGI: Comando
- Inventario esperado por Puesto.
- Las discrepancias críticas pueden continuar mediante excepción autorizada; SGI genera alerta y auditoría.

### SGI-08 — Consignas
#### SGI: Comando → SGI: Agente / SGI: Supervisor / futuro SGI: Operador
- Tipo: eventos/API de publicación de Consignas.
- Datos: consignment_id, versión, título/contenido, alcance Punto/Puesto, vigencia, recurrencia, prioridad.

#### Interfaces operativas → SGI: Comando
- Tipo: evento de acuse/confirmación.
- Datos: employee_id, consignment_version_id, Puesto, Relevo cuando aplica, fecha/hora, confirmación de comprensión/recepción.

#### CORE → SGI: Comando
- Zona horaria/calendario para evaluar vigencias y recurrencias.

### SGI: Comando → STC — Tareas derivadas
- Familias: Incidentes, Requerimientos, Actividades.
- Origen: SGI: Cliente, SGI: Agente, SGI: Supervisor, SGI: Comando, futuro SGI: Operador.
- Convergencia: SGI: Comando.
- Tipo: API/eventos.
- Datos mínimos: tipo/subtipo, origen, autor, Cliente/Servicio/Punto/Puesto, descripción, prioridad/criticidad aplicable, evidencias, fecha/hora, `instance_country_id`.
- Debe existir correlación entre el registro SGI y el objeto/tarea creado en STC.
- Pendiente congelar si SGI conserva el registro de origen como entidad autoritativa mientras STC es SoR del workflow de resolución.

### SGI: Comando ↔ futuro ATS — Componentes de Seguridad / Vulnerabilidades
- Hallazgos `Componentes de Seguridad` y Vulnerabilidades quedan en SGI: Comando.
- A futuro deberán poder conciliarse con el inventario/modelo técnico de componentes de ATS.
- Ejemplos: nueva cámara observada, cámara reparada, cambio de estado de un componente.
- Hasta que ATS exista, SGI conserva el hecho operacional y la relación con Vulnerabilidades.

### SGI: Cliente — Novedades
- `Novedades` es una vista/agrupación compuesta por Hallazgos, Vulnerabilidades e Incidentes.
- Criterio: elementos que ameritan atención del cliente.
- No duplica entidades; SGI: Cliente consume las entidades fuente desde SGI: Comando con filtros/visibilidad autorizada.

### SGI: Cliente / Agente / Supervisor → SGI: Comando — Consigna Propuesta
- Tipo: API/evento de propuesta.
- Datos: autor, canal, Punto/Puesto, contenido, prioridad, vigencia/aplicación propuesta, evidencia.
- Estado inicial: Pendiente de aprobación.
- Coordinador/Asistente en SGI: Comando aprueban/publican o rechazan.
- Solo la publicación genera Consigna oficial + nueva versión de REGESEP.

### STC → SGI: Comando — feedback de resolución
- Para Incidentes/Requerimientos/Actividades, STC devuelve estado, responsable, progreso/cierre y resultado.
- SGI mantiene el contexto operacional original y la correlación con el objeto STC.

### SGI-NOV-01 — Hallazgos/Vulnerabilidades
#### SGI: Cliente / Agente / Supervisor / Comando / futuro Operador → SGI: Comando
- Tipo: API/eventos de reporte.
- Objetos: Hallazgo, Vulnerabilidad.
- Datos: actor, canal, Cliente/Servicio/Punto/Puesto, subtipo, descripción, evidencia, ubicación, fecha/hora.

#### SGI: Comando → SGI: Cliente
- Tipo: API/vista `Novedades`.
- Datos: Hallazgos + Vulnerabilidades + Incidentes autorizados para el cliente.
- No duplica entidades.

#### SGI: Comando ↔ ATS (futuro)
- Vulnerabilidades: vínculo con UAP/UTA/componentes/riesgos.
- Hallazgos `Componentes de Seguridad`: conciliación de cambios físicos/estado con el modelo técnico.
- SGI conserva el hecho operacional; ATS conserva el diseño técnico.

### SGI-NOV-01 — ATS por Componentes confirmados
- Un Hallazgo `Componentes de Seguridad` confirmado genera una necesidad de conciliación técnica futura con ATS.
- SGI conserva el hecho operacional, actor, turno y evidencia.
- ATS deberá incorporar/conciliar el componente y versionar el `.ats` correspondiente.
- Hasta que ATS exista, el estado de conciliación permanece `PENDING`.

### SGI-TASK-01 — contrato SGI ↔ STC
#### SGI: Comando → STC
- Tipo: API/eventos idempotentes.
- Objetos: Incidente, Requerimiento, Actividad.
- Datos: `instance_country_id`, `work_origin_id`, tipo/subtipo, Cliente/Servicio/Punto/Puesto, autor/canal, título/descripción, evidencia, fechas, prioridad/criticidad aplicable.
- STC devuelve `stc_task_id`.

#### STC → SGI: Comando
- Tipo: eventos/API de feedback.
- Datos: `stc_task_id`, estado, responsable visible, fechas clave, progreso/resolución/cierre, correlation_id.
- SGI actualiza snapshot para sus interfaces.

#### Resiliencia
- Outbox/reintentos.
- Idempotencia por `work_origin_id`/`event_id`.
- Estados de routing SGI: pendiente/enviado/error.

### STC ↔ SGI — catálogo de Incidentes
- SGI envía categoría, subcategoría y tipo de Incidente conforme al catálogo vigente.
- STC debe conservar estos identificadores/taxonomía como contexto de origen, sin reinterpretarlos silenciosamente.
- La taxonomía de referencia actual contiene 3 categorías, 20 subcategorías y 90 tipos.

### SGI-09 — Bitácora / Protocolos de Acceso
#### SGI: Comando → SGI: Agente / Supervisor / futuro SGI: Operador
- Tipo: API/configuración operacional.
- Datos: Puesto, Protocolo/Acreditación, versión, PAX/VHL/CONT, Ingreso/Egreso/Ambos, campos de Identificación/Verificación/Autorización.

#### Interfaces operativas → SGI: Comando
- Tipo: eventos de Bitácora.
- Datos: movimiento, objeto, protocolo/version usado, campos capturados, evidencias, autorizador, resultado, timestamp, Agente ejecutor.

#### Biblioteca interna de Protocolos
- Protocolos exportables pueden importarse entre Puestos autorizados.
- La importación crea copia versionada y conserva referencia al origen.
- No hay propagación silenciosa de cambios.

### SGI-09 — lectura QR/Barcode y rollout de estándares
#### SGI: Agente / futuro SGI: Operador → SGI: Comando
- Captura manual o automática de campos de Bitácora.
- Lectores admitidos inicialmente: QR y Código de Barras.
- Los payloads se mapean a campos configurados; SGI conserva provenance por valor.

#### SGI: Comando — gobierno interno de Protocolos
- Protocolos exportables de Compañía: alcance restringido a la misma Compañía.
- Protocolos Estándar Cajamarca: alcance transversal.
- Director de Operaciones Nacional puede ordenar `FORCED_ROLLOUT` a todos los Puestos derivados.
- Cada rollout debe generar nuevas versiones locales y de REGESEP, con auditoría/correlación completa.

### SGI-09 — movimiento multiobjeto
- La ejecución genera un `AccessMovement` con una o más instancias PAX/VHL/CONT.
- Cada objeto conserva identificación/verificación/evidencia propia.
- Todos comparten contexto de Punto/Puesto, Protocolo, dirección, timestamp y resultado del movimiento.
- La estructura evita crear registros independientes y enlazarlos manualmente.

### SGI-09 — offline y caché móvil
#### SGI: Comando → SGI: Agente / futuro Operador
- Protocolos vigentes por Puesto.
- Último snapshot autorizado de Listas Blancas/Negras y datos necesarios para operación offline.
- Datos estructurados recientes para caché local limitada.

#### SGI: Agente / futuro Operador → SGI: Comando
- Movimientos, pasos internos, regularizaciones y evidencias pendientes.
- Sincronización idempotente con ACK server-side antes de purga local.

#### Retención móvil
- Texto: 7 días post-sincronización.
- Fotos/evidencias: mantener hasta sincronización confirmada; luego pueden eliminarse del dispositivo.
- Base histórica: SGI: Comando.

### SGI-10 — Patrullas
#### SGI: Comando → SGI: Agente / futuro SGI: Operador
- Tipo: API/configuración operacional.
- Datos: Ruta/version, Plan, Ocurrencia, Hitos, reglas, evidencia requerida.

#### SGI: Agente / futuro Operador → SGI: Comando
- Tipo: eventos de ejecución.
- Datos: inicio/fin, Hitos, GPS, precisión, evidencia, resultado, observaciones, Novedades vinculadas.

#### SGI: Comando → SMC
- Hechos normalizados de cumplimiento, puntualidad, completitud y Hitos.

#### SGI: Comando ↔ REGESEP
- Patrullas vigentes se incorporan al reglamento estructurado y sus cambios efectivos lo versionan.

### SGI-11 — REGESEP / ATS
#### ATS → SGI: Comando / REGESEP
- ATS es SoR del diseño técnico.
- Entrega versión `.ats` efectiva y/o eventos/API futuros.
- Datos: planos, UAP, UTA, amenazas, vulnerabilidades, riesgos, componentes, coberturas/medidas e índices técnicos publicados.
- REGESEP conserva la versión ATS exacta en su manifest de fuentes.

#### SGI: Comando → REGESEP
- Puestos y configuración normativa.
- Relevos.
- Protocolos de Bitácora.
- Patrullas.
- Consignas.

#### REGESEP → interfaces
- SGI: Comando: versión completa/histórica.
- SGI: Cliente: versión vigente/histórica según permisos.
- SGI: Agente/futuro Operador: ejecución mediante deltas y módulos operativos; acceso al reglamento según política.
### SGI-06 — Asignaciones / SIC: RRHH / SIC: COM / SMC
#### SIC: RRHH → SGI: Comando
- Personal por Compañía: Agentes, Escoltas y Supervisores.
- Estado laboral.
- Vacaciones, permisos médicos y otras indisponibilidades con intervalos desde/hasta.
- Habilidades/competencias.
- SGI consume snapshots operacionales; RRHH conserva el SoR.

#### SIC: COM → SGI: Comando
- Puesto, Formato y exactamente los Turnos comerciales vigentes.
- FHE y TIER por Puesto.
- UAT usa templates locales y materializa `ShiftOccurrence` por semana.

#### SMC → SGI: Comando
- ID vigente por colaborador.
- `ID < mínimo TIER` genera warning, no bloqueo.

#### SGI: Comando → interfaces operativas
- Plan publicado inmutable y Reasignaciones posteriores como eventos explícitos.
## SGI-06 v0.2 — delta de interconexiones
### SIC: COM → SGI-06
- SoR: SIC: COM.
- Datos: Punto, Puesto, Formato, Turnos exactos, FHE, TIER, versión comercial.
- SGI materializa `ShiftOccurrence`; no redefine el compromiso comercial.

### SIC: RRHH → SGI-06
- SoR: SIC: RRHH.
- Datos: membresía de Compañía, rol, estado laboral, habilidades, vacaciones, permiso médico e indisponibilidades con intervalos.
- SGI conserva snapshot operacional read-only para evaluación y trazabilidad histórica.

### SMC → SGI-06
- SoR: SMC para ID/KPI.
- Dato: ID vigente.
- ID bajo TIER = warning, nunca bloqueo.

### SGI-06 → SGI-07 Relevos
- Publica Plan y asignación planificada por `ShiftOccurrence`.
- SGI-07 será SoR de ejecución real del Relevo.

### SGI-06 → Dashboard / SMC
- Hechos: cobertura publicada, cobertura actual, vacantes, asignaciones posteriores, Reasignaciones.
- Fan-out mediante Outbox cuando corresponda.


## UAT v0.3 — SGI-00T Territorio
- CORE → SGI-00T: `instance_country_id` y contexto país.
- SIC: RRHH / identidad corporativa → SGI: Comando: cargo/rol del usuario.
- SGI-00T → SGI-01/02/03/04/06: scope territorial País/Zona/Región/Compañía.
- SGI-00T es SoR dentro de SGI: Comando para la estructura operacional Zona/Región; no reemplaza geografía regulatoria de CORE.

## UAT v0.3 — SGI-06
- SIC: COM puede entregar N Turnos por Puesto; SGI materializa una `ShiftOccurrence` por Turno y fecha.
- El endpoint de evaluación de elegibilidad usa las mismas reglas backend del Drag & Drop para producir GREEN/AMBER/RED.

## UAT v0.4 — extensiones
- SIC: RRHH → SGI-00T Territorio: listado de personal activo para Responsable de Zona/Región. SGI almacena referencia, no expediente.
- CORE/Instancia–País → SGI-00T: catálogo de País y futura fuente maestra de Provincias/Estados; UAT v0.4 usa seed Ecuador local.
- SGI-00T → SGI-01 Compañías: `region_id` obligatorio; Zona se deriva de Región.
- SGI-00T → todas las consultas operativas: scope País/Zona/Región/Compañía server-side.
- SIC: RRHH → SGI-06: vacaciones/permiso médico, rol, habilidades y pertenencia a Compañía.
- SIC: COM → SGI-06: Turnos exactos por Puesto; SGI deriva únicamente la etiqueta visual Diurno/Nocturno/Mañana/Tarde/Noche/Madrugada.
- SMC → SGI-06: ID vigente; en resumen de Puesto se presenta normalizado a porcentaje solo para semáforo visual.


## UAT v0.5 — Territorio / CORE
- **CORE → SGI-00T Territorio**: catálogo canónico de Provincias/Estados/Departamentos equivalentes por `instance_country_id`. CORE es SoR; SGI asigna esas subdivisiones a Zonas/Regiones. UAT usa adapter/snapshot `CORE LOCAL`.
- **SIC: RRHH → SGI-00T Territorio**: personal activo elegible para responsables de Zona/Región.
- **SIC: COM → SGI-06 Asignaciones**: Turnos lógicos exactos. El frontend puede segmentarlos visualmente por día calendario sin crear nuevos Turnos ni cambiar el SoR.

## TER v0.1 — contrato territorial CORE
**CORE → TER (SGI: Comando)**  
CORE es System of Record del catálogo político-administrativo del país. Por `instance_country_id` debe entregar como mínimo:
- código/nombre de país;
- `subdivisionType` (`PROVINCE`, `STATE`, etc.);
- `subdivisionSingular` (texto UI singular);
- `subdivisionPlural` (texto UI plural);
- catálogo de subdivisiones `{code, name, status}`;
- opcionalmente geometría/metadata cartográfica.

TER no crea estas unidades. Solo administra su agrupación operacional en Zonas/Regiones. UAT Ecuador usa `CORE LOCAL · UAT` como adapter/snapshot.
