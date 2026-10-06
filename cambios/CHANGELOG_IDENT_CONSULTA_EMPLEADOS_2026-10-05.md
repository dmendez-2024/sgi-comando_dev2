# CHANGELOG — Consulta de empleados IDENT → SGI:Comando

## Alcance

- `GET /api/v1/employees?instanceCountryId={instanceCountryId}` pertenece a la interconexión `IDENT_SGI_COM_0001_v001`.
- El endpoint utiliza una credencial exclusiva de IDENT.
- No se aceptan identificadores ni credenciales de RRHH/DHO para esta consulta.
- `POST /api/v1/inbound/sic-rrhh/employee-events` conserva sin cambios su contrato y credencial de RRHH/DHO.

## Configuración

- Referencia: `SGI_IDENT_EMPLOYEES_INBOUND_CREDENTIAL_REF=IDENT_SGI_COM_0001_v001`.
- Secreto de runtime: `SGI_CREDENTIAL_IDENT_SGI_COM_0001_V001`.
- Los secretos efectivos no forman parte del contrato ni del catálogo portable.
