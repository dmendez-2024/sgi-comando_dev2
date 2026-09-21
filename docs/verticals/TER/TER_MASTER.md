# TER — Territorio

**Sistema:** SGI: Comando  
**Vertical:** TER — Territorio  
**Versión:** v1.0  
**Estado:** **CONGELADA**  
**Base técnica:** SGI: Comando UAT v0.5 / TER v0.1 aprobado visualmente  
**Fecha de congelamiento:** 2026-09-08

## Propósito
TER administra la estructura territorial **operacional** de una Instancia–País. No crea ni mantiene la geografía político-administrativa oficial del país; la consume desde CORE y agrupa esas unidades oficiales en Zonas y Regiones para mando, alcance y RBAC operacional.

## Jerarquía

`Instancia → Instancia–País → Zona → Región → Compañía → Punto → Puesto`

Las subdivisiones oficiales del país (por ejemplo **Provincias** en Ecuador o **Estados** en otros países) pertenecen a CORE. TER referencia sus `subdivision_id` para asignarlas a Zonas y Regiones.

## Responsabilidades de TER
- Crear, editar, activar e inactivar Zonas.
- Crear, editar, activar e inactivar Regiones dentro de una Zona.
- Asignar subdivisiones oficiales de CORE a Zonas.
- Asignar a cada Región únicamente subdivisiones previamente incluidas en su Zona.
- Asignar Responsable de Zona y Responsable de Región desde SIC: RRHH.
- Mostrar Compañías pertenecientes a cada Región en modo consulta; la creación/edición de Compañías pertenece a COM.
- Proveer la estructura Zona/Región/Compañía para alcance operacional y RBAC.
- Mantener auditoría de cambios territoriales.
- Renderizar el mapa territorial operacional **dinámicamente** usando geometrías oficiales entregadas por CORE y la clasificación Zona/Región almacenada en TER.

## Responsabilidades de CORE relacionadas con TER
CORE es SoR de:
- tipo de subdivisión territorial por `instance_country_id`;
- singular/plural visible en UI;
- catálogo oficial de subdivisiones;
- códigos oficiales y estados;
- polígonos/geometrías de cada subdivisión;
- versión del dataset territorial.

TER nunca debe hardcodear `Provincia/Estado`; usa la terminología proveniente de CORE.

## Regla del mapa
El mapa de TER no es una imagen maestra ni una geografía duplicada. El comportamiento objetivo es:

`Polígonos CORE + subdivision_id → Zona/Región TER → mapa dinámico`

Un cambio de Provincia/Estado entre Regiones o Zonas repinta el mapa sin subir una nueva imagen.

## Fuera de alcance de TER
- Crear/editar el catálogo oficial de subdivisiones (CORE).
- Crear/editar geometrías oficiales (CORE).
- Crear/editar empleados responsables (SIC: RRHH).
- Crear/editar Compañías o cambiar su Región (COM).
- Modificar Puntos, Puestos, Servicios, Asignaciones u otras verticales.

## Estado de congelamiento
Esta versión congela **modelo, responsabilidades, reglas funcionales, fronteras de SoR, RBAC e integración geográfica**. La integración productiva con geometrías reales depende de que CORE exponga el contrato definido en `CORE_TERRITORIAL_CONTRACT.md`; eso no reabre el diseño de TER.

Cualquier cambio posterior requiere una nueva versión TER y una entrada explícita en el Decision Log.
