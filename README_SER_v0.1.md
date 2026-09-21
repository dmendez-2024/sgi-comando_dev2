# SGI: Comando — SER v0.1 UAT

Primera implementación de la vertical **Servicios**.

## Alcance
- Listado maestro a nivel **Puesto**.
- Columnas: Compañía, Servicio, Cliente, Punto, Puesto, ID Promedio, IC Promedio, Novedades y Estado.
- Estados: **Activo**, **Inactivo**, **Por Configurar**.
- Filtros por Compañía, Cliente, Estado y búsqueda libre.
- Exportación CSV del listado filtrado.
- Métricas ID/IC de ventana móvil de 14 días.

## Ownership
- SIC: COM permanece como System of Record de Servicio, Cliente, Punto, Puesto, TIER, Formato/FHE y estado comercial.
- SGI: Comando agrega la capa operacional.
- `Inactivo` es read-only en SGI.
- La transición manual futura de SGI será `Por Configurar → Activo`.

## Nota UAT sobre ejecución
SGI-07 Relevos todavía no está implementado como fuente de ejecución real. En esta UAT, ID/IC usan como proxy asignaciones de turnos ya finalizados dentro de los últimos 14 días. La integración productiva debe reemplazar este proxy por minutos efectivamente cubiertos según Relevo/Reasignación ejecutada.
