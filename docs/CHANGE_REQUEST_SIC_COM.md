# CHANGE REQUEST — SIC_COM

**CR-ID:** CR-SIC_COM-0001  
**Origen:** SGI_COM P0/P1 RC 2026-09-27  
**Interconexión:** `SIC_COM_SGI_COM_0001_v001`

## Objetivo

Homologar la integración comercial ya implementada por SISTEMAS al estándar SITC-NOM-001 v4.1 sin cambiar su semántica de negocio.

## Requisitos de contraparte

- Enviar `X-Interconnection-Id: SIC_COM_SGI_COM_0001_v001`; los IDs v3 quedan solo como alias temporal del lado SGI.
- `X-Contract-Version: v1`.
- `X-Correlation-Id` e `Idempotency-Key`; el key debe coincidir con `eventId` según el contrato SGI vigente.
- Service identity por `credential_ref`; no token hardcodeado en código/archivo portable.
- Probar reintento idéntico y reutilización de `eventId` con contenido diferente.

## Estado

SGI_COM inbound: UAT. End-to-end: pendiente de homologación/prueba del lado SIC_COM.
