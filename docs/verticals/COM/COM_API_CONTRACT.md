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

## Extensión operacional observada después de COM v1.1.3 — pendiente de RC

La nota de implementación DME describe:

```http
GET /api/companies/responsibles
PUT /api/companies/{id}
```

El `PUT` admite `responsibleEmployeeId` como UUID o `null`; la respuesta incorpora `responsibleEmployeeId`, `responsibleName` y `responsibleRoleCode`. El catálogo devuelve candidatos según la regla de cargo implementada en el backend.

Esta extensión cambia modelo/API y no se considera aprobada por esta documentación. Confirmar CR, permisos, regla de elegibilidad y versión objetivo antes de cerrar contrato.
