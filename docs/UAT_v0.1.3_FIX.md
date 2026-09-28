# UAT v0.1.3 — corrección de autenticación UAT

## Síntoma
- `Compañías` mostraba `Error: 401`.
- `Servicios / Puntos` aparecía vacío porque el rechazo 401 no se mostraba en UI.

## Causa
La migración UAT v2 contenía un hash bcrypt que no correspondía a la contraseña publicada `CajamarcaUAT!2026`.

## Corrección
- Se agrega `V3__uat_auth_fix.sql` con un bcrypt MCF válido para la contraseña UAT.
- V2 no se modifica para evitar conflictos de checksum de Flyway en instalaciones ya levantadas.
- `Servicios / Puntos` ahora muestra errores de API en vez de fallar silenciosamente.
- Cambiar Usuario UAT fuerza recarga de la vertical activa para que el RBAC sea visible inmediatamente.

## Credenciales
Usuarios: `don`, `dnacional`, `dzonal`, `coord`, `asistente`, `supervisor`, `agente`, `cliente`.
Contraseña UAT: `CajamarcaUAT!2026`.
