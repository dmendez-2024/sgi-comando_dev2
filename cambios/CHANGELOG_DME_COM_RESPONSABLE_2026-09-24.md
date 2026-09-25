# Cambio DME — Responsable operacional de Compañía

## Fecha

2026-09-24

## Alcance

- Opción: Operaciones → Compañías → Editar Compañía.
- Se agrega un combo opcional Responsable, cuyo valor inicial es Sin asignar.
- El combo muestra únicamente el nombre de la persona, igual que en Zona y Región; el cargo no se concatena al texto visible y se conserva solo para filtrar y validar la elegibilidad.
- Se consideran únicamente empleados de la misma Instancia–País, laboralmente activos, dentro del alcance visible y cuyo `role_code` contenga `Coordinador`, `Jefe`, `Director` o `Presidente`, sin distinguir mayúsculas y minúsculas.
- La validación se ejecuta tanto al consultar el combo como al guardar para impedir el envío directo de empleados no elegibles.
- La elección no cambia la Compañía laboral ni la membresía del empleado.
- Cada guardado mantiene el versionamiento COM y registra el identificador, nombre y cargo del Responsable en el snapshot histórico.

## Base de datos

- Migración: `V28__company_responsible.sql`.
- Campo nuevo: `company.responsible_employee_id UUID DEFAULT NULL`.
- No se realiza carga retroactiva: todas las Compañías existentes permanecen Sin asignar hasta que un usuario autorizado las edite.

## API

- Nuevo catálogo: `GET /api/companies/responsibles`.
- `PUT /api/companies/{id}` admite `responsibleEmployeeId` como UUID o `null`.
- La respuesta de Compañía incorpora `responsibleEmployeeId`, `responsibleName` y `responsibleRoleCode`.

## Archivos modificados

- `backend/src/main/java/com/cajamarca/sgi/comando/companies/Company.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/companies/CompanyResource.java`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/db/migration/V28__company_responsible.sql`
- `backend/src/test/java/com/cajamarca/sgi/comando/companies/CompanyResponsibleRoleTest.java`
- `frontend/src/api.ts`
- `frontend/src/pages/Companies.tsx`
- Documentación de la vertical COM en `docs/verticals/COM`.

## Verificación ejecutada

- Compilación del frontend TypeScript/Vite: correcta.
- Compilación del backend Java 25/Quarkus: correcta.
- Suite Java: 5 pruebas ejecutadas, 0 fallos y 0 errores.
- Flyway validó 28 migraciones y aplicó correctamente `V28` sobre una base que se encontraba en `V27`.
- Salud del backend y frontend desplegados: HTTP 200; base de datos `UP`.
- Catálogo real: de 16 empleados activos se devolvieron únicamente los 3 que cumplen la regla actual: Director, Coordinador y Jefe.
- Prueba funcional temporal: un Agente fue rechazado con HTTP 400; un Coordinador fue guardado, devolvió la versión 2 y generó `RESPONSIBLE_UPDATED` en historial.
- La Compañía temporal, su relación regional y su historial fueron eliminados; la consulta de control devolvió 0 registros restantes.

## Reversa

- La aplicación puede dejar de utilizar el campo sin afectar las Compañías existentes.
- La eliminación física de la columna requiere una migración posterior explícita; no debe revertirse manualmente una migración Flyway ya aplicada.
