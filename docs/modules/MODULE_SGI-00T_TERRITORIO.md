# TER — Territorio v0.1

**Estado:** Candidato a congelar. Ver `docs/verticals/TER/`.

## Jerarquía
`Instancia → Instancia–País → Zona → Región → Compañía → Punto → Puesto`.

## CORE como SoR territorial
CORE entrega el catálogo político-administrativo oficial del país y también la **denominación que corresponde usar en UI**:
- tipo (`PROVINCE`, `STATE`, etc.);
- singular (`Provincia`, `Estado`, ...);
- plural (`Provincias`, `Estados`, ...);
- catálogo `{código, nombre, estado}` por `instance_country_id`.

Por tanto, SGI: Comando no debe mostrar el literal genérico `Provincia/Estado`. Para Ecuador, CORE determina `Provincia / Provincias`.

## Configuración TER
- Zona: código, nombre, estado, Responsable SIC: RRHH, unidades territoriales provenientes de CORE.
- Región: código, nombre, Zona, estado, Responsable SIC: RRHH, subconjunto de unidades previamente asignadas a su Zona.
- Compañías: se muestran por Región para contexto; su creación/edición pertenece a COM.

## Ciclo de vida
- Borrador: editable/eliminable si no tiene dependencias.
- Activa: no eliminable; conserva trazabilidad. Responsable y cobertura territorial siguen editables según permiso.
- Inactiva: histórica.

## RBAC territorial
- Presidente / Director Nacional: País.
- Director Zonal: Zona.
- Jefe Regional: Región.
- Coordinador / Asistente de Coordinación: Compañía.

## UAT Ecuador
El adaptador `CORE LOCAL · UAT` representa las 24 Provincias del Ecuador. El mapa operacional aprobado se usa como referencia visual; el diseño objetivo admite geometría desde CORE para render dinámico.
