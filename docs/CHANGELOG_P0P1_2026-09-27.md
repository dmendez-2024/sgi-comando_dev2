# SGI_COM — P0/P1 RC — 2026-09-27

Baseline: `sgi-comando_dev.zip` / commit `8c528e8` de SISTEMAS (2026-09-25)  
Responsable de esta corrección: ChatGPT / revisión solicitada por usuario  
Norma: SITC-NOM-001 v4.1

## Cambios realizados

- `P0-MIG-001` | APROBADO | V30/V31 destructivos/carga UAT salen del camino automático: tombstones Flyway + fixtures explícitos.
- `P1-SITC-001` | APROBADO | CURRENT y delta regenerados bajo SITC-NOM-001 v4.1; CORE=UNIVERSAL.
- `P1-INT-001` | APROBADO | IDs canónicos migrados a `ORIGEN_DESTINO_NNNN_vNNN`; aliases v3 preservados solo para transición.
- `P1-INT-002` | APROBADO | SIC:RRHH inbound adopta `credential_ref`, idempotencia persistente y deja de inventar Kaibil como Compañía fuente para altas nuevas.
- `P1-IMP-001` | APROBADO | CORE queda SoR de reglas versionadas de Impulsos; SGI_COM PE SoR del ledger/saldo.
- `P1-SEC-001` | APROBADO | `.env` excluido; `.env.example`; sin passwords default en application.properties; Compose exige secretos.
- `P1-REP-001` | APROBADO | dependencias frontend directas dejan `latest` y se fijan a versiones exactas.

## Archivos/migraciones

- V30/V31 modificados como tombstones seguros.
- Originales preservados en `database/uat-fixtures/legacy-original-flyway/`.
- `scripts/uat-seed-employees.ps1` agregado.
- V34 agrega únicamente `sic_rrhh_employee_event_receipt` para idempotencia/auditoría de `SIC_RRHH_SGI_COM_0001_v001`; no reescribe empleados.
- No se cambia UI.
- No se implementa EVC/Eventos de Cumplimiento.

## Integraciones / SITC

- CURRENT: `sitc/SGI_Comando_CURRENT.sitcpack`.
- Delta: `sitc/SGI_COM_P0P1_20260927_COMPONENT_DELTA.sitcpack`.
- 25 interconexiones canónicas / 31 interfaces.
- `SGI_COM_CORE_0001_v001` reservado/alineado con el ecosistema para reglas de Impulsos.
- Los IDs antiguos v3 se almacenan como aliases de transición.

## Validación pendiente

- Build Maven/Docker end-to-end: pendiente en entorno con Maven/Docker y acceso a imágenes.
- `npm install` / package-lock: pendiente por falta de acceso npm desde este runtime.
- Base que ya ejecutó V30/V31 antiguos: requiere revisión de `flyway_schema_history` y posible `Flyway repair` controlado.
