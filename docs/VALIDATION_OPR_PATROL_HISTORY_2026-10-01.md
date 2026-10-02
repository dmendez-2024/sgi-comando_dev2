# Validación - OPR-PATROL-HISTORY-001 RC1

Fecha: 2026-10-01  
Frontend: `0.11.2`

## Clasificación SITC

- Cambio aditivo del contrato interno SGI Comando frontend/backend.
- No modifica el envío móvil existente, la BD, topología, identidad, SoR ni interconexiones externas.
- No requiere migración Flyway ni nuevo `*.sitcpack`.

## Seguridad y alcance

- La consulta exige actor autenticado y tenant vigente.
- `AGENTE_SEGURIDAD` solo obtiene sus propias ejecuciones.
- Los perfiles de Comando solo obtienen Compañías permitidas por `scope.canAccessCompany`.
- La UI no vuelve a filtrar las filas reales con códigos DEMO.

## Evidencia funcional

- Ejecución: `2c30a07d-6794-43df-814b-eb14363c2c04`.
- Patrulla: `PAT-001` / Patrulla Alfa.
- Protocolo: `PRO-PAT-0004` / Nuevo protocolo de patrullas.
- Operador: Alex Enrique Chiriboga Mafla.
- Contexto: Galvarino / Telconet / Telco-City / Control de Acceso Principal.
- Resultado: `COMPLETA`, 2/2 hitos (`Bodega`, `Patio`).
- Endpoint UAT con perfil Coordinador: PASS; devuelve la ejecución y su línea de tiempo.

## Gates

- Compilación backend Quarkus en imagen Docker: PASS.
- Despliegue backend y health HTTP 200: PASS.
- TypeScript: PASS.
- Build Vite: PASS (`1898` módulos).
- Confirmación visual en navegador: pendiente del usuario UAT.
