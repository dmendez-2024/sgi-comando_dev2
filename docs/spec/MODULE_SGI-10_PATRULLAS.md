# SGI-10 — Patrullas v0.1 (En diseño)

## Propósito
Configurar y ejecutar patrullas preventivas/operativas dentro de un Punto, con rutas, hitos, frecuencia, evidencias, cumplimiento y generación contextual de Novedades.

Patrulla pertenece a la familia `Consignas` dentro de la taxonomía maestra de Tareas.

## Principio de modelado
Separar tres conceptos:

1. `PatrolRoute`
   - qué recorrido existe;
   - a qué Punto pertenece;
   - cuáles son sus Hitos y reglas de validación.

2. `PatrolPlan`
   - quién debe ejecutar la ruta;
   - desde qué Puesto/responsabilidad;
   - frecuencia/calendario;
   - tolerancias y vigencia.

3. `PatrolExecution`
   - una ejecución concreta;
   - Agente;
   - timestamps;
   - hitos realizados;
   - evidencias;
   - novedades;
   - resultado.

Esto evita duplicar una misma ruta cuando más de un Puesto puede ser responsable de ejecutarla.

## Alcance
Configuración:
`SGI: Comando → Servicio → Punto → Patrullas`

La Ruta pertenece al `Punto`.
El Plan de Patrulla asigna responsabilidad operacional a uno o más `Puestos` según configuración.

## Ruta de Patrulla
Entidad conceptual `PatrolRoute`:
- `route_id`
- `instance_country_id`
- `point_id`
- código
- nombre
- descripción
- estado
- versión
- geometría/trazado opcional
- punto de inicio opcional
- punto final opcional
- distancia estimada opcional
- duración estimada opcional
- auditoría

Estados recomendados:
- Borrador
- Vigente
- Suspendida
- Finalizada

Ruta publicada = inmutable; cambios crean nueva versión.

## Hitos
Entidad conceptual `PatrolCheckpoint`:
- `checkpoint_id`
- `route_version_id`
- nombre
- descripción
- orden
- ubicación GPS opcional
- radio/geofence opcional
- referencia visual/fotografía estándar opcional
- métodos de validación
- evidencia requerida
- instrucciones
- obligatoriedad
- auditoría

Ejemplos:
- Portón Norte
- Bodega 3
- Cerco perimetral Oeste
- Transformador
- Garita Secundaria

## Validación del Hito
El Hito debe poder aceptar uno o más métodos:
- GPS / Geofence
- QR
- Código de Barras
- NFC
- Fotografía
- Confirmación manual

Recomendación:
reutilizar el mismo motor lógico `ALL / ANY` ya definido para Bitácora.

Ejemplo:
`GPS Y (QR O NFC)`

La app traduce la lógica a lenguaje operativo.

## Secuencia
La Ruta debe poder definir:
- secuencia estricta;
- secuencia flexible.

En secuencia estricta:
- Hito 2 no se valida antes de Hito 1 salvo excepción permitida.

En secuencia flexible:
- deben completarse todos los Hitos obligatorios, pero el orden puede variar.

## Plan de Patrulla
Entidad conceptual `PatrolPlan`:
- `patrol_plan_id`
- `route_id`
- `responsible_post_id`
- vigencia
- calendario
- frecuencia
- ventana/tolerancia
- prioridad
- roles habilitados
- activo/inactivo
- auditoría

## Calendario y frecuencia
Debe soportar:
- horarios específicos;
- recurrencia por intervalo;
- ventanas por calendario.

Ejemplos:
- cada hora;
- cada 2 horas;
- 22:00, 00:00, 02:00, 04:00;
- lunes a viernes 18:00–06:00.

Cada obligación genera una `PatrolOccurrence` esperada.

## Ocurrencia planificada
Entidad conceptual `PatrolOccurrence`:
- `occurrence_id`
- `patrol_plan_id`
- `scheduled_at`
- ventana válida desde/hasta
- estado
- `patrol_execution_id` nullable

Estados funcionales iniciales:
- Pendiente
- Disponible
- En ejecución
- Ejecutada
- Ejecutada tardía
- Incompleta
- No ejecutada
- Cancelada/Exceptuada si aplica

La nomenclatura exacta queda pendiente de congelar.

## Ejecución
Flujo:
1. App identifica Puesto/turno/Relevo activo.
2. Muestra Patrullas disponibles/próximas.
3. Agente inicia una ocurrencia.
4. SGI registra inicio, ubicación y Agente.
5. App guía Hitos.
6. Cada Hito valida sus reglas y evidencia.
7. Agente puede generar Hallazgo/Vulnerabilidad/Incidente contextual.
8. Finaliza la Patrulla.
9. SGI calcula resultado de ejecución y cumplimiento.
10. Evento queda disponible para SGI: Comando y SMC.

## Hito ejecutado
Entidad conceptual `PatrolCheckpointExecution`:
- `checkpoint_execution_id`
- `patrol_execution_id`
- `checkpoint_id`
- `arrived_at`
- `validated_at`
- ubicación capturada
- método(s) usados
- evidencias
- resultado
- observaciones
- Agente/dispositivo
- auditoría

## Novedades desde Patrulla
Desde cualquier Hito o desde la Patrulla completa:
- Crear Hallazgo
- Crear Vulnerabilidad
- Crear Incidente

Precargar:
- Cliente/Servicio/Punto/Puesto
- Ruta
- Hito
- ubicación
- timestamp
- Agente
- fotografías/evidencias seleccionadas

La Novedad conserva su lifecycle propio y queda vinculada a la Patrulla.

## Offline
La Patrulla debe poder ejecutarse sin conexión:
- ruta/version previamente sincronizada;
- hitos y reglas en caché;
- GPS/evidencia local;
- fotos retenidas hasta ACK server-side.

Al recuperar conectividad:
- sincronización idempotente;
- conservar timestamps originales del dispositivo;
- conservar hora de recepción/sincronización por separado.

## Integridad / antifraude
Recomendaciones:
- timestamp automático;
- ubicación GPS cuando el dispositivo lo permita;
- registrar precisión GPS;
- registrar dispositivo;
- QR/NFC físico cuando se requiera prueba de presencia;
- no permitir editar silenciosamente un Hito ya validado;
- correcciones por evento auditable.

No asumir que GPS por sí solo prueba presencia física.

## Métricas
Métricas base:
- Patrullas planificadas
- Ejecutadas
- Ejecutadas tardías
- Incompletas
- No ejecutadas
- % cumplimiento
- tiempo promedio
- Hitos omitidos
- Novedades generadas por Patrulla

Recomendación:
enviar hechos normalizados a SMC para que los KPIs/ID se calculen fuera de SGI.

## REGESEP
Recomendación:
las Patrullas vigentes (Ruta + Plan/Frecuencia + Hitos) deben formar parte del REGESEP estructurado del Punto, porque son instrucciones operacionales permanentes/recurrentes.

Publicar/modificar una Ruta o Plan vigente debe versionar el REGESEP correspondiente.

## Integraciones SITC
### SGI: Comando → SGI: Agente / futuro SGI: Operador
- Rutas/versiones
- Planes
- Ocurrencias
- Hitos
- reglas/evidencia

### App operativa → SGI: Comando
- inicio/fin
- Hitos
- GPS
- evidencias
- resultado
- Novedades vinculadas

### SGI: Comando → SMC
- hechos de cumplimiento de Patrulla
- puntualidad
- completitud
- Hitos
- incidencias relevantes

### SGI: Comando ↔ REGESEP
- publicación/versionado de Patrullas vigentes

## Clasificación maestra de Patrullas

Las Patrullas se clasifican mediante DOS ejes independientes:

### Eje temporal
#### Patrulla Programada
Tiene un rango/ventana horaria definida para su ejecución.

Ejemplo:
- Ventana: 19:00–20:00
- El Agente puede iniciar y completar la Patrulla dentro de esa ventana conforme a las reglas del Plan.
- Si la ventana cierra sin ejecución, puede resultar `No ejecutada`.
- Si inicia/finaliza fuera de ventana, el sistema puede clasificarla como tardía según tolerancias.

#### Patrulla No Programada
No tiene una ocurrencia horaria predefinida.
Puede ser iniciada en cualquier momento y ejecutarse n veces.

Cada inicio genera una nueva `PatrolExecution`.

No existe una `PatrolOccurrence` programada obligatoria para cada ejecución, salvo que en el futuro se defina una meta/cupo adicional.

### Eje estructural
#### Patrulla Cerrada
Tiene Hitos predefinidos.

Puede ser:
- `Secuencia Estricta`
- `Secuencia Flexible`

Los Hitos pertenecen a la versión de la Ruta/Definición.

#### Patrulla Abierta
No tiene Hitos predefinidos.

Durante la ejecución:
1. el Agente inicia la Patrulla;
2. crea un Hito cuando identifica un punto/actividad relevante de control;
3. captura ubicación/evidencia/observaciones según corresponda;
4. cierra ese Hito;
5. continúa patrullando;
6. crea tantos Hitos como necesite;
7. finaliza la Patrulla.

Los Hitos creados durante una Patrulla Abierta son parte de esa ejecución concreta y no modifican automáticamente ninguna Ruta estándar.

## Combinaciones válidas
Se permiten las cuatro combinaciones:

1. `Programada + Cerrada`
2. `Programada + Abierta`
3. `No Programada + Cerrada`
4. `No Programada + Abierta`

Ejemplos:
- Programada + Cerrada: ronda perimetral 22:00–23:00 con 8 Hitos definidos.
- Programada + Abierta: recorrido preventivo entre 02:00–03:00 donde el Agente documenta los puntos que inspecciona.
- No Programada + Cerrada: supervisor ordena en cualquier momento ejecutar una Ruta fija.
- No Programada + Abierta: patrulla extraordinaria libre por un sector del Punto.

## Ajuste del modelo conceptual

### `PatrolDefinition`
Entidad superior que define la Patrulla:
- `patrol_definition_id`
- `point_id`
- nombre
- descripción
- `structure_type`: CLOSED | OPEN
- estado
- versión
- auditoría

### Si `structure_type = CLOSED`
Debe existir una `PatrolRoute` con Hitos predefinidos.

### Si `structure_type = OPEN`
No existe lista previa de Hitos obligatorios.
Puede existir:
- área/zona de referencia;
- instrucciones generales;
- duración objetivo opcional;
- evidencia mínima opcional.

## `PatrolPlan`
Añadir:
- `schedule_type`: PROGRAMMED | UNPROGRAMMED

### PROGRAMMED
Campos:
- ventana de inicio/fin;
- calendario/recurrencia;
- tolerancias.

Genera `PatrolOccurrence` esperadas.

### UNPROGRAMMED
No genera una ocurrencia horaria obligatoria.
Cada inicio autorizado crea directamente una `PatrolExecution`.

## Hitos dinámicos de Patrulla Abierta
Entidad conceptual:
`DynamicPatrolCheckpoint`
- `dynamic_checkpoint_id`
- `patrol_execution_id`
- nombre/título
- descripción
- opened_at
- closed_at
- GPS/precisión
- evidencias
- observaciones
- Novedades vinculadas
- created_by
- auditoría

Un Hito dinámico debe quedar cerrado explícitamente antes de finalizar la Patrulla, salvo que se permita finalizar con Hitos abiertos como excepción auditable.

## Secuencia
La opción de Secuencia aplica únicamente a Patrullas Cerradas:
- Estricta
- Flexible

En Patrullas Abiertas no existe secuencia predefinida.

## Métodos de validación de Hitos
Aprobados:
- GPS
- QR
- Código de Barras
- NFC
- Fotografía
- Manual

Se reutiliza el motor lógico Y/O (`ALL/ANY`) ya definido en SGI-09 Bitácora.

## Frecuencia / calendario
Aprobado soportar:
- cada X minutos/horas;
- horas específicas;
- rangos/ventanas horarias;
- calendarios recurrentes.

Solo Patrullas Programadas generan obligación temporal esperada.

## Novedades durante todo el recorrido

En una `Patrulla Cerrada`, el Agente puede crear:
- Hallazgo
- Vulnerabilidad
- Incidente

en cualquier momento del recorrido, no únicamente al validar un Hito.

### Si ocurre en un Hito
La Novedad se vincula a:
- `patrol_execution_id`
- `checkpoint_id`
- GPS/timestamp
- evidencia seleccionada

### Si ocurre entre Hitos
La Novedad se vincula a:
- `patrol_execution_id`
- `checkpoint_id = null`
- GPS/timestamp
- Hito anterior y Hito siguiente opcionales para identificar el tramo
- evidencia seleccionada

Conceptualmente puede existir:
`PatrolRouteSegmentContext`
- `previous_checkpoint_id`
- `next_checkpoint_id`
- `captured_at`
- `location`

La Novedad mantiene su lifecycle propio y no altera por sí sola el estado de la Patrulla.

## Decisiones congeladas adicionales
- Patrulla Abierta: exige al menos 1 Hito creado y cerrado para poder finalizar normalmente.
- Patrulla Programada: es válida si se ejecuta dentro de la ventana horaria definida, sin exigir una hora exacta dentro de esa ventana.
- Patrulla No Programada: puede ser iniciada libremente por los Agentes habilitados del Puesto.

## Decisiones congeladas — responsabilidad, breadcrumb y tardanza

### Responsabilidad de ejecución
La obligación de Patrulla pertenece al `Puesto`, no a una persona específica.

Regla:
- el `PatrolPlan` asigna la Patrulla al Puesto;
- la ejecución corresponde al Agente que se encuentre efectivamente de servicio en ese Puesto según Relevo/turno vigente;
- SGI registra al Agente ejecutor real en `PatrolExecution`;
- no es necesario preasignar nominalmente cada Patrulla a un colaborador.

### Breadcrumb GPS
Además de la validación puntual de Hitos, SGI puede registrar opcionalmente el recorrido entre Hitos mediante muestras GPS.

Frecuencia inicial:
- una muestra cada 2 minutos durante una Patrulla activa.

Objetivo:
- reconstruir de forma aproximada la trayectoria real;
- verificar continuidad básica del recorrido;
- evitar generar volumen excesivo de datos.

Cada muestra debe guardar al menos:
- `captured_at`
- latitud
- longitud
- precisión GPS
- `patrol_execution_id`

El breadcrumb es evidencia complementaria y no reemplaza la validación de Hitos.

### Patrulla Programada y tardanza
Una Patrulla Programada debe respetar su ventana de ejecución.

Ejemplo:
- ventana: `19:00–20:00`
- inicio real: `19:55`
- fin real: `20:20`

Resultado:
- `Ejecutada tardía`

Por tanto, no basta con haber iniciado dentro de la ventana; la finalización también forma parte del cumplimiento temporal.

El sistema conserva:
- ventana planificada;
- inicio real;
- fin real;
- desviación temporal;
- clasificación final.

La tolerancia adicional, si se habilita, debe ser configurable y explícita.

## Decisiones congeladas — Relevo, Hito No cumplido y ventana

### Relevo durante una Patrulla
Una `PatrolExecution` NO se transfiere entre Agentes.

Si el Agente saliente llega al momento de Relevo con una Patrulla todavía abierta:
1. debe cerrarla;
2. si no completó todos los requisitos, se registra como `INCOMPLETA`;
3. luego puede proceder el Relevo;
4. el Agente entrante, si corresponde, inicia una nueva ejecución independiente.

No existe traspaso de una misma ejecución entre Agentes.


### Apertura de Patrulla Programada
Una Patrulla Programada:
- antes de la ventana aparece como `PRÓXIMA`;
- no puede iniciarse antes de la hora de apertura;
- al abrir la ventana pasa a `DISPONIBLE`;
- debe completarse dentro de la ventana para ser considerada puntual;
- si finaliza fuera de la ventana, se clasifica `EJECUTADA_TARDIA` salvo regla/tolerancia explícita futura.

Ejemplo:
Ventana 19:00–20:00
- 18:50 → Próxima / no iniciable.
- 19:00 → Disponible.
- 19:55 inicio / 20:20 fin → Ejecutada tardía.

## Decisiones congeladas — no ejecutada, métricas e interrupciones

### Cierre automático de Patrulla Programada no iniciada
Cuando finaliza completamente la ventana de una Patrulla Programada y nadie la inició:
- SGI la marca automáticamente `NO_EJECUTADA`;
- no requiere cierre manual;
- conserva la ventana esperada, Puesto responsable y contexto operacional;
- genera el hecho correspondiente para estadísticas/SMC.

### Métricas Programadas vs No Programadas
Las Patrullas No Programadas:
- se registran;
- aparecen en estadísticas;
- pueden medirse por cantidad, duración, Hitos y Novedades;
- NO forman parte del denominador de cumplimiento de Patrullas Programadas;
- NO compensan una Patrulla Programada no ejecutada.

Ejemplo:
- Programadas esperadas: 10
- Programadas ejecutadas: 9
- No Programadas ejecutadas: 7

Cumplimiento Programadas:
`9 / 10 = 90%`

Las 7 No Programadas se reportan separadamente.

### Interrupción por emergencia u otra causa excepcional
No existe estado global `INTERRUMPIDA_POR_EXCEPCION`.

Si una Patrulla se interrumpe antes de completar sus requisitos:
- resultado global: `INCOMPLETA`;
- puede registrar motivo;
- puede vincular Incidente/Hallazgo/Vulnerabilidad relacionado;
- conserva timestamps y Hitos alcanzados;
- la causa queda disponible para auditoría/análisis.

Razón de diseño:
evitar que un estado favorable de excepción se convierta en mecanismo de abuso para justificar Patrullas incompletas.

Esto NO elimina la figura de `NO_CUMPLIDO` previamente aprobada para un Hito individual debidamente justificado.

## Simplificación congelada — Hitos obligatorios

### Todos los Hitos son obligatorios
En una `Patrulla Cerrada`, todos los Hitos definidos en la Ruta son obligatorios.

No existen:
- Hitos opcionales.
- Hitos exceptuados.
- Resultado global `Ejecutada con Excepción`.

### Resultado de cada Hito
Cada Hito tiene únicamente dos resultados funcionales:
- `CUMPLIDO`
- `NO_CUMPLIDO`

Si uno o más Hitos quedan `NO_CUMPLIDO`, la Patrulla completa queda:
- `INCOMPLETA`

La causa puede conservarse en Observaciones y puede vincularse a un Incidente/Hallazgo/Vulnerabilidad, pero no cambia el resultado.

### Duración
En v0.1 no se define una duración estimada/objetivo de la Patrulla como regla, alerta o métrica de cumplimiento.

Se conservan naturalmente:
- `started_at`
- `finished_at`

para trazabilidad histórica y análisis futuro, sin evaluar al Agente contra una duración esperada.

## Cierre funcional v0.1 — secuencia estricta y reintentos

### Secuencia Estricta
En una Patrulla Cerrada con `Secuencia Estricta`:
- el Hito siguiente NO puede validarse mientras el Hito anterior no haya sido `CUMPLIDO`;
- no existe salto operativo de Hitos;
- si un Hito no puede cumplirse, el Agente puede finalizar la Patrulla;
- el Hito queda `NO_CUMPLIDO`;
- la Patrulla completa queda `INCOMPLETA`.

La aplicación puede mostrar los Hitos posteriores, pero no habilitarlos para validación mientras la secuencia esté bloqueada.

### Reintentos de validación
Si la validación de un Hito falla:
- el Agente puede reintentar el mismo Hito;
- cada intento queda registrado en auditoría;
- se conserva timestamp, método, GPS/precisión y causa de falla cuando sea detectable;
- un intento fallido no convierte inmediatamente el Hito en `NO_CUMPLIDO`;
- el Hito queda `NO_CUMPLIDO` solo cuando la Patrulla se finaliza sin haber logrado validarlo.

Ejemplos de intentos fallidos:
- GPS fuera del radio permitido;
- QR no leído/no coincidente;
- NFC no detectado;
- evidencia requerida no capturada.

### Cierre funcional
Con estas decisiones, `SGI-10 — Patrullas v0.1` queda funcionalmente cerrado a nivel conceptual.

---

## Decisiones canónicas SER v0.7 — sustituyen el modelado de Configuración anterior

Para **Configuración**, la jerarquía visible/canónica queda:
`Punto → Puesto → Protocolo → Patrulla → Reglas`.

`PatrolRoute / PatrolPlan / PatrolExecution` puede seguir siendo una descomposición útil para el futuro motor de ejecución, pero **no debe obligar al Coordinador a navegar capas adicionales en Configuración**. En v0.7, `Patrulla` reúne la definición configurable del recorrido/obligación bajo un Protocolo versionado.

### Matriz de Patrullas
- Cerrada + Programada.
- Cerrada + No Programada.
- Abierta + Programada.
- Abierta + No Programada.
- Programada: ventana de máximo 1 hora.
- Cerrada: hasta 25 Hitos y secuencia Estricta/Flexible.
- Abierta: los Hitos se crean durante la ejecución y no se preconfiguran.

### Levantamiento mixto de Hitos Cerrados
Dos acciones canónicas:
- **+ Agregar hito en plano**: selección sobre el plano del `.ats`; almacena package/revisión ATS y X/Y normalizado.
- **+ Agregar hito en campo**: SGI: Comando móvil captura GPS, precisión, fecha/hora y usuario durante la supervisión inicial.

Una misma Patrulla puede mezclar ambos métodos. Cada Hito conserva `ATS`, `FIELD` o `MIXED`. Un Hito ATS puede enriquecerse luego con GPS y uno de campo puede vincularse luego al plano.

No convertir coordenadas del plano a WGS84 si ATS no entrega calibración geográfica explícita.

### Foto estándar
El Hito puede asociar una Foto estándar real, tomada en campo o cargada desde oficina. Es un activo formal/versionado y quedará disponible como patrón para futura comparación **VISINT**.

### Versionado
- BORRADOR: editable y no cambia el REGESEP vigente.
- PUBLICADO/VIGENTE: snapshot inmutable.
- Editar publicado = clonar a nueva versión BORRADOR.
- Publicar la nueva versión conserva la anterior como histórico/No vigente y cambia el REGESEP aplicable.
- El snapshot incluye Patrullas, Hitos, Reglas, ubicaciones y Fotos estándar.
