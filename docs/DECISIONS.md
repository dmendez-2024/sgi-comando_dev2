# SGI: Comando — Decisiones

## D-001 Nombre del sistema
El módulo antes denominado SGI: Administrador pasa a llamarse **SGI: Comando**.

## D-002 Identidad visual
Usar el logo oficial de SGI: Comando entregado por el usuario. UI administrativa: sidebar oscuro / acento rojo / área de trabajo clara y de alta densidad informativa.

## D-003 Contexto CORE
Toda arquitectura SGI parte de `Instancia–País`; no se duplican parámetros editables de CORE en SGI.

## D-004 Compañías
Las Compañías se definen en SGI: Comando, no en SIC: COM. Una Compañía agrupa aproximadamente 200–400 Agentes de Seguridad más mando/supervisión y puede incluir Escoltas de Seguridad (Ronin).

## D-005 Servicios, Puntos y Puestos
SIC: COM entrega Servicio, Punto y Puesto vendidos. SGI agrega la capa operacional de seguridad sin recrearlos comercialmente.

## D-006 Compañía por Punto
Un Servicio puede estar operado por varias Compañías, pero un Punto tiene una sola Compañía responsable activa.

## D-007 FHE
FHE viene de SIC: COM/CCS, puede ser fraccionario y es read-only en SGI. Se agrega por Compañía sumando FHE de Puestos de sus Puntos; no se usa un total inter-Compañías como métrica operacional.

## D-008 Dotación Humana
Estados: Subdotación / Óptimo / Sobredotación. Se muestran situación actual y proyectada considerando `Cambio requerido`.

## D-009 Cambio requerido
Coordinador o Asistente puede solicitar el cambio de un Agente sin retirarlo todavía. Afecta inmediatamente el proyectado, conserva trazabilidad y no altera la relación laboral en RRHH.

## D-010 ATS / REGESEP
ATS es externo y exporta `.ats` (reemplaza antiguo `.uta`). REGESEP se arma para Puntos estáticos usando el diseño ATS como insumo. Seguridad dinámica futura usará REGESER para Rutas.

## D-011 Formato de Puesto
La estructura horaria vendida se denomina **Formato**. Puede ser resumible (24/7, 12/5, etc.) o **Personalizado** cuando requiere calendario/horario detallado.

## D-012 Tipo de Puesto y habilidades
Actividades base: ACC, PAT y VIG. SGI deriva automáticamente el Tipo; dos o más actividades = MIX. Se mantienen 8 habilidades escala 0–5. En MIX, el default por habilidad es el máximo de las actividades componentes.

## D-013 Identidad de Puesto
Un Puesto no se traslada entre Puntos conservando identidad. Se cierra en el Punto original y SIC: COM crea uno nuevo en el nuevo Punto.

## D-014 ATS diferido
SGI-05 se difiere hasta construir ATS. El paquete `.ats` deberá transportar al menos planos, ubicación inicial de Puestos, UAP, amenazas, vulnerabilidades, riesgos y componentes/equipos de seguridad en plano. Estos datos permitirán generar un Índice de Riesgo; su fórmula queda pendiente del diseño ATS.

## SGI-06 — decisiones 2026-09-07
- Mantener la estructura funcional general del prototipo histórico de Asignaciones; modernizar UI sin reemplazar el flujo.
- Compatibilidad se denomina Índice de Compatibilidad (IC) y mide únicamente las 8 habilidades del AdS vs mínimos del Puesto.
- IC por turno = 100% cuando cumple/supera todo; menor proporcionalmente ante déficits. IC semanal = promedio de IC por turno.
- IC <100% no bloquea asignación.
- Compañía, estado laboral, vacaciones/permisos y descanso son reglas de elegibilidad, no factores de IC.
- No auto-relevo: prohibido planificar al mismo AdS en el turno consecutivo, incluso en otro Puesto/Punto.
- Se permite extensión parcial del turno saliente en el mismo Puesto por máximo 3 horas para cubrir tardanza/continuidad hasta relevo.
- `Reemplazo` se denomina `Reasignación`.
- La asistencia real se determina mediante Relevo ejecutado en SGI: Agente/Operador con foto facial y validación IA.
- Falto con Reasignación genera notificación a SIC: RRHH para descuento del ausente y pago de día extra al AdS que cubrió.
- Tardanza con cobertura parcial genera notificación a SIC: RRHH para descuento al tardío y bonificación al AdS que extendió turno.
- Vista inicial de Asignaciones: semanal.
- Publicar es suficiente; no requiere segunda aprobación.

## SGI-06 — IC, ID y TIER
- IC (Índice de Compatibilidad) y ID (Índice de Desempeño) son métricas distintas.
- IC mide solo habilidades del AdS vs habilidades mínimas del Puesto.
- Fórmula IC: `100 × Σ min(A_i,R_i) / Σ R_i`, sin compensación por sobrecumplimiento.
- IC semanal planificado = promedio de IC por turno publicado.
- IC semanal ejecutado = promedio de IC por turno realmente cubierto según Relevo/Reasignación.
- ID mide desempeño laboral/operacional mediante fórmulas propias.
- SIC: COM envía TIER contractual dentro de la Orden de Servicio.
- ID mínimo por TIER: I=6.5; II=7.5; III=8.5; IV=9.5.
- El requisito de ID por TIER no forma parte del IC.

## SGI-06 — ID por TIER y SMC
- El TIER es atributo del Puesto.
- Un Punto puede contener Puestos con TIER diferentes.
- ID mínimo por Puesto: TIER I=6.5, II=7.5, III=8.5, IV=9.5.
- ID inferior al mínimo NO bloquea la asignación; la UI muestra advertencia visual diferenciada.
- SMC es el System of Record del ID y demás KPIs de Cajamarca.
- Flujo de datos: SGI: Operador → SGI: Comando → SMC → SGI: Comando.
- SGI: Comando consume el ID calculado por SMC; no lo recalcula.

## SGI-06 — planificación semanal y ejecución
- La unidad principal de planificación es Compañía + semana.
- Los turnos requeridos se materializan desde la Orden de Servicio vigente de SIC: COM.
- Se conserva la estructura general histórica de UI: pool de AdS + filtros + matriz semanal + Drag&Drop + Copy + trazabilidad.
- Validación separada en elegibilidad, ID mínimo TIER e IC.
- Publicar convierte el plan en oficial sin segunda aprobación.
- Reasignación preserva la asignación original.
- Relevo es la fuente de verdad de asistencia real.
- Se conservan IC Planificado semanal e IC Ejecutado semanal.

## SGI-06 — cierre de planificación semanal
- Se permite publicar una semana con turnos sin asignar; SGI muestra advertencia crítica y contador visible.
- IC se muestra siempre con valor numérico exacto.
- El color del IC puede obedecer a rangos visuales configurables; umbrales exactos quedan pendientes.
- Si un turno es cubierto por más de un AdS, el IC Ejecutado del turno se pondera por minutos efectivamente cubiertos por cada AdS.
- La vista semanal puede filtrarse por un Punto específico o mostrar Todos los Puntos; la planificación sigue siendo por Compañía + semana.

## SGI-07 — confirmación y relevo integral
- Actualmente existen SGI: Agente y SGI: Supervisor; a futuro se unifican en SGI: Operador móvil.
- Confirmación previa de asistencia se realizará a futuro ~T-90 mediante IVR y Cajamarca Conmigo.
- Separar estado de confirmación de asistencia del estado del Relevo.
- Ciclo UI simplificado: Planificado → Por confirmar → Confirmado → Ejecutado.
- `Confirmado` significa intención declarada de asistir; `Ejecutado` significa Relevo realmente realizado.
- Tolerancia de tardanza configurable por Instancia–País; default 0 minutos.
- Relevo normal valida rostro + uniforme de saliente y entrante.
- Se permite Relevo unilateral validado por SGI: Operador cuando falta el saliente; queda como anomalía auditable.
- Relevo verifica Agentes, Puesto/Inventario y Consignas.
- Inventario Cajamarca del Puesto se consume desde SIC: RRMM.

## SGI-07 — cierre validaciones de Relevo
- Uniforme: incluir desde v0.1 `validation_method = MANUAL | AI`.
- Inventario crítico faltante no bloquea continuidad; permite Relevo con excepción autorizada + alerta crítica + auditoría.
- En cada Relevo el entrante confirma todas las Consignas vigentes.
- La UI prioriza Consignas nuevas/modificadas antes de las ya conocidas.
- En la práctica se esperan hasta ~10 Consignas por Puesto; no es tope técnico rígido.

## SGI-08 — Consignas (base)
- SGI: Comando es System of Record de Consignas operacionales.
- Conserva diseño de frecuencia tipo calendario.
- En Relevo, el entrante confirma todas las Consignas vigentes; nuevas/modificadas se priorizan.
- Consignas deben ser versionadas; una versión publicada es inmutable.
- Alcance inicial propuesto: Punto o Puesto.

## SGI-08 / Taxonomía de Tareas y Novedades
- Consignas: `Vigencia = Permanente|Temporal`; `Aplicación = Todo el tiempo|Calendario`. No existe `Recurrente` como tipo independiente.
- Prioridad de Consignas: Normal / Alta / Crítica.
- Toda Consigna normativa o ad hoc forma parte del REGESEP; cada cambio operativo genera nueva versión de REGESEP.
- Consigna nueva/modificada en turno activo se distribuye inmediatamente y requiere confirmación.
- Alcance de Consignas v0.1: Punto o Puesto; Punto se hereda a Puestos.
- Actores que pueden generar Consignas/Hallazgos/Vulnerabilidades/Incidentes/Requerimientos/Actividades: Cliente, Coordinador, Asistente, Agente, Supervisor; futuro SGI: Operador.
- Todo converge en SGI: Comando.
- Consignas, Hallazgos y Vulnerabilidades permanecen en SGI: Comando.
- Incidentes, Requerimientos y Actividades se enrutan a STC.
- Hallazgos incorpora subtipo `Componentes de Seguridad`.

## Novedades / aprobación de Consignas / STC
- `Novedades = Hallazgos + Vulnerabilidades + Incidentes`.
- Novedades es un agrupador funcional para información que amerita atención del cliente y se muestra en SGI: Cliente; no es entidad separada.
- Consignas, Requerimientos y Actividades no forman parte de Novedades.
- Cliente/Agente/Supervisor generan `Consigna Propuesta`; Coordinador o Asistente aprueban/publican.
- Solo una Consigna publicada entra al REGESEP y lo versiona.
- Para Incidentes/Requerimientos/Actividades, SGI conserva el registro operacional de origen y STC es SoR del workflow de resolución.
- STC devuelve estado/resultado/cierre a SGI.
- Hallazgo `Componentes de Seguridad` puede sugerir relación con Vulnerabilidad y permitir marcar Mitigada/Resuelta, pero nunca cerrar automáticamente.

## SGI-NOV-01 — base Hallazgos/Vulnerabilidades
- Novedades es vista/agrupador, no entidad.
- Hallazgos y Vulnerabilidades son entidades separadas en SGI: Comando.
- Hallazgo subtipos: Orden, Limpieza, Mantenimiento, Estacionamiento, Componentes de Seguridad.
- Vulnerabilidad subtipos: Acceso, Perímetro, Interno.
- Todo Hallazgo/Vulnerabilidad es visible en SGI: Cliente por definición de Novedad.
- Hallazgo `Componentes de Seguridad` puede vincular/sugerir mitigación o resolución de Vulnerabilidad, pero nunca cerrarla automáticamente.
- Se prepara referencia futura a ATS sin convertir SGI en SoR del diseño técnico.

## SGI-NOV-01 — lifecycle Hallazgos/Vulnerabilidades
- Hallazgos Orden/Limpieza/Mantenimiento/Estacionamiento: solo estado `Reportado`; visibilidad activa configurable 12h/24h/48h/1 semana, default 48h; históricos permanentes.
- Estos Hallazgos son responsabilidad del cliente y no generan tareas STC.
- Hallazgos Componentes de Seguridad: `Reportado → Confirmado | Descartado`.
- Si hay varios AdS en el Punto, confirma otro AdS; si hay uno solo, puede confirmar él mismo en un turno posterior.
- Componente confirmado queda pendiente de conciliación/incorporación a ATS/`.ats`.
- Vulnerabilidades: `Reportada / Confirmada(n) / Mitigada / Resuelta / Descartada`.
- Confirmaciones de Vulnerabilidad son acumulativas y visibles.
- Agente/Cliente/Coordinador/Asistente pueden Reportar y Confirmar.
- Solo Coordinador/Asistente pueden pasar a Mitigada/Resuelta/Descartada.

## SGI-NOV-01 — aprobación de cambios de Vulnerabilidad
- Agente/Supervisor/Cliente pueden proponer `Mitigada`, `Resuelta` o `Descartada`.
- La propuesta queda `Pendiente de aprobación` y no cambia aún el estado efectivo.
- Coordinador o Asistente aprueban/rechazan.
- Coordinador/Asistente pueden originar cambios conforme a permisos.
- Máximo una confirmación de Vulnerabilidad por Agente por turno.
- Hallazgos simples usan ventana global por Instancia–País: 12h/24h/48h/1 semana; default 48h.

## SGI-TASK-01 — base integración STC
- Incidentes, Requerimientos y Actividades convergen en SGI: Comando y se enrutan a STC.
- SGI conserva el registro operacional original; STC es SoR del workflow de resolución.
- Incidentes forman parte de Novedades; Requerimientos y Actividades no.
- Correlación recomendada: 1 origen SGI → 1 tarea/caso principal STC; subtareas pueden vivir solo en STC.
- SGI no duplica estados internos de workflow; mantiene snapshot de lectura devuelto por STC.
- Si STC está caído, SGI conserva el origen y reintenta de forma idempotente.

## SGI-TASK-01 — cierre visibilidad/prioridad
- Requerimientos del Cliente: sección `Mis Requerimientos`, separada de Novedades.
- Actividades: internas y no visibles al Cliente por defecto.
- Incidentes: prioridad Normal / Alta / Crítica.
- Incidentes cerrados permanecen visibles en SGI: Cliente hasta que el usuario los filtre/cambie de vista; no tienen ventana de expiración automática.
- Se incorporó como referencia el catálogo actual de Incidentes: 3 categorías, 20 subcategorías, 90 tipos.

## SGI-09 — Bitácora base
- Bitácora se configura por Puesto.
- Protocolos de Acceso se organizan bajo Acreditaciones aplicables a `PAX`, `VHL` o `CONT`.
- Cada Protocolo incluye: Identificación, Elementos sujetos a verificación, Autorización y Reutilización/Exportable.
- Campo obligatorio adicional: aplicación `Ingreso / Egreso / Ambos`.
- `Exportable` significa reutilizable/importable por otros Puestos dentro del alcance autorizado; no implica propagación automática.
- Importar crea snapshot/copia versionada, no vínculo vivo.
- Protocolos publicados deben versionarse y registros históricos conservan la versión exacta usada.

## SGI-09 — gobierno de Protocolos y lógica Y/O
- Protocolo exportable creado por Coordinador: visible/importable solo dentro de su propia Compañía.
- Protocolo Estándar Cajamarca: transversal a toda la operación del contexto; solo Director Zonal/Director Nacional pueden crearlo.
- Director de Operaciones Nacional tiene permiso exclusivo `FORCED_ROLLOUT` para aplicar una nueva versión estándar a todos los Puestos derivados.
- Forced rollout conserva versiones anteriores, auditoría y versiona REGESEP de cada Puesto afectado.
- Protocolos soportan lógica Y/O mediante árbol `ALL/ANY`, incluyendo anidación.
- Ejemplo congelado: `Rostro Y (Cédula O Licencia)`.
- Todo campo siempre admite captura manual; opcionalmente QR/Código de Barras pueden autocompletar campos configurados.
- Se registra provenance por dato: manual/QR/barcode.
- Protocolos activos forman parte del REGESEP y sus cambios lo versionan.

## SGI-09 — AMBOS, resultado y multiobjeto
- `AMBOS` = mismas reglas para Ingreso y Egreso; si difieren, crear Protocolos separados.
- Resultado: Autorizado / Rechazado / Excepción Autorizada.
- Excepción Autorizada exige motivo, autorizador, reglas incumplidas y auditoría.
- Protocolo puede incluir uno o más tipos de objeto: PAX, VHL, CONT y combinaciones.
- Cada tipo tiene cardinalidad configurable, p. ej. 1 VHL + 1..N PAX.
- Un movimiento de Bitácora puede contener múltiples objetos relacionados dentro de una sola transacción.

## SGI-09 — multiobjeto congelado
- Se permiten combinaciones PAX/VHL/CONT sin restricciones rígidas del modelo.
- En PAX+VHL, conductor y pasajeros son PAX individuales dentro del mismo movimiento.
- Se propone separar `AccessMovement` (evento) de `AccessPresenceSession` (permanencia Ingreso→Egreso).
- Bitácora conserva siempre Observaciones y atribución/timestamp del Agente.
- Se incorpora soporte base para Listas Blancas/Negras; efecto exacto pendiente.

## SGI-09 — sesiones, listas y regularización
- Sesiones abiertas permiten saber qué PAX/VHL/CONT siguen dentro.
- Egreso puede ser independiente por objeto.
- Lista Blanca solo satisface reglas si el Protocolo lo configura.
- Lista Negra admite Excepción Autorizada con trazabilidad.
- Alcance inicial de listas: Punto o Cliente.
- Si un nuevo Ingreso corresponde a un objeto que ya figura Dentro, SGI exige regularizar/cerrar primero la sesión anterior.
- Regularización conserva hora declarada de Egreso y hora real de corrección como timestamps separados.
- Nunca se sobrescribe el Ingreso anterior; se agrega un evento de Egreso Regularizado.

## SGI-09 — ejecución UX base
- SGI: Comando configura; SGI: Agente/futuro Operador ejecutan la versión vigente.
- UX scan-first con fallback manual.
- Flujo separado Ingreso/Egreso.
- Protocolo compatible se auto-selecciona si solo existe uno.
- Egreso reutiliza datos del Ingreso y cierra sesiones por objeto.
- Duplicidad de presencia obliga regularización antes de nuevo Ingreso.
- Reglas ALL/ANY se traducen a lenguaje operativo, no técnico.
- Vista `Actualmente Dentro` y `Historial` forman parte de la ejecución.

## SGI-09 — móvil, multi-Puesto y salida superficial
- SGI: Agente/futuro Operador no muestra historial general completo.
- SGI: Comando conserva historial autoritativo.
- Móvil conserva texto de últimos 7 días después de sincronizar; evidencias sincronizadas pueden purgarse.
- Registros no sincronizados conservan texto + fotos/evidencias hasta ACK de SGI: Comando.
- Bitácora soporta offline con Protocolos/caché/snapshots previamente sincronizados.
- La presencia se abre a nivel Punto y puede atravesar varios Puestos de Control de Acceso sin reingresar todos los datos.
- Puestos internos reutilizan identidad/datos y ejecutan solo reglas adicionales de su Protocolo.
- Umbral inicial de permanencia sin Egreso: 24h configurable.
- Al superar umbral no se inventa Egreso; la sesión pasa a `Por regularizar / Presencia no confirmada` y sale de la vista activa.
- Si aparece en Egreso: se registra salida oficial actual.
- Si aparece en Ingreso: se regulariza Egreso anterior retroactivamente y luego se registra nuevo Ingreso.
- Se permite crear Incidente/Hallazgo desde Bitácora con contexto precargado.

## SGI-09 — cierre funcional v0.1
- Caché móvil de 7 días restringida al Punto de servicio actual del Agente.
- No se permite consultar desde móvil registros recientes de otros Puntos previamente trabajados.
- Los Puestos internos reutilizan datos previos como solo lectura.
- Un Puesto posterior solo agrega nuevas verificaciones/evidencias de su propio Protocolo.
- SGI-09 Bitácora v0.1 cerrado funcionalmente a nivel conceptual.

## SGI-10 — Patrullas base
- Patrullas se modelan como Consignas especializadas.
- Se separan Ruta (`PatrolRoute`), Plan (`PatrolPlan`) y Ejecución (`PatrolExecution`).
- La Ruta pertenece al Punto; el Plan asigna responsabilidad a Puesto(s).
- Hitos versionados con métodos de validación/evidencia.
- Patrullas pueden generar Hallazgos/Vulnerabilidades/Incidentes con contexto precargado.
- Offline conserva ruta/reglas/evidencia hasta sincronización.
- Hechos de Patrulla se envían a SMC para KPIs/ID.

## SGI-10 — clasificación Programada/No Programada y Cerrada/Abierta
- Las Patrullas tienen dos ejes independientes:
  - temporal: Programada / No Programada;
  - estructural: Cerrada / Abierta.
- Programada: tiene rango/ventana horaria definida.
- No Programada: puede iniciarse en cualquier momento y ejecutarse n veces.
- Cerrada: Hitos predefinidos; Secuencia Estricta o Flexible.
- Abierta: sin Hitos predefinidos; el Agente crea y cierra Hitos durante la ejecución.
- Se permiten las cuatro combinaciones.
- Ruta pertenece al Punto; Plan asigna Puesto(s) responsables.
- Métodos de Hito aprobados: GPS/QR/Barcode/NFC/Fotografía/Manual con lógica Y/O.
- Calendario soporta intervalos, horas específicas y rangos cuando aplique.

## Códigos de Puesto y cierre de decisiones SGI-10
- Código de Puesto: inicial Provincia/Estado + inicial Ciudad + inicial Cliente + inicial Punto + secuencia.
- Ejemplo: Guayas/Guayaquil/Telconet/Telco-City → `GGTT01`.
- Si otro Punto genera la misma base, usar discriminador de Punto: `GGTT-2-01`, `GGTT-3-01`, etc.
- El código visible no es PK técnica y se mantiene estable una vez asignado.
- En Patrulla Cerrada pueden generarse Novedades tanto en Hitos como entre Hitos.
- Patrulla Abierta exige al menos 1 Hito creado y cerrado.
- Patrulla Programada se cumple dentro de su ventana.
- Patrulla No Programada puede iniciarse libremente por Agentes habilitados.

## SGI-10 — responsabilidad del Puesto, GPS cada 2 min y tardanza
- La obligación de Patrulla pertenece al Puesto.
- La ejecuta el Agente que esté efectivamente de servicio en ese Puesto.
- No se preasigna nominalmente cada Patrulla.
- Breadcrumb GPS opcional durante ejecución: una muestra cada 2 minutos.
- El breadcrumb complementa, no reemplaza, la validación de Hitos.
- Patrulla Programada que finaliza fuera de la ventana se clasifica `Ejecutada tardía`, aunque haya iniciado dentro de ella.

## SGI-10 — relevo, excepción y disponibilidad
- Una Patrulla en ejecución no se transfiere durante Relevo.
- El Agente saliente debe cerrarla; si queda incompleta, se registra `Incompleta` antes del Relevo.
- Patrulla Programada no puede iniciarse antes de abrir su ventana; antes se muestra `Próxima`.

## SGI-10 — cierre automático y métricas separadas
- Patrulla Programada no iniciada al cerrar su ventana → `No ejecutada` automática.
- Patrullas No Programadas se miden aparte y no compensan incumplimientos de Programadas.
- Patrulla interrumpida antes de completarse → `Incompleta`; no existe `Interrumpida por Excepción`.
- Puede conservarse motivo y vínculo a Incidente/evento causante.

## SGI-10 — simplificación final de Hitos
- Se elimina `Hito Exceptuado`.
- Todos los Hitos de una Patrulla Cerrada son obligatorios.
- Cada Hito solo puede resultar `Cumplido` o `No cumplido`.
- Uno o más Hitos no cumplidos → Patrulla `Incompleta`.
- No existen Hitos opcionales.
- No existe resultado `Ejecutada con Excepción`.
- No se define duración objetivo/estimada como regla o alerta en v0.1.

## SGI-10 — cierre funcional v0.1
- Secuencia Estricta: no se puede validar el Hito siguiente sin cumplir el anterior.
- Si un Hito bloquea la secuencia, la Patrulla puede cerrarse como `Incompleta`.
- Los Hitos admiten reintentos de validación.
- Los intentos fallidos quedan auditados.
- Un Hito solo termina `No cumplido` al cerrar la Patrulla sin haber logrado validarlo.
- SGI-10 Patrullas v0.1 queda funcionalmente cerrado.

## SGI-11 — REGESEP base
- REGESEP pertenece al Punto y es un reglamento estructurado, vivo y versionado.
- No se edita directamente; se compone desde Systems of Record/configuraciones efectivas.
- ATS será la fuente principal del capítulo técnico de seguridad.
- REGESEP referencia la versión `.ats` exacta usada.
- Consignas, Protocolos de Bitácora, Patrullas y configuraciones normativas efectivas versionan REGESEP.
- Borradores no generan versión.
- REGESEP histórico es inmutable.
- PDF es una representación, no el SoR.

## SGI-11 — decisiones base congeladas
- Un REGESEP por Punto.
- Nueva versión ATS efectiva → nueva versión REGESEP automática.
- Vulnerabilidad SGI no altera directamente capítulo ATS; pasa primero por ATS.
- SGI: Cliente ve vigente + históricos según permisos.
- SGI: Agente/Operador puede consultar vigente, pero trabaja principalmente con deltas operacionales.
## SGI-06 — decisiones reforzadas para implementación UAT v0.2
- SIC: RRHH entrega a SGI los Agentes, Escoltas y Supervisores pertenecientes a cada Compañía.
- SIC: RRHH determina vacaciones y permisos médicos con fecha/hora desde/hasta; SGI los trata como indisponibilidad read-only.
- La matriz usa exactamente los Turnos provenientes de SIC: COM por Puesto.
- Compatibilidad (rol/IC/habilidades) y desempeño (ID vs mínimo TIER) son ADVERTENCIAS, nunca bloqueos.
- `Cambio Requerido` sigue siendo advertencia, no bloqueo mientras el colaborador continúe activo.
- Publicar crea snapshot inmutable del plan; cualquier cambio posterior es Reasignación explícita y auditable.
## SGI-06 — Asignaciones v0.2 — Decision Log congelado (DEC-018 a DEC-025)
- **SGI-06-DEC-018**: personal con vacaciones, permiso médico u otra indisponibilidad permanece visible en el pool, claramente marcado; no se oculta.
- **SGI-06-DEC-019**: al publicar se conserva snapshot operacional de rol, ID, habilidades, indisponibilidades y fuentes/versiones utilizadas. RRHH/SMC conservan el SoR.
- **SGI-06-DEC-020**: semáforo Drag & Drop: verde = asignable; ámbar = asignable con warnings; rojo = bloqueo real.
- **SGI-06-DEC-021**: copia múltiple parcial: destinos válidos se crean aunque otro destino falle; se reporta cada fallo individualmente.
- **SGI-06-DEC-022**: cobertura = ocurrencias de Turno Requerido con asignación / ocurrencias totales × 100.
- **SGI-06-DEC-023**: una vacante cubierta después de publicar se presenta como **Asignación posterior a publicación**, no como Reasignación.
- **SGI-06-DEC-024**: se conservan dos métricas: **Cobertura Publicada** (snapshot) y **Cobertura Actual** (estado vigente).
- **SGI-06-DEC-025**: el Plan Semanal pasa automáticamente a `CERRADO` al finalizar la semana operativa; queda inmutable para planificación.


## SGI-00T — Territorio v0.1 — Decision Log
- **SGI-00T-DEC-001**: la jerarquía operacional es `Instancia–País → Zona → Región → Compañía → Punto → Puesto`.
- **SGI-00T-DEC-002**: `Territorio` es el primer ítem de Operaciones, antes de Compañías.
- **SGI-00T-DEC-003**: toda Compañía debe pertenecer a una Región y toda Región a una Zona.
- **SGI-00T-DEC-004**: autorización = rol funcional + alcance territorial. Presidente/Director LATAM/Director Nacional ven País; Director Zonal ve Zona; Jefe Regional ve Región; Coordinador/Asistente ven Compañía.

## SGI-06 — Asignaciones v0.3 — Decision Log
- **SGI-06-DEC-026**: click sobre una persona evalúa los Turnos de la semana y resalta verde = posible, ámbar = posible con alertas, rojo = bloqueo.
- **SGI-06-DEC-027**: el UAT utiliza retratos ficticios consistentes para dar sensación de operación viva; en producción la foto podrá provenir de SIC: RRHH y tendrá fallback.
- **SGI-06-DEC-028**: un Puesto soporta exactamente los N Turnos definidos por SIC: COM. El UAT incluye un caso de tres Turnos de 8 horas: 06:00–14:00, 14:00–22:00 y 22:00–06:00.
- **SGI-06-DEC-029**: las tarjetas de asignación priorizan legibilidad del nombre completo; se permiten dos líneas y se reduce contenido secundario en la celda.
- **SGI-06-BACKLOG-001**: copiar un ciclo/semana/patrón de asignación hacia semanas futuras, con revalidación de reglas en cada destino. Se implementará en la vertical inmediatamente posterior a v0.3.

## SGI-00T — Territorio v0.2 / decisiones UAT v0.4
- `Territorio` es el primer ítem de Operaciones y configura `País → Zona → Región → Compañía`.
- Toda Compañía pertenece obligatoriamente a una Región; Zona se deriva por transitividad.
- Zonas y Regiones se crean en Borrador y pueden activarse. Una Zona/Región activa no se elimina físicamente; se conserva para auditoría.
- En objetos activos se mantiene editable al menos Responsable y cobertura de Provincias/Estados.
- Las Provincias/Estados se asignan primero a Zona. Una Región solo puede recibir Provincias/Estados que ya pertenezcan a su Zona.
- Los Responsables de Zona/Región se seleccionan desde personal activo proveniente de SIC: RRHH; SGI no crea personas.
- La configuración territorial genera historial auditable de altas/cambios/bajas permitidas.
- Cuando existe territorio activo, Territorio incluye mapa operacional de referencia del país.
- Alcance: Presidente / Director LATAM / Director Nacional = país; Director Zonal = Zona; Jefe Regional = Región; Coordinador/Asistente = Compañía.

## SGI-06 — Asignaciones v0.4 / decisiones UAT
- En Borrador, Coordinador/Asistente pueden trabajar y guardar sin publicar.
- Existe botón explícito `Guardar borrador` y checkpoint automático cada 15 minutos.
- Solo Coordinador de Compañía y Asistente de Operaciones pueden modificar, guardar y publicar; superiores consultan en solo lectura según alcance territorial.
- Click sobre persona evalúa toda la matriz visible: Verde asignable; Ámbar asignable con alertas; Rojo bloqueo real.
- Eliminación de una asignación en Borrador es directa, sin modal de confirmación.
- La matriz incorpora selección múltiple tipo hoja de cálculo para Copiar / Pegar / Eliminar conjuntos de celdas.
- Una selección copiada puede pegarse en una semana futura; cada destino vuelve a ejecutar reglas de elegibilidad y warnings.
- Vacaciones y Permiso Médico permanecen visibles con iconografía específica y fechas/días.
- `ID` significa Índice de Desempeño; no usar `IP`.
- El Puesto muestra TIER, horas/semana requeridas, IC promedio e ID promedio de las asignaciones de la semana.
- En resumen de Puesto, ID se normaliza visualmente a porcentaje (`ID 9.2 = 92%`).
- Semáforo IC/ID de resumen: >=90 verde; 80–<90 amarillo; 70–<80 naranja; <70 rojo.
- Cada tarjeta persona-turno muestra explícitamente `IC`.
- Click en IC abre detalle de las 8 habilidades actuales vs requeridas del Puesto.
- Cumplimiento general del detalle = habilidades que cumplen o superan el mínimo / 8 × 100.
- Click en Agente abre ficha operacional ampliada; click en Puesto abre ficha del Puesto con Consignas, requisitos y `Últimas novedades`.
- Si un Puesto tiene 1 o 2 turnos, se rotulan Diurno/Nocturno por mayoría de minutos en 06:00–18:00 vs 18:00–06:00.
- Si tiene más de 2 turnos, se rotulan por mayoría de minutos: Mañana 05:00–12:00; Tarde 12:00–17:00; Noche 17:00–00:00; Madrugada 00:00–05:00. Empates exactos conservan temporalmente el nombre recibido de SIC: COM hasta definir desempate.

## SGI-06 — Asignaciones v0.5 / decisiones UAT
- **SGI-06-DEC-036**: un día calendario corresponde a una sola columna del plan semanal; la grilla no puede tratar un turno que cruza medianoche como si perteneciera íntegramente al día de inicio.
- **SGI-06-DEC-037**: un Turno lógico que cruza medianoche se representa en dos segmentos visuales contiguos: inicio hasta 24:00 y continuación desde 00:00. Ambos segmentos comparten el mismo `shift_occurrence_id` y toda acción sobre cualquiera de ellos (asignar, eliminar, seleccionar, copiar/pegar) opera sobre el Turno lógico completo.
- **SGI-06-DEC-038**: click en una persona del pool izquierdo realiza simultáneamente evaluación de elegibilidad de la matriz y abre la misma ficha operacional disponible desde una asignación del calendario.
- **SGI-06-DEC-039**: iconografía de Vacaciones y Permiso Médico usa los recursos visuales aprobados por UAT; no se sustituyen por iconos genéricos.
- **SGI-06-DEC-040**: Asignaciones debe ser responsive sin superposición entre IC, foto, nombre, ID y alertas. En pantallas menores la matriz conserva semántica de calendario mediante scroll horizontal interno, no mediante compresión destructiva de tarjetas.
- **SGI-06-DEC-041**: los permisos de edición no cambian: solo Coordinador de Compañía y Asistente de Operaciones pueden drag/drop, editar, guardar y publicar. Presidente/Directores/Jefe Regional mantienen solo lectura según alcance.

## SGI-00T — Territorio v0.3 / decisiones UAT
- **SGI-00T-DEC-005**: el catálogo de Provincias/Estados es propiedad de CORE. SGI: Comando solo consume el catálogo y administra su asignación a Zonas/Regiones. En UAT se usa adapter/snapshot `CORE LOCAL`.
- **SGI-00T-DEC-006**: el mapa mostrado en Territorio debe ser un mapa operacional del país con Zonas/Regiones, nunca material de grados/cargos organizacionales.
- **SGI-00T-DEC-007**: acciones de crear/guardar/editar/eliminar deben emitir retroalimentación visible de éxito/error y desaparecer automáticamente aproximadamente a los 5 segundos.
- **SGI-00T-DEC-008**: la UI de Territorio debe ser legible a zoom 100%; se aumenta jerarquía tipográfica y separación de controles sin perder densidad operacional.

## TER — Territorio v0.1 / metodología por vertical
- **TER-DEC-001**: `TER` es la sigla oficial de la vertical Territorio para trabajo/UAT independiente.
- **TER-DEC-002**: CORE es SoR del catálogo político-administrativo y entrega además `subdivisionType`, `subdivisionSingular` y `subdivisionPlural`; SGI no usa el literal genérico `Provincia/Estado`.
- **TER-DEC-003**: en Ecuador la UI debe mostrar `Provincia` / `Provincias` porque así lo informa CORE.
- **TER-DEC-004**: TER muestra Compañías por Región solo como contexto; editar la Región de una Compañía pertenece a COM.
- **TER-DEC-005**: selector UAT de esta fase: Presidente, Director Nacional, Director Zonal, Jefe Regional, Coordinador y Asistente de Coordinación.
- **TER-DEC-006**: TER se congela independientemente del resto de SGI: Comando; otras verticales no se modifican durante su cierre salvo componentes compartidos estrictamente necesarios.

## SER v0.5 — Decisiones superseding (2026-09-10)
- El listado maestro de Servicios es Cliente · Punto, no Puesto.
- Configuración y Operación son accesos separados.
- La extensión canónica ATS es `.ats` y SGI ya implementa su importación como consumidor.
- La ubicación de Puestos se selecciona sobre el plano real importado y se persiste en coordenadas normalizadas vinculadas a la revisión ATS vigente.
- Habilidades requeridas del Puesto usan mínimo 1, máximo una en 5, máximo dos en 4, suma máxima 22.
- Ver `docs/verticals/SER/SER_DECISIONS.md` como decisión canónica vigente.
