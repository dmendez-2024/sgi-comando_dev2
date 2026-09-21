# COM v1.1.3 — Freeze Note

**Fecha:** 2026-09-19  
**Estado:** **FROZEN / CONGELADA**

COM v1.1.3 queda congelada como baseline vigente.

## Alcance congelado
- CORE es SoR de identidad de Compañía: Nombre, Logo y Reseña histórica son read-only en SGI.
- SGI activa Compañías desde catálogo CORE; no existe creación local desde cero.
- SGI mantiene Estado, Motivo del cambio, Zona y Regiones operativas.
- Zona única y multi-región dentro de la misma Zona.
- Inactivación condicionada a no tener Servicios activos; reactivación permitida.
- Historial y versionado operacional.
- Kaibil es Compañía de coordinación, `always_active=true`, siempre Activa y no puede desactivarse.
- Kaibil puede contener personal de coordinación y participar en transferencias de personal, pero **no opera Servicios de clientes**.

## Fronteras congeladas
- CORE conserva identidad de Compañía.
- TER conserva Zona/Región.
- SER decide y registra la Compañía operativa de un Servicio.
- SIC: RRHH conserva SoR de persona–Compañía.

Cualquier cambio funcional posterior requiere abrir una nueva versión COM, actualizar Decision Log/SITC y ejecutar regresión.
