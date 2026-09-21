# COM v1.0 FROZEN — Handoff a Sistemas

Este documento forma parte del paquete acumulativo que eventualmente se entregará a Sistemas para integración, hardening y producción.

## Fuente de verdad de la vertical

Leer en este orden:

1. `COM_MASTER.md`
2. `COM_DECISIONS.md`
3. `COM_CONTEXT.md`
4. `COM_DATA_MODEL.md`
5. `COM_API_CONTRACT.md`
6. `COM_INTEGRATIONS.md`
7. `COM_ACCEPTANCE_CRITERIA.md`
8. `COM_FREEZE_NOTE.md`
9. `sitc/COM_v1.0_FROZEN_delta.sitcpack`
10. `sitc/SGI_Comando_CURRENT.sitcpack`

## Migración de base de datos

La implementación COM actual incorpora la migración Flyway:

```text
V8__companies_vertical_v01.sql
```

No modificar migraciones ya aplicadas; cualquier cambio posterior debe utilizar una nueva migración.

## Dependencias productivas

### TER

Requiere TER v1.0 FROZEN como contrato territorial.

### SER

Antes de cerrar la integración productiva multirregión, SER debe exponer Región operacional explícita por Servicio. El bridge UAT por Punto/Provincia no es el diseño final.

### MinIO

El adapter UAT de logo no es productivo. Sistemas debe utilizar MinIO para el archivo y PostgreSQL para metadata/ref/hash/version.

## Controles obligatorios antes de producción

- Compilación Java 25 / Quarkus.
- Build React 19.2 / TypeScript 6.
- Flyway limpio sobre PostgreSQL 17 representativo.
- Pruebas de RBAC y aislamiento `instance_country_id`.
- Pruebas de Zona única / multi-región.
- Prueba de bloqueo de retiro de Región con Servicios activos.
- Prueba de bloqueo de inactivación.
- Prueba de reactivación.
- Prueba de historial/versionamiento.
- Integración MinIO.
- Integración SER para `region_id` operacional.
- Backup/restore y rollback de despliegue.
- Smoke tests post-deploy.

## Regla de handoff

No reinterpretar decisiones congeladas durante despliegue. Si Sistemas detecta un conflicto técnico que requiera cambiar comportamiento funcional, debe regresar como Change Request de la vertical COM y generar una nueva versión.
