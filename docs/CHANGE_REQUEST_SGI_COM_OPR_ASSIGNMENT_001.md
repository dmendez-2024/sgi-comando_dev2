# CR-SGI_COM-OPR-ASSIGNMENT-001

**Estado:** APROBADO PARA RC UAT  
**Fecha:** 2026-09-30  
**Programa:** `SGI_COM`  
**Vertical:** Integración SGI Operador / selección temporal de asignación  
**Baseline:** `SGI_COM_BIT_INT_001_RC1_2026-09-29`  
**RC resultante:** `SGI_COM_OPR_ASSIGNMENT_RC2_2026-09-30`

## Problema

`GET /api/v1/operator/runtime` devolvía asignaciones dentro de una ventana simétrica de 24 horas. Esto mezclaba turnos vencidos, vigentes y futuros; SGI Operador recibía más de una opción y bloqueaba el acceso por ambigüedad.

## Decisión aprobada

1. Una asignación en curso es elegible cuando `startsAt <= now < endsAt`.
2. Si no existe turno en curso, se permite acceso online al próximo turno desde exactamente 59 minutos antes de `startsAt`.
3. Un turno en curso prevalece sobre cualquier turno próximo dentro de la ventana anticipada.
4. Entre varios turnos próximos se elige el de inicio más cercano.
5. Dos asignaciones en curso, o dos próximas con el mismo inicio, se conservan como ambigüedad y SGI Operador bloquea el acceso para que se corrija la planificación.
6. El cálculo usa instantes autoritativos del backend; no depende de la hora manipulable del dispositivo.
7. La ventana se valida tanto al listar asignaciones como al solicitar el contexto por `assignmentId`; un identificador histórico no evita el control temporal.
8. La base de datos descarta turnos vencidos y futuros fuera de ventana antes de materializar el contexto operacional.

## Clasificación

`REQUIERE APROBACION`: ajusta una regla de acceso operacional. La solicitud expresa del responsable funcional en esta intervención autoriza su implementación únicamente en RC UAT. No modifica la baseline congelada.

## Integraciones y SoR

- No crea una nueva interconexión ni cambia la topología.
- SGI_COM continúa como System of Record de asignaciones y turnos.
- SGI_OPR consume la selección mediante `SGI_OPR_SGI_COM_0001_v001 / IF01`.
- Sin hosts, credenciales, puertos ni secretos nuevos.

## Compatibilidad y rollback

- El campo existente `assignments` conserva su forma de arreglo.
- Se agregan los campos compatibles `endsAt` y `accessMode`.
- Rollback: volver al `OperatorResource` de RC1; no existe migración de base de datos.
