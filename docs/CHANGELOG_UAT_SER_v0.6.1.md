# SGI: Comando — CHANGELOG UAT SER v0.6.1

## Corrección conceptual: Acreditaciones
- Se introduce explícitamente **Acreditación** entre Protocolo y Reglas/Campos.
- Jerarquía: `Punto → Puesto → Protocolo → Acreditación → Reglas/Campos`.
- Un Protocolo puede contener múltiples Acreditaciones.
- Cada Acreditación posee su propia Identificación, Verificación, Autorización, Evidencias, Captura y Listas.
- Evidencias continúa siendo un resumen derivado de Identificación/Verificación.
- Foto estándar y futura preparación VISINT permanecen a nivel de Campo, ahora vinculado a una Acreditación.
- Trazabilidad futura incorpora la Acreditación utilizada en cada ejecución.

## Persistencia
- Nueva tabla `logbook_accreditation`.
- `logbook_protocol_field` incorpora `accreditation_id`.
- La migración v0.6 → v0.6.1 materializa una Acreditación principal para cada Protocolo existente y migra sus campos sin pérdida.

## API
- `POST /api/bitacora/protocols/{protocolId}/accreditations`
- `PUT /api/bitacora/accreditations/{accreditationId}`
- `DELETE /api/bitacora/accreditations/{accreditationId}`
- `POST /api/bitacora/accreditations/{accreditationId}/fields`

## Aclaración doctrinal — función del Protocolo
- Se documenta explícitamente por qué **Protocolo** permanece como capa superior a Acreditación.
- El Protocolo permite conservar varias configuraciones completas preparadas en distintos estados de vigencia y definir cuál aplica en cada momento.
- Semántica canónica: **Protocolo = paquete/versionado de operación y vigencia**; **Acreditación = forma específica de acreditar al objeto dentro del Protocolo**.
- Flujo conceptual: seleccionar Protocolo vigente/aplicable → determinar Acreditación → ejecutar reglas/campos de esa Acreditación.
- Esta aclaración es documental y no cambia la versión funcional SER v0.6.1.
