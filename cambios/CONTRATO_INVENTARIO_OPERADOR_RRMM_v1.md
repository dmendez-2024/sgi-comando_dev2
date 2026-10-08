# Contrato de inventario SGI Operador ↔ SGI Comando ↔ SIC:RRMM

Fecha: 2026-10-07  
Versión contractual: `v1`

## Responsabilidades

- `SIC_RRMM` es la fuente maestra de activos, asignaciones y condición esperada.
- `SGI_COM` mantiene una instantánea por Puesto, la entrega al Operador y conserva los reportes del relevo.
- `SGI_OPR` presenta los activos al agente, carga evidencias y reporta la condición observada.
- Si RRMM no responde, SGI Comando entrega la última instantánea como `STALE`; sin instantánea responde `PENDING_SOURCE`.
- El relevo se conserva aunque la entrega del reporte a RRMM falle. SGI reintenta la entrega hasta diez veces.

## Cabeceras de integración

Las llamadas entre sistemas usan:

```http
Authorization: Bearer <credencial-resuelta-por-credential_ref>
X-Correlation-Id: <uuid-o-identificador-trazable>
X-Interconnection-Id: <id-canónico-versionado>
X-Contract-Version: v1
Idempotency-Key: <clientRequestId>  # obligatorio en operaciones POST
```

Las URL efectivas y credenciales se resuelven desde CORE. SGI Comando no almacena secretos del otro sistema en el contrato.

## 1. SGI Comando consulta el inventario esperado

- Interconexión: `SGI_COM_SIC_RRMM_0001_v001`
- Interfaz: `SGI_COM_SIC_RRMM_0001_IF01`
- Método: `GET`
- Ruta RRMM: `/api/v1/operational-assets/posts/{postId}/expected?instanceCountryId={instanceCountryId}`

Respuesta:

```json
{
  "sourceVersion": "RRMM-2026.10.07",
  "items": [
    {
      "assetId": "0f339371-d769-4ded-9f11-a4b42e71505c",
      "productId": "62799de6-9cbd-4ae7-b77a-6a0956c58abc",
      "code": "RAD-001",
      "description": "Radio portátil",
      "expectedCondition": "GOOD",
      "assignmentStatus": "ASSIGNED",
      "critical": true
    }
  ]
}
```

`assetId`, `code`, `description` y `sourceVersion` son obligatorios. Todos los ítems de una respuesta deben tener la misma versión.

## 2. SGI Operador obtiene el inventario

- Interconexión: `SGI_OPR_SGI_COM_0001_v001`
- Método: `GET`
- Ruta SGI Comando: `/api/v1/operator/runtime?assignmentId={assignmentId}`
- Autenticación: identidad del usuario Operador y vínculo activo usuario-empleado.

Fragmento de respuesta:

```json
{
  "inventory": {
    "status": "AVAILABLE",
    "sourceVersion": "RRMM-2026.10.07",
    "fetchedAt": "2026-10-07T18:30:00Z",
    "items": [
      {
        "assetId": "0f339371-d769-4ded-9f11-a4b42e71505c",
        "productId": "62799de6-9cbd-4ae7-b77a-6a0956c58abc",
        "code": "RAD-001",
        "description": "Radio portátil",
        "expectedCondition": "GOOD",
        "assignmentStatus": "ASSIGNED",
        "critical": true
      }
    ]
  }
}
```

Estados de lectura:

| Estado | Acción del Operador |
|---|---|
| `AVAILABLE` | Presenta y exige confirmar todos los activos. |
| `STALE` | Presenta la última instantánea, muestra advertencia y permite reportar. |
| `PENDING_SOURCE` | No presenta activos y envía el relevo con ese mismo estado. |

## 3. Evidencia por activo

- Interconexión: `SGI_OPR_SGI_COM_0002_v001`
- Método: `POST multipart/form-data`
- Ruta: `/api/v1/operator/evidences`
- `targetType`: `INVENTORY_ASSET`
- `targetId`: `assetId`
- `eventId`: identificador del relevo que se enviará después.

Cada archivo conserva la idempotencia por `clientEvidenceId`. Un activo con condición `DAMAGED` requiere por lo menos una evidencia válida del mismo evento, asignación, activo y usuario.

## 4. Operador confirma el inventario y el relevo

- Método: `POST`
- Ruta: `/api/v1/operator/executions`

Fragmento del evento `RELIEF_SUBMITTED`:

```json
{
  "type": "RELIEF_SUBMITTED",
  "eventId": "386e3a76-fad0-4dfd-a135-950531f696b5",
  "assignmentId": "ca2138df-3e12-4777-a275-e264e55dfb2c",
  "inventoryStatus": "REPORTED",
  "inventoryItems": [
    {
      "assetId": "0f339371-d769-4ded-9f11-a4b42e71505c",
      "condition": "DAMAGED",
      "observation": "Pantalla rota",
      "evidenceIds": ["e1498d8e-b7e9-4475-827d-1227ef4ab44f"]
    }
  ]
}
```

Reglas:

- `inventoryStatus`: `PENDING_SOURCE`, `REPORTED` o `COMPLETE`.
- Condiciones: `GOOD`, `DAMAGED`, `MISSING`, `REPLACED`, `NOT_VERIFIED`.
- Cuando el inventario está disponible, deben reportarse exactamente todos los activos de la versión incluida en `configurationVersion`.
- `observation` admite hasta 1000 caracteres.
- No se admiten activos ni evidencias duplicadas.
- La respuesta incluye `inventoryDeliveryStatus`: `NOT_REQUIRED`, `PENDING`, `DELIVERED` o `FAILED`.

## 5. SGI Comando reporta a SIC:RRMM

- Interconexión: `SGI_COM_SIC_RRMM_0002_v001`
- Interfaz: `SGI_COM_SIC_RRMM_0002_IF01`
- Método: `POST`
- Ruta RRMM: `/api/v1/operational-assets/state-reports`
- `Idempotency-Key`: `clientRequestId`, igual al `reliefEventId`.

Solicitud:

```json
{
  "reportId": "d28f846f-7230-474e-a128-135b46f2c2d5",
  "clientRequestId": "386e3a76-fad0-4dfd-a135-950531f696b5",
  "correlationId": "29bfd076-2b8d-4caf-98a7-25f18fc393f0",
  "instanceCountryId": "81000000-0000-0000-0000-000000000007",
  "reliefEventId": "386e3a76-fad0-4dfd-a135-950531f696b5",
  "assignmentId": "ca2138df-3e12-4777-a275-e264e55dfb2c",
  "postId": "6dfa8316-329f-4edb-aec3-32fab754bbdd",
  "pointId": "e32e4247-508d-44dd-a9cd-90e04c7d1acc",
  "employeeId": "c58bb07a-686d-4c8a-b1e6-5052e07fa88c",
  "reportedAt": "2026-10-07T18:35:00Z",
  "sourceVersion": "RRMM-2026.10.07",
  "inventoryStatus": "REPORTED",
  "items": [
    {
      "assetId": "0f339371-d769-4ded-9f11-a4b42e71505c",
      "productId": "62799de6-9cbd-4ae7-b77a-6a0956c58abc",
      "code": "RAD-001",
      "description": "Radio portátil",
      "expectedCondition": "GOOD",
      "condition": "DAMAGED",
      "observation": "Pantalla rota",
      "evidenceIds": ["e1498d8e-b7e9-4475-827d-1227ef4ab44f"]
    }
  ]
}
```

Respuesta esperada de RRMM:

```json
{
  "accepted": true,
  "rrmmReportId": "RRMM-REP-10425",
  "receivedAt": "2026-10-07T18:35:02Z"
}
```

## 6. SIC:RRMM consulta el catálogo operativo de SGI Comando

- Interconexión: `SIC_RRMM_SGI_COM_0001_v001`
- Autenticación: `sgi.rrmm.inbound.credential-ref`.
- `GET /api/v1/integration/rrmm/companies/{companyId}/points`
- `GET /api/v1/integration/rrmm/points/{pointId}/posts`

Solo se exponen Puntos `ACTIVE`, asignados operativamente a la Compañía, y sus Puestos. Ambas respuestas incluyen `generatedAt`, `items[]` y una versión por registro basada en `updatedAt`.

## Persistencia y auditoría

La migración `V68__rrmm_inventory_integration.sql` crea:

- `inventory_expected_asset`: instantánea vigente de activos esperados.
- `inventory_sync_state`: versión y fecha de la última consulta válida.
- `inventory_report`: cabecera, JSON original, hash, correlación y estado de entrega.
- `inventory_report_item`: condición esperada y observada por activo.
- `inventory_report_item_evidence`: vínculo entre activo reportado y evidencia.

Los reportes pendientes se reintentan cada 60 segundos. Después de diez intentos quedan en `FAILED` para intervención operativa.

## Configuración necesaria para UAT

1. Registrar en CORE el binding de `SGI_COM_SIC_RRMM_0001_v001` y su interfaz `SGI_COM_SIC_RRMM_0001_IF01`.
2. Registrar en CORE el binding de `SGI_COM_SIC_RRMM_0002_v001` y su interfaz `SGI_COM_SIC_RRMM_0002_IF01`.
3. Configurar en el proveedor de credenciales la referencia `SIC_RRMM_SGI_COM_0001_v001`, o cambiar `SGI_RRMM_INBOUND_CREDENTIAL_REF`. En Docker local, el secreto se inyecta con `SGI_CREDENTIAL_SIC_RRMM_SGI_COM_0001_V001`.
4. SIC:RRMM debe implementar los dos endpoints de activos y aceptar las cabeceras canónicas.
5. SGI Operador debe consumir `inventory`, cargar evidencia con `INVENTORY_ASSET` y enviar `inventoryItems[]`.
