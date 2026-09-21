# SGI: Comando — SER v0.7.3

Entrega UAT correctiva de **SER — Servicios / Configuración → Patrullas**.

## Estado de verticales
- TER — Territorio v1.0: **FROZEN**
- COM — Compañías v1.0: **FROZEN**
- ASI — Asignaciones v0.6.5: **FROZEN**
- SER — Servicios v0.7.3: **UAT_CANDIDATE**

## Foco de SER v0.7.3
- Mantener la jerarquía `Punto → Puesto → Protocolo → Patrulla → Reglas`.
- Mantener las cuatro modalidades Cerrada/Abierta × Programada/No Programada.
- Mantener Hitos en modo mixto: **+ Agregar hito en plano** / **+ Agregar hito en campo**.
- Mantener Foto estándar por Hito para futura auditoría VISINT.
- Mantener el patrón transversal: una versión publicada es inmutable; editar crea un nuevo borrador.
- Corregir CRUD sobre las tablas canónicas baseline de Patrullas sin duplicarlas.
- Rehacer la jerarquía visual de la cabecera/editor de Patrullas y aumentar legibilidad.

## Persistencia canónica
La configuración y la futura ejecución comparten las entidades canónicas:

`patrol_protocol → patrol_definition → patrol_checkpoint → patrol_checkpoint_rule`

Las tablas temporales `patrol_config_*` no forman parte del esquema final.

`patrol_definition` conserva los campos baseline requeridos por Operación (`point_id`, `status`, `version`) y los campos SER de configuración/versionado. `patrol_checkpoint` conserva `validation_rule_json` únicamente por compatibilidad baseline; las reglas normalizadas son `patrol_checkpoint_rule`.

## Ejecución UAT
Desde la carpeta externa que contiene `repo/`:

```powershell
powershell -ExecutionPolicy Bypass -File ".\repo\scripts\uat-start.ps1"
```

Abrir:

```powershell
powershell -ExecutionPolicy Bypass -File ".\repo\scripts\uat-open.ps1"
```
