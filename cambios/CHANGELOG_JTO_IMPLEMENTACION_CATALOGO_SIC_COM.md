# CHANGELOG_JTO_IMPLEMENTACION_CATALOGO_SIC_COM

## Alcance implementado

Se implementa el receptor local de catalogo comercial de SIC:COM aprobado para SGI:Comando. No recibe, crea ni persiste ordenes de SIC:COM.

## Componentes agregados

- `V27__sic_com_commercial_catalog_inbound.sql`: crea `client`, relaciona `service.client_id`, agrega `post.commercial_status` y registra la idempotencia en `sic_com_commercial_event_receipt`.
- `operations/ClientEntity.java`: replica local del Cliente comercial identificado por codigo SIC:COM e Instancia PE.
- `siccom/SicComInboundResource.java`: recibe `POST /api/v1/inbound/sic-com/commercial-events`.
- `siccom/SicComCommercialCatalogService.java`: valida y aplica altas, actualizaciones e inactivaciones logicas del catalogo comercial.
- `siccom/SicComCommercialEventReceipt.java`: persiste respuesta y hash por `eventId` para reintentos idempotentes.

## Componentes modificados

- `operations/ServiceEntity.java`: incorpora `clientId`; conserva `clientName` como dato de compatibilidad de la vista existente.
- `operations/PostEntity.java`: incorpora `commercialStatus` para soportar la inactivacion logica de puestos.
- `services/ServiceOverviewResource.java`: expone un puesto inactivo como servicio inactivo en la vista de servicios.
- `docs/API_CATALOG.md` y `docs/interconnections/SGI_COM_INTERCONNECTION_CATALOG.json`: describen el contrato implementado sin ordenes.

## Filtro Cliente en Servicios

- `GET /api/clients` consulta `client` por Instancia PE y devuelve `id`, `code` y `name` para el combo Cliente. La pantalla ofrece únicamente clientes que tengan detalle visible para el usuario, evitando opciones sin filas.
- La vista Servicios recibe `clientId` desde `service.client_id` y filtra el detalle por ese UUID.
- La opción `Todos los clientes` no añade un filtro de Cliente; conserva sin cambios los filtros existentes de Compañía, Estado y búsqueda.

## Reglas de aplicacion

- SIC:COM sigue siendo el System of Record para Cliente, Servicio, Punto, Puesto, FHE, TIER, turnos y estado comercial.
- SGI:Comando conserva la Compania operativa, configuracion, historiales y ejecucion. Un Punto nuevo queda pendiente de asignacion operacional.
- Los codigos comerciales son unicos dentro de la Instancia PE. La respuesta devuelve los UUID SGI para Cliente, Servicio, Punto y Puesto.
- Una inactivacion usa `INACTIVE`; no se realiza `DELETE` ni se eliminan historiales. Al inactivar un Puesto se desactivan sus plantillas de turno.
- El mismo `eventId` con el mismo contenido devuelve la respuesta originalmente registrada; con contenido distinto devuelve conflicto.

## Pendiente de activacion externa

La CR habilita este alcance local. Antes de trafico real se deben provisionar en CORE/IDENT el binding efectivo y la identidad de servicio para `SIC_COM__SGI_COM__00001__V0001`; no se agregaron host, token ni secreto al repositorio.

## Ajuste de prueba Postman: identidad de servicio

- `siccom/SicComInboundServiceTokenFilter.java` valida el `Bearer` como primera acción del receptor, antes de aplicar el evento. Se retiró la dependencia de un rol JPA inexistente para esta identidad de integración.
- La referencia se configura mediante `SGI_SIC_COM_INBOUND_CREDENTIAL_REF`; el secreto se obtiene exclusivamente por `CredentialRefResolver` desde la variable de entorno derivada `SGI_CREDENTIAL_<CREDENTIAL_REF_NORMALIZADA>`. El `docker-compose.yml` admite la referencia y, para la referencia local del contrato `SIC_COM__SGI_COM__00001__V0001`, pasa `SGI_CREDENTIAL_SIC_COM__SGI_COM__00001__V0001` al backend.
- La colección conserva `Authorization: Bearer {{serviceToken}}`, deja `serviceToken` vacío y ya incorpora el `instanceCountryId` UAT y el identificador de interconexión del contrato. El token debe introducirse solamente en Postman desde la identidad de servicio del entorno.
- Sin referencia o secreto disponible, el receptor devuelve `503`; con token vacío o diferente, devuelve `401`. No se incorpora ningún token de prueba ni credencial efectiva al repositorio.
- `backend/src/main/resources/interconnections/sgi-comando-catalog.json` se alinea con el mismo contrato de Cliente/Servicio/Punto/Puesto y respuesta de UUIDs; el catálogo interno ya no publica el payload genérico anterior.

## Colección Postman regenerada

- `SIC_COM_SGI_COMANDO_CATALOGO_V3_2.postman_collection.json` incorpora tres eventos nuevos y secuenciales para Crear, Actualizar e Inactivar un puesto, sin reutilizar las claves de idempotencia de V3.1. Conserva la identidad UAT, el identificador de interconexión y `Bearer {{serviceToken}}` sin una credencial efectiva.

## Corrección de persistencia del catálogo

- `SicComCommercialCatalogService` completa los campos obligatorios de Cliente, Servicio, Punto, Puesto, ciclo y turno antes de persistir una entidad nueva. Un Puesto nuevo sin `name`, `format`, `fhe` o `tier` ahora responde validación en vez de provocar un error técnico de base de datos.

## Horarios obligatorios en altas y actualizaciones

- Para cada Puesto de un evento `COMMERCIAL_CATALOG_CREATED` o `COMMERCIAL_CATALOG_UPDATED`, el receptor exige `rotation.code`, `rotation.cycleLengthDays` entre 1 y 366 y al menos un turno con `code`, `name`, `startTime`, `endTime`, `dayMask` y `active`.
- El receptor no deduce horarios desde `format` ni `fhe`; ambos conservan su información comercial de origen SIC:COM.
- Un evento `COMMERCIAL_CATALOG_INACTIVATED` puede omitir ciclo y turnos, pues solamente realiza la inactivación lógica y desactiva las plantillas existentes del Puesto.
- La colección Postman usa identificadores `-003` nuevos y provee tres turnos de ocho horas por Puesto para que la prueba no colisione con los recibos idempotentes `-002` ya procesados.

## Carga de prueba Adheplast

- Se agrega `SIC_COM_SGI_COMANDO_ADHEPLAST_CATALOGO_CREATE.postman_collection.json` con la carga de `Adheplast S.A.`, el Servicio `SER-003467`, el Punto `PTO-2339` y los Puestos `CCT-122` y `CTT-123`.
- Se usa un `eventId` nuevo (`siccom-adheplast-create-001`) para evitar conflicto con recibos idempotentes anteriores.
- Se incluyen rotaciones y los tres turnos (`D08`, `T08`, `N08`) por Puesto. `service.clientCode` se mantiene porque el contrato vigente todavía lo exige y debe coincidir con `client.code`.
- Se agrega `SIC_COM_SGI_COMANDO_ADHEPLAST_CATALOGO_CRUD.postman_collection.json` con los tres casos ejecutables: Crear, Actualizar e Inactivar lógicamente `CTT-123`.
- Se agrega `SIC_COM_SGI_COMANDO_GUAYAQUIL_CATALOGO_CRUD.postman_collection.json` con cliente ficticio RUC `1234569807001`, ciudad `GUAYAQUIL` y los tres casos ejecutables: Crear, Actualizar e Inactivar lógicamente `PUE-GYE-001-03`.
- Se actualiza la colección de Guayaquil con el cliente `Luque Cia Ltda` y turnos encadenados de 05:00–14:00, 14:00–22:00 y 22:00–05:00.
- La colección de Guayaquil usa autenticación Bearer nativa de Postman mediante `{{serviceToken}}`; el backend quedó levantado con la referencia `SIC_COM__SGI_COM__00001__V0001` y el token efectivo se mantiene solo en runtime.
- La autenticación Bearer quedó declarada tanto a nivel de colección como en cada uno de sus tres requests para evitar que Postman envíe la solicitud sin `Authorization` al importar la colección.
- `serviceToken` queda visible como variable placeholder al importar la colección; el valor efectivo debe introducirse en Postman y no se almacena en el repositorio.
