# CHANGELOG — NEX v0.1 — 2026-09-27

## Nuevo
- Nuevo módulo **Nexus** en Configuración del Punto, después de Consignas y antes de Recursos Humanos.
- Checklist y tarjeta inferior de Nexus habilitados para navegación.
- Página Nexus con únicamente las pestañas **Reglas** e **Historial**.
- Listado de reglas con estados Activa / Inactiva / Borrador y versión.
- Constructor simple de reglas basado en:
  - Evento objetivo = Objeto + Evento/Estado.
  - Relación temporal = ANTES / DURANTE / DESPUÉS.
  - Evento de referencia = Objeto + Evento/Estado.
  - Ventana temporal opcional.
  - Correlación opcional.
  - Acciones opcionales de cumplimiento / incumplimiento.
- Horario de aplicación por días y rango horario.
- Código automático `NEX-####`.
- Historial DEMO de modificaciones.
- Persistencia UAT local por Punto mediante `localStorage`.

## Sin cambios
- Backend.
- Base de datos / Flyway.
- SITC / interconexiones.
- Verticales FROZEN existentes.
- CSL v0.2.5 permanece como baseline funcional de Consola.
