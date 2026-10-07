# BITACORA-EXECUTION-001 - Validación UAT

## Alcance

SGI Operador registra una ejecución de Bitácora contra el único Protocolo activo aplicable al Puesto. SGI Comando valida tenant, identidad, asignación, Punto/Puesto, versión de Protocolo, acreditación, campos y evidencias obligatorias antes de persistir.

## Trazabilidad

- Interconexión: `SGI_OPR_SGI_COM_0002_v001`.
- Interfaz: `SGI_OPR_SGI_COM_0002_IF03`.
- Escritura: `POST /api/v1/operator/logbook-records`.
- Lectura interna: `GET /api/v1/operator/logbook-records`.
- Idempotencia: `Idempotency-Key` coincide con `recordId`; reintento idéntico devuelve el acuse existente y contenido diferente devuelve `409`.
- Auditoría: usuario, empleado, tenant, `correlationId`, captura y recepción quedan persistidos.

## Casos

1. Sin Protocolo/acreditación vigente: la app bloquea el envío.
2. Protocolo cambiado o fuera del Puesto: Comando responde `409`/`400` y no persiste.
3. Campo o evidencia obligatoria ausente: Comando responde `400`.
4. Registro válido: Comando responde `REGISTERED` y aparece en Operaciones / Bitácora.
5. Mismo `recordId` y payload: respuesta idempotente sin duplicado.
6. Mismo `recordId` con payload distinto: `409`.
