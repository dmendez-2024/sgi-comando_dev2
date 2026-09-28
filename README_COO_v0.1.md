# SGI: Comando — COO v0.1

Baseline: SER v0.10.10 **FROZEN** + COM v1.1.3 FROZEN + ASI v0.7.4 UAT.

## Alcance
- Menú `Coordinación` entre Servicios y Asignaciones.
- Puestos internos `Monitoreo` y `Supervisión` por Compañía.
- Códigos automáticos por Compañía (`MON-xxx`, `SUP-xxx`).
- Estados `Borrador / Inactivo / Activo`.
- Formatos cerrados: `24/7 + 6-2`, `12/7 + 6-2`, `12/5 + 5-2`.
- 24/7 usa dos turnos de 12 h derivados desde la hora de relevo.
- 12/5 exige exactamente cinco días de operación.
- Supervisión permite construir Ruta aun con el Puesto en Borrador.
- Ruta con código automático, nombre editable y estados `Borrador / Activa / Reemplazada`.
- Secuencia ordenada de un subconjunto de Puntos.
- Preview por turno usando `post_shift_template` como snapshot operativo del horario requerido de Servicios/Puestos.
- Alertas de Puntos activos sin cobertura.

## Base de datos
Flyway `V25__coo_coordination_v01.sql`.
No modifica migraciones SER existentes.
