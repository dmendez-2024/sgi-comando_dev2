# CHANGELOG — CNS v0.1.2

- Nueva página Operaciones > Consignas.
- Incorpora Relevos, Patrullas y Consignas ad-hoc.
- No incorpora Bitácora porque existe la vertical BIT dedicada.
- Añade triage de alcance por rol.
- Añade filtros, KPIs, progreso, detalle rápido, timeline y exportación CSV.
- Pantalla de consulta solamente.


## Ajustes v0.1.1
- Reordenamiento de filtros en tres filas según validación UAT.
- Nuevo filtro de Puesto.
- Dependencias: Punto requiere Cliente; Puesto requiere Punto; Responsable requiere Compañía.
- Fechas obligatorias con rango máximo de 1 año.


## Corrección v0.1.2
- Se corrige selector CSS truncado de v0.1.1 que aplicaba `display:grid` de cinco columnas a los paneles principales.
- Se restaura el layout aprobado: panel principal + detalle lateral.
- Se mantienen los filtros dependientes y validación de rango máximo de 1 año.
- Se agregan guardrails `min-width:0` y ancho mínimo de tabla para evitar nuevos colapsos visuales.
