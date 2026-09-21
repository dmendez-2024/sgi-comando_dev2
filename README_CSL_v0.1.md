# SGI: Comando — CSL v0.1 UAT

## Vertical
`Operaciones > Consola`

## Objetivo
Workspace operativo para Monitores / Operadores de Consola. Consola unifica en una sola bandeja tres categorías de trabajo:

- Consignas
- Novedades
- Alarmas electrónicas

Además refleja reasignaciones operativas dentro del flujo de monitoreo.

## Alcance funcional UAT
- KPI superiores de atención requerida.
- Bandeja unificada con tabs: Todos / Consignas / Novedades / Alarmas electrónicas.
- Filtros homologados a Consignas/Novedades:
  - Ciudad / Compañía / Cliente / Punto / Puesto
  - Estado / Responsable / Fecha inicio / Fecha fin
  - Búsqueda general
- Reglas de dependencia:
  - Punto requiere Cliente
  - Puesto requiere Punto
  - Responsable requiere Compañía
  - Fecha inicio y fecha fin obligatorias; rango máximo 1 año
- Tabla central de casos operativos.
- Panel derecho de detalle con acciones rápidas según categoría.
- Exportación CSV.

## Notas
- UAT con dataset DEMO/local.
- No crea un sistema separado de SGI: Monitoreo; Consola se implementa como workspace dentro de SGI: Comando.
- El System of Record permanece en las verticales origen (Consignas / Novedades / futuras Alarmas).
