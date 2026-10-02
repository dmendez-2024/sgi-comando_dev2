# Cuenta UAT — alex.chiriboga

- Persona: Alex Enrique Chiriboga Mafla
- Perfil: Agente de Seguridad
- Ambiente: UAT local exclusivamente
- Fuente de permisos y alcance: usuario `agente`
- Fixture: `database/uat-fixtures/BIT_INT_001_alex_chiriboga_operator_UAT.sql`

El fixture es idempotente y sólo se aplica si existe exactamente una persona activa con el nombre indicado en la misma instancia que `agente`. Replica el perfil, alcance y credencial común de UAT al crear la cuenta, pero conserva la credencial de Alex si la cuenta ya existe. Crea una identidad separada y auditable y el vínculo `operator_employee_binding` requerido por el runtime móvil. No duplica las asignaciones de turno: deben programarse para el empleado Alex.

No forma parte de Flyway y no debe promoverse a producción. Para producción, IDENT debe aprovisionar una credencial independiente mediante el flujo oficial.

## Resultado 2026-09-29

- Fixture aplicado correctamente en la base aislada `sgi-com-bit-int-001-rc1`.
- Autenticación Basic UAT: HTTP 200.
- Identidad vinculada: `fcf488ea-9c43-3a1d-9494-a5c8a4145fa3`.
- Asignaciones activas devueltas: 0. La programación de turno no se clonó porque no forma parte de las credenciales.

## Resultado 2026-10-02 · base local `sgi_comando`

- Se detectó la instancia desde el usuario UAT `agente` y se aplicó el fixture sin crear ni modificar la ficha laboral existente de Alex.
- `alex.chiriboga` quedó activo con rol `AGENTE_SEGURIDAD`, alcance de compañía replicado y vínculo con el empleado `fcf488ea-9c43-3a1d-9494-a5c8a4145fa3`.
- `GET /api/v1/operator/runtime` autenticado como `alex.chiriboga` respondió HTTP 200 y devolvió la identidad de Alex.
- El runtime devolvió cero asignaciones vigentes. Para recibir turnos y tareas en Operador, debe publicarse una asignación del empleado Alex en un puesto y ventana horaria válidos.

