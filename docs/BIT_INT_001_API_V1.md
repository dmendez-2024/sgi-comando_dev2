# BIT-INT-001 — contrato UAT v1

Interconexión: `SGI_OPR_SGI_COM_0001_v001`  
Operación: `GET /api/v1/operator/runtime?assignmentId={id}`

La respuesta mantiene el contrato existente y añade:

```json
{
  "relief": {
    "bitacoraProtocols": [{
      "protocolId": 1,
      "code": "BIT-PAX",
      "name": "Ingreso de personas",
      "objectType": "PAX",
      "applicationType": "INGRESO",
      "versionNo": 1,
      "status": "ACTIVO",
      "publishedAt": "2026-09-29T10:00:00Z",
      "accreditations": [{
        "accreditationId": "uuid",
        "code": "VISITANTE",
        "name": "Visitante",
        "description": "...",
        "fields": [{
          "fieldId": "uuid",
          "section": "IDENTIFICACION",
          "sortOrder": 1,
          "name": "Documento",
          "fieldType": "TEXT",
          "required": true,
          "evidenceRequired": false,
          "captureMode": "MANUAL"
        }]
      }]
    }]
  }
}
```

Invariantes: todos los protocolos tienen `status=ACTIVO`, pertenecen al tenant y al puesto de la asignación. Los campos pertenecen a sus acreditaciones. Este slice es de lectura; el envío del evento será un contrato posterior.

