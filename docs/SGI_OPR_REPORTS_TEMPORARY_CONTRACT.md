# Contrato temporal SGI Operador → SGI Comando

Estado: **UAT local / pendiente de registro definitivo en CORE**.

Este adaptador permite probar Incidentes, Hallazgos y Vulnerabilidades sin convertir la ruta temporal en una dependencia definitiva. CORE debe asumir posteriormente la resolución de los bindings; el payload `v1` y la idempotencia deben conservarse.

## Crear reporte

- Interconexión: `SGI_OPR_SGI_COM_0018_v001`
- Interfaz: `SGI_OPR_SGI_COM_0018_IF01`
- Temporal: `POST /api/v1/operator/reports`
- Cabecera obligatoria: `Idempotency-Key`, igual a `reportId`
- Autorización: Agente o Supervisor autenticado, empleado vinculado y asignación publicada.

```json
{
  "reportId": "UUID estable generado en el dispositivo",
  "assignmentId": "UUID",
  "type": "INCIDENT | FINDING | VULNERABILITY",
  "category": "texto",
  "subcategory": "texto opcional",
  "title": "texto",
  "description": "texto",
  "severity": "LOW | MEDIUM | HIGH | CRITICAL",
  "occurredAt": "ISO-8601 UTC"
}
```

Repetir el mismo `reportId` devuelve confirmación con `duplicate: true` y no crea otro registro.

## Consultar reportes propios

- Interconexión: `SGI_OPR_SGI_COM_0019_v001`
- Interfaz: `SGI_OPR_SGI_COM_0019_IF01`
- Temporal: `GET /api/v1/operator/reports?type=INCIDENT|FINDING|VULNERABILITY`
- La identidad autenticada limita los resultados al empleado vinculado.

## Lectura de SGI Comando

- `GET /api/operational-reports`
- Restringido a roles operativos de Comando y supervisión.
- Alimenta la bandeja unificada como Novedades.

## Reglas para la migración a CORE

1. Registrar ambos IDs y sus interfaces con la versión `v001`.
2. Conservar `Idempotency-Key`, los códigos HTTP y el payload `v1`.
3. Reemplazar únicamente la resolución temporal/local; SGI Operador no debe cambiar sus pantallas ni su cola local.
4. Mantener el patrón local-first: `PENDING/RETRY → SYNCING → ACKED`; un rechazo funcional pasa a `NEEDS_ATTENTION`.
5. No sincronizar registros del usuario demo Agente cuando aplique la exclusión offline definida para ese usuario.
