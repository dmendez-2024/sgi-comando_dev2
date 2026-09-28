# SGI: Comando — SER v0.9.1 + COM v1.1.3 FROZEN + ASI v0.7.4

Entrega combinada UAT del 19-09-2026.

## Estado de verticales
- TER — Territorio v1.0: **FROZEN**
- COM — Compañías v1.1.3: **FROZEN**
- ASI — Asignaciones v0.7.4: **UAT_CANDIDATE**
- SER — Servicios v0.9.1: **UAT_CANDIDATE**

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
