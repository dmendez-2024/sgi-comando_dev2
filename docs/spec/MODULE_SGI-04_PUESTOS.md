# SGI-04 — Puestos v0.1 (Cerrado)

## Propósito
Representar operacionalmente los Puestos vendidos por SIC: COM. SGI no crea ni edita comercialmente el Puesto; agrega configuración operacional, tipo/actividades, perfil de habilidades, requerimientos y enlaces futuros a REGESEP, consignas, relevos, patrullas y bitácora.

## Fuente y propiedad
- `post_id`, identidad comercial, Punto padre, Formato, turnos/calendario y FHE provienen de SIC: COM.
- SGI: Comando agrega la capa operacional y no modifica los datos vendidos.
- La Compañía se hereda obligatoriamente del Punto.

## Formato
En lenguaje Cajamarca la estructura horaria vendida se denomina **Formato**.
- Formatos resumibles: `24/7`, `12/5`, `12/7`, etc.
- Cuando no puede resumirse en dos números se denomina **Personalizado**, conservando siempre el calendario/horario estructurado por día.

## FHE
- Se presenta y opera con **dos decimales**.
- Puede ser fraccionario.
- `FHE Punto = Σ FHE de Puestos vigentes del Punto`.
- `FHE Compañía = Σ FHE de Puestos vigentes de todos los Puntos de la Compañía`.
- FHE es read-only en SGI.

## Tipo de Puesto y actividades
Actividades base:
- `ACC` — Control de Acceso.
- `PAT` — Patrulla Operativa.
- `VIG` — Vigilancia.

El usuario selecciona actividades y SGI deriva el Tipo:
- Una sola actividad → ACC, PAT o VIG.
- Dos o tres actividades → `MIX` (Mixto).
- MIX nunca se selecciona manualmente y siempre conserva sus actividades componentes.

## Perfil de habilidades
- Se mantiene arquitectura de **8 habilidades**, escala **0–5**.
- Cada actividad ACC/PAT/VIG tiene un perfil predeterminado editable desde configuración.
- Al configurar un Puesto, SGI aplica el perfil predeterminado correspondiente, que luego puede ajustarse para ese Puesto sin alterar el default global.
- Para MIX, el valor inicial de cada habilidad es el **máximo** entre los perfiles de las actividades que lo componen.

## Fotografías operacionales
- SGI permite múltiples fotografías por Puesto.
- Una fotografía se marca como principal.
- Las imágenes operacionales pertenecen a SGI, no a SIC: COM.

## Identidad del Puesto
Un Puesto no puede trasladarse entre Puntos conservando identidad. Si cambia de Punto:
1. Se cierra/finaliza el Puesto en Punto A.
2. SIC: COM crea un nuevo Puesto en Punto B con nuevo `post_id`.
3. SGI conserva íntegro el histórico del Puesto anterior.

## Cambios comerciales futuros
Cambios provenientes de SIC: COM pasan por:
`Pendiente de revisión → Revisado → Preparado → Vigente`.
Los cambios efectivos de FHE recalculan automáticamente FHE de Punto, FHE de Compañía y Dotación Humana.

## Nomenclatura de código de Puesto

El código visible de Puesto se construye con:

1. Primera inicial mayúscula de Provincia/Estado.
2. Primera inicial mayúscula de Ciudad.
3. Primera inicial mayúscula del Cliente.
4. Primera inicial mayúscula del Punto.
5. Secuencia del Puesto.

Ejemplo:
- Provincia: Guayas → `G`
- Ciudad: Guayaquil → `G`
- Cliente: Telconet → `T`
- Punto: Telco-City → `T`
- Primer Puesto → `01`

Resultado:
`GGTT01`

### Colisión de base entre Puntos
Si otro Punto produce la misma base de cuatro letras, se agrega un discriminador de Punto:

- Primer Punto con base `GGTT` → primer Puesto `GGTT01`
- Segundo Punto con base `GGTT` → primer Puesto `GGTT-2-01`
- Tercer Punto con base `GGTT` → primer Puesto `GGTT-3-01`

La secuencia de Puesto sigue `01`, `02`, `03`, etc.

### Regla técnica
El código visible:
- no es la clave primaria;
- no reemplaza `post_id`;
- debe conservarse estable una vez asignado;
- cambios posteriores de nombre geográfico/Cliente/Punto no deben reescribir retrospectivamente códigos históricos sin una migración explícita y auditable.
