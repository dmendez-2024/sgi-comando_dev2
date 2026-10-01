# CHANGELOG DME — Consulta de empleado por personaId

## Alcance

- Se implementa `GET /api/v1/employees/by-persona/{personaId}`.
- `personaId` corresponde a `personas.id` de DHO almacenado en `employee_operational_snapshot.persona_id`.
- La búsqueda siempre se limita a la Instancia–País resuelta por SGI:Comando.
- El endpoint es únicamente de lectura y no modifica empleados, compañías ni membresías.
- No se agrega migración ni campo de base de datos.

## Respuesta

- Empleado: `personaId`, `employeeId`, `fullName`, `roleCode` y `employmentStatus`.
- Compañía: `id`, `coreCatalogId`, `code`, `name` y `status`.
- Membresía: `companyMembershipActive`, calculado a partir de una membresía `PRIMARY` con `ends_at IS NULL` para la Compañía actual.
- No se exponen avatar, habilidades, turnos, ID/SMC, datos personales ni campos de auditoría.

## Seguridad y errores

- Reutiliza la credencial técnica Bearer de `SIC_RRHH_SGI_COM_0001_v001` mediante `credential_ref`; no incorpora secretos nuevos.
- Exige `X-Correlation-Id`, `X-Interconnection-Id` y `X-Contract-Version: v1`.
- Responde `400` cuando `personaId` no es mayor que cero y `404` cuando no existe en la Instancia–País.
