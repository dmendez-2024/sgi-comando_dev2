# TER — Integraciones v1.0 — CONGELADO

## CORE → TER
**SoR:** CORE  
**Clave:** `instance_country_id`

### Contrato obligatorio
- `countryCode`
- `countryName`
- `subdivisionType`
- `subdivisionSingular`
- `subdivisionPlural`
- `datasetVersion`
- `subdivisions[]`:
  - `id`
  - `code`
  - `name`
  - `status`
  - `geometry`
  - `geometryVersion` o trazabilidad equivalente

### Responsabilidad
CORE define qué tipo de subdivisión utiliza el país, cuáles son las subdivisiones oficiales y su geometría. TER solo referencia `subdivision_id`.

### Mapa
TER renderiza el mapa con:

`CORE.geometry + TER.zoneId + TER.regionId`

Un cambio de clasificación territorial repinta el mapa automáticamente.

## SIC: RRHH → TER
**SoR:** SIC: RRHH
- personal activo elegible para Responsable de Zona/Región;
- identificador de empleado;
- nombre;
- cargo/rol de referencia.

## TER → RBAC SGI: Comando
TER provee jerarquía Zona/Región/Compañía utilizada por `OperationalScopeService` para resolver alcance territorial server-side.

## COM → TER (lectura)
TER muestra Compañías asociadas a Regiones para contexto. Crear/editar Compañías y cambiar Región permanece en COM.

## SITC
Registrar:

`CORE → TER: official territorial catalog + geometry + dataset version`

`SIC: RRHH → TER: responsible candidates`

`TER → SGI RBAC: operational territorial hierarchy`

`COM → TER: Companies by Region (read-only)`
