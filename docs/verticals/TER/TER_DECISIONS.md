# TER — Decision Log v1.0 — CONGELADO

## TER-DEC-001 — Jerarquía territorial
Toda Compañía pertenece a exactamente una Región; toda Región pertenece a exactamente una Zona; toda Zona pertenece a una Instancia–País.

## TER-DEC-002 — CORE es SoR territorial oficial
CORE es el System of Record del tipo de división territorial, catálogo oficial, códigos, estados, polígonos/geometrías y versionamiento del dataset territorial por `instance_country_id`.

CORE entrega como mínimo:
- `countryCode`, `countryName`;
- `subdivisionType`;
- `subdivisionSingular`;
- `subdivisionPlural`;
- `datasetVersion`;
- `subdivisions[] { id, code, name, status, geometry }`.

SGI: Comando no mantiene geografía oficial propia. El almacenamiento UAT representa un adaptador/snapshot `CORE LOCAL · UAT`, nunca el SoR.

## TER-DEC-003 — Terminología dinámica
La UI no muestra `Provincia/Estado`. Usa la terminología enviada por CORE. Ecuador: `Provincia` / `Provincias`; otros países pueden usar `Estado` / `Estados`, etc.

## TER-DEC-004 — Responsables
Responsable de Zona y Responsable de Región se seleccionan del personal activo proveniente de SIC: RRHH. TER conserva referencia/snapshot operacional necesario.

## TER-DEC-005 — Restricción territorial de Regiones
Una Región solo puede contener subdivisiones que ya pertenecen a su Zona.

## TER-DEC-006 — Exclusividad de subdivisión
En TER v1.0 una subdivisión oficial completa pertenece a una sola Zona y a una sola Región operacional a la vez. TER no divide una Provincia/Estado entre dos Regiones.

## TER-DEC-007 — Eliminación e historia
Una Zona o Región `ACTIVA` no puede eliminarse físicamente. Mantiene historia para auditoría. Sí se pueden actualizar Responsable y asignaciones territoriales conforme a permisos.

## TER-DEC-008 — Compañías en TER
TER muestra Compañías por Región para contexto y conteo. Crear/editar Compañía y cambiar su Región pertenece a COM.

## TER-DEC-009 — Alcance operacional
Roles de alcance país ven el país; Director Zonal su Zona; Jefe Regional su Región; Coordinador/Asistente su Compañía. La restricción se aplica server-side.

## TER-DEC-010 — Usuarios UAT visibles
Para pruebas de TER el selector visible contiene únicamente:
1. Presidente
2. Director Nacional
3. Director Zonal
4. Jefe Regional
5. Coordinador
6. Asistente de Coordinación

## TER-DEC-011 — Feedback
Crear, guardar, eliminar y errores muestran feedback visible mediante toast transitorio de aproximadamente 5 segundos.

## TER-DEC-012 — Mapa dinámico
El mapa operacional se construye automáticamente desde los polígonos entregados por CORE y las asignaciones `subdivision_id → zone_id → region_id` de TER.

No existe un mapa territorial estático como fuente maestra en SGI. Una imagen de referencia UAT puede existir únicamente como fallback visual del adaptador local mientras CORE no exponga geometría.

## TER-DEC-013 — Versionamiento geográfico
CORE versiona el dataset territorial (ej. `EC-2026.01`). TER conserva/referencia la versión utilizada para trazabilidad y reproducción histórica.

## TER-DEC-014 — Convención visual del mapa
El mapa distingue Zonas y Regiones a partir de configuración operacional. Borde de subdivisión, borde/estilo de Región y agrupación visual de Zona deben derivarse de datos, no de una imagen preparada manualmente.

## TER-DEC-015 — Auditoría
Crear/editar/eliminar Zonas/Regiones y modificar responsables o asignaciones territoriales genera eventos de auditoría. La consulta respeta alcance territorial.

## TER-DEC-CORE-GEO-001 — Decisión interproyecto CORE ↔ TER
> CORE es el System of Record del tipo de división territorial, catálogo oficial y polígonos geográficos de las subdivisiones administrativas de cada país. SGI: Comando / TER únicamente asigna esas subdivisiones oficiales a Zonas y Regiones operacionales. El mapa de Territorio se genera dinámicamente usando los polígonos proporcionados por CORE y la configuración Zona/Región almacenada por SGI.

**Estado:** APROBADA / CONGELADA
