# SGI-05 — ATS / Importación `.ats`

## Estado
**Implementación UAT activa en SER v0.5.**

ATS continúa siendo un sistema externo especializado en diseño de seguridad del Punto. SGI: Comando actúa como consumidor de la publicación `.ats`; no reconstruye ni edita el diseño ATS.

## Contrato observado `.ats`
El paquete real validado es un ZIP estructurado con, entre otros:
- `manifest.json`
- `model/pto.json`
- `model/baseline.json`
- `model/scenarios.json`
- `model/topology.json`
- `model/uap.json`
- `model/amn.json`
- `runs/*.json`
- `publications/current.json`
- `assets/index.json`
- `assets/plans/<level-id>.<ext>`

El plano principal se resuelve desde:
`model/pto.json → levels[].plan.internalPath`.

## Responsabilidad de SGI
SGI debe:
- recibir manualmente el archivo `.ats` por Punto;
- validar estructura mínima e integridad del plano;
- conservar el binario original;
- materializar el asset de plano para visualización;
- versionar cada nueva importación;
- exponer historial y descarga;
- permitir vínculos operacionales sobre el plano publicado;
- guardar ubicación de Puestos como coordenadas normalizadas ligadas a la revisión ATS vigente.

## Índice de Riesgo de Punto
El Índice de Riesgo pertenece al contenido publicado por ATS. SGI **no lo calcula** ni lo infiere. Si el `.ats` no contiene un valor explícito, la interfaz debe mostrar “no informado”.

## Seguridad del importador
- No se ejecuta contenido del paquete.
- No se extraen archivos a rutas arbitrarias del filesystem.
- Se bloquean rutas ZIP inválidas (`../`, absolutas).
- Se controlan tamaño de archivo y tamaño descomprimido.
- Solo se materializan PNG, JPEG o WebP como plano.
- Cuando ATS entrega SHA-256 del plano, SGI lo valida.

## Regla sobre ubicación de Puestos
La ubicación configurada por SGI representa el punto base del Puesto sobre la publicación ATS vigente. No implica inmovilidad del Agente durante su turno.
