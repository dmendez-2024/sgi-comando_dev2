# Cambio DME — Filtro de responsables territoriales

## Fecha

2026-09-24

## Alcance

- Opción: Operaciones → Territorio.
- Aplica a los combos Responsable de Zona y Responsable de Región.
- Se conservan los filtros por Instancia–País, ámbito de Compañías permitido y estado laboral activo.
- La lista incluye únicamente cargos cuyo `roleCode` contiene `Coordinador`, `Jefe`, `Director` o `Presidente`, sin distinguir mayúsculas y minúsculas.
- No se modifican los filtros ni las listas de personal de otros módulos.

## Archivo modificado

- `backend/src/main/java/com/cajamarca/sgi/comando/territory/TerritoryResource.java`
