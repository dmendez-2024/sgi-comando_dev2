# COM v1.1.3 FROZEN — Integraciones

## CORE → COM — identidad de Compañía
**SoR:** CORE

CORE entrega catálogo/identidad de Compañía: Nombre, Logo, Reseña histórica, tipo, versión y estado fuente. SGI activa la entidad operacional y no edita esos campos.


## TER → COM

**SoR:** TER v1.0 FROZEN

COM consume:

- `zone_id`
- `region_id`
- catálogo de Zonas
- catálogo de Regiones
- relación Región → Zona
- alcance territorial requerido para RBAC

COM no modifica Zonas ni Regiones.

## CORE → TER → COM

COM no mantiene geografía oficial.

```text
CORE
  tipo de subdivisión / catálogo / geometría
        ↓
TER
  Zona / Región operacional
        ↓
COM
  Zona única + Regiones operativas de la Compañía
```

## SER ↔ COM

**Contrato funcional congelado:**

- COM debe conocer qué Servicios activos pertenecen a cada Compañía.
- Para retirar una Región, COM debe conocer cuántos Servicios activos de la Compañía operan en esa Región.
- Para inactivar una Compañía, Servicios activos asociados debe ser 0.
- COM no migra ni finaliza Servicios.
- SER será responsable de la asignación operacional inicial y de futuros flujos de transferencia/finalización de Servicios.
- SER deberá tener `region_id` operacional explícito para soportar el modelo multi-región.

### Bridge UAT existente

Mientras SER no esté migrada al contrato definitivo, el código UAT puede inferir temporalmente Región mediante:

```text
Servicio activo
→ Punto activo
→ Provincia/Estado del Punto
→ subdivisión CORE
→ Región TER
```

Este bridge es transitorio y no debe tratarse como contrato de producción.

## MinIO ↔ COM

**Arquitectura productiva:**

```text
COM → MinIO: archivo Logo
COM → PostgreSQL: object key/ref + hash + metadata + versión
```

El Data URL local del UAT se mantiene únicamente como adapter de prueba hasta el hardening final.

## PostgreSQL

COM mantiene:

- `company`
- `company_region`
- `company_version`

Todas las entidades operacionales respetan `instance_country_id`.

## SITC

La versión congelada se registra en:

- `sitc/COM_v1.0_FROZEN_delta.sitcpack`
- `sitc/SGI_Comando_CURRENT.sitcpack`


## SER v0.9 → COM / frontera de responsabilidad
SER usa el catálogo de Compañías activas de COM para asignar inicialmente Servicios recibidos desde SIC: COM. La bandeja Kaibil es lógica: un Punto pendiente mantiene `company_id = NULL`. Kaibil nunca es destino operativo de un Servicio de cliente.
