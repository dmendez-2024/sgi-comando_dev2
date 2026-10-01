# PRIORIDAD DE HANDOFF — SISTEMAS — 2026-09-28

La baseline funcional queda **FROZEN** al 2026-09-27. No continuar cambios sobre estas versiones. SISTEMAS debe tomar esta entrega como baseline inmutable y abrir nuevas versiones para cualquier corrección o implementación productiva.

Pendientes prioritarios: gate técnico completo; productivización de CSL Incidentes/Reasignación; y diseño/implementación productiva de NEX runtime EVC/BPM, persistencia y contratos de eventos.

---

# SGI: Comando — siguientes acciones después del RC P0/P1 2026-09-27

1. **SISTEMAS: validar una base desechable/fresca.** Levantar PostgreSQL vacío y comprobar que Flyway aplica V1..V33 sin que V30/V31 borren o siembren empleados.
2. **SISTEMAS: clasificar cualquier base existente.** Si ya aplicó V30/V31 antiguos, respaldar y seguir `docs/MIGRATION_SAFETY_V30_V31.md`; no desactivar validación ni alterar schema history manualmente.
3. **SISTEMAS: generar y versionar lockfile frontend.** Ejecutar instalación controlada con las versiones exactas de `frontend/package.json`, generar `package-lock.json`, ejecutar build y registrar hash/resultado.
4. **CORE:** cargar `sitc/SGI_COM_P0P1_20260927_COMPONENT_DELTA.sitcpack` o CURRENT como ESCENARIO, revisar preview/merge y conflictos; confirmar CORE `UNIVERSAL` y los 25 IDs canónicos.
5. **CORE / Impulsos:** crear/confirmar el binding de `SGI_COM_CORE_0001_v001` y la definición versionada de reglas de Impulsos. No activar productivo hasta tener contrato y pruebas.
6. **SIC_COM y SIC_RRHH:** homologar los mismos IDs v4.1/contractVersion/credential_ref y ejecutar pruebas bilaterales. Para RRHH, enviar `companyCoreCatalogId` o `companyCode` en altas nuevas y probar cambio de Compañía, idempotencia, stale events, errores y trazabilidad.
7. **SGI_OPR:** mantener los IDs v4.1 ya definidos para runtime/relevo y probar contra esta baseline de Comando.
8. **Gate pre-producción:** Maven/build, tests, Docker/UAT, migraciones, seguridad, regresión básica y comparación baseline vs RC. Actualizar changelog con resultados reales.

**Fuera de alcance:** no iniciar EVC/Eventos de Cumplimiento dentro de este RC. Ese concepto se evaluará posteriormente sobre esta baseline una vez aceptada por SISTEMAS.

## Después de UAT CSL v0.2.1
1. Validar UX del panel Notificación de Incidente y la taxonomía exacta del Excel con Operaciones.
2. Implementar backend de Incidentes: persistencia, adjuntos, consulta de colaboradores de 14 días y cálculo real de disponibilidad desde Asignaciones.
3. Definir activación/routing STC y almacenamiento de imágenes sin cambiar contratos hasta decisión formal.
4. Mantener EVC fuera de esta vertical.

**Decisión resuelta:** `Inasistencia programada` / `Inasistencia efectiva` son tipos de incidente bajo `Asistencia y Puntualidad`, no subcategorías.

## CSL v0.2.5 — pendiente productivo
- Definir SoR/API productiva para teléfono del colaborador.
- Definir SoR/API y regla de cómputo para `Francos Trabajados` en ventana móvil de 6 meses.
- En UAT ambos valores permanecen DEMO/local.

## NEX v0.1 — pendiente productivo
- Confirmar catálogo canónico de Objetos + Eventos/Estados por vertical.
- Definir contratos de publicación/consumo de eventos y correlación.
- Definir persistencia/versionado productivo de reglas Nexus.
- Definir runtime EVC y acciones permitidas por seguridad/rol.
- Mantener en NEX v0.1 la UAT frontend/localStorage hasta aprobación funcional.
