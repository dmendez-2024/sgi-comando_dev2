# SGI: Comando — SER v0.7.2

Entrega UAT de **SER — Servicios / Configuración**.

## Estado de verticales
- TER — Territorio v1.0: **FROZEN**
- COM — Compañías v1.0: **FROZEN**
- ASI — Asignaciones v0.6.5: **FROZEN**
- SER — Servicios v0.7.2: **UAT_CANDIDATE**

## Foco de SER v0.7.2
- Configuración de **Patrullas** por `Punto → Puesto → Protocolo → Patrulla → Reglas`.
- Matriz Cerrada/Abierta × Programada/No Programada.
- Hitos Cerrados en modo mixto: selección sobre plano ATS o captura GPS en campo desde móvil.
- Foto estándar por Hito para futura auditoría VISINT.
- Patrón transversal de versionado: publicado = snapshot inmutable; cambios se realizan en una nueva versión BORRADOR.
- Bitácora se ajusta al mismo patrón inmutable de publicación.
- Consolidación de persistencia: se elimina el uso de tablas duplicadas `patrol_config_*` y se adopta el árbol canónico `patrol_protocol → patrol_definition → patrol_checkpoint → patrol_checkpoint_rule`.
- Pulido UI de Patrullas: cabecera reorganizada, mejor ubicación de acciones, mensaje de solo lectura más claro y tipografía más legible.

## Ejecución UAT
Desde la carpeta externa que contiene `repo/`:

```powershell
powershell -ExecutionPolicy Bypass -File ".\repo\scripts\uat-start.ps1"
```

Abrir:

```powershell
powershell -ExecutionPolicy Bypass -File ".\repo\scripts\uat-open.ps1"
```

Detener:

```powershell
powershell -ExecutionPolicy Bypass -File ".\repo\scripts\uat-stop.ps1"
```

## UAT
Ver `docs/CHANGELOG_UAT_SER_v0.7.2.md` y `docs/verticals/SER/SER_UAT.md`.
