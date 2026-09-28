# Sincronización SIC: RRHH → SGI: Comando y cargos asignables

**Fecha:** 2026-09-23  
**Estado:** Implementado sin commit, pendiente de UAT integrada  
**Módulos:** Integraciones RRHH y ASI/Asignaciones  
**Contrato:** `SIC_RRHH_SGI_COM_0001_v001` / `SIC_RRHH_SGI_COM_0001_IF01`

## Objetivo

Recibir desde SIC: RRHH los empleados activos de Seguridad Física que el usuario sincroniza desde DHO, llenar o actualizar `employee_operational_snapshot` y su membresía primaria, y limitar el pool de Asignaciones a los cargos operacionales acordados.

## Alcance funcional

- DHO conserva el endpoint manual legacy `sgi/sincronizar/empleado/informacion` y la sincronización masiva sin cambios. En el flujo real `rrhh/empresa/persona/tiempo/laborado/activation` se deja comentada la invocación anterior y, después de activar la relación laboral, se delega al método nuevo para SGI: Comando.
- DHO exige relación laboral activa, cargo vigente con nombre y `departamento_id=4`; no mantiene una lista fija de cargos y envía el nombre vigente desde su tabla maestra.
- El request contiene solo `employeeId` (origen `personas.id`), `fullName`, `roleCode`, `employmentStatus` y `updatedFromSourceAt`.
- SGI genera un UUID interno estable a partir del tenant y `personas.id`.
- Un empleado nuevo se registra inicialmente en Kaibil. Un empleado ya existente conserva su Compañía actual para no revertir transferencias.
- No se crean datos de habilidades, vacaciones, permisos, indisponibilidades, fotografía, turno preferido ni ID/SMC.
- El frontend representa como `—` el ID/SMC que llega nulo y no intenta calcular su tendencia hasta disponer del dato.
- Eventos anteriores a `updated_from_source_at` se aceptan sin sobrescribir el snapshot más reciente.

## Cargos recibidos

SGI recibe cualquier cargo activo que DHO haya validado como perteneciente a Seguridad Física. Los 14 nombres informados inicialmente quedan como referencia del catálogo actual, no como una lista fija en el código de sincronización: Escolta Líder; Supervisor de Seguridad CL; Coordinador de Compañia; Jefe de Operaciones; Asistente de Logistica; Agente de Consola de Monitoreo Senior; Director Nacional de Operaciones; Pasante de Asignaciones; Agente de Seguridad; Supervisor de Seguridad; Analista de Inteligencia; Escolta Líder JT; Coordinador de Compañía CL; Responsable de Punto.

Solo son asignables: Agente de Seguridad; Agente de Consola de Monitoreo Senior; Supervisor de Seguridad; Supervisor de Seguridad CL; Escolta Líder; Escolta Líder JT; Responsable de Punto.

## Configuración

- SGI exige `SGI_RRHH_INBOUND_TOKEN`; con valor vacío el endpoint inbound permanece cerrado.
- DHO usa `DHO_SGI_COMANDO_USE_CORE=false` para URL directa y `true` para resolver en CORE.
- En modo directo DHO requiere `DHO_SGI_COMANDO_DIRECT_URL`.
- En modo CORE DHO requiere `DHO_SGI_COMANDO_CORE_RESOLVER_URL`, `DHO_SGI_COMANDO_INSTANCE_COUNTRY_ID` y opcionalmente `DHO_SGI_COMANDO_ENVIRONMENT` (UAT por defecto).
- DHO exige `DHO_SGI_COMANDO_SERVICE_TOKEN` en ambos modos.
- No se versionan valores efectivos de tokens ni URLs físicas nuevas.
- Para UAT local, el token efectivo se carga desde el archivo `.env` excluido de Git; DHO recibe el mismo valor mediante variables de entorno y apunta al backend local de SGI:Comando, no al frontend.

## Archivos de SGI: Comando

- `backend/src/main/java/com/cajamarca/sgi/comando/rrhh/RrhhEmployeeEventResource.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/rrhh/RrhhEmployeeSyncService.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/assignments/AssignmentRolePolicy.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/assignments/AssignmentResource.java`
- `backend/src/main/java/com/cajamarca/sgi/comando/interconnections/InterconnectionIds.java`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/interconnections/sgi-comando-catalog.json`
- `backend/src/test/java/com/cajamarca/sgi/comando/AssignmentRolePolicyTest.java`
- `frontend/src/api.ts`
- `frontend/src/pages/Assignments.tsx`
- `.env.example` y `docker-compose.yml`
- Catálogo API, catálogo de interconexiones, decisiones, integraciones y changelog técnico.

## Verificación ejecutada

- API DHO: `mvn -DskipTests compile` con JDK 8 — `BUILD SUCCESS`.
- Backend SGI: construcción Docker con Maven/Temurin 25 — `BUILD SUCCESS`.
- Backend SGI: 3 pruebas ejecutadas (salud y política de cargos), 0 fallos y 0 errores.
- Endpoint SGI local: evento válido aceptado y solicitud sin credencial rechazada con `401`.
- Tras retirar el catálogo fijo, un cargo nuevo validado por DHO como Seguridad Física fue aceptado con `200`, persistido con su nombre original y excluido del pool de Asignaciones por no ser asignable.
- Persistencia local comprobada: snapshot activo y membresía `PRIMARY` en Kaibil; campos no disponibles permanecen nulos.
- Cliente DHO real ejecutado con JDK 8 en modo directo contra SGI local: respuesta `accepted=true`.
- Imagen final del backend recreada sin la credencial temporal: salud `UP`, conexión a base de datos `UP` y 27 migraciones validadas.
- Con el token sin configurar, el endpoint inbound respondió `503` y no persistió el evento, manteniendo la integración cerrada por defecto.
- Los dos empleados ficticios y sus membresías creados para las pruebas fueron eliminados; la consulta de control devolvió cero registros.
- El empleado ficticio usado para validar el cargo dinámico y su membresía también fueron eliminados; la consulta final devolvió cero registros.
- Los endpoints existentes de roles y personal de Asignaciones respondieron `200` después del reinicio; el contenedor frontend existente respondió `200`.
- Frontend: la construcción no pudo completarse porque el acceso local a `registry.npmjs.org` terminó en `ECONNRESET`; queda pendiente repetir `npm run build` cuando el registro esté disponible.
- `git diff --check` de SGI sin errores de espacios; DHO conserva los finales CRLF del repositorio y Git los reporta en las líneas nuevas de configuración.
- No se usaron credenciales reales: las pruebas de integración utilizaron una credencial temporal no versionada.

## Riesgos y controles

- La asignación inicial a Kaibil contradice temporalmente ASI-DEC-057; ASI-DEC-059 documenta la excepción y exige retirarla cuando RRHH entregue la adscripción real.
- Si `departamento_id=4` cambia en DHO, la sincronización rechazará empleados hasta actualizar el contrato/configuración.
- La identidad interna depende de que `personas.id` sea estable dentro del mismo tenant.
- Los empleados sincronizados sin ID ni habilidades pueden asignarse, pero conservarán las alertas existentes de compatibilidad en lugar de inventar valores.
- No hay cambio de esquema ni migración de base de datos.

## Reversa

Los cambios están sin commit. Para una reversa selectiva deben restaurarse únicamente los archivos enumerados y eliminarse los tres archivos Java nuevos de SGI y los tres archivos Java nuevos de DHO. No debe usarse un reset destructivo porque el repositorio contiene cambios previos del usuario. Referencias previas observadas: SGI `dcaf21b094c0ec1d23d8a1b3c8d1ec6702360522`; DHO `edc4bc733f1f2f557cee6cb0197c1702b8ad7e1d`.
