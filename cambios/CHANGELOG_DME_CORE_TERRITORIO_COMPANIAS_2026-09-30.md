# Integración provisional CORE para Territorios y Compañías

## Identificador

`CHANGELOG_DME_CORE_TERRITORIO_COMPANIAS_2026-09-30`

## Alcance

- Se consulta `GET /catalog/countries` y se selecciona Ecuador por `isoAlpha2 = EC`.
- Se utiliza el campo `id` devuelto por CORE como `countryId` para el catálogo territorial y el GeoJSON.
- Para Compañías, el `id` del país se envía a `GET /catalog/instance-countries?countryId={id}` y se obtiene el `id` de la Instancia–País; ese segundo UUID es el que CORE exige en `GET /catalog/companies?instanceCountryId={instanceCountryId}&status=ACTIVE`.
- La prueba integrada del 2026-09-30 confirmó que enviar directamente el UUID de país como `instanceCountryId` produce `404`; para Ecuador CORE devolvió una única instancia activa (`ECU-CM`).
- El código `EC` es configuración transitoria. Cuando IDENT exista, deberá reemplazarse únicamente el proveedor del código de país por el valor entregado en el contexto autenticado.

## Territorios

- SGI sincroniza la identidad y metadata de las subdivisiones oficiales conservando sus asignaciones existentes de Zona y Región.
- Flyway `V35__core_territorial_catalog_reference.sql` agrega `core_subdivision_id`, `official_code`, `subdivision_type` y `source_version`.
- El mapa consume el GeoJSON de CORE y mantiene la regla existente: una subdivisión solo se pinta cuando su Zona y su Región están ambas en estado `ACTIVE`.
- Se elimina la dependencia del mapa respecto de polígonos provinciales hardcodeados en React.
- No se incorpora Leaflet, CDN ni plugin nuevo.

## Compañías

- La consulta de “Activar desde CORE” actualiza primero `core_company_catalog_snapshot` con las Compañías activas devueltas por CORE.
- Se mapean `id`, `code`, `name`, `description`, `logoUrl`, `companyType`, `status` y `sourceVersion` sin alterar la configuración operacional existente de SGI.
- Si CORE no está disponible, se conserva el último snapshot local y se registra la falla en el log del backend.

## Configuración

- `SGI_CORE_CATALOG_BASE_URL`: base del API CORE seleccionable por ambiente. El valor predeterminado para DEVELOPMENT es `https://apps.cajamarca.ec/dev.core/api/v1`; para pruebas locales se puede sobrescribir con `http://192.168.20.138:5173/api/v1` sin modificar código.
- `SGI_CORE_COUNTRY_CODE`: código ISO alpha-2 provisional; valor por defecto `EC`.
- `SGI_CORE_INSTANCE_COUNTRY_CODE`: código opcional para escoger la Instancia–País cuando CORE devuelva más de una para el país; si existe una sola, no hace falta configurarlo.
- `SGI_CORE_CATALOG_TIMEOUT_MS`: timeout de las consultas, por defecto 5000 ms.
- `SGI_CORE_COUNTRY_CACHE_SECONDS`: vigencia local de la resolución país → id, por defecto 300 segundos.

## Actualización 2026-10-02

- Territorios y Compañías apuntan por defecto al catálogo público de CORE en DEVELOPMENT.
- LOCAL y DEVELOPMENT utilizan la misma variable `SGI_CORE_CATALOG_BASE_URL`; no se agrega una bandera paralela.
- El cambio de ambiente requiere recrear el backend para que el contenedor vuelva a leer la variable.

## Corrección del mapa para configuraciones en borrador

- Las provincias seleccionadas en Zonas o Regiones con estado `DRAFT` se guardan en `draft_zone_id` y `draft_region_id`.
- `zone_id` y `region_id` conservan exclusivamente la configuración territorial efectiva que utiliza el mapa.
- La selección pendiente se promueve a la configuración efectiva únicamente cuando la Zona y la Región cumplen las condiciones de activación.
- Al eliminar un borrador se limpian solo sus referencias `draft_*`, sin modificar el mapa vigente.
- Flyway `V37__repair_territory_draft_assignments.sql` corrige registros borrador que hayan quedado en los campos efectivos después de aplicada `V27`.

## Archivos principales

- `backend/src/main/java/com/cajamarca/sgi/comando/core/CoreCatalogService.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/territory/TerritoryResource.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/companies/CompanyResource.java`
- `frontend/src/pages/Territory.tsx`
- `backend/src/main/resources/db/migration/V35__core_territorial_catalog_reference.sql`
