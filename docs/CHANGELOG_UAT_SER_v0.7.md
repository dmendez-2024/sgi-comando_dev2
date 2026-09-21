# SGI: Comando — SER v0.7 UAT

Fecha: 2026-09-12  
Estado: UAT_CANDIDATE

## Objetivo
Incorporar **Configuración → Patrullas** y convertir el patrón de versionado de Configuración en una regla efectiva para Patrullas y Bitácora.

## Patrón de versionado transversal
- Una versión publicada es un **snapshot inmutable**.
- **Guardar borrador** solo modifica el borrador y no cambia el REGESEP vigente.
- Editar una versión publicada requiere **Crear nueva versión**, que clona toda la estructura a un nuevo borrador.
- Al publicar el nuevo borrador, la versión anterior queda histórica/No vigente y la nueva pasa a Vigente.
- El snapshot incluye hijos y activos: Patrullas, Hitos, Reglas, ubicaciones, Fotos estándar; en Bitácora incluye Acreditaciones, Campos y Fotos estándar.

## Patrullas
Jerarquía canónica:
`Punto → Puesto → Protocolo → Patrulla → Reglas`

Se implementan las cuatro combinaciones:
- Cerrada + Programada.
- Cerrada + No Programada.
- Abierta + Programada.
- Abierta + No Programada.

Reglas UAT:
- Programada: ventana Desde/Hasta, máximo 1 hora.
- Cerrada: máximo 25 Hitos.
- Cerrada: secuencia Estricta o Flexible.
- Abierta: no contiene Hitos predefinidos; los Hitos se generan en la ejecución futura.

## Configuración mixta de Hitos
Cada Hito puede provenir de:
- `ATS`: creado mediante **+ Agregar hito en plano** sobre el plano vigente importado de ATS.
- `FIELD`: creado mediante **+ Agregar hito en campo**, capturando GPS desde el dispositivo móvil.
- `MIXED`: Hito enriquecido con ambos orígenes.

El modelo no inventa una transformación ATS→GPS. El vínculo de oficina se conserva como X/Y normalizado + revisión/package ATS. El levantamiento de campo conserva latitud, longitud, precisión, fecha/hora y usuario de captura.

## Foto estándar / VISINT
- Cada Hito puede almacenar una Foto estándar JPG/PNG/WebP de hasta 5 MB.
- En móvil, el selector permite invocar la cámara del dispositivo; en oficina permite cargar un archivo.
- Reemplazar la Foto estándar incrementa su versión dentro del borrador.
- VISINT permanece como integración futura; no se ejecuta comparación automática en v0.7.

## Bitácora
SER v0.7 retroajusta Bitácora al mismo patrón: una versión publicada queda bloqueada; **Crear nueva versión** clona Protocolo, Acreditaciones, Campos y activos de imagen a un nuevo borrador antes de permitir cambios.

## Base de datos
Flyway: `V14__ser_versioned_configuration_and_patrols.sql`.

Nuevas tablas:
- `patrol_protocol`
- `patrol_config_definition`
- `patrol_config_checkpoint`
- `patrol_config_checkpoint_rule`

> Corrección física consolidada en SER v0.7.1 para evitar colisión con las tablas homónimas del baseline.

Cambios de Bitácora:
- `logbook_protocol.series_id`
- `logbook_protocol.based_on_protocol_id`
- índice único por serie + versión.

## No incluido todavía
- Ejecución real de Patrullas por SGI: Agente/Operación.
- Geofence/QR/NFC como prueba de presencia ejecutada.
- Sincronización offline de recorridos.
- Motor VISINT.
