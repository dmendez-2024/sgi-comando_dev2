# Avatares ficticios en Personal disponible

**Fecha:** 2026-09-24

**Módulos:** ASI/Asignaciones e integración temporal SIC: RRHH

## Problema

Los empleados cargados manualmente tenían `photo_key`, pero las respuestas de Personal
disponible y ficha de persona no exponían ese campo. El frontend construía
`/avatars/{employeeId}.png`, ruta inexistente para los UUID derivados de `personas.id`.

## Corrección

- `PersonnelDto` y `EmployeeDetails` exponen `photoKey`.
- Personal disponible, selección, ficha y diálogos de transferencia consumen `photoKey`.
- Se conserva la ruta histórica basada en `employeeId` cuando no llega una referencia válida.
- La sincronización SIC: RRHH asigna uno de los 12 avatares UAT cuando `photo_key` está vacío
  y nunca reemplaza un avatar previamente asignado.
- El contrato inbound de SIC: RRHH no cambia y continúa sin foto, avatar o sexo/género.
- La tarjeta compacta dentro de la matriz continúa sin fotografía conforme ASI-DEC-039.

## Base de datos y activos

No se agrega migración ni campo nuevo. Se reutilizan `employee_operational_snapshot.photo_key`
y los 12 PNG existentes en `frontend/public/avatars`.

## Verificación requerida

- Prueba unitaria de estabilidad y pertenencia del avatar asignado localmente.
- Compilación y pruebas del backend.
- Compilación del frontend.
- Consulta de la API para confirmar `photoKey` en Personal disponible y ficha.
