# COM v1.0 FROZEN — UAT / Regresión

La vertical fue aceptada funcionalmente y congelada. Este guion queda como regresión obligatoria.

## 1. Alta
- Crear Compañía con nombre, Logo y Reseña.
- Seleccionar una Zona.
- Seleccionar una o más Regiones de la Zona.
- Guardar y verificar feedback.

## 2. Edición y versión
- Editar nombre, logo o reseña.
- Guardar.
- Verificar incremento de versión e historial.

## 3. Multi-región
- Agregar Región adicional de la misma Zona en una Compañía Activa.
- Verificar que se permita.
- Intentar agregar Región de otra Zona: debe bloquear.

## 4. Retiro de Región
- Con Servicio activo en Región: bloquear retiro.
- Sin Servicios activos: permitir retiro.

## 5. Cambio de Zona
- Con Regiones bloqueadas por Servicios activos: bloquear.
- Sin bloqueos: permitir luego de sustituir la estructura regional conforme a la nueva Zona.

## 6. Inactivación / reactivación
- Con Servicios activos: bloquear Inactivar.
- Con 0 Servicios activos: permitir.
- Reactivar: permitir, manteniendo identidad e historial.

## 7. Alcance
- Presidente / Director Nacional: alcance país.
- Director Zonal: compañías dentro de su Zona.
- Jefe Regional: operación correspondiente a su Región.
- Coordinador / Asistente: su Compañía según alcance configurado.

## 8. Regresión TER
- Confirmar que TER v1.0 permanece funcionalmente sin cambios.
