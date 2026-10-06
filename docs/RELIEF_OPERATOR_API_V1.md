# Contrato UAT de Relevo desde SGI Operador

Estado: UAT local. No habilitado por defecto. Versión contractual: `v1`.

## Interconexiones

- `SGI_OPR_SGI_COM_0001_v001`, interfaz `SGI_OPR_SGI_COM_0001_IF01`: consulta de contexto mediante `GET /api/v1/operator/runtime`.
- `SGI_OPR_SGI_COM_0002_v001`, interfaz `SGI_OPR_SGI_COM_0002_IF01`: recepción idempotente mediante `POST /api/v1/operator/executions`.
- `SGI_OPR_SGI_COM_0002_v001`, interfaz `SGI_OPR_SGI_COM_0002_IF02`: carga y lectura autenticada de evidencia mediante `PUT` y `GET /api/v1/operator/relief-evidence/{eventId}/{purpose}?assignmentId={assignmentId}`.

La URL efectiva y las credenciales se resuelven mediante CORE. CORE no transporta el tráfico funcional. Operador no accede a la base de datos de Comando.

## Alcance implementado

El evento admitido es `RELIEF_SUBMITTED`. Comando valida la instancia, el vínculo usuario-empleado, la asignación efectiva, el turno, el puesto, el agente saliente, la prohibición de auto relevo, las fotografías obligatorias y todas las consignas vigentes con su versión.

Cada relevo mantiene el mismo `eventId` al reintentarse. Un reintento idéntico devuelve la misma aceptación; el mismo identificador con contenido diferente responde `409`. Solo puede existir un relevo recibido por asignación.

Las evidencias UAT se guardan como JPEG, hasta 5 MB cada una, vinculadas a evento, asignación, usuario, instancia y propósito. Su lectura vuelve a validar identidad y asignación, y responde con `Cache-Control: no-store`. No se generan fotografías DEMO.

## Estado pendiente obligatorio

Mientras no exista fuente integrada de materiales y novedades, el request exige `inventoryStatus=PENDING_SOURCE`. Comando guarda el relevo con estado `PENDIENTE`, inventario `PENDING_SOURCE` y validación `PENDING_REVIEW`. La respuesta de recepción no equivale a ejecución completa, validación VISINT ni recompensa.

## Activación local

La propiedad `SGI_OPERATOR_RELIEF_UAT_ENABLED=true` habilita el contrato. Además debe existir un registro explícito en `operator_employee_binding` para el usuario autenticado. La propiedad permanece deshabilitada por defecto.

> Compatibilidad transitoria: los IDs v3 históricos pueden aceptarse como aliases donde esté documentado, pero no deben usarse para nuevos bindings CORE.

## Ubicación GPS y VISINT del puesto (2026-10-05/06, aditivo)

- El evento `RELIEF_SUBMITTED` acepta `latitude`, `longitude` y `accuracyM` (opcionales); se aplican a las 3 fotos del puesto (`station_0..2`), porque esas fotos se suben como JPEG sin metadatos.
- El runtime entrega `relief.postLocation` (`latitude`, `longitude`, `radiusM`) y `relief.stationVisint`, fuera de `configurationVersion`: cambiarlos en Comando no invalida un relevo en curso.
- Si el Puesto tiene "Validar las fotos del puesto del relevo", cada foto del puesto se valida con VISINT contra las fotos estándar del Puesto. El acuse añade `stationVisintStatus` (`QUEUED_FOR_VISINT` | `NOT_REQUESTED`); `validationStatus` no cambia.
- Ni la ubicación ni VISINT bloquean el relevo: fuera del radio solo queda el aviso "Fuera del radio GPS" en Operación. Resultado por foto: `GET /api/v1/operator/executions?groupId={eventId}` (`station`, `canRetake=false`).
- Detalle: `docs/API_CATALOG.md` → `OPR-EVIDENCE-LOCATION-001`.
