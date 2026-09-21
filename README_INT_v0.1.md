# SGI: Comando — INT v0.1 UAT

Entrega técnica para incorporar el módulo genérico de interconexiones y normalizar todos los contratos de SGI: Comando conforme a SITC-NOM-001 v3.0.

## Sin cambios de UI
Todas las verticales funcionales permanecen congeladas. INT v0.1 agrega infraestructura backend, documentación y SITC.

## Artefacto principal para CORE
`repo/sitc/v3/SITC-ECOSISTEMA-CM-20260921-SGI_COM-INTERCONNECTIONS-SCENARIO_SNAPSHOT.sitcpack`

Al importarlo en CORE debe aparecer el catálogo maestro existente más las 24 interconexiones / 30 interfaces de SGI_COM. La importación es PREVIEW/MERGE y no activa bindings productivos automáticamente.

## Módulo backend
`backend/src/main/java/com/cajamarca/sgi/comando/interconnections/`

- CoreInterconnectionResolver
- ResolutionCache
- GenericInterconnectionExecutor
- CredentialRefResolver
- CircuitRegistry
- InterconnectionIds
- InterconnectionCatalogService/Resource

## Configuración bootstrap
`SGI_INTERCONNECTIONS_CORE_RESOLVER_URL` es la única URL bootstrap. Todos los demás endpoints/credenciales se resuelven desde CORE.

## Documentación
- `docs/INTERCONNECTIONS.md`
- `docs/API_CATALOG.md`
- `docs/SITCPACK.md`
- `docs/SECURITY.md`
- `docs/interconnections/SGI_COM_INTERCONNECTION_CATALOG.json`
