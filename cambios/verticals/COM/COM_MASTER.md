# SGI: Comando — COM / Compañías

**Versión:** v1.1.3  
**Estado:** **FROZEN / CONGELADA**  
**Fecha de congelamiento:** 2026-09-19  
**Base:** TER v1.0 FROZEN  
**Scope:** exclusivamente vertical COM. TER y las demás verticales permanecen funcionalmente intactas.

## Objetivo

Administrar la capa operacional de las **Compañías** activadas desde CORE, su pertenencia a una única Zona y su operación en una o más Regiones de esa Zona, preservando trazabilidad, historial y versionamiento. CORE conserva la identidad maestra.

## Jerarquía territorial

```text
Instancia–País
└── Zona (exactamente 1 por Compañía)
    └── Compañía
        ├── Región 1
        ├── Región 2
        └── Región N
```

Reglas:

- Toda Compañía pertenece exactamente a **una Zona**.
- Una Compañía opera en **una o más Regiones**.
- Todas sus Regiones deben pertenecer a su única Zona.
- Una Compañía nunca puede operar simultáneamente en dos Zonas.

## Datos de Compañía

**Fuente CORE / solo lectura en SGI:**
- `core_catalog_id`.
- Nombre.
- Logo.
- Reseña histórica.

**Configuración operacional SGI:**
- `company_id` interno estable.
- Estado: Activa / Inactiva.
- Zona única.
- Una o más Regiones de esa Zona.
- Motivo del cambio / auditoría.
- Cambio Requerido, conservado del baseline.
- Número de versión vigente.

## Activación

SGI no crea Compañías desde cero. El usuario autorizado selecciona una entidad del **catálogo CORE** y la activa en SGI. La activación vincula `core_catalog_id`, conserva la identidad proveniente de CORE y exige la configuración territorial cuando corresponda.

**Kaibil** es `company_type = COORDINATION`, `always_active = true`, permanece siempre Activa y no requiere Zona/Regiones como una Compañía comercial normal.

## Edición de Compañía Activa

En SGI se pueden modificar únicamente:
- Estado (excepto Kaibil, que no puede inactivarse).
- Zona.
- Regiones operativas.
- Motivo del cambio.

Nombre, Logo y Reseña histórica permanecen bloqueados porque CORE es SoR. Cada guardado operacional genera versión e historial.

## Inactivación y reactivación

- Una Compañía solo puede pasar a **Inactiva** cuando no tenga Servicios activos asociados.
- Antes de inactivarla, los Servicios activos deben migrarse o finalizarse desde SER.
- Los Servicios históricos/finalizados no bloquean la inactivación.
- Una Compañía Inactiva puede volver a **Activa**, conservando `company_id`, código, historia y versiones.
- Una Compañía que haya estado activa no se elimina físicamente.

## Frontera con SER

COM no migra ni reasigna Servicios. Esa responsabilidad pertenece a **SER — Servicios**.

Para el estado congelado de COM, la regla funcional es:

```text
Servicio activo → Compañía + Región operacional
```

Cuando SER sea trabajada, deberá exponer `region_id` operacional explícito para soportar Compañías multirregión. La inferencia UAT basada en Punto/Provincia es solamente un bridge técnico transitorio y no es arquitectura final.

## Logo — arquitectura productiva

La arquitectura productiva congelada es:

```text
Logo binario → MinIO
Metadata / referencia / hash / versión → PostgreSQL
```

El adapter LOCAL/Data URL existente en la UAT es exclusivamente transitorio y **no debe utilizarse en producción**.

## Dependencia congelada

COM consume **TER v1.0 FROZEN** para:

- `zone_id`
- `region_id`
- relación Región → Zona
- alcance territorial

COM no modifica TER.

## Estado de freeze

Las reglas funcionales de esta versión quedan congeladas. Cambios posteriores requieren una nueva versión COM y un nuevo registro de impacto/regresión.
