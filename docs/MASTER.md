# SGI: Comando — Modelo Maestro

**Versión documental:** 0.1 (work in progress)  
**Fecha:** 2026-09-07

## Propósito
SGI: Comando es la capa de configuración, conducción, monitoreo y control de la operación de seguridad física de Cajamarca. Consume datos maestros desde CORE y los SIC; agrega contexto y estado operacional sin duplicar los System of Record.

## Arquitectura raíz
CORE → Instancia → Instancia–País → SGI: Comando.

Todo objeto operacional SGI queda aislado por `instance_country_id`.

## Alcance inicial
- Seguridad física estática: Cliente → Servicio → Punto → Puesto → operación.
- Seguridad física dinámica (futuro): Escoltas de Seguridad (Ronin), rutas y REGESER.

## Módulos / verticales
- SGI-00 CORE / Instancia–País — definido v0.1.
- SGI-01 Compañías — definido v0.1.
- SGI-02 Servicios — SER v0.1 UAT.
- SGI-03 Puntos — definido v0.1.
- SGI-04 Puestos — cerrado v0.1.
- SGI-05 ATS / importación `.ats` — diferido; contrato reservado.
- SGI-06 Asignaciones — ASI v0.6.5 FROZEN.
- SGI-07 Relevos — pendiente.
- SGI-08 Consignas — pendiente.
- SGI-09 Bitácora — pendiente.
- SGI-10 Patrullas — pendiente.
- SGI-11 REGESEP — pendiente.
- SGI-12 Comunicación — pendiente.
- SGI-13 Recurso Humano — pendiente.
- SGI-14 Recurso Material — pendiente.
- SGI-15 Dashboard / Estado Operacional — pendiente.
- SGI-16 Reportes — pendiente.
- SGI-17 Auditoría — pendiente.
- SGI-18 Configuración — pendiente.

## Reglas maestras congeladas
1. CORE es SoR de Instancia–País y parámetros transversales.
2. SIC: COM es SoR de Cliente, Servicio, Punto, Puesto, estructura de turnos y FHE vendido; FHE proviene de CCS.
3. SGI: Comando no recalcula ni modifica unilateralmente turnos/FHE vendidos.
4. SIC: RRHH es SoR de personas, relación laboral, cargos y estado laboral.
5. SIC: RRMM es SoR de activos/materiales.
6. ATS es módulo externo de diseño de seguridad y exporta `.ats`; SGI consume el diseño.
7. REGESEP se construye/gestiona en SGI: Comando para seguridad estática, apoyándose en ATS.
8. Un Servicio puede contener Puntos bajo distintas Compañías; cada Punto tiene exactamente una Compañía responsable activa.
9. Los FHE se agregan por Compañía, nunca inter-Compañías como métrica operacional.
10. Un Punto puede cambiar de Servicio conservando su identidad.
11. La Compañía es la fuente operacional de personal: RRHH → Compañía → Puntos → Puestos.

## Compañías
- Nombre libre + código automático `COM-###`.
- Nombre único por Instancia–País; puede repetirse en otra Instancia–País.
- Logo y reseña histórica obligatorios al alta.
- UI estados: Borrador / Activa / Inactiva.
- Tamaño referencial: 200–400 **Agentes de Seguridad**; advertencia, no bloqueo.
  - <200: Sub Capacidad.
  - 200–400: Capacidad Normal.
  - >400: Sobre Capacidad.
- Roles: Coordinador de Compañía; Asistente de Coordinación; Supervisor de Seguridad (Kidon Rouge); Agente de Seguridad; Escolta de Seguridad (Ronin).
- Cada persona tiene una sola Compañía Primaria activa; apoyo/refuerzo temporal será figura separada futura.

## Dotación Humana
Métrica separada de Dotación de Materiales.

Por Compañía:
- `FHE_compañía = Σ FHE de Puestos vigentes de todos sus Puntos`.
- `Delta_actual = AdS_reales - FHE_compañía`.
- `AdS_proyectados = AdS_reales - Agentes_con_cambio_requerido`.
- `Delta_proyectado = AdS_proyectados - FHE_compañía`.

Estados para delta actual/proyectado:
- `<0`: Subdotación.
- `0..1`: Óptimo.
- `>1`: Sobredotación.

El FHE puede ser fraccionario.

### Cambio requerido
Coordinador o Asistente puede marcar un Agente activo como `Cambio requerido`. El Agente sigue contando en AdS Reales hasta que el cambio se ejecuta, pero se descuenta inmediatamente de AdS Proyectados. No requiere aprobación previa de RRHH para afectar la proyección.

Motivos v0.1:
- CR-01 Bajo desempeño.
- CR-02 Incumplimiento de consignas o procedimientos.
- CR-03 Conducta o disciplina inadecuada.
- CR-04 Incompatibilidad con el puesto o servicio.
- CR-05 Solicitud del cliente.
- CR-06 Cambio de perfil requerido por el servicio.
- CR-07 Renuncia anunciada.
- CR-08 Solicitud de cambio del colaborador.
- CR-09 Disponibilidad futura incompatible.
- CR-10 Requisito o habilitación no cumplido.
- CR-11 Decisión operacional del mando.
- CR-99 Otro.

`Cambio requerido` es una necesidad operacional; no equivale a sanción, baja laboral ni modificación de la relación laboral en SIC: RRHH.


## Puestos — reglas clave
- La estructura horaria se denomina **Formato**; puede ser resumible (24/7, 12/5, etc.) o **Personalizado**.
- FHE se maneja con dos decimales y puede ser fraccionario.
- Actividades: Control de Acceso (ACC), Patrulla Operativa (PAT), Vigilancia (VIG); dos o más actividades derivan Tipo Mixto (MIX).
- 8 habilidades escala 0–5; MIX toma el máximo por habilidad entre actividades componentes.
- Varias fotografías operacionales por Puesto, una principal.
- Un Puesto no cambia de Punto conservando identidad: se cierra y se crea uno nuevo en SIC: COM.

## ATS — estado diferido
ATS todavía no está construido. Se reserva la integración `.ats` para planos, ubicación inicial de Puestos, UAP, amenazas, vulnerabilidades, riesgos, componentes de seguridad sobre plano y generación de Índice de Riesgo.

### SGI-10 Patrullas — simplificación de Hitos
- Patrulla Cerrada: todos los Hitos son obligatorios.
- Hito: Cumplido / No cumplido.
- Cualquier Hito no cumplido → Patrulla Incompleta.
- Sin Hitos opcionales, sin Hito Exceptuado y sin duración objetivo en v0.1.


### Estado de implementación — UAT v0.2
`SGI-06 Asignaciones` pasa de diseño congelado a primera vertical operacional implementada sobre PostgreSQL real y providers LOCAL de SIC: RRHH / SIC: COM / SMC.

## Control de versión por vertical
La matriz canónica de versiones de esta entrega está en `VERSION_MATRIX.md`. La vertical activa es **SGI-06 Asignaciones v0.2**.


## Checkpoint UAT v0.3
Se incorpora SGI-00T Territorio antes de Compañías. La jerarquía pasa a País → Zona → Región → Compañía → Punto → Puesto y el alcance de autorización se resuelve server-side. SGI-06 Asignaciones v0.3 añade selección de persona con evaluación visual GREEN/AMBER/RED, fotos ficticias y un caso de tres turnos de 8 horas en un mismo Puesto.

## Checkpoint UAT v0.4
La jerarquía operacional incorpora `Territorio` como vertical previa a Compañías: Instancia–País → Zona → Región → Compañía. SGI-06 Asignaciones v0.4 prioriza operación tipo hoja de cálculo, borradores persistentes y trazabilidad de IC/ID/TIER. Ver `docs/DECISIONS.md`, `docs/CHANGELOG_UAT_v0.4.md`, `docs/UAT_TEST_PLAN_v0.4.md` y `sitc/delta_SGI_UAT_v0.4.sitcpack`.

## Checkpoint SER v0.5 — 2026-09-10
SER pasa a Configuración del Punto con integración ATS real.

### ATS
- ATS ya no se considera diferido para el flujo de importación consumidor.
- SGI acepta `.ats`, conserva el paquete original, lee `manifest.json` / `model/pto.json` y materializa el plano declarado.
- Cada reemplazo se versiona por Punto.
- El Índice de Riesgo de Punto solo se muestra si ATS lo publica explícitamente.

### Puestos
- SIC: COM sigue siendo SoR de Código, Nombre, TIER, Formato, Rotación, Turnos y Horas.
- SGI agrega Tipo de Puesto, descripción, ubicación sobre plano ATS y habilidades requeridas.
- Habilidades requeridas del Puesto: mínimo 1 por habilidad; máximo una habilidad en 5; máximo dos en 4; suma máxima 22.
- La ubicación del Puesto se guarda como X/Y normalizados vinculados a la revisión ATS vigente.

> Este checkpoint **supersede** las referencias anteriores de este documento a ATS “diferido” y a habilidades requeridas de Puesto en escala 0–5.


## Checkpoint SER v0.6 — 2026-09-12
Se incorpora `Configuración → Bitácora` por Puesto mediante Protocolos. Identificación y Verificación admiten campos predefinidos/personalizados, lógica ALL/ANY, evidencia y Foto estándar versionada. La Foto estándar prepara futura integración con VISINT; Operación/ejecución real de Bitácora permanece fuera de alcance.


## Checkpoint SER v0.6.1 — 2026-09-12
Bitácora incorpora la capa explícita de **Acreditación**. La jerarquía canónica es Punto → Puesto → Protocolo → Acreditación → Reglas/Campos.

## Checkpoint SER v0.7 — 2026-09-12
- Vertical nueva: Configuración → Patrullas.
- Jerarquía canónica: `Punto → Puesto → Protocolo → Patrulla → Reglas`.
- Patrullas: Cerrada/Abierta × Programada/No Programada.
- Hitos de Patrulla Cerrada: hasta 25, secuencia Estricta/Flexible.
- Levantamiento mixto: Plano ATS (`ATS`) + Campo/GPS (`FIELD`) + enriquecimiento cruzado (`MIXED`).
- Foto estándar por Hito preparada para futura integración VISINT.
- Regla transversal de Configuración: publicado = snapshot inmutable; edición = nueva versión BORRADOR; publicación sustituye vigencia y conserva histórico.
- Bitácora queda retroajustada a este mismo patrón de versionado.


## Actualización 2026-09-19 — CORE / SIC: RRHH / SIC: COM / SER v0.9
- **CORE** es SoR de identidad de Compañía; COM v1.1.3 queda FROZEN.
- **SIC: RRHH** es SoR de la relación persona–Compañía. Para ingreso a SGI debe entregar personal adscrito a **Seguridad Física (SF) + Compañía**.
- **SIC: COM** es SoR de `Servicio = Cliente + Punto` y sus Puestos; no determina Compañía operativa.
- **SER v0.9** recibe Servicios nuevos con Compañía pendiente (`company_id = NULL`), los presenta en bandeja lógica **Kaibil** y Coordinación asigna la Compañía operativa según ámbito. Kaibil no opera Servicios de clientes.
- Tras la asignación inicial se habilita Configuración del Punto; mover posteriormente un Servicio ya asignado queda fuera de v0.9.


## SER v0.9.1 — retiro y reasignación de Servicios
- Coordinación autorizada puede retirar un Servicio asignado hacia la bandeja lógica Kaibil según alcance territorial.
- La configuración operacional permanece ligada al Punto y no se borra/copia al cambiar de Compañía.
- Solo las asignaciones futuras desaparecen de planificación activa; histórico y turno en curso se conservan.
- `operational_transition_until` protege el cierre del turno heredado y evita doble cobertura en la nueva Compañía.
- Desde Kaibil se reasigna directamente a otra Compañía autorizada sin aceptación del Coordinador destino.

## Checkpoint transversal VISINT / Impulsos — 2026-09-20

Se congela conceptualmente la orquestación de evidencia visual y gamificación entre SGI: Operador, SGI: Comando y VISINT:

1. Operador completa la tarea y captura evidencia.
2. La evidencia entra a SGI: Comando; no existe integración directa Operador→VISINT.
3. SGI: Comando solicita y recibe la revisión VISINT.
4. VISINT devuelve la evaluación visual, sin decidir premios.
5. SGI: Comando ejecuta la regla vigente de Impulsos (elegibilidad, probabilidad, cantidad y habilidad) y registra el resultado de forma idempotente.
6. SGI: Operador muestra estado de revisión y, cuando exista, el premio otorgado.

Ver `docs/SGI_OPR_VISINT_IMPULSOS.md`.


## INT v0.1 — Módulo genérico de interconexiones (2026-09-21)
SGI: Comando incorpora un único módulo reusable para integraciones salientes conforme a SITC-NOM-001 v3.0. La lógica de negocio ya no debe crear clientes HTTP ad-hoc ni hardcodear hosts/puertos/credenciales.

Flujo técnico: `Business Adapter → GenericInterconnectionExecutor → CORE resolver/cache → auth/resilience/observability → programa destino`. CORE resuelve configuración por `interconnectionId + instance_country_id + ambiente`, pero no actúa como proxy del tráfico funcional.

Componentes: `CoreInterconnectionResolver`, `ResolutionCache`, `CredentialRefResolver`, `CircuitRegistry`, `GenericInterconnectionExecutor`, catálogo canónico e IDs. La URL bootstrap del resolver CORE se configura por ambiente; todos los demás bindings provienen de CORE.

Ver `docs/INTERCONNECTIONS.md`, `docs/API_CATALOG.md`, `docs/SITCPACK.md` y `sitc/SGI_Comando_CURRENT.sitcpack`.
