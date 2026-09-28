# Scripts controlados para reemplazar el catálogo de empleados

**Fecha:** 2026-09-24

**Módulos:** Integración RRHH y ASI/Asignaciones

**Estado:** Scripts generados y verificados; no ejecutados sobre la base local

## Objetivo

Preparar dos scripts PostgreSQL independientes para eliminar el catálogo operativo de
empleados existente y cargar los 200 registros de `empleados.xlsx`, usando `id` como
`persona_id`, `nombre` como `full_name` y `cargo o rol` como `role_code`.

## Archivos

- `scripts/DME_01_eliminar_empleados_actuales.txt`
- `scripts/DME_02_insertar_200_empleados.txt`

## Eliminación

- Opera únicamente sobre la Instancia–País
  `11111111-1111-1111-1111-111111111111`.
- Limpia responsables de Compañías, Zonas y Regiones para no dejar UUID de empleados
  eliminados.
- Elimina transferencias, indisponibilidades, habilidades, membresías y snapshots del
  catálogo anterior.
- No elimina Compañías, Territorios, Servicios ni planes operacionales.
- Antes de modificar datos, aborta toda la transacción si encuentra empleados asociados a
  asignaciones, confirmaciones de vulnerabilidad o ejecuciones de patrulla. Esto evita borrar
  silenciosamente evidencia histórica.
- Verifica que las tablas del catálogo queden vacías antes de confirmar la transacción.

## Inserción

- La fuente fue validada con 200 empleados, sin IDs repetidos, nombres vacíos, cargos vacíos
  ni fórmulas.
- Se eliminaron únicamente los espacios exteriores de dos celdas; no se modificaron los
  nombres ni los cargos.
- `employee_id` se deriva con el mismo algoritmo temporal del servicio de integración:
  UUID v3/MD5 de `SIC_RRHH|<instanceCountryId>|PERSONAS|<personaId>`.
- Cada empleado se crea activo, sin cambio requerido y con una membresía primaria activa.
- Los 200 empleados se reparten de forma determinística entre las seis compañías activas,
  ordenadas por código: Galvarino y Atahualpa reciben 34; Lanceros, Cóndor, Andes y Kaibil
  reciben 33 cada una.
- `id_score` y `preferred_shift` permanecen en `NULL`, porque el Excel no aporta esos datos.
- `photo_key` referencia uno de los 12 avatares ficticios UAT. Ocho nombres femeninos
  inequívocos usan el grupo de dibujos femeninos; los demás usan el grupo masculino. Los
  activos pueden repetirse y no representan fotografías reales de las personas.
- El script exige que la eliminación se haya ejecutado, valida las seis compañías esperadas
  y comprueba al final 200 snapshots, 200 `persona_id` y 200 membresías primarias activas.

## Verificación realizada

- Ambos scripts se ejecutaron correctamente en una instancia PostgreSQL 17 desechable con
  todas las migraciones `V1` a `V29` del proyecto.
- Resultado: 200 snapshots, 200 `persona_id` únicos, 200 `employee_id` únicos y 200
  membresías primarias activas.
- La distribución obtenida fue 34, 34, 33, 33, 33 y 33 entre las seis compañías.
- La base local no fue modificada: después de la prueba conserva sus 18 snapshots y 18
  membresías originales.
- SHA-256 del Excel utilizado:
  `455f68914c8854f2e1baaa099afefe5cbd59a03b8c7ae80774c1c81e245a6fe7`.

## Ejecución

Ejecutar primero el archivo `DME_01_eliminar_empleados_actuales.txt` y, únicamente si termina
con `COMMIT`, ejecutar `DME_02_insertar_200_empleados.txt`. Cada archivo usa una transacción y
revierte sus cambios ante cualquier error o validación fallida.
