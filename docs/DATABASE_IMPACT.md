# SGI: Comando — impacto de esquema y migraciones

**Fuente:** migraciones `backend/src/main/resources/db/migration/V27` a `V33` y Markdown bajo `cambios/`.  
**Revisión:** documental; no se ejecutó Flyway ni se inspeccionó una base viva.  
**Importante:** `quarkus.flyway.migrate-at-start=true` está activo en `backend/src/main/resources/application.properties`.

| Migración | Vertical/ámbito | Tablas/columnas y efecto | Estado documental / control |
|---|---|---|---|
| V27 `territory_draft_assignments` | TER | Agrega `country_subdivision.draft_zone_id` y `draft_region_id`, índices y backfill de asignaciones que apuntaban a entidades no activas; limpia de las columnas efectivas las referencias inactivas. | Código fuente presente. `cambios/CHANGELOG_DM.md` lo declara TER v1.0.1 UAT candidate; faltan UAT/CR consolidados y armonizar la reapertura. |
| V28 `company_responsible` | COM | Agrega `company.responsible_employee_id UUID NULL`. | El changelog DME describe API/UI y V28, y la migración la etiqueta COM v1.1.4; los documentos congelados todavía dicen COM v1.1.3. CR/reapertura y release formal pendientes de localizar. |
| V29 `employee_persona_identity_bridge` | SIC:RRHH/DHO y ASI (identidad de personal) | Agrega `employee_operational_snapshot.persona_id bigint` e índice único parcial por `(instance_country_id, persona_id)` cuando no es NULL. No backfill histórico. | Changelog DME informa SGI implementado y despliegue DHO pendiente. Requiere CR/contrato end-to-end y actualizar integración/UAT ASI. |
| V30 `eliminar_todos_los_empleados` | **QUARANTINED** | Tombstone Flyway sin DML. El SQL destructivo original fue movido a `database/uat-fixtures/` y requiere ejecución explícita. | P0 corregido 2026-09-27; ver `MIGRATION_SAFETY_V30_V31.md`. |
| V31 `insertar_200_empleados_con_avatares` | **QUARANTINED** | Tombstone Flyway sin DML. La carga de 200 empleados fue movida a fixture UAT explícito. | P0 corregido 2026-09-27; no se ejecuta al arrancar. |
| V32 `operator_relief_uat` | SGI: Operador / captura UAT de relevos | Crea `operator_employee_binding`, `operator_relief_submission`, `operator_relief_evidence`; almacena payload/contexto JSON y evidencia binaria hasta 5 MiB. | UAT-only indicado en comentarios; definir retención/almacenamiento productivo y completar contrato con SGI_OPR antes de producción. |
| V33 `sic_com_commercial_catalog_inbound` | SIC:COM ↔ SGI_COM; COM/SER | Crea `client` con unicidad por instancia y código; crea recibos `sic_com_commercial_event_receipt` únicos por instancia y `event_id`; agrega `service.client_id`, crea clientes LEGACY desde `service.client_name`, rellena FK y la hace NOT NULL; agrega `post.commercial_status` e índices. | Código fuente presente. La implementación corresponde en lo esencial al modelo propuesto en SIC:COM V3.1. `cambios/CHANGELOG_JTO_IMPLEMENTACION_CATALOGO_SIC_COM.md` cita V27 y debe reconciliarse a V33. Validar CR/contraparte y contrato SIC:COM antes de operación real. |
| V34 `rrhh_employee_event_idempotency` | SIC:RRHH → SGI_COM | Crea `sic_rrhh_employee_event_receipt` con unicidad `(instance_country_id, idempotency_key)` para retries auditables y conflicto ante reutilización del key con payload distinto. No siembra ni reescribe empleados. | P1 2026-09-27; complementa `SIC_RRHH_SGI_COM_0001_v001`. |

## Riesgos de datos y compatibilidad

- V30/V31 permanecen como números de historia Flyway pero sus archivos automáticos son tombstones sin DML. Los SQL UAT originales están fuera del path automático.
- La destrucción/carga UAT solo puede ejecutarse explícitamente mediante los fixtures y `scripts/uat-seed-employees.ps1 -Force`. Bases que ya aplicaron los V30/V31 antiguos requieren reconciliación controlada de checksum.
- V33 transforma el cliente legacy de texto a referencia `client_id` obligatoria. La compatibilidad depende del backfill y de que cada servicio conserve su cliente antes del `NOT NULL`.
- V33 no crea las tablas `post_shift_template` ni `post_planning_cycle_snapshot`; el receptor las reutiliza para guardar plantillas/ciclos comerciales versionados. Sus efectos de actualización e inactivación también deben quedar en el contrato/UAT, en particular la política para turnos futuros ya generados (`shift_occurrence`).
- Las especificaciones SIC:COM V2.0–V3.1 proponen tabla/relación e idempotencia de forma evolutiva. V3.1 es la más reciente localizada, pero todavía se declara pendiente de CR, contrato bilateral, CORE y UAT; no debe tratarse como aprobación formal del esquema desplegable.
- El changelog V28 menciona pruebas en una base temporal y limpieza de datos temporales; no se toma como aprobación formal ni como evidencia de despliegue productivo.
- No alterar migraciones Flyway aplicadas. Cambios posteriores deben ser nuevas migraciones y preservar historia/checksum.

## Clasificación de tablas por vertical

- **TER:** `country_subdivision` (borrador/efectivo de asignación territorial).
- **COM / SER, catálogo SIC:COM:** `client`, `service`, `point`, `post`, `sic_com_commercial_event_receipt`; el procesamiento también escribe en `post_shift_template` y `post_planning_cycle_snapshot`. `shift_occurrence` representa turnos ya generados y su preservación/regeneración queda por decidir en CR.
- **ASI/RRHH:** `employee_operational_snapshot`, `sic_rrhh_employee_event_receipt` y tablas vinculadas de membresía, habilidades, indisponibilidad y transferencias.
- **SGI: Operador:** `operator_employee_binding`, `operator_relief_submission`, `operator_relief_evidence`.
- **Transversal de datos UAT:** V30/V31 afectan además referencias territoriales, responsables e historial operacional; no deben agruparse como un cambio de una sola vertical.
