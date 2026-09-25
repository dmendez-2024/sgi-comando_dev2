# Puente de identidad DHO → SGI: Comando

**Fecha:** 2026-09-24  
**Estado:** Implementado y verificado en SGI; redespliegue local DHO pendiente de credencial GlassFish  
**Módulos:** Integraciones RRHH y ASI/Asignaciones  
**Contrato:** `SIC_RRHH_SGI_COM_0001_v001` / `SIC_RRHH_SGI_COM_0001_IF01`

## Objetivo

Conservar el identificador numérico `personas.id` del DHO actual en
`employee_operational_snapshot.persona_id` sin cambiar el UUID operacional que SGI: Comando
ya utiliza en `employee_id`, y dejar preparado un modo independiente para recibir el UUID
canónico de una fuente futura.

## Cambios funcionales

- Se agrega `persona_id bigint NULL` a `employee_operational_snapshot`.
- La unicidad de `persona_id` se controla por `instance_country_id` únicamente cuando el
  valor no es nulo.
- El request admite `personaId` y `canonicalEmployeeId` como extensiones opcionales de v1.
- `employeeId` numérico permanece como alias legacy de `personas.id`; si llega junto con
  `personaId`, ambos valores deben coincidir.
- Con `SGI_RRHH_USE_CANONICAL_EMPLOYEE_ID=false`, SGI deriva el mismo UUID interno que
  utilizaba antes y guarda además `personaId`.
- Con `SGI_RRHH_USE_CANONICAL_EMPLOYEE_ID=true`, SGI exige `canonicalEmployeeId` UUID.
- Si `personaId` ya está ligado a otro `employee_id`, el evento se rechaza con un mensaje
  explícito y no se reescriben automáticamente las relaciones de Asignaciones.
- El DHO actual envía `personas.id` en los dos campos numéricos para mantener compatibilidad
  con receptores anteriores y llenar el nuevo campo en SGI.

## Compatibilidad y datos existentes

- La bandera queda en `false` por defecto y es independiente de
  `DHO_SGI_COMANDO_USE_CORE`.
- Los snapshots existentes no se actualizan en bloque: después de V29 conservan su
  `employee_id` y quedan con `persona_id=NULL` hasta una nueva sincronización válida.
- Una futura activación del UUID canónico no migra empleados históricos por sí sola. Si un
  `personaId` ya corresponde a un UUID derivado, debe ejecutarse una migración controlada
  de todas las referencias antes de encender la bandera.

## Archivos de SGI: Comando

- `backend/src/main/resources/db/migration/V29__employee_persona_identity_bridge.sql`
- `backend/src/main/java/com/cajamarca/sgi/comando/assignments/EmployeeOperationalSnapshot.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/rrhh/RrhhEmployeeEventResource.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/rrhh/RrhhEmployeeSyncService.java`
- `backend/src/test/java/com/cajamarca/sgi/comando/rrhh/RrhhEmployeeIdentityResolutionTest.java`
- `backend/src/main/resources/application.properties`
- `.env.example` y `docker-compose.yml`
- `docs/API_CATALOG.md`, `docs/verticals/ASI/ASI_INTEGRATIONS.md` y los dos catálogos
  JSON de interconexiones.

## Verificación ejecutada

- API DHO compilada y empaquetada con JDK 8: 886 fuentes, `BUILD SUCCESS`.
- Backend SGI construido con Maven/Temurin 25: `BUILD SUCCESS`.
- Suite SGI: 9 pruebas, 0 fallos y 0 errores; 4 pruebas cubren la resolución legacy,
  `personaId`, UUID canónico y rechazo cuando falta el UUID en modo canónico.
- Flyway validó 29 migraciones y aplicó V29 correctamente.
- La base local conservó 17 snapshots; inmediatamente después de migrar, los 17 mantuvieron
  `persona_id=NULL`.
- Evento local con `employeeId` y `personaId` aceptado; SGI persistió el valor numérico y
  mantuvo el UUID derivado. Las filas ficticias del snapshot y membresía se eliminaron al
  terminar y la consulta de control devolvió cero.
- Backend recreado con `SGI_RRHH_USE_CANONICAL_EMPLOYEE_ID=false` y salud `UP`.
- Los dos catálogos JSON se validaron sintácticamente.
- El WAR DHO nuevo contiene `personaId`; el GlassFish local continúa ejecutando la clase
  anterior porque rechazó el redespliegue por autenticación administrativa. Debe
  redesplegarse el WAR generado con una credencial autorizada antes de validar el flujo
  completo desde la pantalla de DHO.

## Reversa

Mantener la bandera en `false` conserva el comportamiento de identidad anterior. Para
retirar completamente el puente deben revertirse primero el envío y la lectura de los campos
nuevos; la columna e índice de V29 solo deben eliminarse mediante una migración posterior y
después de comprobar que ninguna sincronización haya llenado `persona_id`. No se debe editar
ni borrar una migración Flyway ya aplicada.
