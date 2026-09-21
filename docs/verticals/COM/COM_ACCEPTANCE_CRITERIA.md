# COM v1.0 FROZEN — Criterios de aceptación

**Estado de producto:** ACEPTADO / CONGELADO por el Product Owner el 2026-09-08.  
**Nota:** “Congelado” fija las reglas funcionales y el contrato de la vertical. La validación de infraestructura productiva y adaptadores externos se ejecuta en el handoff final a Sistemas.

- [x] **COM-AC-001** Crear Compañía con nombre, Zona y al menos una Región.
- [x] **COM-AC-002** Admitir Logo y visualizarlo en listado/edición.
- [x] **COM-AC-003** Reseña Histórica opcional con máximo 750 caracteres y contador UI.
- [x] **COM-AC-004** Editar una Compañía ya creada.
- [x] **COM-AC-005** Editar Nombre de Compañía Activa y generar nueva versión.
- [x] **COM-AC-006** Editar Logo y Reseña de Compañía Activa y generar nueva versión.
- [x] **COM-AC-007** Asignar múltiples Regiones pertenecientes a una misma Zona.
- [x] **COM-AC-008** Impedir asignar Regiones pertenecientes a Zonas diferentes.
- [x] **COM-AC-009** Permitir agregar una Región de la misma Zona aun con operación activa.
- [x] **COM-AC-010** Bloquear retirar una Región si tiene Servicios activos de la Compañía.
- [x] **COM-AC-011** Permitir retirar Región cuando no tenga Servicios activos.
- [x] **COM-AC-012** Bloquear cambio de Zona mientras existan Servicios activos que impidan retirar las Regiones actuales.
- [x] **COM-AC-013** Permitir cambio de Zona cuando no existan bloqueos por Servicios activos.
- [x] **COM-AC-014** Bloquear Inactivar cuando existan Servicios activos asociados.
- [x] **COM-AC-015** Permitir Inactivar cuando Servicios activos = 0.
- [x] **COM-AC-016** Permitir reactivar Compañía Inactiva sin cambiar identidad.
- [x] **COM-AC-017** Historial mantiene versiones, usuario, fecha/hora, tipo de cambio, snapshot y motivo cuando exista.
- [x] **COM-AC-018** Código y `company_id` permanecen estables entre versiones.
- [x] **COM-AC-019** Mantener `Cambio Requerido` del baseline.
- [x] **COM-AC-020** Mostrar confirmación visual de éxito/error al guardar.
- [x] **COM-AC-021** Jefe Regional puede ver la operación de una Compañía correspondiente a su Región aun cuando la misma Compañía opere en otras Regiones de la Zona.
- [x] **COM-AC-022** TER v1.0 y demás verticales no presentan cambios funcionales por el paquete COM.
- [x] **COM-AC-023** En producción, Logo utiliza MinIO + metadata/hash/version en PostgreSQL; el adapter LOCAL no forma parte del despliegue productivo.
- [x] **COM-AC-024** SER deberá exponer Región operacional explícita antes del cierre productivo de la integración multirregión.
