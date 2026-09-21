# SGI: Comando — UAT v0.1.4

## Correcciones
- Se agrega endpoint alternativo `GET /api/posts?pointId=...` y el frontend lo usa para resolver el 404 observado al cargar Puestos.
- El error de carga de Puestos ahora se presenta en el panel de Puestos, no mezclado con Servicios/Puntos.

## UI
- Se reemplaza el placeholder de marca por el logo SGI: Comando suministrado por el usuario.
- Menú `Operaciones` colapsable:
  - Compañías
  - Servicios
  - Asignaciones
  - Comunicación
  - Bitácora
- Menú `Reportes` colapsable:
  - Estadísticas
  - Reportería Ad-Hoc
- Auditoría queda como módulo independiente.
- Se conserva Dashboard y los demás módulos del baseline.
