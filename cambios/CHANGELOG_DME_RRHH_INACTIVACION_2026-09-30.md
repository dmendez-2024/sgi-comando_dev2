# CHANGELOG DME — Inactivación de colaboradores desde SIC: RRHH

## Alcance

- Se amplía `SIC_RRHH_SGI_COM_0001_IF01` para aceptar `employmentStatus=ACTIVE|INACTIVE`.
- Un evento `INACTIVE` actualiza `employee_operational_snapshot.employment_status` sin eliminar el colaborador ni sus historiales.
- Al inactivar se cierra la membresía primaria vigente mediante `company_membership.ends_at`; no se elimina la membresía.
- Una inactivación solo aplica sobre un colaborador previamente sincronizado en SGI:Comando.
- Asignaciones conserva su regla vigente: solo lista y permite colaboradores con estado laboral `ACTIVE`.

## Integración DHO

- El flujo existente `rrhh/salidaEmpleado/detProceso/update` envía la desvinculación al mismo endpoint de eventos de empleado.
- SIC: RRHH envía `personas.id` como `employeeId` y `personaId`, además de nombre, cargo, estado `INACTIVE` y fecha de actualización del origen.
- La integración anterior con SGI legado se conserva en el código, pero su invocación queda comentada en el flujo de salida.

## Limitación conocida

- Este cambio no incorpora cola ni reintento automático. Si SGI:Comando no está disponible, DHO conserva la desvinculación y registra el fallo para su atención posterior.
