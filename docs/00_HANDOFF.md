# Handoff final — SGI: Comando — ENTREGA SISTEMAS 2026-09-28

> **RC derivada 2026-10-01 — OPR-CONSIGNMENT-COMPLIANCE-001 RC1:** implementado el receptor idempotente de cumplimiento de Consignas y persistencia auditable `V38`. Reutiliza la interfaz SGI_COM–SGI_OPR existente. Validación en `docs/VALIDATION_OPR_CONSIGNMENT_COMPLIANCE_2026-10-01.md`.

> **RC derivada 2026-10-01 — OPR-CONSIGNMENT-CONTEXT-001 RC1:** el runtime de Operador publica el Protocolo de Consignas vigente y sus reglas parametrizadas. Ampliación aditiva sin migración BD ni nueva interconexión SITC. Validación en `docs/VALIDATION_OPR_CONSIGNMENT_PROTOCOL_CONTEXT_2026-10-01.md`.

> **RC derivada 2026-10-01 — OPR-PATROL-LIFECYCLE-001 RC1:** SGI_COM controla el inicio y fin real de la Patrulla. `START`/`FINISH` son idempotentes sobre la ejecución canónica y `GET /api/v1/operator/patrol-executions/current` permite recuperar el estado. `V37` agrega trazabilidad por asignación; validación en `docs/VALIDATION_OPR_PATROL_LIFECYCLE_2026-10-01.md`.

> **RC derivada 2026-10-01 — OPR-PATROL-HISTORY-001 RC1:** frontend `0.11.2`. La pestaña Patrullas consume ejecuciones reales mediante `GET /api/v1/operator/patrol-executions`, con autorización por tenant/identidad/alcance. Sin migración BD ni nueva interconexión SITC. Validación en `docs/VALIDATION_OPR_PATROL_HISTORY_2026-10-01.md`.

**Freeze:** 2026-09-27.  
**Estado:** **CLOSED / FROZEN**.  
**Baseline final:** CSL v0.2.5 FROZEN + NEX v0.1 FROZEN, acumulativa sobre P0/P1 y verticales previamente congeladas.

Documento canónico de entrega: `docs/SYSTEMS_HANDOFF_2026-09-28.md`.

Toda modificación posterior debe crear una nueva versión; no modificar esta baseline.

---

# Handoff — SGI: Comando CSL v0.2 UAT

**Fecha:** 2026-09-27  
**Baseline inmediata:** `SGI_Comando_CSL_v0.1.1_UAT.zip`.  
**Cambio:** `Operaciones > Consola` incorpora Notificación de Incidentes según `Pagina Consola.docx`.  
**Arquitectura/SITC:** sin cambios.  
**Backend/BD:** sin cambios en esta UAT; lógica de formulario/ranking se valida con dataset DEMO/local.  
**EVC:** fuera de alcance.

## Lectura de esta versión
1. `README_CSL_v0.2.md`
2. `docs/CSL_INCIDENT_NOTIFICATION_v0.2.md`
3. `docs/CHANGELOG_CSL_v0.2_2026-09-27.md`
4. `docs/VALIDATION_CSL_v0.2_2026-09-27.md`
5. El handoff CSL v0.1.1 / P0P1 preservado a continuación.

---

# Handoff — SGI: Comando CSL v0.1.1 UAT

**Fecha:** 2026-09-27  
**Baseline inmediata:** `SGI_Comando_P0P1_RC_2026-09-27.zip`.  
**Cambio:** únicamente `Operaciones > Consola`: filtros dentro de accordion/collapsible cerrado por defecto.  
**Arquitectura/SITC:** sin cambios.  
**Backend/BD:** sin cambios.  
**EVC:** fuera de alcance.

## Lectura de esta versión
1. `README_CSL_v0.1.1.md`
2. `docs/CHANGELOG_CSL_v0.1.1_2026-09-27.md`
3. El handoff P0/P1 preservado a continuación.

---

# Handoff — SGI: Comando P0/P1 RC

**Fecha:** 2026-09-27  
**Baseline de código recibida de SISTEMAS:** `sgi-comando_dev.zip`, commit `8c528e8` (2026-09-25 19:54 -05:00).  
**Norma:** SITC-NOM-001 v4.1.  
**Estado:** RC técnico/arquitectónico para validación UAT; no implica aprobación de producción.  
**UI:** sin cambios intencionales.  
**EVC/Eventos de Cumplimiento:** expresamente fuera de alcance de esta RC.

## Orden de lectura

1. `README.md`
2. `docs/CHANGELOG_P0P1_2026-09-27.md`
3. `docs/MIGRATION_SAFETY_V30_V31.md`
4. `docs/MASTER.md`
5. `docs/ARCHITECTURE.md`
6. `docs/DECISIONS.md`
7. `docs/INTERCONNECTIONS.md`
8. `docs/API_CATALOG.md`
9. `docs/SITCPACK.md`
10. `docs/SECURITY.md`
11. `docs/DATABASE_IMPACT.md`
12. `docs/KNOWN_LIMITATIONS.md`
13. `docs/NEXT_ACTIONS.md`
14. `docs/VALIDATION_P0P1_2026-09-27.md`
15. `docs/CHANGE_REQUEST_CORE.md`
16. `docs/CHANGE_REQUEST_SIC_RRHH.md`
17. `docs/CHANGE_REQUEST_SIC_COM.md`

## Cambios P0

- V30/V31 dejaron de ejecutar destrucción/carga UAT automáticamente.
- Los SQL originales se preservan fuera de Flyway y solo se ejecutan mediante `scripts/uat-seed-employees.ps1 -Force`.
- Bases que ya ejecutaron los antiguos V30/V31 requieren reconciliación controlada de checksum; ver documento específico.

## Cambios P1

- `CURRENT` pasa a SITC v4.1.
- `CORE.scope = UNIVERSAL`; los Program ID PE-específicos usan `PE_SPECIFIC`.
- IDs canónicos de interconexión adoptan `ORIGEN_DESTINO_NNNN_vNNN`.
- IDs v3 previos se conservan únicamente como aliases de transición.
- Se agrega `SGI_COM_CORE_0001_v001` para consumir reglas versionadas de Impulsos desde CORE.
- CORE es SoR de reglas de Impulsos; SGI_COM PE es SoR del cálculo aplicado y del ledger/saldo.
- SIC:RRHH inbound usa `credential_ref`, receipt idempotente persistente (V34) y la Compañía fuente entregada por RRHH; altas nuevas sin compañía se rechazan en vez de asignarse a Kaibil.
- secretos y `.env` se excluyen del repositorio/ZIP portable.
- dependencias frontend top-level fijadas a versiones exactas.

## Arranque UAT

1. Copiar `.env.example` a `.env`.
2. Reemplazar todos los `CHANGE_ME`.
3. Revisar `flyway_schema_history` si la base ya pudo haber ejecutado V30/V31 históricos.
4. Ejecutar:

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-start.ps1
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-open.ps1
```

## Regla de continuidad

No usar el ZIP previo de ChatGPT como base funcional. Esta RC conserva los avances de SISTEMAS del 25-09 y aplica únicamente las correcciones P0/P1 descritas.

## 2026-09-27 — CSL v0.2.5 UAT_CANDIDATE

- Baseline acumulativa: CSL v0.2.4.
- Ícono de Incidentes limpio: se elimina texto residual incrustado en el asset.
- Cobertura/Reasignación muestra teléfono y Francos Trabajados (últ. 6 meses) por candidato.
- Datos de teléfono/francos: DEMO/local en UAT.
- Sin cambios de BD/Flyway/SITC/interconexiones.

## 2026-09-27 — NEX v0.1 UAT_CANDIDATE
- Baseline: CSL v0.2.5.
- Se incorpora Nexus en Configuración del Punto, entre Consignas y Recursos Humanos.
- UI limitada a Reglas + Historial.
- Modelo de regla: Evento objetivo (Objeto + Evento/Estado) + ANTES/DURANTE/DESPUÉS + Evento de referencia (Objeto + Evento/Estado) + ventana/correlación + acciones opcionales.
- UAT frontend/localStorage únicamente; runtime EVC, persistencia productiva, backend, DB y contratos quedan pendientes.
