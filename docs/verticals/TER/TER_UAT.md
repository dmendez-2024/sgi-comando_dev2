# TER — Plan UAT v0.1

## Preparación
Usar primero `Director Nacional` para pruebas de configuración completa.

## Secuencia
1. Validar cabecera, resumen Ecuador, mapa y terminología `Provincias`.
2. Buscar y filtrar la estructura.
3. Expandir Zona y Región; revisar Provincias y Compañías.
4. Crear una Zona Borrador y asignarle Responsable + Provincias.
5. Editar la Zona y validar toast de éxito.
6. Crear una Región dentro de esa Zona; verificar que solo muestre Provincias de la Zona.
7. Intentar eliminar una Zona/Región Activa: debe estar deshabilitado/rechazado.
8. Abrir Historial desde `⋮`.
9. Cambiar usuario: Director Zonal, Jefe Regional, Coordinador, Asistente; validar scope.
10. Confirmar que no existe opción para crear Provincias ni para editar Compañías desde TER.

## Congelación
TER v0.1 se declara `CONGELADA` únicamente cuando los criterios obligatorios TER-AC estén PASS por UAT del usuario.
