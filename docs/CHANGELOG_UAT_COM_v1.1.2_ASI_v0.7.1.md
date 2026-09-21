# SGI: Comando — COM v1.1.2 / ASI v0.7.1 UAT FIX

## Incidente corregido
En una base UAT existente, V18 encontró simultáneamente una fila con el código canónico `COM-002` y otra fila distinta con el nombre canónico `Atahualpa`. El merge anterior por código intentaba renombrar la primera fila y chocaba con la restricción única `(instance_country_id, name)`.

## Corrección
- V18 ya no asume que código y nombre canónicos pertenecen a la misma fila legacy.
- La reconciliación por Compañía usa la prioridad: `core_catalog_id` → nombre CORE → código CORE → inserción.
- Se conserva el UUID de la fila elegida como identidad SGI existente.
- Si el nombre CORE ya existe con otro código legacy, ese registro se vincula a CORE y conserva su código SGI para evitar una colisión destructiva.
- `company_region`, `company_version` y Kaibil se resuelven por `core_catalog_id`.
- La acción futura “Activar desde CORE” reutiliza también una Compañía legacy por nombre/código en vez de intentar crear un duplicado.

## Alcance funcional
No cambia la lógica aprobada de COM v1.1 ni ASI v0.7. El cambio es de compatibilidad de migración/activación con bases UAT evolucionadas.

## Flyway
La base reportó rollback completo de V18 y permaneció en versión 17. Por ello se corrige el archivo V18 existente; no se agrega V19.
