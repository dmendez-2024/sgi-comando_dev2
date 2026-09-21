# Requerimientos de SGI: Comando hacia CORE

**Fecha:** 2026-09-20  
**System of Record:** CORE para los dominios indicados en este documento.  
**Consumidor:** SGI: Comando.

## 1. Contexto raíz: Instancia / País / Instancia-País

SGI: Comando debe recibir y conservar un contexto raíz estable por `instance_country_id`.

Datos mínimos:
- `instance_id`
- `country_id`
- `instance_country_id`
- `country_code`
- nombre oficial / nombre de despliegue
- estado y vigencia del vínculo Instancia-País

Regla: los objetos operativos de SGI: Comando deben ser trazables a `instance_country_id`. SGI no redefine el país de forma local.

## 2. Internacionalización y parámetros operativos

CORE debe entregar por `instance_country_id`:
- idioma
- locale
- zona horaria
- moneda
- sistema de unidades
- formato de fecha
- formato de hora
- formato numérico y demás convenciones regionales relevantes

SGI consume estos parámetros y no los redefine.

## 3. Calendario

CORE debe proveer:
- inicio de semana
- calendario nacional aplicable
- feriados nacionales
- calendarios/feriados territoriales cuando correspondan
- versión y vigencia del calendario

SGI usa este contexto en turnos, asignaciones, cronogramas y formatos como 12/5. CORE define el calendario; SGI define la operación.

## 4. Perfiles regulatorios

CORE debe permitir conocer qué perfil regulatorio aplica, su versión y vigencia. El modelo debe soportar al menos:
- laboral
- nómina
- seguridad social
- tributario/contable
- seguridad privada cuando aplique
- otros perfiles país requeridos por el ecosistema

Datos mínimos conceptuales:
- `regulatory_profile_id`
- tipo
- versión
- `effective_from`
- `effective_to`
- estado

No toda regla será consumida inmediatamente por SGI, pero SGI debe poder referenciar el perfil/versionado que gobierna la operación.

## 5. Geografía oficial

CORE es System of Record de la división territorial oficial. SGI: Comando no crea una geografía nacional paralela.

CORE debe entregar:
- tipo de subdivisión oficial (`subdivision_type`)
- etiquetas singular/plural
- `subdivision_id` estable
- código oficial
- nombre oficial
- estado/vigencia
- geometría/polígono
- fuente
- `dataset_version`
- `effective_from` / `effective_to`

SGI usa las subdivisiones oficiales para construir Zonas y Regiones operacionales. Las Zonas/Regiones son objetos SGI; las subdivisiones oficiales son objetos CORE.

## 6. Catálogo maestro de Compañías

CORE es el catálogo maestro de identidad corporativa. SGI: Comando configura la operación de una Compañía, pero no debe inventar una identidad corporativa fuera de CORE.

CORE debe entregar como mínimo:
- `company_id` estable
- `instance_country_id`
- nombre canónico
- nombre de visualización
- logo/referencia de logo
- tipo de compañía cuando aplique
- estado
- metadata corporativa e histórico relevante
- versión/fuente de catálogo

Separación de responsabilidad:
- CORE: identidad de la Compañía.
- SGI: Comando: activación operativa, Zona/Región, servicios y configuración operacional.

Kaibil se mantiene como Compañía especial de coordinación en el modelo operacional.

## 7. Contrato, versionado y trazabilidad

Todo catálogo CORE consumido por SGI debe usar IDs estables y transportar metadata suficiente para auditar la fuente:
- ID estable
- versión del dataset/objeto
- vigencia
- fuente
- timestamps de actualización

SGI debe conservar la referencia a la versión CORE utilizada cuando sea necesaria para reproducibilidad histórica.

Patrón conceptual:

`CORE → API/Event/Adapter → SGI: Comando`

Para geografía, una forma de contrato de referencia es:

`GET /core/instance-countries/{instance_country_id}/territorial-structure`

La nomenclatura final del endpoint puede variar; la semántica y el ownership no.

## 8. Dominios consolidados CORE → SGI: Comando

1. Instance
2. Country
3. InstanceCountry
4. Locale / Language / Timezone
5. Currency / Units / Formats
6. Calendar
7. RegulatoryProfile
8. Geography + Company Catalog

### Entidades mínimas adicionales dentro de Geography
- SubdivisionType
- Subdivision
- SubdivisionGeometry
- TerritorialDatasetVersion

## 9. Información que NO corresponde a CORE

| Información | System of Record |
|---|---|
| Login / identidad de usuario | IDENT |
| Personas / empleados | SIC: RRHH |
| Relación persona-Compañía | SIC: RRHH |
| Habilidades, vacaciones, permisos | SIC: RRHH |
| Recursos materiales / inventario | SIC: RRMM |
| Clientes / servicios comerciales | SIC: COM |
| ATS / diseño técnico de seguridad | ATS |
| Ejecución de relevo, patrulla, consigna | SGI: Operador / SGI: Comando según objeto |
| Novedades de campo | SGI: Operador; moderación en SGI: Comando |
| Evaluación visual de evidencia | VISINT |
| Reglas y ledger de Impulsos | SGI: Comando |

## 10. Principio arquitectónico final

SGI: Comando debe permanecer como hub/orquestador operacional. CORE provee el contexto maestro transversal y oficial, pero no absorbe dominios operativos que pertenecen a SGI, SIC, IDENT, ATS o VISINT.
