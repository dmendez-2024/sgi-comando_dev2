# CHANGELOG — NOV v0.1

## Nuevo
- Vertical `Operaciones → Novedades`.
- KPIs de Pendientes, Hallazgos, Vulnerabilidades, Incidentes, Aprobadas hoy y Críticas.
- Filtros homologados a Consignas/CNS.
- Tabla de novedades con tipos, subcategorías, origen y estado.
- Modal de detalle.
- Edición previa a revisión para corregir información cargada desde SGI: Operador.
- Aprobación con comentario opcional.
- Descarte con comentario obligatorio.
- Auditoría local de edición / aprobación / descarte.
- Aprobación habilita `clientVisible=true` para futura exposición en SGI: Cliente.

## Source of Record / Integraciones
- Origen de novedades en Fase I: `SGI_OPR` — SGI: Operador.
- Revisión / moderación: `SGI_COM` — SGI: Comando.
- Destino de publicación tras aprobación: `SGI_CLI` — SGI: Cliente.

## Sin cambios
- No se modifica el esquema de base de datos en NOV v0.1.
- Las verticales TER, COM, ASI, SER, COO y BIT permanecen congeladas según sus baselines vigentes.
