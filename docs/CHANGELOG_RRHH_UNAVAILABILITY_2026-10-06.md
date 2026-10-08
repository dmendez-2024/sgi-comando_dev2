# SGI:Comando · ingreso de permisos y vacaciones SIC:DHO

Fecha: 2026-10-06

## Cambios

- Nuevo endpoint versionado `POST /api/v1/inbound/sic-rrhh/unavailability-events`.
- Autenticación técnica, correlación, contrato e idempotencia obligatorios.
- Resolución del empleado por `personaId` dentro del `instance_country_id` obtenido de CORE por `TenantContext`.
- Upsert por `SIC_DHO:PERMISO:{idPermiso}` y protección frente a eventos antiguos.
- Metadatos de origen: motivo, descripción, estado DHO y fecha de actualización.
- Etiqueta UI para indisponibilidades generales `PERMISSION`.
- El combo de Asignaciones incorpora **Permisos generales** y el backend admite el filtro `PERMISSION`.
- En el listado de personal se muestra el motivo concreto de DHO para permisos generales cuando `sourceReasonLabel` está disponible.

## Migración

`V59__rrhh_unavailability_inbound.sql` es únicamente estructural. Agrega columnas, completa `updated_from_source_at` de filas históricas usando sus timestamps locales y crea la tabla de acuses idempotentes. No elimina ni inserta datos de negocio.

## Configuración pendiente

La interconexión específica aprobada en CORE utiliza el identificador canónico `SIC_DHO_SGI_COM_0002_v001`. No se reutiliza el identificador del contrato de empleados ni se requiere una variable de ambiente para el ID.

## Validación segura

Las pruebas de contrato usan datos ficticios y no inicializan ni escriben una base de datos.
