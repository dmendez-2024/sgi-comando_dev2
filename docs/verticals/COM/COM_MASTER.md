# SGI: Comando — COM / Compañías

**Versión:** v1.0  
**Estado:** **FROZEN / CONGELADA**  
**Fecha de congelamiento:** 2026-09-08  
**Base:** TER v1.0 FROZEN  
**Scope:** exclusivamente vertical COM. TER y las demás verticales permanecen funcionalmente intactas.

## Objetivo

Administrar la identidad operacional de las **Compañías** de Cajamarca, su pertenencia a una única Zona y su operación en una o más Regiones de esa Zona, preservando trazabilidad, historial y versionamiento.

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

- `company_id` interno estable.
- Código humano estable `COM-###`.
- Nombre.
- Estado: Activa / Inactiva; Borrador solo cuando corresponda a creación no publicada por flujo futuro.
- Zona única.
- Una o más Regiones de esa Zona.
- Logo.
- Reseña histórica opcional, máximo **750 caracteres**.
- Cambio Requerido, conservado del baseline.
- Número de versión vigente.

## Creación

Al crear una Compañía se debe poder registrar:

1. Nombre.
2. Logo.
3. Reseña histórica (máx. 750 caracteres).
4. Zona.
5. Una o más Regiones pertenecientes a esa Zona.

La identidad `company_id` y el código `COM-###` son estables.

## Edición de Compañía Activa

Una Compañía Activa puede modificar:

- Nombre.
- Logo.
- Reseña histórica.
- Agregar Regiones de su misma Zona.
- Retirar Regiones cuando no existan Servicios activos de esa Compañía en la Región a retirar.
- Cambiar de Zona únicamente cuando previamente puedan retirarse todas las Regiones actuales; por tanto no puede completarse mientras existan Servicios activos que bloqueen esas remociones.

Cada guardado genera una nueva versión y un evento histórico/auditable.

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
