# Nómina dual SGI antiguo + SGI Comando — 2026-10-07

## SGI Comando

- Se agregó el endpoint de solo lectura `POST /api/v1/inbound/sic-rrhh/payroll-shifts/query`.
- La consulta expone únicamente asignaciones publicadas y devuelve turno, persona, RUC, tier y rotación.
- Se agregó `client.tax_identifier` mediante Flyway `V68` y soporte opcional de `client.taxIdentifier` en el catálogo comercial.
- Se agregó configuración separada para la interfaz CORE de nómina.

## DHO

- El proceso de pago consulta SGI antiguo y SGI Comando para todos los empleados elegibles y el período dinámico de la cabecera.
- SGI Comando tiene prioridad por empleado, RUC y día cuando contiene turnos; el SGI antiguo cubre solamente los días sin información de Comando.
- Los turnos de Comando reproducen la matriz vigente `h0/h25/h50/h100` del SGI antiguo.
- Una falla técnica de cualquiera de las fuentes detiene el proceso y conserva los resultados anteriores.
- No se agregaron tablas ni campos a DHO y no se modificó el SGI antiguo.

## Configuración pendiente por ambiente

- Registrar en CORE el contrato POST de consulta de nómina.
- Configurar `DHO_SGI_COMANDO_PAYROLL_INTERCONNECTION_ID` en DHO.
- La consulta utiliza el identificador canónico `SIC_DHO_SGI_COM_0003_v001`; no requiere una variable de ambiente para el ID.
- Cargar `client.tax_identifier` para los clientes que tengan asignaciones utilizadas en nómina.
