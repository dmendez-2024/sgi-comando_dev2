# UAT v0.4.0.1 — Hotfix técnico

## Causa
El build Java 25 de UAT v0.4 detectó cuatro referencias a `ConflictException` en `TerritoryResource.java`. Jakarta REST no define esa clase.

## Corrección
Se reemplazaron por `WebApplicationException` con `Response.Status.CONFLICT` (HTTP 409), preservando exactamente la semántica funcional de impedir eliminación de Zonas/Regiones activas o con dependencias.

## Versiones funcionales
- SGI-00T Territorio: v0.2
- SGI-06 Asignaciones: v0.4

No se modifica el Decision Log funcional.
