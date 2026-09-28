# SGI-09 — Bitácora v0.1 (En diseño)

## Propósito
Configurar, por Puesto, cómo se registran y controlan movimientos de acceso/egreso en la Bitácora operacional.

La configuración debe ser generalizable y reutilizable mediante Protocolos de Acceso asociados a una Acreditación.

## Unidad de configuración
El protocolo se configura desde:

`SGI: Comando → Servicio → Punto → Puesto → Bitácora`

Cada Puesto puede tener cero o más Protocolos de Acceso activos.

## Conceptos

### Acreditación
Definición/configuración que indica qué debe verificarse para permitir o registrar el movimiento de un objeto.

Cada Acreditación se aplica a un tipo de objeto:
- `PAX` — Persona
- `VHL` — Vehículo
- `CONT` — Contenedor

### Dirección / Aplicación
Campo obligatorio:
- `INGRESO`
- `EGRESO`
- `AMBOS`

Internamente puede modelarse como enum `INGRESS | EGRESS | BOTH`.

### Protocolo de Acceso
Contenedor operacional **versionado y gobernable** que agrupa una o más Acreditaciones para un escenario de Bitácora. El Protocolo no se elimina del modelo aunque la Acreditación sea la capa que determina los campos/reglas que se ejecutan sobre el objeto.

**Razón de existencia del Protocolo:** permite mantener varias configuraciones completas preparadas para un mismo Puesto/escenario, conservarlas en distintos estados de vigencia y decidir cuál debe aplicar en un momento determinado sin recrear ni borrar Acreditaciones. Por ejemplo:
- `PRO-BA-0001 · Ingreso PAX estándar` — vigente.
- `PRO-BA-0002 · Ingreso PAX contingencia` — no vigente/suspendido.
- `PRO-BA-0003 · Ingreso PAX evento especial` — borrador/listo para futura activación.

Cada Protocolo puede contener varias Acreditaciones (por ejemplo, Visitante autorizado, Contratista autorizado y Proveedor temporal), cada una con sus propias reglas de Identificación, Verificación, Autorización, Evidencias, Captura y Listas.

En ejecución, primero se determina el **Protocolo vigente/aplicable** para el Puesto, objeto y movimiento; luego se determina la **Acreditación** correspondiente al objeto concreto y se ejecutan sus reglas. Por tanto:
- **Protocolo = paquete/versionado de operación y vigencia.**
- **Acreditación = forma específica de acreditar al objeto dentro de ese Protocolo.**

Código humano automático recomendado:
`PRO-BA-####`

Campos base:
- `protocol_id`
- `protocol_code`
- `name`
- `object_type`
- `movement_scope`
- `status`
- `is_exportable`
- `source_type`
- `origin_protocol_version_id` nullable
- `current_version_id`
- auditoría

## Estructura funcional del Protocolo

### 1. Identificación
Pregunta:
`¿Cómo identifico al objeto?`

Contiene campos configurables.

Ejemplos PAX:
- Nombre
- Cédula
- Rostro
- Credencial
- Código QR

Ejemplos VHL:
- Placa
- Marca/modelo
- Conductor
- QR

Ejemplos CONT:
- Número de contenedor
- Sello
- Documento/referencia
- QR

### 2. Elementos sujetos a verificación
Pregunta:
`¿Qué partes, pertenencias o condiciones del objeto debo verificar?`

Ejemplos:
- PAX → mochila, bolso, credencial visible.
- VHL → cajuela, cabina, carga.
- CONT → sello, puertas, integridad exterior.

Cada elemento puede exigir evidencia/estándar.

### 3. Autorización
Pregunta:
`¿Quién o qué mecanismo autoriza el movimiento?`

Debe permitir reglas configurables para registrar:
- autorizador;
- rol/grupo autorizador;
- referencia externa;
- evidencia de autorización;
- observaciones.

La fuente exacta del autorizador queda pendiente de congelar.

### 4. Reutilización / Exportable
`is_exportable = true|false`

Si es exportable, el Protocolo puede aparecer como fuente en la Biblioteca de Protocolos para ser importado por otros Puestos dentro del alcance autorizado.

`Exportable` NO significa compartir automáticamente los registros de Bitácora ni propagar cambios en vivo.

## Campos configurables
Para mantener generalidad, cada sección usa un esquema común `ProtocolField`:

- `field_id`
- `section`: IDENTIFICATION | VERIFICATION | AUTHORIZATION
- `name`
- `description`
- `input_type`
- `required`
- `display_order`
- `reference_standard_ref` nullable
- `evidence_policy`
- validaciones/opciones según tipo

Tipos iniciales recomendados:
- Texto
- Sí/No
- Número
- Fecha/Hora
- Selección
- Fotografía
- Documento/archivo
- QR/Barcode
- Firma/confirmación

La imagen/fotografía de `Estándar` funciona como referencia visual del estado/forma esperada, distinta de la evidencia capturada durante la ejecución.

## Biblioteca de Protocolos
Objetivo: evitar que Coordinador/Asistente creen el mismo Protocolo desde cero para cada Puesto.

Acciones:
- Crear Protocolo
- Editar Protocolo
- Importar Protocolo

### Importación recomendada
La importación crea una COPIA VERSIONADA/SNAPSHOT en el Puesto destino:
- conserva referencia al protocolo/version origen;
- puede ser modificada localmente;
- NO recibe cambios automáticos del origen.

Razón: un cambio en un protocolo estándar no debe modificar silenciosamente la operación de decenas de Puestos.

A futuro puede existir:
`Actualización disponible`
para comparar versión origen vs versión local y aplicar conscientemente una nueva versión.

## Versionado
Un Protocolo publicado/activo es inmutable.

Modificar:
1. crea nueva versión;
2. mantiene versión histórica;
3. nuevos registros usan la nueva versión desde su vigencia;
4. registros antiguos conservan la versión exacta con la que fueron ejecutados.

## Ejecución en Bitácora
Flujo base:

1. Seleccionar `INGRESO` / `EGRESO`.
2. Seleccionar tipo de objeto `PAX / VHL / CONT`.
3. SGI determina Protocolos/Acreditaciones aplicables al Puesto y dirección.
4. Seleccionar Acreditación cuando exista más de una aplicable.
5. Completar Identificación.
6. Completar Elementos sujetos a verificación.
7. Completar Autorización.
8. Registrar decisión/resultado del movimiento.
9. Guardar evento de Bitácora.

## Registro de Bitácora
Entidad operacional conceptual `LogbookAccessEvent`:
- `event_id`
- `instance_country_id`
- `company_id`
- `client_id`
- `service_id`
- `point_id`
- `post_id`
- `movement_type`: INGRESS | EGRESS
- `object_type`: PAX | VHL | CONT
- `protocol_id`
- `protocol_version_id`
- `accreditation_name`
- `occurred_at`
- `performed_by_employee_id`
- valores/campos capturados
- evidencias
- autorizador/evidencia
- resultado
- observaciones
- auditoría

El registro debe ser inmutable; correcciones posteriores se hacen por evento de rectificación con trazabilidad.

## Resultados
Pendiente de congelar nomenclatura exacta, pero el modelo debe distinguir al menos:
- movimiento autorizado/registrado;
- movimiento rechazado/no autorizado;
- registro incompleto/excepción cuando proceda.

## Interfaces
### SGI: Comando
Configura Protocolos por Puesto y consulta Bitácora.

### SGI: Agente / SGI: Supervisor
Ejecutan Bitácora actualmente según permisos.

### Futuro SGI: Operador
Unificará la ejecución móvil.

### SGI: Cliente
Puede consumir registros/visibilidad según política de Cliente y permisos; alcance pendiente de congelar.

## QR
El modelo queda preparado para QR/Barcode como método de identificación o precarga:
- escanear QR;
- recuperar datos conocidos;
- completar únicamente los campos restantes;
- conservar qué datos fueron precargados y cuáles capturados manualmente.

## Auditoría
Eventos conceptuales:
- ACCESS_PROTOCOL_CREATED
- ACCESS_PROTOCOL_VERSION_PUBLISHED
- ACCESS_PROTOCOL_IMPORTED
- ACCESS_PROTOCOL_DEACTIVATED
- LOGBOOK_ACCESS_EVENT_RECORDED
- LOGBOOK_ACCESS_EVENT_REJECTED
- LOGBOOK_ACCESS_EVENT_RECTIFIED

## Relación con REGESEP
Recomendación pendiente de congelar:
Los Protocolos de Acceso activos del Puesto deben formar parte del REGESEP estructurado del Punto/Puesto. Una nueva versión publicada de un Protocolo operativo debería versionar el REGESEP, preservando la misma formalidad definida para Consignas.

## Gobierno y alcance de la Biblioteca de Protocolos

### Protocolos exportables de Compañía
Un Coordinador puede crear un Protocolo y marcarlo `Exportable`.

Regla:
- solo es visible/importable al configurar Puestos pertenecientes a la misma Compañía del Coordinador;
- no se publica transversalmente a otras Compañías;
- conserva autor, Compañía propietaria y versiones.

### Protocolos Estándar Cajamarca
Alcance:
- toda la operación autorizada de la Instancia–País;
- visibles/importables para todos los Puestos y Compañías del contexto.

Creación:
- Director Zonal;
- Director Nacional.

Los Estándares Cajamarca deben tener identidad y versionado propios y nunca depender de un Puesto específico.

### Aplicación forzada
Privilegio exclusivo:
- Director de Operaciones Nacional.

Puede publicar una nueva versión de un Protocolo Estándar Cajamarca y ordenar `FORCED_ROLLOUT`.

Efecto:
1. se identifica cada Protocolo/Puesto derivado del Estándar;
2. se crea una nueva versión local efectiva basada en la nueva versión estándar;
3. se preserva la versión local anterior;
4. se conserva referencia al Estándar/version origen;
5. el cambio entra en vigor conforme a la política de despliegue;
6. se versiona el REGESEP de cada Punto/Puesto afectado;
7. se registra auditoría completa.

Regla recomendada ante personalizaciones locales:
- las personalizaciones compatibles se conservan;
- si una personalización contradice explícitamente una regla forzada del Estándar, prevalece el cambio forzado;
- la diferencia/conflicto queda registrada y visible en auditoría;
- nunca se sobrescribe historial.

Para actualizaciones NO forzadas:
- se muestra `Actualización disponible`;
- Coordinador/Asistente decide si comparar/aplicar.

## Lógica booleana de requisitos — Y/O
Los requisitos de Identificación, Verificación y Autorización deben poder componerse mediante grupos lógicos.

Operadores:
- `ALL` = Y = deben cumplirse todos los hijos.
- `ANY` = O = debe cumplirse al menos uno de los hijos.

Se permiten grupos anidados.

Ejemplo:
`Rostro Y (Cédula O Licencia de Conducir)`

Representación:
```text
ALL
├── Rostro
└── ANY
    ├── Cédula
    └── Licencia de Conducir
```

Cada nodo hoja puede tener:
- nombre;
- descripción;
- obligatorio según la expresión;
- método/tipo de captura;
- fotografía/archivo de Estándar de referencia;
- política de evidencia;
- validaciones.

Entidad conceptual:
`ProtocolRuleNode`
- `node_id`
- `parent_node_id`
- `node_type`: GROUP | FIELD
- `logical_operator`: ALL | ANY (solo GROUP)
- `field_id` nullable
- `display_order`

La evaluación del Protocolo debe resolver el árbol completo para determinar si la sección cumple.

## Captura manual + lectura automática
Principio:
- todo campo configurado en SGI: Comando SIEMPRE puede llenarse manualmente en SGI: Agente / futuro SGI: Operador;
- la lectura automática es un acelerador, no una dependencia obligatoria.

Fuentes de captura iniciales:
- `MANUAL`
- `QR`
- `BARCODE`

Un campo puede aceptar una o varias fuentes.

Configuración conceptual:
`FieldCaptureConfig`
- `field_id`
- `allowed_sources`
- `auto_fill_mapping`
- `manual_override_allowed = true`
- validación de formato

Ejemplo:
Una credencial con QR puede llenar:
- nombre;
- número de identificación;
- empresa;
- vigencia.

Luego el Agente completa manualmente:
- rostro/fotografía;
- mochila;
- autorización adicional.

El Registro de Bitácora conserva provenance por valor:
- `MANUAL`
- `QR`
- `BARCODE`

y el identificador de lectura/payload cuando corresponda.

## REGESEP
Regla congelada:
- todos los Protocolos de Acceso activos forman parte formal del REGESEP del Punto/Puesto;
- publicar/modificar/desactivar un Protocolo efectivo genera nueva versión del REGESEP;
- una aplicación forzada de Estándar Cajamarca genera nuevas versiones de REGESEP en todos los Puestos afectados;
- cada versión conserva qué Protocolo/version originó el cambio.

## Decisiones congeladas — AMBOS y resultado
### Aplicación `AMBOS`
`AMBOS` significa que el mismo Protocolo y exactamente las mismas reglas aplican tanto a Ingreso como a Egreso.

Si Ingreso y Egreso requieren reglas distintas:
- crear dos Protocolos separados;
- uno `INGRESO`;
- otro `EGRESO`.

### Resultado del Protocolo
Estados de resultado:
- `AUTORIZADO`
- `RECHAZADO`
- `EXCEPCION_AUTORIZADA`

`EXCEPCION_AUTORIZADA` permite completar el movimiento aunque una o más reglas no se hayan cumplido, pero exige:
- motivo;
- usuario/rol/autorizador;
- fecha/hora;
- regla(s) incumplida(s);
- evidencia/observación;
- auditoría completa.

## Protocolos multiobjeto
Un Protocolo de Acceso puede aplicar a uno o más tipos de objeto dentro de un mismo movimiento.

Ejemplos:
- `PAX`
- `VHL`
- `CONT`
- `PAX + VHL`
- `VHL + CONT`
- `PAX + VHL + CONT`

La UI puede ofrecer primero las combinaciones más comunes, sin limitar el modelo.

### Cardinalidad por tipo
Cada tipo incluido debe poder definir cuántas instancias admite o exige.

Ejemplo `PAX + VHL`:
- `VHL`: exactamente 1.
- `PAX`: 1..N.

Ejemplo `VHL + CONT`:
- `VHL`: exactamente 1.
- `CONT`: 1..N según operación.

Conceptualmente:
`ProtocolObjectRequirement`
- `protocol_id`
- `object_type`: PAX | VHL | CONT
- `min_count`
- `max_count` nullable
- `display_order`

### Configuración por objeto
En un Protocolo multiobjeto, cada objeto conserva sus propias reglas:

`PAX`
- Identificación
- Verificación
- Autorización si aplica

`VHL`
- Identificación
- Verificación
- Autorización si aplica

Además pueden existir reglas compartidas del movimiento completo.

Ejemplo:
- `PAX`: Rostro Y (Cédula O Licencia).
- `VHL`: Placa Y fotografía de cajuela.
- Regla compartida: autorización del ingreso por Administrador de Planta.

### Ejecución
No se crean registros independientes que luego deban enlazarse manualmente.

Se crea un único:
`AccessMovement`

que contiene:
- 1..N `AccessObjectInstance`.

Ejemplo:
`MOV-00842`
- VHL: ABC-1234
- PAX: Juan Pérez
- PAX: Ana Torres
- PAX: Luis Gómez

Todos pertenecen al mismo movimiento, hora, Puesto, Protocolo y resultado.

### Ventaja
Esto permite modelar correctamente casos reales como:
- vehículo con conductor y pasajeros;
- camión con conductor + contenedor;
- vehículo con varios PAX;
- transporte con VHL + PAX + CONT.

Cada objeto mantiene su identidad y evidencia, pero la operación se audita como una sola transacción de acceso.

## Decisiones congeladas — combinaciones de objetos
- El Protocolo puede incluir cualquier combinación de `PAX`, `VHL` y `CONT`.
- La UI puede priorizar combinaciones frecuentes, pero el modelo no se limita a ellas.
- En `PAX + VHL`, PAX incluye conductor y todos los pasajeros.
- Cada PAX se acredita individualmente dentro del mismo `AccessMovement`.

## Movimiento vs permanencia
Recomendación de diseño:
- `AccessMovement` registra cada evento de `INGRESO` o `EGRESO`.
- `AccessStay` / `AccessPresenceSession` vincula el Ingreso con su Egreso posterior para saber qué objetos continúan dentro del Punto.

Ejemplo:
- Ingreso MOV-0101 → abre SES-0501.
- Egreso MOV-0198 → cierra SES-0501.

La sesión puede contener varios objetos:
- 1 VHL
- 3 PAX
- 1 CONT

Cada objeto puede egresar conjuntamente o, si la operación lo permite, quedar pendiente de egreso individual según reglas futuras.

## Estado de presencia
La Bitácora debe poder responder en tiempo real:
- Personas actualmente dentro.
- Vehículos actualmente dentro.
- Contenedores actualmente dentro.
- Movimientos sin Egreso correlacionado.
- Tiempo de permanencia.

Esto es una vista derivada de movimientos/sesiones; no reemplaza el registro inmutable.

## Observaciones
Cada registro de Bitácora debe incluir siempre un campo libre de `Observaciones`, aun cuando el Protocolo no lo configure explícitamente.

Uso:
- detalle de autorización;
- irregularidades;
- contexto adicional;
- comentarios del Agente.

## Firma operacional
Cada movimiento conserva:
- timestamp;
- employee_id del Agente ejecutor;
- turno/relevo activo cuando aplique;
- dispositivo/canal;
- firma/atribución electrónica operacional.

No implica necesariamente firma criptográfica avanzada; representa atribución auditable del registro al Agente autenticado.

## Listas Blancas / Listas Negras — diseño base
La Bitácora debe soportar listas de entidades conocidas para PAX/VHL/CONT.

### Lista Blanca
Objetos/personas previamente reconocidos o autorizados según política.

### Lista Negra
Objetos/personas con restricción, alerta o prohibición según política.

Cada entrada debe permitir:
- object_type;
- clave(s) de identificación;
- nombre/descripcion;
- vigencia;
- motivo;
- alcance Punto/Cliente/Instancia según permiso;
- evidencia/documento;
- creado_por;
- estado;
- auditoría.

El efecto exacto de White/Black List sobre el resultado del Protocolo queda pendiente de congelar.

## Matching
Al capturar identificación manual/QR/Barcode:
1. SGI normaliza claves (cédula, placa, número contenedor, etc.).
2. Busca coincidencias en listas aplicables.
3. Muestra alerta/estado al Agente.
4. El Protocolo continúa según política configurada.

No debe existir matching biométrico implícito sin una regla/servicio específico.

## Decisiones congeladas — sesiones y listas
- SGI mantiene sesión/presencia abierta para cada PAX/VHL/CONT que continúa dentro.
- Cada objeto puede egresar independientemente de otros objetos con los que ingresó.
- Lista Blanca no autoriza automáticamente; solo satisface una condición si el Protocolo lo define.
- Lista Negra genera alerta/bloqueo normal, pero puede continuar mediante `EXCEPCION_AUTORIZADA` por un autorizador habilitado.
- Alcance inicial de Listas Blancas/Negras: Punto o Cliente.

## Regularización de presencia duplicada
Caso:
se intenta registrar un nuevo `INGRESO` de un objeto que ya figura `DENTRO` por una sesión previa no cerrada.

Ejemplo:
- PAX Juan Pérez figura dentro desde 07:42.
- A las 16:10 intenta ingresar nuevamente.
- En realidad salió a las 12:35, pero el Egreso no fue registrado.

Regla:
SGI NO crea una segunda presencia activa silenciosamente.

Flujo:
1. detectar coincidencia con una `AccessPresenceSession` abierta;
2. mostrar al Agente el Ingreso previo y sus datos;
3. preguntar cuándo ocurrió realmente el Egreso anterior;
4. registrar un `EGRESO_REGULARIZADO`;
5. cerrar la sesión previa con `actual_exit_at` declarado;
6. conservar `regularized_at` como momento real en que el sistema fue corregido;
7. conservar Agente que regularizó, motivo/observación y evidencia si aplica;
8. continuar con el nuevo Ingreso como un movimiento nuevo e independiente.

### Datos de regularización
Entidad/evento conceptual:
`PresenceRegularization`
- `regularization_id`
- `presence_session_id`
- `object_instance_id`
- `declared_exit_at`
- `regularized_at`
- `regularized_by_employee_id`
- `reason`
- `observation`
- `source_channel`
- `linked_new_ingress_movement_id` nullable
- auditoría

### Principio temporal
Deben conservarse dos tiempos distintos:
- `declared_exit_at`: cuándo el objeto realmente habría salido según la información recabada.
- `regularized_at`: cuándo el Agente corrigió el sistema.

Nunca se reemplaza ni elimina el Ingreso original.

### Resultado histórico
La línea de tiempo debe mostrar:
- Ingreso original.
- Egreso regularizado/retroactivo.
- quién lo regularizó y cuándo.
- nuevo Ingreso posterior.

### Alcance
La misma regla debe poder aplicarse a:
- PAX
- VHL
- CONT

aunque el caso más frecuente sea PAX.

## Ejecución UX — SGI: Agente / futuro SGI: Operador

### Principio
SGI: Comando define el Protocolo. La app operativa NO rediseña reglas; únicamente ejecuta la versión efectiva del Protocolo para el Puesto y registra evidencia.

Objetivos de UX:
- mínimo número de toques;
- scan-first, manual siempre disponible;
- mostrar únicamente campos/reglas aplicables;
- evitar reingresar datos ya conocidos;
- advertir conflictos antes de terminar;
- conservar trazabilidad completa.

### Pantalla principal de Bitácora
Acciones principales:
- `Nuevo Ingreso`
- `Nuevo Egreso`
- `Actualmente Dentro`
- `Historial`

El Puesto activo se deriva del Relevo/turno del Agente cuando exista.

### Flujo de Ingreso
1. Seleccionar `INGRESO`.
2. Opción preferente: `Escanear QR / Código de Barras`.
3. Alternativa: `Registro Manual`.
4. Identificar composición del movimiento: PAX/VHL/CONT según Protocolo.
5. SGI busca Protocolos/Acreditaciones aplicables a:
   - Puesto;
   - dirección;
   - composición de objetos.
6. Si existe un único Protocolo compatible, se selecciona automáticamente.
7. Si existen varios, el Agente selecciona Acreditación/Protocolo.
8. Capturar objetos y evaluar reglas `ALL/ANY`.
9. Consultar Listas Blanca/Negra.
10. Completar Verificación.
11. Completar Autorización.
12. Mostrar resumen de cumplimiento.
13. Resultado: Autorizado / Rechazado / Excepción Autorizada.
14. Guardar `AccessMovement` y abrir/actualizar sesiones de presencia.

### Flujo de Egreso
1. Seleccionar `EGRESO`.
2. Escanear/identificar un PAX/VHL/CONT.
3. SGI busca sesión(es) abierta(s) compatibles.
4. Mostrar datos del Ingreso original y objetos relacionados.
5. Seleccionar qué objetos efectivamente egresan.
6. Ejecutar Protocolo de Egreso aplicable.
7. Guardar movimiento y cerrar la presencia de cada objeto seleccionado.

### Detección de presencia duplicada
En un nuevo Ingreso:
- si un objeto ya figura `DENTRO`, bloquear la creación de una segunda sesión activa;
- mostrar la sesión previa;
- permitir `Regularizar Egreso anterior`;
- registrar hora declarada de salida y hora de regularización;
- continuar después con el nuevo Ingreso.

### Ejecución visual de reglas Y/O
La app no muestra lógica técnica `ALL/ANY`; la traduce a lenguaje operativo.

Ejemplo:
`Debe completar:`
- Rostro ✓
- `Uno de los siguientes:`
  - Cédula
  - Licencia de conducir

Cada opción puede mostrar su fotografía/Estándar de referencia.

### Captura de evidencia
Por requisito:
- botón de fotografía/documento/scan según configuración;
- Estándar de referencia visible cuando exista;
- evidencia capturada separada del Estándar;
- provenance por campo: Manual / QR / Barcode.

### Scan-first
Si el QR/Barcode contiene campos configurados:
- autocompletar;
- marcar visualmente `Precargado`;
- permitir revisión;
- manual override disponible;
- cualquier override conserva valor original y valor final en auditoría.

### Listas
Al identificar un objeto:
- Lista Blanca: indicador informativo y, si el Protocolo lo permite, satisface la regla correspondiente.
- Lista Negra: alerta prominente; resultado normal es Rechazado salvo Excepción Autorizada.
- mostrar motivo/vigencia conforme a permisos.

### Autorización
Fuentes previstas:
- Usuario SGI específico.
- Rol/Grupo del Cliente.
- Contacto externo/manual.
- No requiere autorización.
- Lista Blanca, únicamente si el Protocolo la acepta como mecanismo.

Cuando sea digital, la solicitud/respuesta debe quedar vinculada al movimiento.

### Resumen previo a guardar
Mostrar:
- dirección;
- Acreditación/Protocolo/version;
- objetos;
- reglas cumplidas/no cumplidas;
- listas;
- autorizador;
- resultado;
- observaciones.

### Registro rápido / no duplicación
El sistema debe reutilizar datos del Ingreso cuando registra el Egreso.
No se solicita nuevamente información ya conocida salvo que el Protocolo de Egreso exija nueva evidencia/verificación.

### Actualmente Dentro
Vista operativa por Puesto/Punto:
- PAX dentro.
- VHL dentro.
- CONT dentro.
- duración de permanencia.
- ingreso de origen.
- alerta de permanencias inusualmente largas (umbral pendiente).
- búsqueda por cédula/placa/contenedor/nombre.

### Historial
Filtros:
- fecha/hora;
- Ingreso/Egreso;
- PAX/VHL/CONT;
- Acreditación;
- resultado;
- Agente;
- Protocolo/version;
- Lista Blanca/Negra;
- regularizado sí/no.

### Novedad/Incidente desde Bitácora
Recomendación pendiente:
el Agente debería poder iniciar un `Incidente` o `Hallazgo` desde un registro de Bitácora, conservando automáticamente el vínculo y contexto, sin duplicar datos.

### Operación sin conectividad
Pendiente de definición.
El diseño debe considerar que captura local puede ser necesaria en un Puesto sin conexión, pero funciones como autorización digital y consulta de listas pueden depender de datos vigentes del servidor. Debe congelarse una política explícita antes de implementar modo offline.

## Decisiones congeladas — historial móvil, offline y permanencia

### Historial en SGI: Agente / futuro SGI: Operador
Por confidencialidad y peso de la aplicación:
- la app móvil NO expone un historial general/completo de Bitácora;
- SGI: Comando conserva la base histórica autoritativa;
- el dispositivo mantiene únicamente una caché operativa limitada.

#### Caché local
Después de sincronización confirmada:
- conservar texto/datos estructurados de los últimos 7 días;
- fotografías/evidencias ya sincronizadas pueden eliminarse del dispositivo;
- los datos siguen disponibles históricamente en SGI: Comando.

Antes de sincronización confirmada:
- conservar texto + fotografías + evidencias localmente;
- NO purgar evidencia pendiente;
- sincronizar automáticamente al recuperar conectividad.

La política de retención móvil de 7 días puede quedar parametrizada a futuro, pero v0.1 usa 7 días.

### Modo offline
La app debe permitir registrar movimientos sin conexión.

Offline puede utilizar:
- configuración de Protocolos previamente sincronizada;
- caché textual reciente;
- último snapshot disponible de Listas Blancas/Negras y otros datos operativos necesarios.

Debe indicarse visualmente cuando una decisión se toma con datos potencialmente desactualizados.

Al recuperar conectividad:
1. sincronizar movimientos pendientes;
2. subir evidencias/fotografías;
3. recibir confirmación server-side;
4. solo entonces aplicar política de purga local de evidencias.

## Presencia a nivel Punto y pasos entre Puestos
La presencia principal se modela a nivel `Punto`, no exclusivamente por Puesto.

Caso:
- Puesto A = Control de Acceso en perímetro exterior.
- Puesto B = Control de Acceso en perímetro interior.

Flujo:
1. Puesto A registra el Ingreso completo y abre `PointPresenceSession`.
2. PAX/VHL/CONT queda reconocido como actualmente dentro del Punto.
3. En Puesto B, el Agente identifica/escanea el objeto.
4. SGI recupera identidad y datos ya capturados desde la sesión activa.
5. El Agente NO vuelve a registrar todo desde cero.
6. Puesto B ejecuta únicamente las verificaciones/reglas adicionales de su propio Protocolo.
7. El paso queda registrado como evento interno vinculado a la misma sesión.

Entidad conceptual:
`CheckpointPassage`
- `passage_id`
- `point_presence_session_id`
- `post_id`
- `protocol_id`
- `protocol_version_id`
- `direction`: INWARD | OUTWARD | INTERNAL
- `occurred_at`
- `performed_by_employee_id`
- reglas verificadas;
- evidencia nueva;
- resultado;
- auditoría.

### Regla de datos
Los Puestos internos:
- reutilizan datos existentes;
- no sobrescriben evidencia histórica;
- pueden agregar nueva evidencia/verificación requerida por su Protocolo;
- pueden exigir reconfirmar un dato si su Protocolo expresamente lo requiere.

Esto permite múltiples anillos/perímetros dentro del mismo Punto sin duplicar acreditaciones.

## Permanencia prolongada y salida superficial
Umbral inicial:
- 24 horas;
- configurable.

Si una presencia continúa sin Egreso registrado al superar el umbral:
- SGI NO crea un Egreso oficial;
- SGI la retira de la vista principal `Actualmente Dentro`;
- la marca `POR_REGULARIZAR` / `PRESENCIA_NO_CONFIRMADA`;
- mantiene la sesión histórica sin cierre oficial;
- conserva alerta para Comando/auditoría.

El término `salida superficial` describe comportamiento de UI/operación, no un evento jurídico/operacional de Egreso.

### Si luego aparece en un Puesto de Egreso
- identificar la sesión `POR_REGULARIZAR`;
- registrar Egreso oficial con hora actual;
- cerrar la sesión normalmente;
- conservar que excedió el umbral y estuvo temporalmente retirada de la vista activa.

### Si luego aparece en un Puesto de Ingreso
- detectar sesión `POR_REGULARIZAR`;
- preguntar cuándo salió realmente;
- crear `EGRESO_REGULARIZADO` retroactivo;
- conservar `declared_exit_at` y `regularized_at`;
- luego permitir el nuevo Ingreso.

### Vistas
`Actualmente Dentro` muestra por defecto:
- sesiones activas confirmadas dentro del umbral.

Vista adicional para Comando:
- `Por regularizar / Presencia no confirmada`.

## Creación contextual de Novedades
Regla aprobada:
Desde un movimiento/evento de Bitácora se puede iniciar:
- Incidente;
- Hallazgo.

SGI precarga:
- Cliente/Servicio/Punto/Puesto;
- PAX/VHL/CONT;
- timestamp;
- Agente;
- Protocolo/version;
- resultado;
- evidencias seleccionadas;
- relación al movimiento original.

La nueva entidad conserva su propio lifecycle y no modifica el registro de Bitácora.

## Cierre funcional v0.1
### Caché móvil por Punto
En SGI: Agente / futuro SGI: Operador:
- la caché textual de 7 días se limita al Punto en el que el Agente está actualmente de servicio;
- el Agente no puede navegar registros recientes de otros Puntos donde trabajó anteriormente;
- SGI: Comando conserva el historial autoritativo completo conforme a permisos.

### Reutilización inmutable entre Puestos del mismo Punto
Cuando un PAX/VHL/CONT ya fue acreditado en un Puesto del Punto:
- otro Puesto puede reutilizar identidad y datos previamente capturados;
- los datos/evidencias originales son de solo lectura;
- el segundo Puesto puede agregar nuevas verificaciones, evidencias o datos exigidos por su propio Protocolo;
- toda nueva evidencia queda atribuida al Puesto, Protocolo/version y Agente que la capturó;
- nunca se sobrescribe el registro original.

Con estas decisiones, SGI-09 Bitácora v0.1 queda funcionalmente cerrado a nivel conceptual.

---

## Delta de implementación SER v0.6

SER v0.6 implementa la primera superficie funcional de Configuración → Bitácora respetando la separación Configuración / Operación. Se agrega persistencia de Protocolos y campos, upload de Foto estándar y preparación explícita para futura comparación VISINT.

La ejecución real de eventos de Bitácora permanece fuera de alcance de esta versión.


## Delta SER v0.6.1 — Acreditaciones explícitas
La implementación corrige la jerarquía funcional a `Punto → Puesto → Protocolo → Acreditación → Reglas/Campos`. Un Protocolo puede contener varias Acreditaciones, y cada una posee sus propias reglas ALL/ANY, Autorización, Captura, Listas y campos de Identificación/Verificación. Evidencias continúa siendo un resumen derivado de los campos marcados con evidencia dentro de la Acreditación seleccionada. La ejecución futura deberá persistir la Acreditación utilizada.
