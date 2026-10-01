# Seguridad de migraciones V30/V31 — P0

**Fecha:** 2026-09-27  
**Baseline corregida:** código dev de SISTEMAS, commit `8c528e8` (2026-09-25).  
**Objetivo:** impedir que una carga/destrucción UAT se ejecute automáticamente mediante Flyway.

## Cambio aplicado

Las versiones Flyway `V30` y `V31` se conservan como números históricos, pero sus archivos automáticos ahora son **tombstones sin DML**:

- `V30__eliminar_todos_los_empleados.sql`: ya no elimina datos.
- `V31__insertar_200_empleados_con_avatares.sql`: ya no inserta fixtures.

Los SQL originales se preservan byte-for-byte fuera de `db/migration`:

- `database/uat-fixtures/legacy-original-flyway/V30__eliminar_todos_los_empleados.sql`
- `database/uat-fixtures/legacy-original-flyway/V31__insertar_200_empleados_con_avatares.sql`

Y se exponen como fixtures UAT explícitos:

- `database/uat-fixtures/DME_01_eliminar_todos_los_empleados_UAT.sql`
- `database/uat-fixtures/DME_02_insertar_200_empleados_con_avatares_UAT.sql`
- `scripts/uat-seed-employees.ps1 -Force`

## Regla operativa

La aplicación **nunca** debe borrar/resembrar empleados por el solo hecho de arrancar.

La carga UAT requiere una acción humana explícita, una base UAT respaldada y el parámetro `-Force`.

## Atención: bases que ya ejecutaron los V30/V31 anteriores

Modificar una migración que ya fue aplicada cambia su checksum. Por tanto:

1. Antes de iniciar esta RC contra una base existente, consultar `flyway_schema_history`.
2. Si V30/V31 **no fueron aplicadas**, no hay acción especial: la RC ejecutará los tombstones seguros.
3. Si V30/V31 **sí fueron aplicadas con los SQL destructivos originales**, el arranque detectará checksum mismatch.
4. No desactivar `validate-on-migrate` y no editar manualmente `flyway_schema_history`.
5. En una copia/backup verificado de esa base, Sistemas debe reconciliar esos checksums con el procedimiento oficial `Flyway repair` y documentar el cambio.
6. Producción no debe recibir ni ejecutar los fixtures UAT.

Los originales preservados permiten verificar exactamente qué se ejecutó antes de autorizar un `repair`.

## Rollback

La corrección no intenta revertir datos que V30/V31 anteriores ya pudieran haber modificado. Restaurar esos datos requiere backup o reconstrucción desde el SoR correspondiente.
