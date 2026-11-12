# Finalizar turno y sincronizar (con conexión)

Contrato v1: POST /api/v1/operator/shift-sync, autenticado como Agente.
Identidad/instancia salen de la sesión; assignmentId se autoriza contra el agente efectivo.
CORE: binding propuesto SGI_OPR_SGI_COM_0037_v001, interfaz SGI_OPR_SGI_COM_0037_IF01,
POST a esta ruta. Requiere registrar/confirmar este binding antes de activar el flujo en UAT.

PREPARE: {action:"PREPARE",assignmentId,deviceId}. Se llama al cargar el turno,
antes de su fin. Guarda configuración aplicable, fechas y responsable. Un turno
preparado en otro dispositivo no se puede cerrar desde este. No se reconstruye
retroactivamente la configuración de un turno que nunca se preparó.

CHECK / COMPLETE: {action:"CHECK"|"COMPLETE",assignmentId,deviceId,allAcknowledged:true,operations:[{kind,id,action,photoCount}]}.
CHECK valida el manifiesto y responde READY sin cerrar ni generar incumplimientos.
La app lo usa antes de sellar, para que una patrulla abierta, evidencia faltante o
un intento antes de terminar el turno no impidan seguir enviando los registros.
La app sella y conserva el manifiesto después de vaciar sus dos colas y resolver
errores/fotos; un envío repetido usa el mismo manifiesto. Comando verifica los IDs,
el turno terminado y la configuración preparada. Una diferencia o una ejecución
de patrulla abierta devuelve 409 y conserva el turno pendiente. Los reintentos
idénticos devuelven el cierre existente sin duplicar incumplimientos.

Respuesta: {status:"PREPARED"|"READY"|"CLOSED",assignmentId,duplicate,patrolsNotExecuted,consignmentsNotDone}.
No hay cierre por temporizador, pérdida de señal, vencimiento de token ni logout
ordinario. Sin conexión o sin recibo CLOSED no se borra la sesión ni se producen
incumplimientos automáticos. El usuario conserva el logout ordinario por separado.

La conciliación genera NO_CUMPLIDA para consignas aplicables sin registro y
omisiones de patrullas programadas sin inicio. Solo estos registros conciliados
se entregan al publicador SMC. Se conserva la hora de ejecución de consignas en
executed_at, distinta de confirmed_at (recepción). VISINT pendiente no equivale a
actividad no realizada. Cambios de configuración o responsable requieren revisión.

No se procesa historial anterior a la preparación ni se corrigen hechos ya
publicados a SMC. La primera versión requiere un único dispositivo por turno.

## Operación y despliegue

- En el perfil del Agente se añade **Finalizar turno y sincronizar**. El logout
  ordinario permanece separado y no significa cierre del turno.
- La aplicación espera hasta 60 segundos los envíos pendientes. Si Android no
  consigue enviarlos en ese intervalo, conserva el turno pendiente y permite
  reintentar; ese límite no genera incumplimientos ni se usa como plazo offline.
- Las colas se revisan por usuario: en esta primera versión un pendiente de otro
  turno también bloquea la finalización hasta resolverlo. No se descarta un
  rechazo, borrador o archivo faltante para hacer que el contador llegue a cero.
- El manifiesto final queda conservado en el dispositivo hasta obtener CLOSED;
  un reingreso intenta recuperar la confirmación anterior sin sustituirla por
  registros nuevos. El borrado de datos/desinstalación no es una sincronización.
- Comando rechaza registros nuevos de patrullas/consignas/evidencias después del
  cierre; una corrección de un turno conciliado necesita un procedimiento de revisión.
- Los hechos de patrullas y consignas de turnos preparados quedan retenidos hasta
  completed_at. Su cursor usa la fecha de liberación si es posterior a la recepción,
  para no perder registros recibidos antes de que termine la conciliación.
- Las consignas realizadas usan executed_at; las omisiones usan occurred_at con
  el fin del turno. confirmed_at conserva la hora de recepción/registro del servidor.
- Se requiere desplegar Comando con V143 (y migraciones anteriores), registrar el
  binding propuesto en CORE e instalar el APK actualizado. No se habilita el cierre
  retroactivo de turnos que no se prepararon. Si CORE no resuelve el binding, el
  ingreso ordinario sigue disponible y el cierre automático no se autoriza.
- No se registró el binding ni se desplegó o instaló esta versión en DEV/teléfono.
  Debe verificarse en UAT el recorrido IDENT/CORE/Operador/Comando/SMC antes de usar
  estos cierres para valoración real.

## Validación local (12/11/2026)

- Backend, JDK 25 y PostgreSQL 17/MinIO temporales: 21 casos aprobados, 0 fallos,
  0 errores. Incluye ShiftSyncDatabaseTest (8), ShiftSyncResourceTest (2),
  SmcPerformanceFactsDatabaseTest (8) y DayOffWorkedResourceTest (3).
- Tras ajustar el límite de horarios con inicio igual al fin, se repitieron los
  10 casos de cierre y permisos: todos aprobados. No se suman nuevamente al total.
- Flyway aplicó las migraciones hasta V143 en una base nueva de pruebas, sin
  repair, y validó nuevamente el esquema en las ejecuciones posteriores.
- Android: `:app:testDebugUnitTest :app:assembleDebug`, BUILD SUCCESSFUL.
  TurnCompletionManifestTest: 4 casos, 0 fallos y 0 errores. Verifica turno sin
  actividades, START/FINISH independientes, fotos con índices no consecutivos
  y rechazo de manifiestos sin contrato/detalle de evidencias.
- Total: 25 casos locales aprobados. No se ejecutaron las suites completas ni
  pruebas instrumentadas en teléfono. La generación del APK no acredita una
  sincronización real a través de CORE/IDENT o una valoración de SMC.
- SMC deshabilitado y bootstrap CORE dirigido a loopback durante estas pruebas.
  No se enviaron hechos a SMC real ni se modificaron datos de DEV.
- APK de depuración: `SGI_Operador_Cierre_Turno_Online_DEBUG.apk`, SHA-256
  `B4C2CEF166DF38C4D1225B410EE919521272544C7E4B0525FE43B21466293A62`.
- Se corrigió puntualmente una declaración local duplicada de `run` en la captura
  GPS de Patrullas que impedía compilar el estado actual de Operador. Se conservaron
  los demás cambios existentes en ambos repositorios.
