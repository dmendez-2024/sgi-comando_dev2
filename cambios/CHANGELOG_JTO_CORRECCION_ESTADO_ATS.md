# CHANGELOG_JTO_CORRECCION_ESTADO_ATS

## Servicios → Configuración del Punto

- El resumen de Servicios ya no infiere el estado ATS desde el estado de los puestos.
- `services/overview` expone si el Punto tiene un paquete ATS vigente (`current=true`).
- La pantalla muestra `Completo` solo cuando existe un ATS vigente; sin archivo muestra estado bloqueante y `Sin archivo .ats importado`.
- No se agregaron migraciones ni se modificaron datos existentes.
