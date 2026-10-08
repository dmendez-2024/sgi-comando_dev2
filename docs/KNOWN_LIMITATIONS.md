# SGI: Comando — limitaciones y pendientes conocidos

**RC vigente:** P0/P1 2026-09-27  
**Baseline de código:** `sgi-comando_dev.zip`, commit `8c528e8` (SISTEMAS, 2026-09-25)  
**Norma:** SITC-NOM-001 v4.1

## Base de datos / Flyway

- V30 y V31 fueron **cuarentenadas**: el path automático de Flyway contiene tombstones sin DML; el reset y la carga de 200 empleados quedaron como fixtures UAT explícitos fuera del path automático.
- Una base **nueva** puede migrar con los V30/V31 corregidos sin borrar ni sembrar empleados.
- Una base que **ya aplicó** los antiguos V30/V31 tendrá checksum diferente. No desactivar `validate-on-migrate` ni editar `flyway_schema_history` manualmente; respaldar, verificar el estado y ejecutar una reconciliación/`repair` controlada según `docs/MIGRATION_SAFETY_V30_V31.md` antes de iniciar esta RC.
- `quarkus.flyway.migrate-at-start=true` permanece activo; por eso el gate anterior es obligatorio para bases existentes.

## Integraciones / estado end-to-end

- El catálogo CURRENT registra **25 ECOSYSTEM_INTERCONNECTION y 31 interfaces** con nomenclatura v4.1. Los IDs v3 se conservan únicamente como aliases de transición.
- La existencia de una definición en CURRENT no implica que ambas puntas estén implementadas ni probadas. Revisar `docs/INTERCONNECTIONS.md` para estado `DESIGN`, `UAT`, `MANUAL_PENDING`, etc.
- SIC:COM inbound y SIC:RRHH inbound fueron avanzados por SISTEMAS y endurecidos en esta RC; requieren prueba bilateral contra sus contrapartes antes de declararlos `ACTIVE`.
- SIC:RRHH es SoR de Persona–Compañía. SGI ya no asigna automáticamente a Kaibil un empleado nuevo: exige `companyCoreCatalogId` o `companyCode`. La contraparte debe homologar esos campos; hasta entonces el flujo permanece `UAT_PARTIAL`.
- El resolver CORE/cache y `CredentialRefResolver` son la capa reusable vigente. `CredentialRefResolver` resuelve secretos desde runtime/env en UAT; producción puede sustituirlo por Vault/KMS sin cambiar el contrato de negocio.
- Alarmas electrónicas de Consola todavía no tienen un Program ID fuente/gateway confirmado en el catálogo vigente; no se inventó una interconexión.

## Impulsos

- **Implementado sin CORE (2026-10-08):** las reglas viven en `impulse_rule` de cada Instancia PE (versionadas, editables en la base); el motor `ImpulseEngine` evalúa y guarda el ledger en `impulse_evaluation`. Cuando CORE publique las reglas, `impulse_rule` se alimentará desde CORE (SGI-IMP-DEC-009).
- **Valores iniciales sin aprobar:** tomados de la hoja AdS (Rango = mínimo–máximo del premio, Promedio = referencia, probabilidad 1). Escala: 100 Impulsos por 0,1 de nivel (1.000 Impulsos/PH). Con 0–1 Impulso por acción, subir 0,1 toma unas 200 acciones. Gerencia debe confirmar.
- **Reglas activas:** Asistencia (relevo a tiempo, tolerancia 15 min), Porte (foto de cuerpo completo), Control de Acceso (bitácora), Patrullas (completa) y Criterio (consigna cumplida). **Inactivas, sin productor:** Novedades aprobadas, km de patrulla, Liderazgo y QR de cliente. SGI: Operador documenta otras reglas para Condición Táctica y Liderazgo (`IMPULSOS_RULES_FROZEN.md`) que no coinciden con la hoja AdS.
- No hay vista de Impulsos en el front de SGI: Comando; solo los endpoints `/api/impulses/*`.
- El relevo y la patrulla no se probaron de punta a punta en dispositivo; sí la consigna (app → motor) y bitácoras de prueba.

## Seguridad / reproducibilidad

- No se entrega `.env`; solo `.env.example` sin secretos efectivos. Los secretos deben resolverse por `credential_ref`/secret store.
- PostgreSQL y MinIO ya no tienen contraseñas efectivas de fallback en la configuración portable.
- Las dependencias directas frontend dejaron de usar `latest`, pero **no se pudo generar/verificar `package-lock.json`** en este entorno porque el registro npm no respondió. SISTEMAS debe generar y versionar el lockfile antes del gate de producción.
- No se ejecutó build Maven/Docker end-to-end en este entorno. La validación entregada es estática/estructural más validación de JSON/ZIP/SITC; SISTEMAS debe ejecutar build, migraciones sobre DB desechable y UAT antes de producción.

## Fuera de alcance explícito

- **EVC — Eventos de Cumplimiento no se implementa en esta RC.** No se añadieron entidades, migraciones, APIs, UI ni contratos EVC.
- No se hicieron cambios visuales a la UI como parte del P0/P1.

## CSL v0.2 UAT — limitaciones de Notificación de Incidentes
- Persistencia de incidentes creados desde Consola: DEMO/local de frontend; se pierde al recargar la aplicación.
- Adjuntos: se valida tipo imagen y máximo 5 por sección, pero esta UAT conserva metadata en memoria y no carga binarios a backend/objeto storage.
- Historial de colaboradores de 2 semanas, turnos, disponibilidad y coordenadas: dataset DEMO/local para UAT. Producción debe resolverlos desde las tablas/servicios operativos de SGI: Comando.
- Routing a STC: no activado en esta versión; se conservan los contratos existentes sin cambio.
- Sanción: captura UX únicamente; no envía todavía a SIC: RRHH.
- EVC: no implementado.

