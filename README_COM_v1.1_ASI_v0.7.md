# SGI: Comando — COM v1.1.1 + ASI v0.7.1

Entrega combinada UAT del 19-09-2026.

## Estado de verticales
- TER — Territorio v1.0: **FROZEN**
- COM — Compañías v1.1.1: **UAT_CANDIDATE**
- ASI — Asignaciones v0.7.1: **UAT_CANDIDATE**
- SER — Servicios v0.8.1: **UAT_CANDIDATE**, sin cambios funcionales en esta entrega

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

## UAT FIX COM v1.1.1 / ASI v0.7.1
Corrige la migración V18 para bases UAT que ya contienen Compañías adicionales. El merge de Catálogo CORE ahora se realiza por `instance_country_id + code`, preservando el UUID existente y evitando violaciones de unicidad. No cambia la lógica funcional aprobada.

Ver `docs/CHANGELOG_UAT_COM_v1.1.1_ASI_v0.7.1.md`.
