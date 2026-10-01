# CHANGELOG DME — Contexto Instancia–País canónico desde CORE

**Fecha:** 2026-09-30  
**Módulo:** Contexto transversal / CORE  
**Rama:** `dmeSgiComando2`

## Objetivo

Eliminar el UUID provisional configurado para `instance_country_id` y utilizar la identidad canónica Instancia–País resuelta desde el catálogo de CORE, sin perder los datos existentes de SGI: Comando.

## Cambios

- Se eliminó la propiedad fija `sgi.instance-country-id` del runtime.
- Se agregó `instance_country_context` para conservar el último contexto canónico obtenido desde CORE y permitir continuidad cuando CORE esté temporalmente indisponible.
- Se agregó `instance_country_context_history` para auditar el identificador anterior, el identificador canónico, el país, el código Instancia–País y la cantidad de filas migradas.
- Se agregó una migración controlada que descubre el contexto existente sin asumir un UUID de ambiente.
- Antes de reemplazar el contexto, la migración verifica que no existan múltiples tenants ni filas del identificador destino que puedan causar colisiones.
- La actualización se ejecuta atómicamente sobre todas las tablas locales que contienen `instance_country_id`.
- Al iniciar el backend se intenta reconciliar el contexto con CORE. Si CORE no está disponible, se conserva el último contexto persistido y se reintenta al actualizar Territorio o consultar el catálogo CORE de Compañías.
- La sincronización de Territorio y Compañías reconcilia primero la Instancia–País y después guarda los snapshots bajo el identificador canónico.

## Origen de la identidad

1. Se consulta `/catalog/countries` usando temporalmente el código de país configurado mientras IDENT no entregue el contexto.
2. Se consulta `/catalog/instance-countries?countryId={countryId}`.
3. El `id` de la Instancia–País seleccionada se guarda como `instance_country_id` local.

El UUID canónico no está escrito en el código ejecutable, la configuración ni en la nueva migración. Los tests pueden conservar fixtures explícitos del contrato CORE.

## Archivos

- `backend/src/main/java/com/cajamarca/sgi/comando/common/TenantContext.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/core/CoreCatalogService.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/core/CoreTenantBootstrap.java`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/db/migration/V36__canonical_instance_country_context.sql`

## Verificación

- Compilación del backend en contenedor.
- Ensayo integral de V36 dentro de una transacción con `ROLLBACK`.
- El ensayo descubrió el contexto provisional existente, migró 917 filas al identificador canónico entregado por CORE y registró la trazabilidad; luego se revirtió completamente el ensayo.
- V36 aplicada correctamente en la base local y registrada en `flyway_schema_history`.
- Migración real verificada sobre 72 tablas y 917 filas, todas bajo la Instancia–País canónica de CORE.
- `instance_country_context_history` conserva la trazabilidad del contexto provisional al canónico.
- Salud del backend verificada en estado `UP` después del reinicio.

## Observación de ambiente

En la verificación del 2026-09-30, CORE respondió inicialmente desde Windows pero la conexión desde la red Docker expiró. La base se migró usando la misma respuesta CORE ya validada durante la sesión. El backend conserva el contexto canónico almacenado y reintentará la consulta automática; la conectividad Docker → CORE debe habilitarse en el ambiente para refrescos posteriores de Territorio y Compañías.

## Reversa

El historial conserva el identificador anterior. Una reversa debe ejecutarse como migración nueva y controlada utilizando ese historial; no se debe editar V36 después de aplicada ni restaurar UUIDs manualmente tabla por tabla.
