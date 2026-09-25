# CHANGELOG JTO INTEGRACION SIC COM SERVICIOS

## Estado

Propuesta de implementacion. No implementada.

## Documento tecnico asociado

- Se agrega `ESPECIFICACION_TECNICA_JTO_INTEGRACION_SIC_COM_SGI_COMANDO_V2_0.docx` con la propuesta de endpoint entrante, tablas, request, response, CRUD, seguridad, pruebas UAT y decisiones pendientes.
- Se agrega `ESPECIFICACION_TECNICA_JTO_INTEGRACION_SIC_COM_SGI_COMANDO_V2_1.docx`, que incorpora la entidad propuesta `client`, su relacion propuesta con `service`, la migracion conceptual y el contrato actualizado de entrada y respuesta.
- La version 2.1 reemplaza a la 2.0 como referencia propuesta para una futura implementacion; la 2.0 se conserva como antecedente.
- Se agrega `ESPECIFICACION_TECNICA_JTO_INTEGRACION_SIC_COM_SGI_COMANDO_V3_0.docx` como documento final de propuesta: detalla la interfaz POST comercial, CRUD logico, ejemplos de request y response, errores, tablas, migracion conceptual, UAT y decisiones previas a construir.
- Se agrega `SIC_COM_SGI_COMANDO_CATALOGO_V3.postman_collection.json` con ejemplos importables de crear, actualizar e inactivar logicamente un catalogo comercial; no corresponde a una implementacion ejecutable todavia.
- Se agrega `ESPECIFICACION_TECNICA_JTO_INTEGRACION_SIC_COM_SGI_COMANDO_V3_1.docx` como revision de la propuesta: SIC COM valida y conserva sus ordenes internamente; el contrato hacia SGI Comando no incluye ni persiste el objeto `order`.
- Se agrega `SIC_COM_SGI_COMANDO_CATALOGO_V3_1.postman_collection.json` sin objeto `order`; reemplaza la coleccion V3 como referencia de pruebas propuesta.

## Objetivo

Incorporar en SGI Comando el catalogo comercial que pertenece a SIC COM: Cliente, Servicio, Punto, Puesto, turnos, FHE y TIER. SIC COM conserva el System of Record comercial; SGI Comando utiliza esa informacion para configurar y operar el servicio.

## Flujo propuesto

### Carga inicial del catalogo

1. SGI Comando inicia la sincronizacion comercial.
2. El adaptador de negocio de SGI Comando solicita al modulo generico la interconexion de catalogo SIC COM.
3. El modulo generico resuelve en CORE el binding efectivo por interconexion, `instance_country_id` y ambiente.
4. SGI Comando consulta en SIC COM el contrato registrado `GET /api/v1/sgi-export/services`.
5. La consulta usa los parametros definidos en el catalogo actual: `instanceCountryId`, `updatedSince`, `page` y `size`.
6. SIC COM responde un catalogo paginado y versionado con Cliente, Servicio, Punto, Puesto, turnos, FHE, TIER y `commercialVersion`.
7. SGI Comando valida el contrato recibido y actualiza exclusivamente los atributos comerciales que SIC COM posee.
8. SGI Comando conserva su configuracion operacional: asignacion de Compania, ATS, habilidades, protocolos, consignas, patrullas, asignaciones y ejecucion.

### Cambios posteriores al catalogo

1. SIC COM emite un evento de ciclo de vida cuando cambia un Servicio, Punto o Puesto.
2. SGI Comando recibe el evento mediante el contrato registrado `POST /api/v1/inbound/sic-com/commercial-events`.
3. El evento incluye, como minimo, `eventId`, `eventType`, `instanceCountryId`, `entityType`, `entityId`, `commercialVersion`, `occurredAt` y `payload`.
4. SGI Comando evita reprocesar el mismo evento y usa la version comercial para solicitar nuevamente el detalle necesario a SIC COM.
5. SGI Comando actualiza la replica comercial sin sobrescribir configuracion ni ejecucion operacional propias.

## Regla de propiedad de datos

| Datos | Sistema responsable |
|---|---|
| Cliente, Servicio, Punto, Puesto, estado comercial, turnos, FHE y TIER | SIC COM |
| Compania operativa, ATS, ubicacion operacional, habilidades, protocolos, consignas, patrullas, asignaciones, relevos y resultados operativos | SGI Comando |

La Compania operativa no debe ser creada ni impuesta por SIC COM. La migracion vigente indica que un Punto recibido desde SIC COM puede quedar pendiente de asignacion operacional hasta que Coordinacion lo asigne dentro de su ambito territorial.

## Componentes que se deben usar

- El adaptador de negocio de catalogo comercial debe usar `GenericInterconnectionExecutor`.
- La resolucion de bindings debe usar `CoreInterconnectionResolver` y `ResolutionCache`.
- La identidad y credenciales deben provenir de CORE/IDENT mediante `credential_ref`; no deben estar en codigo ni documentacion portable.
- El flujo debe conservar correlation ID, contrato, timeout, reintentos aplicables, auditoria y resultado de la llamada.

## Elementos de la especificacion previa que no se deben reutilizar directamente

- No reutilizar como contrato vigente el endpoint `PUT /api/integrations/sic-com/v1/services/{serviceCode}`.
- No reutilizar Basic Auth ni el rol `SIC_COM_INTEGRATION` sin una decision aprobada compatible con IDENT, identidad de servicio y `credential_ref`.
- No asumir que una carga completa exige `companyCode` activo; esa regla contradice el modelo actual de asignacion operacional posterior.
- No afirmar que existen los componentes `SicComInboundResource`, `SicComCatalogSyncService` o la tabla `sic_com_sync_receipt`; no estan presentes en el repositorio actual.
- No reutilizar identificadores v3 como nueva nomenclatura sin validar la transicion a SITC NOM 001 v4.1.

## Prerrequisitos antes de implementar

1. Crear y aprobar el Change Request de la interconexion.
2. Obtener el SCENARIO_SNAPSHOT vigente y confirmar la baseline aplicable.
3. Confirmar con SIC COM el contrato real de `GET /api/v1/sgi-export/services`: esquema de respuesta, paginacion, orden, filtro `updatedSince`, version comercial, inactivaciones y errores.
4. Confirmar con SIC COM el contrato del webhook de eventos: tipos de evento, idempotencia, autenticacion, reintentos, orden y recuperacion ante eventos perdidos.
5. Resolver formalmente la regla de Compania operativa y los estados que se pueden actualizar desde SIC COM.
6. Validar en CORE los bindings UAT de ambas interconexiones y la identidad de servicio.
7. Acordar la migracion de los identificadores y paquetes SITC v3 actuales al formato vigente v4.1.

## Pruebas requeridas para UAT

- Carga inicial de varias paginas sin duplicar Servicios, Puntos, Puestos ni turnos.
- Actualizacion de datos comerciales con una nueva `commercialVersion`.
- Inactivacion comercial sin borrar historiales ni configuracion operacional de SGI Comando.
- Reintento idempotente de la consulta y de un evento ya recibido.
- Rechazo de datos fuera del contrato y registro de correlation ID, resultado y error.
- Conservacion de la configuracion operacional cuando SIC COM actualiza un dato comercial.
- Resolucion CORE, credenciales por referencia y comportamiento ante indisponibilidad temporal del resolver.
- Validacion end to end con SIC COM antes de declarar la interconexion operativa.

## Estado actual y bloqueos

- `SGI_COM -> SIC_COM` esta registrado como `DESIGN` y el destino como `MANUAL_PENDING`.
- `SIC_COM -> SGI_COM` esta registrado como `DESIGN` y el destino como `BLOCKED`.
- El modulo generico existe, pero los adaptadores de negocio aun no lo invocan para SIC COM.
- CORE debe poder resolver el binding efectivo antes de trafico real.

## Referencias revisadas

- `docs/API_CATALOG.md`: contratos `SGI_COM__SIC_COM__00001__V0001` y `SIC_COM__SGI_COM__00001__V0001`.
- `docs/INTERCONNECTIONS.md`: estado y pendientes de las interconexiones.
- `docs/ARCHITECTURE.md` y `docs/SECURITY.md`: modulo generico, CORE, secretos y aislamiento de Instancia PE.
- `backend/src/main/resources/db/migration/V19__ser_service_company_assignment.sql`: asignacion operacional posterior de Compania.
- `ESPECIFICACION_TECNICA_INTEGRACION_SIC_COM_SGI_COMANDO.docx`: antecedente de carga comercial entrante, no adoptado como contrato vigente.
