# SGI: Comando — SER v0.8.1 — Handoff cerrado a Sistemas

**Fecha de cierre:** 2026-09-12  
**Vertical:** SER — Servicios / Configuración  
**Versión funcional:** 0.8.1  
**Estado del paquete:** `CLOSED_HANDOFF_BASELINE`  
**Propósito:** entregar a Sistemas una fotografía autocontenida del código, esquema, decisiones, documentación y arquitectura alcanzada hasta este punto.

> Este cierre NO significa que toda la vertical SER esté funcionalmente terminada. Significa que **SER v0.8.1 es la baseline cerrada de handoff** a partir de la cual cualquier cambio debe ser explícito, versionado y trazable.

---

## 1. Regla de gobierno del paquete

Sistemas no debe reconstruir requisitos desde conversaciones. Las decisiones vigentes deben leerse en este orden:

1. `docs/HANDOFF_SER_v0.8.1_CERRADO.md` — estado de cierre y límites.
2. `docs/verticals/SER/SER_DECISIONS.md` — decisiones funcionales vigentes.
3. `docs/SYSTEMS_HANDOFF.md` — guía de implementación/handoff.
4. `docs/VERSION_MATRIX.md` — versiones vigentes por vertical.
5. `docs/FROZEN_VERTICALS.md` — verticales que no deben tocarse.
6. `docs/modules/*.md` y `docs/spec/*.md` — especificación por módulo.
7. `docs/CHANGELOG_UAT_SER_*.md` — historial de cambios.
8. `sitc/SGI_Comando_CURRENT.sitcpack` — arquitectura/interconexiones actuales.

Si existe una contradicción entre una decisión antigua y una posterior, prevalece la decisión más reciente marcada como vigente o la que explícitamente supersede a la anterior.

---

## 2. Baseline de verticales

| Vertical | Versión | Estado |
|---|---:|---|
| TER — Territorio | 1.0 | FROZEN |
| COM — Compañías | 1.0 | FROZEN |
| ASI — Asignaciones | 0.6.5 | FROZEN |
| SER — Servicios | 0.8.1 | CLOSED HANDOFF BASELINE |

**Regla:** TER, COM y ASI no deben modificarse como efecto colateral de trabajos posteriores sobre SER.

---

## 3. Alcance funcional de SER v0.8.1

La baseline contiene la corrida de **Servicios / Configuración del Punto** desarrollada hasta:

- listado maestro de Servicios;
- landing de Configuración del Punto;
- ATS importado mediante `.ats`;
- Puestos;
- Bitácora;
- Patrullas;
- Consignas;
- navegación corregida a Consignas desde la landing.

La separación conceptual obligatoria es:

- **Configuración = cómo debe operar el Punto**;
- **Operación = qué está pasando / qué pasó**.

La parte de Operación/live no forma parte del alcance funcional cerrado de esta baseline.

---

## 4. Versionado transversal obligatorio

Toda configuración publicada en SER debe tratarse como **snapshot inmutable**.

Flujo canónico:

`Borrador → Publicar → Versión inmutable`

Si se requiere modificar una versión publicada:

`Versión publicada → nueva versión BORRADOR por copia → editar → publicar`

Guardar un borrador **no cambia** la configuración vigente. Publicar sí puede modificar la configuración vigente/REGESEP aplicable.

El snapshot histórico debe poder reconstruir reglas, alcance, hijos, ubicación, evidencias, Fotos estándar y metadatos que aplicaban en ese momento.

---

## 5. Protocolos: decisión canónica

Los Protocolos son **independientes por vertical**. No existe un Protocolo transversal que gobierne simultáneamente Bitácora, Patrullas y Consignas.

Cada vertical mantiene su propia estructura:

### Bitácora
`Punto → Protocolo de Bitácora → Acreditación → Reglas/Campos`

- Protocolo = paquete/versionado de operación y vigencia.
- Acreditación = forma específica de acreditar un objeto dentro de ese Protocolo.

### Patrullas
`Punto → Protocolo de Patrullas → Patrulla → Reglas/Hitos`

### Consignas
`Punto → Protocolo de Consignas → Consigna → Alcance → Reglas/Evidencias`

Por Punto y por vertical debe existir **un solo Protocolo vigente**. Pueden coexistir Borradores, Protocolos publicados/disponibles y versiones históricas.

---

## 6. Alcance Punto / Puestos

La regla conceptual aprobada para los elementos configurados es:

- **Todo el Punto**, o
- **uno o varios Puestos**.

Esta regla está implementada en **Consignas v0.8**.

Está aprobada conceptualmente para:

- Acreditaciones de Bitácora;
- Patrullas;
- Consignas.

**Pendiente conocido:** el retrofit multi-Puesto de Bitácora y Patrullas NO se implementa en SER v0.8.1. Debe realizarse en una corrida posterior específica, sin alterar silenciosamente la UAT existente.

---

## 7. Bitácora

Jerarquía vigente:

`Punto → Puesto → Protocolo → Acreditación → Reglas/Campos`

Características principales:

- objetos `PAX / VHL / CONT`;
- aplicación `INGRESO / EGRESO / AMBOS`;
- Identificación, Verificación y Autorización;
- lógica ALL / ANY;
- campos institucionales + creación de campos personalizados;
- Evidencias como resumen derivado;
- Foto estándar real subida por usuario;
- preparación futura para VISINT;
- versionado inmutable de Protocolos.

Ver `docs/modules/MODULE_SGI-09_BITACORA.md` y `docs/spec/MODULE_SGI-09_BITACORA.md`.

---

## 8. Patrullas

Jerarquía vigente:

`Punto → Puesto → Protocolo → Patrulla → Reglas`

Matriz funcional:

- Cerrada + Programada;
- Cerrada + No programada;
- Abierta + Programada;
- Abierta + No programada.

Patrullas Cerradas:

- máximo 25 Hitos;
- secuencia Estricta/Flexible;
- `+ Agregar hito en plano`;
- `+ Agregar hito en campo`;
- modo mixto por Hito `ATS / FIELD / MIXED`;
- Foto estándar por Hito;
- futura comparación VISINT.

Modelo físico canónico:

`patrol_protocol → patrol_definition → patrol_checkpoint → patrol_checkpoint_rule`

**No recrear `patrol_config_*`.** Las tablas canónicas `patrol_definition` y `patrol_checkpoint` son las que debe reutilizar la futura ejecución operacional.

Ver `docs/modules/MODULE_SGI-10_PATRULLAS.md`.

---

## 9. Consignas

Jerarquía vigente:

`Punto → Protocolo de Consignas → Consigna → Alcance → Reglas/Evidencias`

Características:

- un solo Protocolo vigente por Punto;
- Consignas con alcance Todo el Punto o 1..n Puestos;
- Vigencia Permanente/Temporal;
- Aplicación Todo el tiempo/Calendario;
- reglas combinables de cumplimiento:
  - Acuse de conocimiento;
  - Confirmación;
  - Evidencia;
  - GPS de ejecución;
  - Observación;
- ubicación esperada opcional mediante ATS/coordenadas;
- múltiples Evidencias;
- Foto estándar real y versionada;
- preparación futura para VISINT;
- Evidencias como resumen/consolidación, no segunda fuente de configuración.

SER v0.8.1 corrige además la navegación desde la landing de Configuración hacia Consignas.

Ver `docs/modules/MODULE_SGI-08_CONSIGNAS.md` y `docs/spec/MODULE_SGI-08_CONSIGNAS.md`.

---

## 10. ATS y georreferenciación

- Extensión canónica: `.ats`.
- ATS es SoR del diseño publicado; SGI importa y versiona una copia consumidora.
- SGI conserva paquete, metadatos y plano materializado.
- Coordenadas de selección sobre plano se guardan normalizadas X/Y y vinculadas a revisión ATS.
- **No inferir WGS84** desde un plano ATS si el paquete no contiene calibración geográfica formal.

---

## 11. Fotos estándar / VISINT

La Foto estándar es un activo formal de configuración y puede aparecer en Bitácora, Patrullas y Consignas.

Debe conservar:

- archivo/activo;
- tipo MIME;
- versión;
- notas del estándar;
- vínculo con el objeto de configuración correspondiente.

VISINT es futuro. SER v0.8.1 solo prepara los datos para que posteriormente pueda comparar evidencia real contra estándar.

---

## 12. Persistencia y migraciones relevantes SER

Las migraciones Flyway incluidas son inmutables. Entre las relevantes de SER:

- V10 — configuración operacional de Puestos;
- V11 — importación ATS;
- V12 — Bitácora / Protocolos;
- V13 — Acreditaciones;
- V14–V16 — versionado y consolidación canónica de Patrullas;
- V17 — Protocolos/Consignas/evidencias/alcan​ce.

No editar migraciones ya aplicadas. Cualquier cambio futuro debe introducir una nueva migración incremental.

---

## 13. Pendientes deliberados / fuera de alcance de esta baseline

No interpretar estos puntos como bugs de la baseline:

- Operación/live de Servicios permanece pendiente.
- VISINT no está implementado; solo está preparada la configuración necesaria.
- Permisos Requeridos de Puestos permanece como placeholder visual.
- Retrofit de alcance `Todo el Punto / 1..n Puestos` para Bitácora y Patrullas está aprobado, pero no implementado aún.
- Algunas integraciones productivas siguen usando adapters/proxies UAT y requieren hardening antes de go-live.
- Recursos Humanos / Recursos Materiales completos dentro de Configuración deben respetar sus SoR respectivos cuando se implementen.

---

## 14. Regla para cambios de Sistemas

A partir de esta baseline:

1. No modificar verticales FROZEN como efecto colateral.
2. Hacer cambios **por vertical**.
3. Incrementar versión ante cambios funcionales.
4. Mantener changelog `.md`.
5. Actualizar `sitc/SGI_Comando_CURRENT.sitcpack` y generar delta `.sitcpack` cuando cambie arquitectura/interconexión.
6. No modificar migraciones Flyway aplicadas; crear una nueva migración.
7. Conservar publicación/versionado inmutable de configuración.
8. Antes de producción, comparar el ZIP modificado contra esta baseline cerrada.

---

## 15. Arranque UAT

Desde la carpeta que contiene `repo/`:

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-start.ps1
```

Abrir:

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-open.ps1
```

Detener:

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-stop.ps1
```

---

## 16. Identificación del handoff

Este paquete debe conservarse como referencia de comparación:

**`SGI_Comando_SER_v0.8.1_CERRADA_SISTEMAS.zip`**

Cualquier ZIP posterior de Sistemas debe poder compararse contra esta baseline para identificar exactamente qué cambió.
