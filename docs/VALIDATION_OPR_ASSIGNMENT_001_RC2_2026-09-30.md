# Validación OPR-ASSIGNMENT-001 — RC2

Fecha: 2026-09-30  
Componente: SGI_COM backend  
Consumidor: SGI_OPR móvil  
Baseline de origen: `SGI_COM_BIT_INT_001_RC1_2026-09-29`

## Regla validada

- Se usa la fecha y hora del servidor SGI_COM como fuente de verdad.
- Una asignación es vigente cuando `inicio <= ahora < fin`.
- Si existe una asignación vigente, esta prevalece sobre asignaciones próximas.
- Si no existe una vigente, se permite la asignación próxima desde exactamente 59 minutos antes.
- Una asignación a más de 59 minutos no habilita el acceso.
- Dos asignaciones vigentes superpuestas siguen siendo una ambigüedad y no se seleccionan silenciosamente.

## Evidencia automatizada

- `OperatorAssignmentWindowTest`: 6 pruebas aprobadas.
- `ReliefContractTest`: 6 pruebas aprobadas.
- Total focalizado: 12 aprobadas, 0 fallidas, 0 errores.
- Build del backend en imagen Docker: aprobado.

## Evidencia integrada UAT

- Backend desplegado en el ambiente UAT local, puerto `18080`.
- Salud del servicio: `UP`.
- Conexión PostgreSQL: `UP`.
- Caso de datos `alex.chiriboga`: el turno vencido del 2026-09-29 se descarta y el turno vigente del 2026-09-30 se selecciona.

## Limitación del entorno local

La suite completa ejecutada directamente con el JDK local reporta un error de arranque en `HealthTest` por falta de credencial PostgreSQL y parámetros de apertura del JDK 25. No corresponde a un fallo de la regla implementada: las pruebas focalizadas pasan y el backend integrado en contenedor inicia con base de datos saludable.

## Pendiente UAT

Confirmar desde SGI_OPR, con el dispositivo en línea, que `alex.chiriboga` inicia sesión y recibe una única asignación válida. Esta confirmación completa el recorrido extremo a extremo; no requiere modificar la APK para esta corrección.

## Rollback

Detener la instancia RC2 y volver a desplegar la imagen de `SGI_COM_BIT_INT_001_RC1_2026-09-29`. No hay migraciones de base de datos que revertir.
