# SGI: Comando — SER v0.9.1 UAT

## Objetivo
Extender SER v0.9 para permitir retirar un Servicio ya asignado hacia la bandeja lógica Kaibil y reasignarlo posteriormente a otra Compañía, conservando toda su configuración operacional.

## Cambios funcionales
- Acción **Retirar** en Servicios para Presidencia, Director Nacional, Director Zonal y Jefe Regional según su ámbito territorial.
- Modal SGI **Retirar Servicio a Kaibil** con impacto explícito y observaciones opcionales.
- El Servicio vuelve a `PENDING`, con `company_id = NULL`; en Kaibil no puede configurarse.
- La configuración existente del Punto/Servicio permanece intacta.
- Las asignaciones futuras se marcan `REMOVED` con razón `SERVICE_RETURN_TO_COORDINATION`; no se borran físicamente.
- El turno actualmente en ejecución se conserva y define `operational_transition_until`.
- Reasignación desde Kaibil a otra Compañía reutiliza la configuración existente y no requiere aceptación del Coordinador destino.
- La pantalla de Asignaciones bloquea turnos previos al fin de la transición para evitar doble cobertura.

## Persistencia / auditoría
- Nueva migración `V20__ser_service_return_to_coordination.sql`.
- `point.operational_transition_until`.
- `service_company_assignment_event.destination_company_id` pasa a nullable para representar retorno a Coordinación.
- Auditoría agrega `released_future_assignments` y `retained_active_assignments`.
- Acciones nuevas: `RETURN_TO_COORDINATION` y `REASSIGNMENT_FROM_COORDINATION`.

## No cambia
- SIC: COM continúa SoR de Servicio/Cliente/Punto/Puestos.
- COM v1.1.3 continúa FROZEN.
- Kaibil nunca opera Servicios de clientes.
