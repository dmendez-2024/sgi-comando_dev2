# SGI: Comando — CHANGELOG UAT SER v0.1

## Nuevo vertical: SER — Servicios
- Se reemplaza el prototipo histórico `Servicios y Puntos / Puestos` por un listado maestro operacional a nivel Puesto.
- Se implementa la UI aprobada: KPIs, filtros, tabla compacta, semáforos ID/IC, Novedades, Estado y exportación CSV.
- Etiqueta aprobada: **Por Configurar** (no “Configuración Pendiente”).
- Estados visibles: Activo / Inactivo / Por Configurar.
- Inactivo deriva de SIC: COM/Punto y es read-only en SGI.
- ID e IC se agregan sobre una ventana móvil de 14 días; en UAT se usa un proxy con turnos terminados hasta que SGI-07 Relevos sea SoR de ejecución.
- ID: verde si cumple el mínimo TIER; amarillo si queda hasta 0.5 puntos por debajo; rojo si la brecha es mayor a 0.5 (umbral visual UAT, pendiente de congelación).
- IC: verde 100%; amarillo 90–99.9%; rojo <90%.
- Novedades: cuenta Hallazgos/Vulnerabilidades no cerrados/revisados disponibles en el baseline actual.

## Verticales congeladas
- TER v1.0
- COM v1.0
- ASI v0.6.5

No se modificó lógica funcional de las verticales congeladas.
