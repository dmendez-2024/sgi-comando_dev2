# CHANGE REQUEST — SIC_DHO

**CR-ID:** CR-SIC_DHO-0001
**Origen:** SGI_COM P0/P1 RC 2026-09-27  
**Interconexión:** `SIC_DHO_SGI_COM_0001_v001` / `SIC_DHO_SGI_COM_0001_IF01`

## Problema / necesidad

SIC:RRHH es System of Record de la relación Persona–Compañía. La implementación recibida de SGI: Comando sincronizaba identidad/rol pero, para un empleado nuevo, asignaba Kaibil localmente por defecto. Esto podía crear una relación laboral que no provenía del SoR. Además, `Idempotency-Key` se validaba en el header pero SGI no persistía un receipt equivalente al de SIC:COM.

## Cambio aplicado del lado SGI_COM

- ID v4.1 canónico `SIC_DHO_SGI_COM_0001_v001`; los IDs SIC_RRHH existentes quedan como aliases de transición.
- Auth por `credential_ref`; ningún token portable.
- Request amplía v1 de manera compatible con `companyCoreCatalogId?` y `companyCode?`.
- Empleado nuevo: debe llegar al menos uno de esos identificadores de Compañía; **no existe fallback automático a Kaibil**.
- Empleado existente: mientras se despliega la contraparte, puede omitir compañía y conservar la relación ya persistida.
- Si la Compañía fuente cambia, SGI cierra la membresía PRIMARY anterior y crea la nueva con timestamp de fuente.
- Receipt idempotente persistente mediante V34.

## Cambio requerido en SIC_DHO

1. Enviar `companyCoreCatalogId` (preferido) o `companyCode` para todo empleado activo de Seguridad Física, especialmente altas nuevas.
2. Usar `X-Interconnection-Id: SIC_DHO_SGI_COM_0001_v001`.
3. Mantener `X-Contract-Version: v1`, `X-Correlation-Id` e `Idempotency-Key` estable por evento lógico.
4. Resolver la credencial mediante el binding/secret autorizado; no hardcodear token.
5. Probar CREATE, UPDATE, cambio de Compañía, retry idéntico, retry con payload distinto y timestamp stale.

## Compatibilidad / estado

Hasta completar y probar la contraparte: `UAT_PARTIAL`. No declarar `ACTIVE` end-to-end.
