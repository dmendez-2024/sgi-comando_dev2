# COM — Decision Log

**Vertical:** COM — Compañías  
**Versión congelada:** v1.0  
**Estado:** FROZEN

## COM-DEC-001 — Zona única
Una Compañía pertenece exactamente a una Zona.

**Estado:** CONGELADA

## COM-DEC-002 — Multi-región
Una Compañía puede operar en una o más Regiones.

**Estado:** CONGELADA

## COM-DEC-003 — Regiones dentro de una sola Zona
Todas las Regiones asignadas a una Compañía deben pertenecer a su única Zona.

**Estado:** CONGELADA

## COM-DEC-004 — Agregar Regiones
Una Compañía Activa puede agregar Regiones de su misma Zona en cualquier momento.

**Estado:** CONGELADA

## COM-DEC-005 — Retirar Región
Una Región solo puede retirarse de una Compañía cuando no existan Servicios activos de esa Compañía en dicha Región.

**Estado:** CONGELADA

## COM-DEC-006 — Históricos no bloquean
Los Servicios históricos/finalizados no bloquean retirar la relación Compañía–Región.

**Estado:** CONGELADA

## COM-DEC-007 — Prohibición multizona
Una Compañía no puede operar simultáneamente en más de una Zona.

**Estado:** CONGELADA

## COM-DEC-008 — Cambio de Zona
Para cambiar de Zona deben poder retirarse previamente todas las Regiones actuales. Por transitividad, el cambio no se permite mientras existan Servicios activos que bloqueen dichas remociones.

**Estado:** CONGELADA

## COM-DEC-009 — Reactivación
Una Compañía Inactiva puede volver a Activa conservando identidad, código, historial y versionamiento.

**Estado:** CONGELADA

## COM-DEC-010 — Edición versionada
Una Compañía Activa puede editar Nombre, Logo, Reseña Histórica y estructura territorial conforme a las reglas COM. Cada guardado genera una nueva versión e historial.

**Estado:** CONGELADA

## COM-DEC-011 — Logo y reseña
Al crear una Compañía se puede cargar Logo y registrar Reseña Histórica de máximo 750 caracteres.

**Estado:** CONGELADA

## COM-DEC-012 — Inactivación por Servicios
Una Compañía solo puede inactivarse cuando no tenga Servicios activos asociados. Debe migrarlos o finalizarlos primero.

**Estado:** CONGELADA

## COM-DEC-013 — Frontera con SER
COM no migra Servicios. La transferencia/finalización corresponde a SER. SER deberá mantener Región operacional explícita para cada Servicio compatible con una Compañía multirregión.

**Estado:** CONGELADA COMO CONTRATO ENTRE VERTICALES

## COM-DEC-014 — Protección TER
TER v1.0 permanece congelada. COM consume Zona/Región de TER y no modifica funcionalmente la vertical TER.

**Estado:** CONGELADA

## COM-DEC-015 — Almacenamiento productivo de Logo
En producción, el archivo de Logo se almacena en MinIO y PostgreSQL conserva referencia, hash, metadata y versión. El adapter LOCAL/Data URL es exclusivamente UAT/transición y no debe llegar a producción.

**Estado:** CONGELADA

## COM-DEC-016 — Identidad estable
Cambios de Nombre, Logo, Reseña, Zona, Regiones, estado o reactivación no crean una Compañía nueva. `company_id` y código `COM-###` permanecen estables.

**Estado:** CONGELADA

## COM-DEC-017 — No eliminación de una Compañía histórica
Una Compañía que haya estado Activa no se elimina físicamente. Se conserva mediante estado Inactiva y su historial/versionamiento.

**Estado:** CONGELADA
