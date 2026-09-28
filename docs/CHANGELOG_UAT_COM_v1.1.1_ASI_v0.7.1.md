# SGI: Comando — COM v1.1.1 / ASI v0.7.1 UAT FIX

## Motivo
La migración V18 fallaba en bases UAT que ya contenían Compañías adicionales (por ejemplo `COM-002`) con UUID distintos a los IDs determinísticos del nuevo seed. PostgreSQL detenía la migración por la restricción única `(instance_country_id, code)`.

## Corrección
- V18 usa ahora `(instance_country_id, code)` como clave natural de merge para Compañías provenientes del catálogo CORE.
- Si la Compañía ya existe, conserva su UUID y se vincula/actualiza con los metadatos CORE.
- Si no existe, se crea con el UUID UAT determinístico.
- `company_region`, `company_version` y los snapshots de personal Kaibil resuelven el UUID real de la Compañía por código, evitando referencias a IDs asumidos.
- Kaibil sigue quedando siempre Activa.

## Compatibilidad
La ejecución reportada de V18 hizo rollback completo y Flyway permaneció en versión 17, por lo que esta corrección puede aplicarse directamente sobre la misma base UAT sin resetearla.

No hay cambios funcionales respecto de COM v1.1 / ASI v0.7; esta entrega es un patch de migración/persistencia.
