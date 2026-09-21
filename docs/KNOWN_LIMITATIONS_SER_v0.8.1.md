# SER v0.8.1 — Pendientes conocidos y límites de la baseline

Este archivo evita que pendientes deliberados sean interpretados como comportamiento faltante accidental.

## Pendientes aprobados

- **Operación/live:** no se ha desarrollado todavía la vista operacional completa de Servicios. La corrida actual se concentró en Configuración.
- **VISINT:** la comparación automática de imágenes no existe todavía. Se almacenan Fotos estándar y metadatos para futura integración.
- **Permisos Requeridos:** permanece como placeholder en Puestos.
- **Alcance multi-Puesto:** implementado en Consignas. Está aprobado para Acreditaciones de Bitácora y Patrullas, pero su retrofit queda pendiente de una corrida explícita.
- **Hardening productivo:** adapters UAT/locales y almacenamiento de ciertos binarios podrán migrar a componentes productivos sin cambiar la semántica funcional.

## Reglas que NO deben reinterpretarse

- Protocolos son independientes por vertical.
- Solo un Protocolo vigente por Punto y vertical.
- Publicado = inmutable; editar = nueva versión borrador.
- Evidencias no deben convertirse en una segunda fuente de reglas.
- ATS no autoriza inferir WGS84 si el `.ats` no entrega georreferenciación/calibración formal.
- Patrullas usa el modelo físico canónico `patrol_protocol → patrol_definition → patrol_checkpoint → patrol_checkpoint_rule`; no crear `patrol_config_*`.
