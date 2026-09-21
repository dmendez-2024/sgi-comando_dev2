# SGI: Comando — INT v0.1 UAT (2026-09-21)

**Cambio activo:** módulo genérico de interconexiones + catálogo/IDs SITC-NOM-001 v3.0. **Sin cambios de UI.** Todas las verticales funcionales permanecen FROZEN.

Ver `README_INT_v0.1.md` y `docs/INTERCONNECTIONS.md`.

---

# SGI: Comando — COO v0.1 + SER v0.10.10 FROZEN + COM v1.1.3 FROZEN + ASI v0.7.4

Entrega combinada UAT del 19-09-2026.

## Estado de verticales
- TER — Territorio v1.0: **FROZEN**
- COM — Compañías v1.1.3: **FROZEN**
- ASI — Asignaciones v0.7.4: **UAT_CANDIDATE**
- SER — Servicios v0.10.10: **FROZEN**
- COO — Coordinación v0.1: **UAT_CANDIDATE**

## COM v1.1
- SGI ya no crea Compañías desde cero.
- Las Compañías se activan desde catálogo **CORE**.
- Nombre, Logo y Reseña histórica son de solo lectura / Fuente CORE.
- Estado, Motivo del cambio, Zona y Regiones operativas permanecen en SGI.
- **Kaibil** existe siempre Activa como Compañía de Operaciones y no puede desactivarse.

## ASI v0.7
- Transferencias de personal entre Compañías con aceptación del destino.
- SIC: RRHH permanece System of Record de persona–Compañía.
- Al enviar una transferencia se liberan inmediatamente las asignaciones futuras del origen.
- El turno actual y el histórico no cambian.
- Origen puede anular mientras está pendiente; destino puede aceptar/rechazar.
- Si se acepta durante un turno actual, el cambio es efectivo al cierre del turno.
- Personal disponible incorpora filtros de Transferencias salientes/entrantes.
- Ficha del colaborador incorpora acciones e historial de movimientos.

## UAT
Desde la carpeta externa que contiene `repo/`:

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

## Documentación principal
- `docs/CHANGELOG_UAT_COM_v1.1.md`
- `docs/CHANGELOG_UAT_ASI_v0.7.md`
- `docs/verticals/COM/COM_DECISIONS.md`
- `docs/verticals/ASI/ASI_DECISIONS.md`
- `docs/SYSTEMS_HANDOFF.md`
- `sitc/COM_v1.1_delta.sitcpack`
- `sitc/ASI_v0.7_delta.sitcpack`

## UAT FIX COM v1.1.2 / ASI v0.7.1
Corrige V18 para bases UAT con compañías legacy que pueden colisionar separadamente por **código** y por **nombre**. La reconciliación CORE ahora prioriza: `core_catalog_id` → nombre canónico → código; reutiliza el UUID existente y evita crear un segundo maestro. Las relaciones territoriales y Kaibil se resuelven por `core_catalog_id`, no por códigos asumidos.

Ver `docs/CHANGELOG_UAT_COM_v1.1.2_ASI_v0.7.1.md`.

## UAT FIX ASI v0.7.2
- Corrige la aceptación inmediata de transferencias de Compañía cuando existe una membresía PRIMARY activa: se cierra y hace `flush` antes de insertar la nueva membresía, evitando violar `ux_membership_primary_active`.
- Sustituye la confirmación nativa del navegador por un modal SGI para aceptar/rechazar/anular transferencias.
- Los errores del backend quedan visibles dentro del modal de confirmación.


## UAT FIX COM v1.1.3 / ASI v0.7.4
- Presidencia, Director Nacional, Director Zonal y Jefe Regional ingresan a Asignaciones con **Kaibil** como Compañía por defecto.
- Para esos perfiles, el selector de Compañía permanece visible también cuando Kaibil está seleccionada.
- La confirmación de **Enviar transferencia** deja de usar el diálogo nativo del navegador y utiliza un modal SGI consistente con Aceptar/Rechazar/Anular.
- No hay migración nueva; la última migración continúa siendo V18.


## SER v0.9 — Asignación inicial de Servicios
- SIC: COM es SoR de `Servicio = Cliente + Punto` y de los Puestos comerciales.
- Servicios nuevos sin Compañía operativa ingresan a una bandeja lógica **Kaibil**.
- En Kaibil no existe Configuración del Servicio: la acción es **Asignación**.
- Presidencia/Director Nacional asignan nacionalmente; Director Zonal por Zona; Jefe Regional por Región.
- Tras la asignación se habilita Configuración en la Compañía destino.
- Kaibil no se persiste como `company_id` del Servicio pendiente; `company_id` permanece NULL hasta asignación.
- V19 agrega estado/auditoría de asignación y fixture UAT.

## Contrato SIC: RRHH → SGI
Para que una persona ingrese a SGI: Comando, SIC: RRHH debe entregarla adscrita a **SF + Compañía**. SIC: RRHH permanece SoR de esa relación laboral.


## SER v0.9.1 — Retiro y reasignación de Servicios
- Presidencia/Director Nacional pueden retirar Servicios asignados a nivel nacional; Director Zonal solo Servicios de sus Zonas y Jefe Regional solo de sus Regiones.
- Retirar un Servicio lo devuelve a la bandeja lógica **Kaibil** con `company_id = NULL` y estado `PENDING`; Kaibil no se convierte en operador.
- La configuración del Punto/Servicio se conserva íntegra durante el retiro y la posterior reasignación.
- Asignaciones futuras se retiran de la planificación activa, pero permanecen históricas con estado `REMOVED` y razón `SERVICE_RETURN_TO_COORDINATION`.
- El turno actualmente en ejecución se deja finalizar. `operational_transition_until` bloquea nuevas asignaciones de la Compañía destino antes del cierre de ese turno.
- Desde Kaibil el Servicio puede reasignarse directamente a una nueva Compañía dentro del ámbito territorial autorizado, sin aprobación del Coordinador destino.
- V20 extiende la auditoría de `service_company_assignment_event` y agrega transición operacional al Punto.

## ASI v0.7.4 — Guardia de transición de Servicio
- Cuando un Servicio fue retirado a Kaibil con un turno activo retenido, la nueva Compañía no puede crear asignaciones sobre turnos que inicien antes de `operational_transition_until`.
- Resumen, cobertura y publicación excluyen esos turnos durante la transición para evitar doble cobertura.


## SER v0.10.1 — Protocolos + Bitácora + Patrullas
- Estados de Protocolos estandarizados en Bitácora, Patrullas y Consignas: **Borrador / Inactivo / Activo**.
- Publicar lleva de Borrador a Inactivo; Activar/Inactivar es una acción operacional separada.
- Bitácora limita cada Protocolo a **10 Acreditaciones**.
- Patrullas adopta la composición visual de Bitácora: Puestos | Protocolos | Detalle, con las Patrullas dentro del panel derecho.
- Migración Flyway: `V21__ser_protocol_states_and_bitacora_accreditation_limit.sql`.
- Frontend UAT: `http://localhost:5173`; backend: `http://localhost:8080`.


## SER v0.10.2 — Hotfix Acreditaciones
- Corrige HTTP 500 al agregar una acreditación cuando existen huecos en los códigos ACC por eliminaciones previas.
- El próximo código se calcula desde el mayor sufijo numérico existente, no desde la cantidad actual.
- Ejemplo: 7 acreditaciones con `ACC-010` existente → siguiente código `ACC-011`, contador funcional `8/10`.
- Se mantiene máximo 10 acreditaciones concurrentes por Protocolo de Bitácora.
- Sin migración nueva de base de datos.


## SER v0.10.4 — Activación por Puesto + confirmaciones SGI
- En Bitácora, el contenido publicado sigue siendo inmutable, pero la **activación operativa se controla por Puesto** desde Definición.
- Marcar un Puesto activa el Protocolo en ese Puesto; desmarcarlo lo inactiva solo allí y muestra una advertencia SGI.
- Se elimina el botón global **Inactivar** de la cabecera.
- Un Protocolo puede estar activo simultáneamente en varios Puestos del Punto.
- Si no queda ningún Puesto activo, el Protocolo pasa globalmente a `Inactivo`; al activar al menos uno, pasa a `Activo`.
- Bitácora elimina `window.confirm` para acreditaciones/campos y adopta modal SGI propio.
- Regla UX: no usar diálogos nativos del navegador en nuevas pantallas de SGI: Comando.
- Migración Flyway: `V23__ser_bitacora_scope_activation_semantics.sql`.


## COO v0.1 — Coordinación
- Nueva opción `Coordinación` entre Servicios y Asignaciones.
- Puestos internos de `Monitoreo` y `Supervisión` por Compañía.
- Formatos cerrados `24/7 + 6-2`, `12/7 + 6-2`, `12/5 + 5-2`.
- Horarios de 12 h derivados desde hora de inicio/relevo; 12/5 exige cinco días.
- Supervisión incorpora Ruta versionada con secuencia ordenada de Puntos.
- Preview por turno usa los horarios requeridos de `post_shift_template`; la visita al Punto incluye todos los Puestos aplicables al turno.
- Alertas de Puntos activos sin cobertura de Ruta.
- Flyway `V25__coo_coordination_v01.sql`.
- SER v0.10.10 permanece FROZEN.
