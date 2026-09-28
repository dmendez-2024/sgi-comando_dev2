# Leyenda del buscador de personal en Asignaciones

**Fecha:** 2026-09-24

**Módulo:** ASI/Asignaciones

## Motivo

El buscador de `Personal disponible` mostraba la leyenda `Buscar nombre o cédula…`,
pero el modelo operacional no almacena cédula y la consulta vigente filtra únicamente por
`employee_operational_snapshot.full_name`.

La documentación de Asignaciones revisada no establece que esta búsqueda deba operar por
cédula.

## Cambio

- Se reemplaza únicamente la leyenda por `Buscar por nombre…`.
- No se cambia la consulta, el contrato de integración, la base de datos ni otra pantalla.

## Archivo modificado

- `frontend/src/pages/Assignments.tsx`
