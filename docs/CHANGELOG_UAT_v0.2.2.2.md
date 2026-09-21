# SGI: Comando — UAT v0.2.2.2

Hotfix técnico sobre UAT v0.2.2.1.

## Corregido

- `AssignmentResource.java`: 6 errores de compilación Java causados por inferencia genérica de Panache en 3 expresiones `list(...).stream().collect(...)`.
- Las consultas se materializan ahora en listas con tipo concreto antes de construir mapas.

## Alcance

- Ningún cambio funcional.
- `SGI-06 Asignaciones` permanece en versión funcional `v0.2`.
- Decision Log e interconexiones SITC no cambian.
