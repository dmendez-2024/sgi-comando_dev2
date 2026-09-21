# SGI: Comando — Changelog UAT v0.4

## Verticales
- SGI-00T Territorio v0.2
- SGI-06 Asignaciones v0.4
- SGI-01 Compañías v0.3 (Región obligatoria visible/creable)

## Territorio
- Administración de Zonas y Regiones con estados Borrador/Activa/Inactiva.
- Responsable por Zona/Región desde SIC: RRHH LOCAL.
- 24 Provincias del Ecuador y asignación Zona/Región editable.
- Restricción: objeto activo no se elimina.
- Auditoría territorial.
- Mapa operacional de Ecuador como referencia visual.
- Compañía ligada obligatoriamente a Región.

## Asignaciones
- Guardar Borrador manual + auto-checkpoint 15 min.
- Edición restringida a Coordinador/Asistente; superiores en lectura.
- Eliminación directa sin confirmación en Borrador.
- Selección múltiple tipo Excel + Copiar/Pegar/Eliminar.
- Clipboard de asignaciones reutilizable al navegar a semanas futuras.
- Refuerzo visual Verde/Ámbar/Rojo al seleccionar persona.
- Tarjetas de personal rediseñadas con foto, ID, turno preferido, horas libres e iconos Vacaciones/Permiso Médico.
- TIER tipo escudo, horas/semana, IC/ID promedio por Puesto.
- Modal IC con 8 habilidades y cumplimiento general.
- Modal Agente ampliado.
- Modal Puesto con Consignas, habilidades requeridas y Últimas novedades.
- Etiquetado automático de Turnos según cantidad de turnos y mayoría horaria.

## Migración
- Flyway V7__territory_assignments_v04.sql.
