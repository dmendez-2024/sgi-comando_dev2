# SGI: Comando — Matriz de verticales

| Vertical | Versión | Estado |
|---|---:|---|
| TER — Territorio | 1.0 | FROZEN |
| COM — Compañías | 1.1.3 | FROZEN |
| ASI — Asignaciones | 0.7.4 | UAT_CANDIDATE |
| SER — Servicios | 0.10.8 | UAT_CANDIDATE |

**Fecha:** 2026-09-19


## Actualización SER
- SER v0.6.1 — UAT — Bitácora con Acreditaciones explícitas.

- SER v0.7 — UAT — Patrullas + configuración mixta ATS/Campo + versionado inmutable transversal.

- SER v0.7.1 — UAT FIX — Corrige colisión Flyway V14 con tablas baseline de Patrullas y agrega espera de backend READY.

- SER v0.7.2 — UAT FIX — consolida patrullas en tablas canónicas y mejora UI de Patrullas.

- SER v0.7.3.1 — UAT FIX — corrige CRUD sobre tablas canónicas baseline de Patrullas y rehace jerarquía visual/legibilidad de la pantalla.

- SER v0.8 — UAT — Configuración de Consignas con Protocolos independientes, alcance multi-Puesto, reglas, GPS, Evidencias y Foto estándar.

- SER v0.8.1 — UAT FIX — habilita navegación a Consignas desde la landing de Configuración.

## Apertura COM / ASI — 2026-09-19
- COM v1.1 — UAT — identidad de Compañías desde catálogo CORE + Kaibil siempre Activa.
- ASI v0.7 — UAT — transferencias de personal entre Compañías con aceptación del destino e integración SIC: RRHH.

- COM v1.1.1 / ASI v0.7.1 — UAT FIX — V18 ahora hace merge de Compañías por `instance_country_id + code`, preservando UUID existentes y evitando colisión con compañías creadas en UAT previas.

- COM v1.1.2 / ASI v0.7.1 — UAT FIX — V18 reconcilia compañías legacy por CORE id → nombre → código y evita colisiones simultáneas de las restricciones únicas de código y nombre.

- COM v1.1.2 / ASI v0.7.2 — UAT FIX — Corrige aceptación de transferencias: cierra y fuerza persistencia del membership PRIMARY anterior antes de crear el nuevo; reemplaza `window.confirm` por modal SGI con manejo de error visible.

- COM v1.1.3 / ASI v0.7.3 — UAT FIX — Kaibil por defecto para liderazgo, selector de Compañía persistente y confirmaciones de transferencia con modal SGI.

- COM v1.1.3 — **FROZEN** — catálogo CORE, Kaibil y reglas de Compañías cerradas para esta baseline.
- SER v0.9 — UAT — ingreso de Servicios desde SIC: COM a bandeja lógica Kaibil y asignación inicial a Compañía operativa según ámbito territorial.

- SER v0.9.1 — UAT — retiro de Servicios a Kaibil, preservación de configuración, liberación de asignaciones futuras y reasignación directa a nueva Compañía.

- ASI v0.7.4 — UAT — guardia de transición de Servicio para impedir doble cobertura durante reasignación entre Compañías.

## 2026-09-19 — SER v0.10.2
- Base: SER v0.10.1.
- Hotfix: generación de código de Acreditación robusta ante eliminaciones intermedias.
- COM v1.1.3 permanece congelado.
- ASI v0.7.4 se preserva sin cambios.

## 2026-09-19 — SER v0.10.4
- Bitácora: activación operativa por Puesto desde Definición.
- Contenido publicado permanece inmutable; el scope operativo por Puesto puede cambiar sin nueva versión.
- Eliminado botón global Inactivar.
- Confirmaciones Bitácora mediante modal SGI; no usar `window.confirm` en nuevas pantallas.
- Flyway V23 normaliza scopes heredados de protocolos INACTIVOS.


## 2026-09-19 — SER v0.10.6
- Homologación UX/UI de Patrullas al estándar visual de Bitácora.
- Protocolos del Punto con columnas: Código / Protocolo / Patrullas / Estado.
- Steps numerados con círculo: Definición, Patrulla, Hitos, Reglas por hito, Evidencias, Trazabilidad.
- Límite backend: máximo 15 Patrullas por Protocolo.


## 2026-09-19 — SER v0.10.7
- Hotfix de build de SER v0.10.6: corrige JSX de PatrolConfig y diagnóstico de salida de Docker Compose.
- Alcance funcional de Patrullas v0.10.6 preservado.
- Sin migraciones Flyway nuevas.


## 2026-09-19 — SER v0.10.8
- Patrullas homologadas estrictamente al estándar UX/UI de Bitácora.
- Activación de Protocolos de Patrullas por Puesto mediante `patrol_protocol_post_scope`.
- Máximo 15 Patrullas por Protocolo.
- Nueva migración V24; V22/V23 preservadas.


## 2026-09-19 — COO v0.1
- Nueva vertical Coordinación entre Servicios y Asignaciones.
- SER v0.10.10 queda **FROZEN** y no se modifica funcionalmente.
- Puestos internos MON/SUP por Compañía, formatos/rotaciones cerrados y horario de relevo.
- Rutas de Supervisión versionadas con secuencia ordenada de Puntos y preview por turno.
- Flyway V25 agrega únicamente entidades COO.

## 2026-09-19 — BIT v0.1
- Nueva vertical Bitácora global de consulta.
- Búsqueda de Personas, Vehículos y Contenedores.
- Triage por rol y exportación CSV.
- Solo consulta; sin cambios de esquema.

## 2026-09-19 — CNS v0.1
- Nueva vertical Operaciones > Consignas.
- Consulta de ejecución de Relevos, Patrullas y Consignas ad-hoc.
- Triage por rol, KPIs, filtros, progreso, detalle rápido y timeline.
- BIT v0.1, COO v0.1, ASI v0.7.4 y SER v0.10.10 permanecen FROZEN.
- Sin cambios de esquema en CNS v0.1.

## 2026-09-20 — NOV v0.1
- Nueva vertical `Operaciones > Novedades`.
- Tipos: Hallazgo, Vulnerabilidad, Incidente.
- Origen Fase I: `SGI_OPR`.
- Moderación / aprobación: `SGI_COM`.
- Solo novedades `Aprobadas` quedan habilitadas para `SGI_CLI`.
- Estados: Pendiente / Aprobada / Descartada.
- Edición permitida mientras la novedad está Pendiente.
- Descarte exige comentario; aprobación permite comentario opcional.
- Filtros y triage homologados a CNS.
- Sin migraciones nuevas en UAT v0.1.
## 2026-09-20 — VISINT / Impulsos v0.1 (documentation/SITC)
- Flujo aprobado: `SGI_OPR → SGI_COM → VISINT → SGI_COM → SGI_OPR`.
- CORE es SoR de reglas versionadas de Impulsos; cada SGI: Comando PE aplica esas reglas y es SoR del ledger/saldo de Impulsos.
- VISINT valida evidencia visual y no calcula premios.
- `PASS` habilita evaluación probabilística; no garantiza recompensa.
- Idempotencia obligatoria para impedir sorteos/premios duplicados ante retries.
- Se incorporan mockups de SGI: Operador en `docs/assets/impulsos_visint/`.
- No se modifica UI ni se reabre ninguna vertical congelada.


## 2026-09-20 — CIERRE SGI: COMANDO

Estado: **CLOSED / FROZEN**.

Baselines: TER v1.0; COM v1.1.3; SER v0.10.10; ASI v0.7.4; COO v0.1; BIT v0.1; CNS v0.1.2; NOV v0.1; CSL v0.1.

Hardening técnico: PERF v0.1, sin cambios de UI. Documentación final CORE: `CORE_REQUIREMENTS_SGI_COMANDO.md` + `SGI_Comando_Requerimientos_CORE.pdf`. SITC acumulativo final: `SGI_Comando_FINAL_2026-09-20.sitcpack`.


## 2026-09-27 — Reapertura acotada CSL
- `CSL v0.1` permanece como baseline FROZEN histórica.
- `CSL v0.1.1` = **UAT_CANDIDATE**, cambio UX únicamente: filtros de búsqueda colapsables, cerrados por defecto.
- Resto de verticales: sin cambio de versión/estado por esta entrega.
- SITC/interconexiones: sin cambios.

## 2026-09-27 — CSL v0.2 — Notificación de Incidentes
- `CSL v0.2` = **UAT_CANDIDATE** sobre CSL v0.1.1.
- Botón `Notificar Incidente` + panel lateral editable.
- Categorías Servicio / Seguridad / Administrativo; subcategorías de la referencia `Incidentes.xlsx`.
- Criticidad Informativo / Menor / Moderado / Mayor / Crítico.
- Cliente/Punto obligatorios, Puesto opcional, colaboradores de últimas 2 semanas sin duplicidad.
- Descripción/Resolución con máximo 5 imágenes por sección; Sanción Sí/No.
- Estados Borrador / Finalizado reeditables desde Casos operativos.
- Inasistencia Programada/Efectiva incluye flujo DEMO de reasignación con prelación mismo Puesto → mismo Punto → misma Compañía por cercanía.
- Backend/BD/SITC: sin cambios en esta UAT.
- EVC: fuera de alcance.


### CSL v0.2.1 — UAT
- Baseline: CSL v0.2.
- Taxonomía exacta del Excel 2026-09-27: 3 categorías / 20 subcategorías / 90 incidentes.
- Sin cambios de backend/SITC.

### CSL v0.2.5 — UAT
- Baseline: CSL v0.2.4.
- Incidente: asset limpio, sin texto residual.
- Reasignación: teléfono + Francos Trabajados (últ. 6 meses) por candidato.
- Sin cambios de BD/SITC.

### NEX v0.1 — UAT
- Estado: UAT_CANDIDATE.
- Baseline: CSL v0.2.5.
- Alcance: Nexus / Reglas + Historial dentro de Configuración del Punto.
- Persistencia: DEMO localStorage por Punto.
- Backend/DB/SITC: sin cambios.


## 2026-09-27 — CIERRE / ENTREGA SISTEMAS
- Estado global de baseline: **CLOSED / FROZEN**.
- Entrega prevista: 2026-09-28.
- TER v1.0 — FROZEN.
- COM v1.1.3 — FROZEN.
- SER v0.10.10 — FROZEN.
- ASI v0.7.4 — FROZEN.
- COO v0.1 — FROZEN.
- BIT v0.1 — FROZEN.
- CNS v0.1.2 — FROZEN.
- NOV v0.1 — FROZEN.
- CSL v0.2.5 — FROZEN.
- NEX v0.1 — FROZEN.
- NEX v0.1 conserva runtime/persistencia productivos como pendiente explícito; CSL v0.2.5 conserva componentes DEMO/local documentados.
- Regla: cualquier cambio posterior exige nueva versión.
