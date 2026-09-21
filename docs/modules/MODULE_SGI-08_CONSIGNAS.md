# SGI-08 — Consignas v0.1 (En diseño)

## Propósito
Administrar instrucciones operacionales vigentes aplicables a un Punto o Puesto, versionarlas, programar su vigencia/frecuencia, distribuirlas a las interfaces operativas y obtener evidencia de lectura/comprensión/recepción.

## Antecedentes conservados
- En el diseño histórico de REGESEP, las Consignas se configuran a nivel de Puesto y contemplan frecuencia tipo calendario.
- El Relevo incluye lectura de Consignas y confirmación de comprensión/recepción.
- En cada Relevo el AdS entrante debe confirmar todas las Consignas vigentes; nuevas/modificadas se priorizan visualmente.

## System of Record
SGI: Comando es System of Record de Consignas operacionales.
SIC: COM no crea Consignas.
ATS puede aportar contexto técnico al futuro REGESEP, pero no administra Consignas.
REGESEP podrá incorporar/referenciar Consignas normativas de Punto/Puesto.

## Alcance inicial
Objetivos de Consigna en v0.1:
- Punto.
- Puesto.

Una Consigna de Punto se hereda por todos los Puestos del Punto.
Una Consigna de Puesto aplica solo a ese Puesto.

## Entidad
`Consignment`
- consignment_id
- instance_country_id
- code
- title
- scope_type: POINT | POST
- scope_id
- status
- priority
- source_type
- current_version_id
- created_at/by
- updated_at/by

`ConsignmentVersion`
- consignment_version_id
- consignment_id
- version_number
- body
- effective_from
- effective_to
- recurrence_rule
- published_at/by
- supersedes_version_id
- change_summary
- immutable_after_publish

## Estados UI
- Borrador
- Vigente
- Suspendida
- Finalizada

Una versión publicada/vigente no se edita en sitio: cualquier modificación crea una nueva versión.

## Vigencia y frecuencia
La programación debe comportarse de forma similar a un calendario:
- Permanente desde fecha/hora.
- Ventana definida (desde/hasta).
- Recurrente por días/horas.
- Personalizada.

La frecuencia define cuándo la Consigna aplica; no convierte automáticamente la Consigna en una Tarea. Una instrucción que exige generar y cerrar una actividad recurrente pertenece al futuro motor de Tareas/Actividades.

## Prioridad
Campo previsto para diferenciación visual/operacional; valores finales pendientes de congelar.

## Publicación
Al publicar:
1. se crea versión inmutable;
2. se calcula alcance efectivo (Punto/Puestos);
3. se distribuye a SGI: Agente / SGI: Supervisor / futuro SGI: Operador;
4. queda disponible para Relevo;
5. se conserva historial completo.

## Confirmación en Relevo
Por cada Consigna vigente aplicable al Puesto:
- lectura;
- confirmación de comprensión/recepción;
- consignment_version_id;
- employee_id;
- post_id;
- relief_id;
- confirmed_at.

La UI ordena:
1. Nuevas desde última confirmación del AdS en ese Puesto.
2. Modificadas desde última confirmación.
3. Vigentes sin cambios.

Se confirman todas en cada Relevo.

## Distribución durante turno activo
Diseño recomendado (pendiente de congelar):
- si una Consigna nueva/modificada entra en vigencia durante un turno ya ejecutándose, notificar inmediatamente al AdS actualmente presente;
- requerir confirmación fuera del Relevo;
- el próximo Relevo vuelve a incluir todas las Consignas vigentes.

## Auditoría
Eventos:
- CONSIGNMENT_CREATED
- CONSIGNMENT_VERSION_PUBLISHED
- CONSIGNMENT_SUSPENDED
- CONSIGNMENT_FINALIZED
- CONSIGNMENT_DISTRIBUTED
- CONSIGNMENT_ACKNOWLEDGED
- CONSIGNMENT_ACKNOWLEDGEMENT_FAILED / CLARIFICATION_REQUESTED (si se adopta)

Toda evidencia se vincula a la versión exacta.

## REGESEP
Consignas formarán parte de la ejecución del REGESEP.
Queda pendiente congelar la distinción entre:
- Consigna normativa/base del REGESEP.
- Consigna operacional temporal/ad-hoc.

## Métricas potenciales
- Consignas vigentes por Punto/Puesto.
- Consignas nuevas/modificadas pendientes de confirmación.
- Confirmaciones por turno/Relevo.
- historial de versiones.
- tiempo entre publicación y confirmación cuando aplica distribución inmediata.

## Interconexiones
### SGI: Comando → SGI: Agente / SGI: Supervisor / futuro SGI: Operador
- Consigna publicada/actualizada.
- versión, alcance, vigencia/frecuencia, prioridad.

### Interfaces operativas → SGI: Comando
- confirmación de lectura/comprensión/recepción.
- employee_id, consignment_version_id, post_id, relief_id cuando corresponda.

### CORE → SGI: Comando
- zona horaria y calendario del contexto Instancia–País para vigencias/recurrencias.

## Decisiones congeladas — vigencia y calendario
Se elimina `Recurrente` como tipo independiente.

La programación de una Consigna se expresa con dos ejes:

### Vigencia
- `PERMANENTE`: vigente hasta que sea suspendida/finalizada o sustituida.
- `TEMPORAL`: vigente dentro de una ventana `effective_from` / `effective_to`.

### Aplicación
- `TODO_EL_TIEMPO`: aplica durante toda su vigencia.
- `CALENDARIO`: aplica únicamente en los días/horas definidos dentro de su vigencia.

Ejemplos:
- Permanente + Todo el tiempo → instrucción 24/7.
- Permanente + Calendario → p. ej. lunes–viernes 22:00–05:00.
- Temporal + Todo el tiempo → p. ej. del 7 al 12 de septiembre, 24/7.
- Temporal + Calendario → p. ej. durante dos semanas, solo sábados 18:00–24:00.

No existe un enum separado `RECURRENTE`.

## Prioridad
Valores UI congelados:
- Normal
- Alta
- Crítica

La prioridad modifica orden, énfasis visual y notificación; no vuelve opcional una Consigna de menor prioridad.

## REGESEP vivo y versionado
Toda Consigna vigente forma parte del REGESEP del Punto/Puesto, incluyendo:
- Consignas normativas/base.
- Consignas operacionales/ad hoc.

Cuando se publica una Consigna nueva, se modifica una vigente, se suspende o finaliza una Consigna con impacto operativo:
1. SGI: Comando actualiza la representación estructurada del REGESEP.
2. Se genera una nueva versión del REGESEP.
3. La versión anterior permanece inmutable.
4. Se conserva qué Consigna/version provocó el cambio.
5. Stakeholders autorizados reciben el cambio formal.
6. Si afecta a un turno en ejecución, se distribuye inmediatamente al personal presente y requiere confirmación.

El REGESEP se comporta como documento/reglamento operacional vivo y versionado, no como PDF estático aislado.

## Autores/canales autorizados de Consignas
Pueden generar Consignas:
- Cliente → SGI: Cliente.
- Coordinador de Compañía → SGI: Comando.
- Asistente de Coordinación → SGI: Comando.
- Agente de Seguridad → SGI: Agente.
- Supervisor de Seguridad → SGI: Supervisor.
- Futuro: Agente/Supervisor → SGI: Operador.

Toda creación ingresa a SGI: Comando y queda sujeta a la misma estructura de alcance, vigencia, versión, prioridad, REGESEP y auditoría.

Queda pendiente definir si todos los autores pueden `PUBLICAR` directamente o si algunos generan una propuesta pendiente de validación.

## Distribución durante turno activo
Regla congelada:
- Una Consigna nueva/modificada que entra en vigencia durante un turno activo se envía inmediatamente al AdS presente.
- Requiere lectura + confirmación de comprensión/recepción.
- No se espera al siguiente Relevo.
- En el siguiente Relevo vuelve a aparecer dentro del conjunto completo de Consignas vigentes.

## Consigna Propuesta vs Consigna Publicada
Autores/canales:
- Cliente → SGI: Cliente → crea `Consigna Propuesta`.
- Agente → SGI: Agente → crea `Consigna Propuesta`.
- Supervisor → SGI: Supervisor → crea `Consigna Propuesta`.
- Futuro SGI: Operador → crea `Consigna Propuesta`.
- Coordinador de Compañía → SGI: Comando → puede crear y publicar.
- Asistente de Coordinación → SGI: Comando → puede crear y publicar.

Una `Consigna Propuesta`:
- no entra todavía al REGESEP;
- no obliga operacionalmente;
- conserva autor, canal, fecha/hora, alcance propuesto, contenido, prioridad y evidencia;
- queda `Pendiente de aprobación`.

Coordinador o Asistente pueden:
- Aprobar y Publicar.
- Rechazar.
- Solicitar ajuste / editar antes de publicar, conservando trazabilidad.

Al aprobar/publicar:
1. se genera la Consigna/version oficial;
2. entra al REGESEP;
3. se versiona el REGESEP;
4. se distribuye según vigencia/aplicación;
5. se audita la relación con la propuesta original.
