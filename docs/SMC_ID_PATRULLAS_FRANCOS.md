# ID: patrullas no ejecutadas y francos trabajados

Este documento describe la ampliación V141. V143 añade el cierre tras confirmación
completa del turno por Operador; ver [CIERRE_TURNO_SINCRONIZADO.md](CIERRE_TURNO_SINCRONIZADO.md).
No se cierra por vencimiento de ventana, falta de conexión ni logout ordinario.

## Contrato y criterio operacional

Comando entrega hechos, no calcula descuentos ni bonificaciones. No cambia el cierre
de consignas ni la política pendiente de sincronización offline.

### Patrullas

- Un `patrol_execution` finalizado con `result=NO_EJECUTADA` produce
  `ROUTE_COMPLETION`, `value=0`, con su empleado y fecha de cierre.
- Una `patrol_occurrence` con `status=NO_EJECUTADA`, sin ejecución y con ventana
  vencida, produce el mismo hecho mediante la fuente `PATROL_NOT_EXECUTED`.
  Se exige una única asignación publicada/cerrada que cubra toda la ventana en el
  puesto del plan. No se atribuye a un empleado si hay varias asignaciones candidatas.
- Se excluye una ocurrencia si tiene ejecución vinculada o una ejecución del mismo
  plan/definición y asignación iniciada en su ventana. Una ejecución abierta no es
  una patrulla no iniciada. Solo aplica a patrullas cerradas y programadas.
- Esta integración consume cierres operacionales explícitos. No infiere omisiones
  de la ausencia de tráfico del teléfono ni genera automáticamente ocurrencias o
  cierres por vencimiento; la captura offline puede llegar después.
- El eventId es estable por origen y el cursor usa la fecha de actualización local.

### Acreditación de franco trabajado

No hay fuente oficial de francos/descansos en el modelo actual. Se incorpora una
acreditación administrativa explícita, no una inferencia desde huecos del calendario
ni desde contadores DEMO. El aprobador verifica ambos documentos antes de registrar:
la referencia al descanso previsto y la referencia que acredita trabajo efectivo.
El backend comprueba turno finalizado, plan publicado/cerrado, agente efectivo y
fecha local; no verifica automáticamente el contenido de documentos externos.

`POST /api/v1/integrations/smc/day-off-worked`

Autenticación de Comando. Roles: PRESIDENTE, GERENCIA_GENERAL,
DIRECTOR_OPERACIONES_LATAM, DIRECTOR_OPERACIONES_NACIONAL o DIRECTOR_NACIONAL.
Un token de otra instancia se rechaza. No se aceptan employeeId ni tenant del cliente.

```json
{
  "assignmentId": "<UUID de asignación finalizada>",
  "restDate": "2026-11-10",
  "restEvidenceRef": "RRHH:descanso-aprobado-123",
  "workEvidenceRef": "OPERACIONES:asistencia-validada-456",
  "reason": "Se acredita descanso previsto trabajado con asistencia verificada."
}
```

`restDate` es la fecha local del inicio del turno según zona de la instancia
(fallback America/Guayaquil). Un turno nocturno se acredita en su día de inicio.
Las referencias y motivo son obligatorios (máximo 500, 500 y 2000 caracteres).
Una acreditación por empleado/fecha de descanso: dos turnos del mismo día no
generan dos bonificaciones. Para descansos que no correspondan a esta convención
se debe acordar otro contrato antes de registrarlos.

201: `{id, status: "ACCREDITED", duplicate: false}`. Repetir el mismo cuerpo devuelve
200 y el mismo id (`duplicate: true`). Contenido distinto para el mismo agente/día
devuelve 409. Datos inválidos/turno futuro o no finalizado: 400. Asignación fuera de
instancia: 404. Plan no publicado, asignación removida: 400. Sin sesión: 401; rol
no permitido o instancia del token distinta: 403.

`GET /api/v1/integrations/smc/day-off-worked/{id}` devuelve el registro auditado
de la instancia actual (mismos permisos). No permite edición/borrado silencioso.
El registro conserva empleado, fecha, turno, puesto/punto, referencias, aprobador,
momento de acreditación y hash del contenido. Correcciones posteriores requieren
un procedimiento acordado con SMC; no se cambia el payload de un hecho ya publicado.

El publicador existente descubre la fuente `DAY_OFF_WORKED` y envía value=1;
occurredAt es el fin del turno acreditado, changedAt es la acreditación. Inicializar
el cursor antes de generar pruebas: la primera ejecución sin FACTS_SINCE no importa
historial previo. El endpoint acredita pero no envía HTTP en su transacción.

## Pruebas de aceptación

1. Patrulla NO_EJECUTADA finalizada: valor 0; repetir no duplica.
2. Ocurrencia NO_EJECUTADA sin inicio y con único responsable: valor 0 y contexto correcto.
3. Ocurrencia con inicio, ventana futura, responsable ambiguo o tenant distinto: excluida.
4. Patrulla completa/incompleta conserva su porcentaje; espontánea no genera demérito de programada.
5. Acreditar descanso trabajado con turno válido: un registro auditado y hecho con valor 1.
6. Repetir cuerpo: mismo id; cambiar referencia/motivo o segundo turno del mismo día: 409.
7. Turno futuro, sin empleado, removido, fuera de instancia o fecha distinta: rechazo sin alta.
8. Verificar permisos, paginación de cursor con fechas iguales y payload estable en bandeja.
9. En UAT confirmar empleado/cargo/período, recibo SENT y valoración en SMC real.

Aplicar V141 en el ambiente de pruebas antes de probar. No reiniciar cursores ni
aplicar cargas históricas implícitas. Las referencias usadas en ejemplos no son
documentos válidos de producción.

## Validación realizada (11/11/2026)

- JDK 25 / Maven 3.9.11, PostgreSQL 17 y MinIO temporales, separados de DEV.
- Flyway aplicó el esquema hasta V141 desde una base nueva y lo validó nuevamente.
- `mvn -B -ntp -Dtest=SmcPerformanceFactsDatabaseTest,SmcImpulseFactsDatabaseTest,SmcApprovalFactsDatabaseTest test`:
  18 pruebas, 0 fallos y 0 errores. Incluye los ocho casos nuevos de datos,
  fuentes existentes y transporte HTTP local con reintento 503/201.
- `mvn -B -ntp -Dtest=DayOffWorkedResourceTest test`: 3 pruebas, 0 fallos y
  0 errores. Cuentas aleatorias propias de la prueba, eliminadas al terminar;
  comprueba 401, 403 y acceso nacional al contrato (400/404).
- Total: 21 pruebas aprobadas. No se ejecutó toda la suite del proyecto.
- `SGI_SMC_ENABLED=false` en el entorno de pruebas; el harness HTTP usa destinos
  y credenciales sintéticos. No se enviaron hechos a SMC real.
- El arranque intentó reconciliar CORE, no obtuvo un contrato válido y conservó
  el contexto local. Estas pruebas no acreditan el binding real de CORE ni el
  reconocimiento/valoración del hecho en SMC.
- Postman CLI no pudo iniciar por un manifiesto corporativo Private Cloud
  inaccesible. Se usaron las pruebas de backend y su harness HTTP existente.
- No se aplicó V141 ni se desplegó backend en DEV. Retirados los contenedores
  temporales al finalizar.
