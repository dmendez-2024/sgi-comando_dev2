# CHANGELOG DME — Lista de empleados activos por Empresa

## Alcance

- Se reemplaza la consulta individual `GET /api/v1/employees/by-persona/{personaId}` de la interfaz `SIC_RRHH_SGI_COM_0001_IF02`.
- La interfaz queda como `GET /api/v1/employees?instanceCountryId={instanceCountryId}`.
- El filtro recibido identifica la Empresa mediante `instanceCountryId`; no corresponde al País ni al identificador de una Compañía.

## Comportamiento

- `instanceCountryId` es obligatorio, identifica la Empresa y debe coincidir con el contexto vigente de SGI: Comando.
- Se consultan únicamente registros de `employee_operational_snapshot` cuyo `employment_status` sea `ACTIVE` y cuyo `instance_country_id` coincida con el filtro.
- La respuesta es un arreglo ordenado por nombre del empleado.
- Cada elemento conserva los datos operacionales relevantes: `personaId`, `employeeId`, `fullName`, `roleCode`, `employmentStatus`, Compañía asignada y estado de la membresía primaria.
- Cuando la Empresa no tiene empleados activos, la respuesta es `200` con un arreglo vacío.
- Un `instanceCountryId` ausente responde `400`; uno distinto al contexto vigente responde `404` y no expone datos de otra instancia.

## Impacto técnico

- No se agregan ni modifican tablas, campos o migraciones.
- No cambia el evento de activación/inactivación `SIC_RRHH_SGI_COM_0001_IF01`.
- Se actualizan el catálogo ejecutable de interconexiones y `docs/API_CATALOG.md` con el nuevo contrato de IF02.
