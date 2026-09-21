# SGI-07 — Relevos v0.1 (En diseño)

## Propósito
Convertir la planificación publicada en evidencia operacional de quién realmente entregó y recibió un Puesto, a qué hora y bajo qué condiciones.

## Principio
- SGI-06 responde: quién debía trabajar.
- SGI-07 responde: quién realmente trabajó.
- El Relevo es la fuente de verdad de asistencia operacional.

## Canales de ejecución
- SGI: Agente (app móvil).
- SGI: Operador.

## Datos mínimos del Relevo
- `relief_id`
- `instance_country_id`
- `company_id`
- `point_id`
- `post_id`
- `assignment_id`
- AdS saliente
- AdS entrante planificado
- AdS entrante real
- hora programada
- hora real de inicio
- hora real de salida
- fotografía de rostro
- resultado de validación facial IA
- dispositivo/canal
- estado del Relevo
- observaciones/incidencias
- auditoría

## Resultados operacionales esperados
- Relevo normal.
- Tardanza.
- Falto/no presentación.
- Reasignación.
- Cobertura parcial del turno siguiente.
- Puesto sin relevo.

## No auto-relevo
No se permite convertir al AdS saliente en asignado del turno completo siguiente.
Excepción: extensión parcial en el mismo Puesto por máximo 3 horas mientras llega el relevo/reasignación.

## Integración con SIC: RRHH
Hechos operacionales confirmados pueden generar:
- solicitud/evento de descuento por falto;
- solicitud/evento de pago de día extra al AdS que cubrió por Reasignación;
- solicitud/evento de descuento por tardanza;
- solicitud/evento de bonificación por cobertura parcial.

SGI reporta el hecho; SIC: RRHH procesa la consecuencia laboral/nómina.

## Integración con SMC
El cumplimiento derivado de la ejecución y tareas puede alimentar el flujo SGI: Operador → SGI: Comando → SMC.

## Interfaces operativas
### Estado actual
Actualmente existen interfaces separadas:
- SGI: Agente.
- SGI: Supervisor.

### Objetivo futuro
A futuro se unificarán en una nueva aplicación móvil:
- SGI: Operador.

El diseño de SGI: Comando debe desacoplar la lógica de negocio del canal para que el mismo contrato de Relevos pueda ser consumido por las interfaces actuales y posteriormente por SGI: Operador sin rediseñar el dominio.

## Confirmación previa de asistencia
A futuro, aproximadamente T-90 minutos antes del inicio del turno, SGI solicitará confirmación de asistencia a los AdS entrantes mediante:
- IVR / llamada automática.
- Cajamarca Conmigo.

La confirmación previa representa intención declarada, no presencia real.

### Estados recomendados de confirmación
Se modelan separados del estado del Relevo:
- `PENDIENTE`: aún no se ha solicitado/obtenido respuesta.
- `POR_CONFIRMAR`: ventana de confirmación abierta (ej. T-90).
- `CONFIRMADO`: el AdS indicó que sí asistirá.
- `NO_ASISTIRA`: el AdS indicó que no podrá asistir.
- `SIN_RESPUESTA`: agotados los intentos configurados sin respuesta.

Una respuesta `CONFIRMADO` NO equivale a asistencia. La presencia real solo nace con el Relevo ejecutado.

### Estado del Relevo
- `PENDIENTE`
- `EJECUTADO`
- `EJECUTADO_TARDIO`
- `EJECUTADO_REASIGNADO`
- `UNILATERAL`
- `SIN_RELEVO`

En UI puede mostrarse el ciclo simplificado:
`Planificado → Por confirmar → Confirmado → Ejecutado`.

## Política de tardanza
La tolerancia de tardanza es parámetro de Instancia–País.
- Default: 0 minutos.
- Se registra siempre hora programada y hora real.
- La política configurable determina desde qué diferencia se clasifica el evento como tardanza.

## Relevo bilateral
En un Relevo normal se valida tanto al AdS saliente como al entrante.

### Bloque 1 — Agentes
Para ambos:
- identificación;
- fotografía/rostro;
- validación facial IA;
- verificación visual de uniforme según estándar aplicable.

El Relevo conserva evidencia separada de saliente y entrante.

## Relevo unilateral
Si el saliente no está disponible, el entrante puede formalizar la toma del Puesto mediante Relevo unilateral validado por SGI: Operador.
- No se bloquea la continuidad operacional.
- Queda marcado como anomalía.
- Requiere motivo y trazabilidad.
- Puede alimentar auditoría/KPI/incidente según reglas futuras.

## Relevo de Puesto
El Relevo verifica tres bloques transaccionales:

### 1. Agentes
- rostro;
- uniforme;
- saliente;
- entrante.

### 2. Puesto / Inventario
- inventario asignado al Puesto;
- fuente de activos Cajamarca: SIC: RRMM;
- se compara lo esperado vs lo entregado/recibido;
- diferencias generan novedad/excepción.

El modelo debe admitir también activos de cliente cuando existan, manteniendo clara su fuente/propiedad.

### 3. Consignas
- lectura de consignas activas aplicables al Puesto;
- confirmación de recepción/comprensión por el entrante;
- trazabilidad de versión de consigna vigente al momento del Relevo.

## Fuente de verdad
- Confirmación previa = intención de asistir.
- Asignación = quién debía trabajar.
- Relevo ejecutado = quién realmente tomó/entregó el Puesto.

## Validación de uniforme
La validación de uniforme forma parte del bloque `Agentes` del Relevo.

Campo requerido desde v0.1:
- `validation_method = MANUAL | AI`

El diseño debe permitir evolucionar de revisión humana a validación automática sin cambiar el modelo de dominio.

Datos mínimos por validación:
- `employee_id`
- `relief_id`
- `validation_method`
- `validation_result`
- `evidence_photo_ref`
- `validated_at`
- `validated_by` o `model/service_ref` según el método
- observaciones cuando aplique

## Excepciones por inventario crítico
Si existe discrepancia entre inventario esperado y recibido:
- el Relevo no se bloquea automáticamente;
- se registra la discrepancia;
- se clasifica criticidad;
- si el elemento es crítico, se requiere autorización explícita de excepción por un usuario con permiso correspondiente;
- el Relevo puede continuar;
- se genera alerta crítica y trazabilidad completa.

La excepción debe conservar como mínimo:
- activo/material esperado;
- resultado encontrado;
- criticidad;
- motivo;
- autorizador;
- fecha/hora;
- evidencia/observación;
- vínculo con el Relevo.

## Confirmación de Consignas
En cada Relevo, el AdS entrante confirma todas las Consignas vigentes del Puesto.

La UI debe ordenar/priorizar:
1. Consignas nuevas desde la última confirmación del AdS en ese Puesto.
2. Consignas modificadas desde la última confirmación.
3. Consignas vigentes sin cambios.

Por cada confirmación:
- `consignment_id`
- `consignment_version`
- `employee_id`
- `post_id`
- `relief_id`
- `confirmed_at`

La práctica esperada es de hasta aproximadamente 10 Consignas por Puesto, pero no se establece un límite técnico rígido en v0.1.
