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

## Extensiones observadas en el código — candidatas de evolución posterior a v1.1.3

El código fuente contiene las siguientes extensiones. Se registran aquí para que el modelo de tablas no quede desactualizado; esta nota no aprueba la reapertura de COM ni declara una nueva baseline congelada.

### `company` — migración V28

```text
responsible_employee_id UUID NULL
```

Campo opcional para la persona responsable operacional. La bitácora DME indica que no se realizó backfill; las Compañías existentes quedan sin responsable hasta edición autorizada.

### Catálogo comercial replicado — migración V33

```text
client(id, instance_country_id, code, name, commercial_status,
       source_system, source_version, created_at, updated_at)
service.client_id UUID NOT NULL REFERENCES client(id)
post.commercial_status varchar(32) NOT NULL DEFAULT 'ACTIVE'
sic_com_commercial_event_receipt(... event_id, content_hash,
                                 commercial_version, processing_status, response_json ...)
```

`client` y los atributos comerciales recibidos son una réplica operacional local; SIC:COM permanece SoR comercial según el contrato. V33 genera referencias LEGACY desde el nombre comercial existente antes de establecer `service.client_id` como obligatorio. Confirmar la correspondencia con el contrato SIC:COM aprobado y la RC aplicable.

## Gobierno pendiente

COM-DEC-023 congela COM v1.1.3. La migración V28 identifica COM v1.1.4 en su comentario, pero no se encontró una aprobación/RC formal que cierre esa evolución. Mantener esta extensión en estado candidato hasta localizar el CR y completar UAT.
