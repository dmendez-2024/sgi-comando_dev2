# CR — SGI_COM BIT-POINT-ACCESS-001

- Fecha: 2026-10-10
- Programa: `SGI_COM`
- Vertical: Bitácora (`SGI-09`)
- Estado: `IMPLEMENTADO / PENDIENTE DE HOMOLOGACIÓN CORE Y UAT`
- Clasificación: `REQUIERE APROBACIÓN`

## Objetivo

Permitir que SGI OPERADOR consulte los ingresos activos del Punto durante una ventana de 48 horas y registre su salida conservando el ingreso, la fecha, el actor y la trazabilidad completa.

## Alcance

1. Los registros nuevos de Bitácora quedan con estado `INSIDE`.
2. Cada Punto mantiene una versión incremental independiente.
3. La carga inicial devuelve únicamente registros `INSIDE` de las últimas 48 horas.
4. Las consultas posteriores pueden solicitar cambios con `afterVersion`.
5. Registrar una salida actualiza el registro a `EXITED`; nunca elimina el ingreso.
6. Se conservan `exitedAt`, empleado y usuario que registraron la salida.
7. `expectedVersion` evita cierres concurrentes incompatibles.
8. `exitOperationId` e `Idempotency-Key` hacen seguro el reintento offline.
9. Operaciones / Bitácora muestra Ingreso, Salida, campos dinámicos y línea de tiempo.

## Contratos

### Consulta activa e incremental

- Interconexión: `SGI_OPR_SGI_COM_0020_v001`.
- Interfaz: `SGI_OPR_SGI_COM_0020_IF01`.
- Método: `GET`.
- Ruta: `/api/v1/operator/logbook-records/active`.
- Query: `assignmentId` obligatorio y `afterVersion` opcional.

### Registro de salida

- Interconexión: `SGI_OPR_SGI_COM_0021_v001`.
- Interfaz: `SGI_OPR_SGI_COM_0021_IF01`.
- Método: `POST`.
- Ruta: `/api/v1/operator/logbook-records/{recordId}/exit`.
- Idempotencia: `Idempotency-Key` debe coincidir con `exitOperationId`.

Los identificadores deberán ser confirmados por CORE antes de producción.

## Datos

La migración `V74__operator_logbook_presence_sync.sql` añade datos de salida, actor, operación idempotente, versión del registro y versión del Punto. Los registros históricos `REGISTERED` se homologan a `INSIDE` sin perder contenido.

## Seguridad y aislamiento

- SGI_COM deriva tenant, empleado, usuario y Punto desde la identidad y la asignación autenticadas.
- No se permite cerrar un acceso perteneciente a otro Punto.
- El cliente no declara el actor de salida.
- El usuario demo `agente` no consume estos contratos; su persistencia permanece local en SGI OPERADOR.

## Rollback

Deshabilitar las interfaces `0020/0021` en CORE y retirar su consumo desde SGI OPERADOR. La migración es aditiva y los datos de ingreso/salida deben conservarse para auditoría.
