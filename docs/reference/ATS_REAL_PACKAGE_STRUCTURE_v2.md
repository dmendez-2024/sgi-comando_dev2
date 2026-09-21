# Referencia de paquete ATS real — esquema 2.0

Validado contra un archivo `.ats` real aportado para UAT el 2026-09-10.

## Cabecera observada
- `atsSchemaVersion`: `2.0`
- `packageType`: `ATS_PUBLISHED_ARCHITECTURE`
- `compatibility.consumer`: `SGI: Comando`

## Estructura relevante
```text
manifest.json
model/pto.json
model/baseline.json
model/scenarios.json
model/topology.json
model/uap.json
model/amn.json
model/com.json
model/vul.json
model/dependencies.json
model/volumes.json
catalog/...
runs/...
publications/current.json
audit/audit_log.json
assets/index.json
assets/plans/<level-id>.png
```

## Resolución del plano
SGI usa:
`model/pto.json → levels[n].plan.internalPath`

y valida opcionalmente:
- `contentType`
- `widthPx`
- `heightPx`
- `sha256`

## Nota sobre Índice de Riesgo de Punto
El archivo de UAT inspeccionado no contiene todavía una clave explícita de Índice de Riesgo de Punto. SGI no genera un valor sustituto.
