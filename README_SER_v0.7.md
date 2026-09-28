# SGI: Comando — SER v0.7

Versión UAT de **Servicios / Configuración**, incorporando Patrullas y el patrón transversal de configuración versionada.

## Nuevo en esta versión
- `Configuración → Patrullas` con jerarquía `Punto → Puesto → Protocolo → Patrulla → Reglas`.
- Patrullas Cerradas/Abiertas y Programadas/No Programadas.
- Hitos Cerrados configurables en modo mixto: **Plano ATS** y **Campo/GPS**.
- Foto estándar por Hito preparada para futura integración VISINT.
- Historial, fork de nueva versión y publicación inmutable.
- Bitácora se retroajusta al mismo patrón de versión publicada inmutable.

## Inicio UAT
Ejecutar `scripts/uat-start.ps1` y luego `scripts/uat-open.ps1` desde la raíz `repo`.

## Nota ATS/GPS
La posición seleccionada sobre el plano ATS se almacena normalizada y vinculada al package/revisión ATS. No se convierte a WGS84 salvo que una futura versión del `.ats` aporte una calibración geográfica formal.
