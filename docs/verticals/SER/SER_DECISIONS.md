# SER — Decisiones vigentes

## Ownership y navegación
- **SER-DEC-001:** SIC: COM es System of Record de Servicio, Cliente, Punto, Puesto, TIER, Formato/FHE, Rotación, Turnos y estado comercial.
- **SER-DEC-002:** El listado maestro de Servicios se presenta operativamente a nivel **Cliente · Punto**. Cada fila expone dos acciones: **Operación** y **Configuración**.
- **SER-DEC-003:** Operación y Configuración son mundos separados: Configuración define cómo debe operar el Punto; Operación visualiza ejecución real.
- **SER-DEC-004:** Operación permanece en stand by durante la primera corrida de Configuración.
- **SER-DEC-005:** Estados visibles: **Por Configurar / Activo / Inactivo**. Inactivo proviene de SIC: COM y es read-only en SGI. SGI puede transicionar manualmente solo `Por Configurar → Activo` cuando se cumplen bloqueantes.

## Métricas de Servicios
- **SER-DEC-006:** ID Promedio e IC Promedio representan las últimas dos semanas de ejecución real; mientras SGI-07 Relevos no exista se usa proxy UAT de turnos ya finalizados.
- **SER-DEC-007:** IC verde únicamente en 100%; bandas UAT amarillo 90–99.9 y rojo <90.
- **SER-DEC-008:** ID se compara con el mínimo requerido por TIER; banda amarilla provisional hasta 0.5 por debajo del mínimo.

## Configuración del Punto
- **SER-DEC-009:** La landing de Configuración del Punto resume bloqueantes, observaciones y acceso a ATS, Puestos, Bitácora, Patrullas, Consignas, Recursos Humanos, Recursos Materiales e Historial.
- **SER-DEC-010:** REGESEP es el reglamento/configuración consolidada del Punto, no el nombre de la acción principal desde Servicios.

## ATS
- **SER-DEC-011:** La extensión canónica del paquete ATS es **`.ats`**.
- **SER-DEC-012:** SGI importa el `.ats`, conserva el paquete original, lee metadatos publicados y materializa el plano incluido; SGI no edita el archivo fuente.
- **SER-DEC-013:** Cada importación/reemplazo genera una revisión SGI y conserva histórico.
- **SER-DEC-014:** El Índice de Riesgo de Punto se **importa** desde `.ats` cuando el paquete lo entrega. SGI no lo calcula ni infiere.
- **SER-DEC-015:** Si el `.ats` no contiene Índice de Riesgo de Punto, SGI muestra “no informado”.
- **SER-DEC-016:** El plano principal se obtiene desde `model/pto.json → levels[].plan.internalPath` y debe existir dentro del paquete.

## Puestos
- **SER-DEC-017:** SIC: COM entrega Código, Nombre, TIER, Formato, Rotación, Turnos y Horas requeridas del Puesto; todos son read-only en SGI.
- **SER-DEC-018:** SGI agrega por Puesto: Tipo (CAA/PAT/VIG/MIX), descripción, ubicación en plano ATS, habilidades requeridas, justificación de excepción y estado de configuración.
- **SER-DEC-019:** Permisos Requeridos se deja creado visualmente pero sin funcionalidad en esta corrida.
- **SER-DEC-020:** La ubicación se selecciona directamente sobre el plano real importado desde `.ats`, no mediante catálogo textual.
- **SER-DEC-021:** SGI guarda la ubicación como coordenadas normalizadas X/Y vinculadas a la revisión ATS vigente.
- **SER-DEC-021A:** Una nueva revisión ATS invalida los vínculos espaciales previos de Puestos y exige re-seleccionar/revalidar ubicación sobre el plano vigente.

## Economía de habilidades
- **SER-DEC-022:** Cada una de las 8 habilidades requiere mínimo 1 y máximo 5 puntos.
- **SER-DEC-023:** Máximo una habilidad puede tener 5 puntos.
- **SER-DEC-024:** Máximo dos habilidades pueden tener 4 puntos.
- **SER-DEC-025:** La suma máxima total de las 8 habilidades es 22 puntos.
- **SER-DEC-026:** Tipo de Puesto carga una plantilla sugerida; cualquier ajuste respecto a plantilla requiere justificación.

## Bitácora — Configuración
- **SER-DEC-027:** Bitácora se configura **por Puesto** dentro de `Servicios → Configuración del Punto → Bitácora`.
- **SER-DEC-028 (superseded por SER-DEC-039):** La jerarquía funcional inicial era `Punto → Puesto → Protocolo → Reglas/Campos`.
- **SER-DEC-029:** REGESEP configura la regla; Bitácora en Operación ejecutará y visualizará los registros reales. Configuración no mezcla actividad ejecutada.
- **SER-DEC-030:** Cada Protocolo define Objeto `PAX / VHL / CONT` y Aplicación `INGRESO / EGRESO / AMBOS`.
- **SER-DEC-031:** Identificación y Elementos Sujetos a Verificación soportan lógica `ALL / ANY`.
- **SER-DEC-032:** Además de campos institucionales predefinidos, siempre existe **Agregar campo** para crear un campo personalizado con nombre, descripción, tipo, obligatoriedad, evidencia y modo de captura.
- **SER-DEC-033:** La captura manual permanece siempre disponible. QR/código de barras/NFC pueden precargar información sin reemplazar la validación del agente.
- **SER-DEC-034:** Un campo puede exigir evidencia y asociar una **Foto estándar** subida por el usuario. Esta imagen es un activo formal del protocolo, no una referencia decorativa.
- **SER-DEC-035:** La Foto estándar se almacena con nombre de archivo, tipo MIME, versión y notas de estándar. Reemplazarla incrementa la versión del activo de imagen.
- **SER-DEC-036:** Se deja preparada la futura integración con **VISINT (Visual Intelligence)**: evidencia capturada por el agente vs. Foto estándar. VISINT no es funcional en SER v0.6.
- **SER-DEC-037:** Modelos de Compañía y Estándares Cajamarca permanecen visibles como arquitectura futura. Importar un modelo deberá crear una copia versionada, no un vínculo vivo.
- **SER-DEC-038:** Publicar un protocolo lo convierte en Vigente; una nueva publicación sobre un protocolo ya vigente incrementa `version_no`. El historial documental completo se implementará en una corrida posterior.

- **SER-DEC-039:** La jerarquía canónica de Bitácora pasa a ser `Punto → Puesto → Protocolo → Acreditación → Reglas/Campos`.
- **SER-DEC-040:** Un Protocolo puede contener una o más Acreditaciones. La Acreditación representa una forma válida de acreditar el objeto PAX/VHL/CONT dentro del Protocolo (ej.: Visitante autorizado, Contratista autorizado, Proveedor temporal).
- **SER-DEC-041:** Identificación, Verificación, Autorización, Evidencias, Captura y Listas pertenecen a la Acreditación seleccionada; Definición (Objeto y Aplicación) pertenece al Protocolo.
- **SER-DEC-042:** Evidencias sigue siendo un resumen derivado de los campos de Identificación/Verificación con `Requiere evidencia = Sí`, pero ahora siempre dentro de una Acreditación específica.
- **SER-DEC-043:** La ejecución futura deberá registrar explícitamente `protocol_id`, versión y `accreditation_id` para trazabilidad.

- **SER-DEC-044:** Se conserva explícitamente la capa **Protocolo** aunque la Acreditación sea la que define las reglas/campos de ejecución. El Protocolo aporta gobierno, versionado y vigencia: permite mantener varias configuraciones preparadas (estándar, contingencia, evento especial, etc.) y decidir cuál aplica sin recrear ni eliminar Acreditaciones.
- **SER-DEC-045:** Semántica canónica: **Protocolo = paquete/versionado de operación y vigencia**; **Acreditación = forma específica de acreditar al objeto dentro de ese Protocolo**. En ejecución se determina primero el Protocolo vigente/aplicable y luego la Acreditación correspondiente al objeto concreto.

## Versionado transversal de Configuración — SER v0.7
- **SER-DEC-046:** Toda configuración publicada en SER se trata como **snapshot inmutable**. No se modifica directamente una versión publicada.
- **SER-DEC-047:** Editar una configuración publicada crea una **nueva versión en BORRADOR por copia completa** de la versión origen; Guardar borrador nunca altera la versión vigente.
- **SER-DEC-048:** Al publicar el nuevo borrador, la versión previamente Vigente queda histórica/No vigente y la nueva pasa a Vigente. La publicación —no el guardado de borrador— es el evento que modifica el REGESEP aplicable.
- **SER-DEC-049:** El snapshot debe incluir toda la estructura hija y activos necesarios para reconstruir exactamente la configuración histórica: reglas, Acreditaciones/Patrullas/Hitos, ubicaciones, evidencias configuradas, Fotos estándar y metadatos relacionados.
- **SER-DEC-050:** El patrón SER-DEC-046..049 es transversal y debe reutilizarse en las siguientes páginas de Configuración (Patrullas, Consignas, etc.), no implementarse como una excepción de Bitácora.

## Patrullas — Configuración
- **SER-DEC-051:** Jerarquía canónica: `Punto → Puesto → Protocolo → Patrulla → Reglas`. Protocolo conserva la misma semántica de gobierno/versionado/vigencia: pueden mantenerse varias configuraciones preparadas y definir cuál aplica.
- **SER-DEC-052:** Patrulla tiene dos ejes independientes: modalidad espacial `Cerrada / Abierta` y modalidad temporal `Programada / No Programada`; las cuatro combinaciones son válidas.
- **SER-DEC-053:** Patrulla Cerrada contiene Hitos predefinidos, máximo 25, y soporta secuencia `Estricta / Flexible`. Patrulla Abierta no contiene Hitos predefinidos; sus Hitos pertenecen a la ejecución futura.
- **SER-DEC-054:** Patrulla Programada usa ventana Desde/Hasta con máximo 1 hora. No Programada carece de hora fija y puede indicar número de ejecuciones requeridas.
- **SER-DEC-055:** Una Patrulla Cerrada admite **configuración mixta** de Hitos. Las dos acciones canónicas son **+ Agregar hito en plano** y **+ Agregar hito en campo**.
- **SER-DEC-056:** `Agregar hito en plano` posiciona el Hito sobre el plano ATS vigente y guarda X/Y normalizado + identificador de package/revisión ATS. SGI no inventa coordenadas WGS84 cuando el `.ats` carece de calibración geográfica.
- **SER-DEC-057:** `Agregar hito en campo` está pensado para SGI: Comando móvil durante la primera supervisión: captura GPS, precisión, fecha/hora y usuario; el Coordinador puede tomar/cargar en ese momento la Foto estándar.
- **SER-DEC-058:** El origen se conserva por Hito como `ATS`, `FIELD` o `MIXED`. Un Hito creado en oficina puede enriquecerse/corregirse en campo y viceversa sin crear otro concepto de Hito.
- **SER-DEC-059:** La Foto estándar del Hito es un activo formal y versionado del estándar. Puede capturarse con cámara móvil o cargarse desde oficina y queda preparada como patrón para futura auditoría mediante VISINT.
- **SER-DEC-060:** La sección Evidencias de Patrullas es un resumen derivado de Hitos/Reglas que requieren evidencia; no duplica la configuración primaria.
- **SER-DEC-061:** Las Novedades de ejecución podrán originarse en un Hito o entre Hitos y conservarán Patrulla + tramo/hitos adyacentes + GPS + fecha/hora. Esta lógica pertenece a Operación y no se implementa todavía en la pantalla de Configuración v0.7.
- **SER-DEC-062:** Patrullas no debe mantener un segundo modelo físico de configuración. El árbol persistente canónico es `patrol_protocol → patrol_definition → patrol_checkpoint → patrol_checkpoint_rule`; `patrol_definition` y `patrol_checkpoint` son las mismas entidades que debe referenciar la futura ejecución operacional.
- **SER-DEC-063:** Las columnas baseline necesarias para compatibilidad de ejecución (`point_id`, `status`, `version` en Patrulla y `validation_rule_json` en Hito) se conservan y sincronizan; las reglas configurables normalizadas viven en `patrol_checkpoint_rule`.

## Consignas — Configuración / alcance común — SER v0.8
- **SER-DEC-064:** Los Protocolos permanecen **independientes por vertical**. Bitácora, Patrullas y Consignas NO comparten un Protocolo transversal. Cada vertical mantiene su propio gobierno/versionado.
- **SER-DEC-065:** Por cada Punto y vertical puede existir **un solo Protocolo vigente**. Pueden coexistir Protocolos en Borrador, Publicados/disponibles y versiones históricas.
- **SER-DEC-066:** Los Protocolos son versionables: una versión publicada es inmutable; editarla crea una nueva versión BORRADOR por copia completa. Publicar preserva el histórico.
- **SER-DEC-067:** El alcance de los elementos configurados debe poder ser **Todo el Punto** o **uno o varios Puestos**. Esto aplica conceptualmente a Acreditaciones de Bitácora, Patrullas y Consignas para evitar configuración redundante. En SER v0.8 se implementa primero en Consignas; el retrofit de Bitácora/Patrullas se realizará de forma controlada para no alterar UAT aprobada sin una corrida específica.
- **SER-DEC-068:** Jerarquía de Consignas: `Punto → Protocolo de Consignas → Consigna → Alcance → Reglas/Evidencias`.
- **SER-DEC-069:** Vigencia y Aplicación son dimensiones distintas. Vigencia: `Permanente / Temporal`. Aplicación: `Todo el tiempo / Calendario`. No existe un tipo separado “Recurrente”.
- **SER-DEC-070:** Una Consigna puede combinar reglas de cumplimiento: Acuse de conocimiento, Confirmación de cumplimiento, Evidencia, GPS de ejecución y Observación.
- **SER-DEC-071:** Las Evidencias soportan nombre, descripción, tipo, obligatoriedad, Foto estándar real subida por el usuario, notas del estándar y preparación futura para VISINT. Reemplazar la Foto estándar versiona el activo dentro del snapshot del Protocolo.
- **SER-DEC-072:** GPS es opcional y se separa en dos conceptos: `Ubicación esperada` (ninguna / Plano ATS / coordenadas) y `GPS de ejecución` (captura futura por SGI: Operador cuando la regla lo exige).
- **SER-DEC-073:** La sección Evidencias consolida las evidencias requeridas por la Consigna; no debe crear una segunda fuente de reglas. La definición primaria vive en la Consigna/Evidencia configurada.
- **SER-DEC-074:** El snapshot versionado del Protocolo de Consignas incluye Consignas, alcance a Puestos, vigencia, calendario, reglas, ubicación esperada, Evidencias, Fotos estándar y metadatos necesarios para reconstrucción histórica.
