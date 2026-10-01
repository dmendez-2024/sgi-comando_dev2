# SGI: Comando — CSL v0.2.5 FROZEN

Baseline acumulativa: CSL v0.2.4. Estado: FROZEN (freeze 2026-09-27).

## Cambios

- El asset `incident.png` fue recortado para dejar únicamente el ícono naranja de incidente. El texto incrustado en la imagen fuente ya no aparece ni en `Notificar Incidente` ni en el encabezado `Notificación de Incidente`.
- En Cobertura / Reasignación cada candidato muestra nombre, teléfono y `Francos Trabajados: N (últ. 6 meses)`.
- Se conserva la prelación existente: mismo Puesto → mismo Punto → misma Compañía por cercanía geográfica.
- Los teléfonos y el contador de francos son datos DEMO/local de esta UAT. La integración productiva deberá obtenerlos de sus SoR correspondientes.
- Sin cambios de base de datos, Flyway, SITC ni interconexiones.
- Se conservan acumulativamente los fixes de build de CSL v0.2.2–v0.2.4.
