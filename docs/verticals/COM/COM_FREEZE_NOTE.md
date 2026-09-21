# COM v1.0 — Freeze Note

**Fecha:** 2026-09-08  
**Estado:** **FROZEN / CONGELADA**

El Product Owner aprobó el modelo funcional de COM y solicitó congelar la vertical antes de avanzar a la siguiente.

## Alcance congelado

- Logo.
- Reseña histórica máx. 750 caracteres.
- Edición de Compañías activas.
- Zona única.
- Una o más Regiones dentro de la Zona.
- Agregar Regiones de la misma Zona aun en Activa.
- Bloquear retiro de Región con Servicios activos en esa Región.
- Bloquear operación multizona.
- Cambio de Zona sujeto a retiro válido de las Regiones existentes.
- Inactivación solo con 0 Servicios activos.
- Reactivación permitida.
- Identidad estable.
- Historial y versionamiento.

## Fronteras congeladas

- TER v1.0 permanece intacta.
- SER será responsable de la migración/finalización de Servicios y de su Región operacional explícita.
- MinIO será el almacenamiento productivo de Logos.

## Cambios futuros

Cualquier cambio funcional posterior requiere:

1. Nueva versión COM.
2. Entrada en Decision Log.
3. Evaluación de impacto SITC.
4. Scope guard contra COM v1.0 FROZEN.
5. Regresión de criterios de aceptación.
