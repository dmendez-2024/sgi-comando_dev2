# SER — Integraciones y catálogo comercial

## SIC:COM → SGI:Comando

SIC:COM conserva el SoR de Cliente, Servicio, Punto, Puesto, estado comercial, turnos, FHE y TIER. SGI:Comando mantiene una réplica local para configuración y operación; la asignación operacional de Compañía no se deriva del catálogo comercial.

### Estado en el código revisado

- Flyway V33 crea `client`, relaciona `service.client_id`, agrega `post.commercial_status` y crea `sic_com_commercial_event_receipt` para idempotencia.
- Flyway V55 establece que SGI:Comando asigna el `post.code` para las altas: SIC:COM puede omitirlo o enviarlo vacío. El código se forma con las iniciales de Provincia, Ciudad, Cliente y Nombre del Puesto; ante colisiones se usan variantes `A`…`Z`, `AA`, `AB`…, y un consecutivo que nunca se reutiliza. La respuesta del evento devuelve el código asignado.
- Flyway V56 agrega `post.code2` para conservar el campo opcional `code2` recibido desde SIC:COM; también se admite el alias histórico `Code2`. V57 lo vuelve único por `instance_country_id` cuando tiene valor y retira la unicidad de `post.code`; los registros históricos pueden permanecer temporalmente con `code2 = NULL`.
- Un `posts.code` no vacío solo identifica un Puesto ya existente de SGI para actualizarlo; no puede crear un Puesto con un código impuesto por SIC:COM. Para actualizaciones futuras, SIC:COM debe conservar el código devuelto por SGI o acordar un identificador externo estable.
- Existe el receptor local `POST /api/v1/inbound/sic-com/commercial-events` descrito en `docs/API_CATALOG.md`.
- El receptor acepta un catálogo por Cliente con `services[]`; cada servicio incluye su propia lista `points[]` y cada punto su lista `posts[]`. El formato previo de un solo `service` más `points` en la raíz continúa aceptado. Los dos formatos no pueden mezclarse en el mismo evento. Todo el lote se aplica o se revierte como una única transacción.
- SIC:COM continúa siendo la contraparte; el catálogo local de interconexiones mantiene el estado de destino como BLOCKED. La implementación del receptor SGI no equivale a integración end-to-end READY.
- `cambios/CHANGELOG_JTO_IMPLEMENTACION_CATALOGO_SIC_COM.md` reporta la migración como V27; el código actual la identifica como V33. La bitácora debe corregirse.

### Especificación y colecciones de prueba bajo `cambios/`

- La especificación más reciente localizada es `ESPECIFICACION_TECNICA_JTO_INTEGRACION_SIC_COM_SGI_COMANDO_V3_1.docx`. Mantiene estado de propuesta pendiente de CR, contrato bilateral, CORE y UAT end-to-end. V2.0/V2.1 transmitían una orden comercial; V3.0/V3.1 ya excluyen la orden interna de SIC:COM y publican un snapshot del catálogo validado.
- V3.1 define `client` como entidad propia, `service.client_id` como FK, recibo idempotente por `instance_country_id + eventId`, inactivación lógica y conservación de la configuración/historial operacional. Flyway V33 implementa el núcleo de ese modelo, incluido el backfill de clientes LEGACY.
- Las siete colecciones Postman localizadas usan el endpoint y los tres tipos de evento del diseño V3.1. Cubren ejemplos de alta, actualización e inactivación para datos genéricos y ciudades/cliente; no incluyen scripts de aserción y no hay evidencia de que se hayan ejecutado. No demuestran idempotencia, rechazo de tenant/identidad/contrato inválidos ni flujo end-to-end.
- Hay que fijar cuál colección es la vigente: conviven V3.1, V3.2 y variantes por cliente/ciudad, algunas con datos diferentes. V3.2 no reemplaza por sí sola el contrato bilateral ni la versión formal del API.
- El código exige `Idempotency-Key` y que coincida con `eventId`, además de `X-Correlation-Id`, `X-Interconnection-Id` y `X-Contract-Version: v1`. Revisar que cada colección/envíe esos valores acordes a CORE; una colección Postman por sí sola no acredita autenticación ni binding efectivo.

La ruta tiene implementación en el repositorio, aunque el catálogo de interconexiones conserva `DESIGN/BLOCKED` para el estado de la relación con la contraparte. Conviene registrar el estado local como implementado con UAT/activación end-to-end pendiente, y mantener bloqueado el estado del lado SIC:COM/CORE hasta contar con su evidencia.

## Cambio y UAT

El cambio afecta contrato/API, réplica comercial y esquema; requiere CR aprobado, compatibilidad con SIC:COM, y evidencia de prueba de ambas puntas antes de operación real. Ver `docs/DATABASE_IMPACT.md`, `docs/ECOSYSTEM_IMPACT.md` y `docs/KNOWN_LIMITATIONS.md`.

Esta nota documenta lo observado, no registra aprobación ni abre una nueva baseline SER.
