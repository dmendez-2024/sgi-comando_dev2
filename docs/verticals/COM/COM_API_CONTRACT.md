# COM v1.0 FROZEN — Contrato API actual

Base path:

```text
/api/companies
```

## Listar

```http
GET /api/companies?page=0&size=50
```

Respeta `instance_country_id` y alcance territorial/RBAC.

## Detalle

```http
GET /api/companies/{company_id}
```

## Historial

```http
GET /api/companies/{company_id}/history
```

Devuelve versiones en orden descendente.

## Crear

```http
POST /api/companies
```

Payload conceptual:

```json
{
  "name": "Galvarino",
  "status": "ACTIVE",
  "zoneId": "uuid",
  "regionIds": ["uuid"],
  "logoDataUrl": "UAT-only",
  "historicalReview": "máx. 750 caracteres",
  "changeReason": "opcional"
}
```

## Editar / cambiar estado

```http
PUT /api/companies/{company_id}
```

El backend valida:

- nombre obligatorio y único dentro de `instance_country_id`
- Zona válida
- al menos una Región
- todas las Regiones dentro de la Zona
- bloqueo de retiro de Región con Servicios activos
- bloqueo de Inactivar con Servicios activos
- versionamiento e historial

## Producción

El campo `logoDataUrl` del contrato UAT deberá sustituirse por un flujo de archivo/MinIO o referencia de objeto antes del despliegue productivo final.

## Responsables operacionales de Compañía

La nota de implementación DME describe:

```http
GET /api/companies/responsibles?companyId={uuid}
PUT /api/companies/{id}
```

El catálogo devuelve personal activo del país-instancia con cargo de Coordinador de Compañía o Asistente de Operaciones y con `persona_id` sincronizado. El parámetro `companyId` valida el alcance de edición, pero no restringe por `employee_operational_snapshot.company_id`: la autorización sobre la Compañía seleccionada se materializa en `user_operational_scope` al guardar.

El `PUT` admite:

```json
{
  "coordinatorEmployeeId": "uuid o null",
  "assistantEmployeeId": "uuid o null"
}
```

Si existen Coordinadores elegibles, `coordinatorEmployeeId` es obligatorio. Si no existe ninguno, puede enviarse `null`. `assistantEmployeeId` siempre es opcional. Ambos responsables generan alcances `COMPANY` independientes en `user_operational_scope` para que puedan acceder a la información de la Compañía. `responsibleEmployeeId` se conserva como alias legado del Coordinador para compatibilidad con consumidores anteriores.
