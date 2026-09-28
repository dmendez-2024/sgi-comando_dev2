# COM v1.0 FROZEN — Modelo de datos

## `company`

Campos funcionales relevantes:

```text
id                    UUID PK
instance_country_id   UUID
code                  COM-###
name                  string
status                ACTIVE | INACTIVE
required_change_count integer
zone_id               UUID
region_id             UUID  (alias transitorio de compatibilidad)
historical_review     varchar(750)
logo_data_url         text  (UAT/transitorio; NO producción)
version_number        integer
created_at
updated_at
```

### Nota de compatibilidad

`company.region_id` se mantiene temporalmente para no romper verticales antiguas. La relación autoritativa multi-región de COM es `company_region`.

## `company_region`

```text
id
instance_country_id
company_id
region_id
created_at
updated_at
```

Reglas:

- ≥ 1 Región por Compañía Activa.
- Todas las Regiones deben pertenecer a `company.zone_id`.
- Unicidad lógica `company_id + region_id`.

## `company_version`

```text
id
instance_country_id
company_id
version_number
change_type
change_reason
actor_username
effective_at
snapshot_json
created_at
updated_at
```

Tipos iniciales observados:

- `CREATED`
- `UPDATED`
- `TERRITORY_UPDATED`
- `INACTIVATED`
- `REACTIVATED`

## Producción — Logo

El modelo objetivo deberá sustituir `logo_data_url` como almacenamiento binario por metadata equivalente a:

```text
logo_object_key
logo_hash
logo_content_type
logo_size
logo_version
```

El objeto binario vive en MinIO.
