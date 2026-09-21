# SGI: Comando — SER v0.9 UAT

**Fecha:** 2026-09-19

## Objetivo
Incorporar la asignación operacional inicial de Servicios recibidos desde SIC: COM, manteniendo a SIC: COM como SoR comercial y a SGI: Comando como responsable de decidir la Compañía operativa.

## Flujo
`SIC: COM → SGI: Comando → bandeja lógica Kaibil → Compañía operativa → Configuración del Punto`.

## Cambios funcionales
- Servicio nuevo sin Compañía aparece como **Kaibil / Pendiente de asignación**.
- No se permite Configuración mientras esté pendiente.
- La acción visible es **Asignación**.
- Presidencia/Director Nacional pueden asignar nacionalmente; Director Zonal por Zona; Jefe Regional por Región.
- Coordinador/Asistente no realizan esta asignación inicial.
- Kaibil no puede seleccionarse como Compañía destino de un Servicio de cliente.
- Al asignar se registra auditoría y se habilita la Configuración normal de SER.

## Persistencia
- `service.source_system/source_version`.
- `point.operational_assignment_status`, `received_from_sic_com_at`, `assigned_by_username`, `assigned_at`.
- `service_company_assignment_event` para auditoría de asignación inicial.
- Migración: `V19__ser_service_company_assignment.sql`.

## UAT fixture
V19 incorpora un Servicio/Punto UAT pendiente proveniente de SIC: COM para poder probar el flujo de asignación sin requerir todavía el conector productivo.

## Fuera de alcance
Mover posteriormente un Servicio ya asignado entre Compañías no forma parte de esta versión.
