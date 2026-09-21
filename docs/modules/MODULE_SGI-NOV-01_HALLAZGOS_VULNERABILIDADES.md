# SGI-NOV-01 — Hallazgos y Vulnerabilidades v0.1 (En diseño)

## Principio
`Novedades` es una vista/agrupador funcional para elementos que ameritan atención del cliente:
- Hallazgos
- Vulnerabilidades
- Incidentes

No es una entidad independiente.

Este módulo cubre Hallazgos y Vulnerabilidades, que permanecen gestionados en SGI: Comando. Los Incidentes conservan su registro operacional en SGI y su workflow de resolución se gestiona en STC.

## Actores y canales de creación
Pueden crear Hallazgos y Vulnerabilidades:
- Cliente → SGI: Cliente.
- Agente → SGI: Agente.
- Supervisor → SGI: Supervisor.
- Coordinador de Compañía → SGI: Comando.
- Asistente de Coordinación → SGI: Comando.
- Futuro Agente/Supervisor → SGI: Operador.

Todo converge en SGI: Comando.

## Contexto obligatorio
Todo Hallazgo/Vulnerabilidad debe quedar asociado a:
- `instance_country_id`
- Cliente
- Servicio
- Punto

Opcional/según caso:
- Puesto
- ubicación libre/GPS
- referencia futura a ATS/UTA/componente
- turno/relevo/patrulla/bitácora que originó el reporte

## Hallazgos
Definición: situaciones observadas que ameritan atención del cliente, pero que no constituyen necesariamente un incidente de seguridad.

### Subtipos
- Orden
- Limpieza
- Mantenimiento
- Estacionamiento
- Componentes de Seguridad

### Campos base
- `finding_id`
- código humano automático
- subtype
- title
- description
- point_id
- post_id nullable
- reported_by
- source_channel
- reported_at
- photos/evidence
- location/GPS optional
- status
- client_visible = true por definición de Novedad
- linked_vulnerability_id nullable
- audit/version

### Componentes de Seguridad
Casos típicos:
- nueva cámara instalada/observada;
- cámara reparada;
- cerco/barrera/sensor nuevo o reparado;
- componente retirado/reubicado;
- cambio de estado de un componente.

Campos adicionales propuestos:
- `component_change_type`: NEW | REPAIRED | DAMAGED | REMOVED | RELOCATED | OTHER
- `component_category`
- `component_description`
- `observed_identifier` / serial si existe
- `ats_component_id` nullable (futuro)
- `ats_reconciliation_status`: NOT_APPLICABLE | PENDING | LINKED | REVIEW_REQUIRED

Un Hallazgo puede sugerir vínculo con una Vulnerabilidad existente; el cierre de la Vulnerabilidad nunca es automático.

## Vulnerabilidades
Definición: debilidades o condiciones que incrementan exposición/riesgo del Punto.

### Subtipos
- Acceso
- Perímetro
- Interno

### Campos base
- `vulnerability_id`
- código humano automático
- subtype
- title
- description
- point_id
- post_id nullable
- reported_by
- source_channel
- reported_at
- evidence
- location/GPS optional
- status
- attention_priority/severity (pendiente de congelar nomenclatura/escala)
- client_visible = true
- linked_finding_ids
- future_ats_ref nullable
- audit/history

### Estados propuestos
- Reportada
- Validada
- Mitigada
- Resuelta
- Descartada

Definiciones:
- Mitigada: el riesgo/impacto fue reducido, pero la condición no necesariamente desapareció.
- Resuelta: la debilidad dejó de existir.
- Descartada: se determinó que el reporte no correspondía a una Vulnerabilidad válida.

El paso a Mitigada/Resuelta requiere acción humana explícita.

## Relación Hallazgo ↔ Vulnerabilidad
Reglas:
1. Un Hallazgo puede vincularse a una Vulnerabilidad existente.
2. Un Hallazgo puede originar una nueva Vulnerabilidad sin destruir el Hallazgo.
3. Un Hallazgo de Componentes de Seguridad puede sugerir que una Vulnerabilidad fue mitigada/resuelta.
4. SGI presenta la sugerencia; un usuario autorizado confirma el cambio.
5. Se conserva trazabilidad entre origen, evidencia y cambio de estado.

## Relación futura con ATS
Hasta que ATS exista:
- SGI conserva la observación operacional.
- no intenta ser SoR del diseño técnico de seguridad.
- guarda referencias preparadas para conciliación futura.

Cuando ATS exista:
- Vulnerabilidades podrán vincularse con UAP/UTA/componentes/riesgos del `.ats`.
- Hallazgos de Componentes de Seguridad podrán conciliar cambios observados con el plano/modelo técnico.
- una conciliación en ATS no elimina el historial SGI.

## SGI: Cliente — Novedades
SGI: Cliente presenta una vista agregada:
- Hallazgos
- Vulnerabilidades
- Incidentes

Cada elemento conserva su entidad y workflow real.

Vista recomendada:
- contador total de Novedades abiertas/relevantes;
- filtros por Punto, tipo, fecha, estado;
- tarjetas/lista con estado y evidencia;
- vínculo al detalle;
- para Incidentes, estado de resolución retornado desde STC.

## Arquitectura de datos
No crear una mega-entidad `Novelty`.

Recomendación:
- entidades específicas `Finding`, `Vulnerability`, `Incident`;
- campos comunes mediante un envelope/audit contract compartido;
- una vista/materialized query `ClientNoveltyView` para SGI: Cliente.

## Auditoría
Eventos conceptuales:
- FINDING_REPORTED
- FINDING_VALIDATED
- FINDING_LINKED_TO_VULNERABILITY
- SECURITY_COMPONENT_CHANGE_REPORTED
- VULNERABILITY_REPORTED
- VULNERABILITY_VALIDATED
- VULNERABILITY_MITIGATED
- VULNERABILITY_RESOLVED
- VULNERABILITY_DISCARDED
- ATS_RECONCILIATION_LINKED (futuro)

## Pendientes
- congelar lifecycle exacto de Hallazgos;
- definir si Hallazgos que requieren acción pueden generar opcionalmente una tarea STC sin dejar de permanecer en SGI;
- congelar escala/nombre de prioridad o severidad de Vulnerabilidades;
- definir permisos para validar/mitigar/resolver Vulnerabilidades.

## Hallazgos — lifecycle congelado por subtipo

### Orden / Limpieza / Mantenimiento / Estacionamiento
No usan workflow de resolución dentro de SGI.

Estado:
- `REPORTADO`

Visibilidad activa en SGI: Cliente:
- configurable por Instancia–País o parámetro SGI;
- opciones iniciales: 12 horas / 24 horas / 48 horas / 1 semana;
- default: 48 horas.

Al terminar la ventana de visibilidad:
- el Hallazgo deja de aparecer en la vista activa por defecto;
- NO se elimina;
- permanece disponible en histórico/auditoría/reportes.

Responsabilidad:
- el cliente es responsable de corregir la condición;
- SGI cumple con informar;
- no se genera automáticamente ni manualmente una tarea STC desde estos Hallazgos.

### Componentes de Seguridad
Lifecycle:
- `REPORTADO`
- `CONFIRMADO`
- `DESCARTADO`

Regla de confirmación:
- si en el Punto prestan servicio dos o más Agentes, la confirmación debe efectuarla un Agente diferente al reportante;
- si solo existe un Agente prestando servicio en el Punto, el mismo Agente puede confirmar en un turno posterior;
- una confirmación debe conservar el turno/relevo desde el cual se hizo.

Una vez `CONFIRMADO`:
- SGI conserva el hecho operacional;
- queda listo para conciliación/incorporación en ATS;
- cuando ATS exista, el componente confirmado debe reflejarse en una nueva versión del `.ats` o flujo equivalente de actualización técnica;
- hasta entonces queda `ats_reconciliation_status = PENDING`.

## Vulnerabilidades — lifecycle congelado
Estados:
- `REPORTADA`
- `CONFIRMADA`
- `MITIGADA`
- `RESUELTA`
- `DESCARTADA`

`CONFIRMADA` admite múltiples confirmaciones acumuladas.

Ejemplo UI:
`Cámara CCTV averiada — x6 confirmaciones`

Cada confirmación conserva:
- `confirmation_id`
- `vulnerability_id`
- `confirmed_by`
- `source_channel`
- `confirmed_at`
- `shift_occurrence_id` / `relief_id` cuando aplica
- comentario/evidencia opcional

La cantidad visible de confirmaciones se deriva del historial, no de un contador editable.

### Permisos de estado
Sin aprobación previa:
- Agente puede Reportar y Confirmar.
- Cliente puede Reportar y Confirmar.
- Coordinador puede Reportar y Confirmar.
- Asistente puede Reportar y Confirmar.

Con aprobación/acción restringida:
- `MITIGADA` → Coordinador o Asistente.
- `RESUELTA` → Coordinador o Asistente.
- `DESCARTADA` → Coordinador o Asistente.

Los estados terminales/gestionados deben conservar autor, fecha/hora, motivo y evidencia.

## Relación Hallazgo de Componente ↔ Vulnerabilidad
- Un Hallazgo `Componentes de Seguridad = REPARADO` puede vincularse a una Vulnerabilidad existente.
- SGI puede sugerir marcarla `MITIGADA` o `RESUELTA`.
- El cambio nunca es automático.
- Coordinador/Asistente deben aprobar el nuevo estado.
- El Hallazgo permanece como evidencia histórica del cambio observado.

## Corrección congelada — propuestas de cambio de estado de Vulnerabilidad
Agente, Supervisor o Cliente pueden originar una propuesta para cambiar una Vulnerabilidad a:
- `MITIGADA`
- `RESUELTA`
- `DESCARTADA`

La propuesta debe incluir motivo y/o evidencia aplicable.

El cambio NO surte efecto inmediatamente. Se registra como:
- `PENDIENTE_DE_APROBACION`

Solo Coordinador de Compañía o Asistente de Coordinación pueden aprobar o rechazar la propuesta.

Al aprobar:
- se aplica el nuevo estado;
- se conserva quién originó la propuesta;
- quién aprobó;
- fecha/hora de propuesta;
- fecha/hora de aprobación;
- evidencia/motivo;
- estado anterior y nuevo.

Coordinador/Asistente también pueden originar directamente el cambio y aprobarlo conforme a permisos.

## Límite de confirmaciones de Vulnerabilidad
Para una misma Vulnerabilidad:
- máximo una confirmación por Agente por turno.
- el mismo Agente puede volver a confirmar en un turno posterior.
- el contador visible `xN confirmaciones` se deriva del historial válido.

## Ventana activa de Hallazgos simples
Para Orden/Limpieza/Mantenimiento/Estacionamiento:
- la duración activa NO la elige el reportante;
- es parámetro global por Instancia–País;
- opciones iniciales: 12h / 24h / 48h / 1 semana;
- default: 48h.
