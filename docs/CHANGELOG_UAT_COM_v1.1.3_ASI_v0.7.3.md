# SGI: Comando — COM v1.1.3 / ASI v0.7.3 UAT FIX

**Fecha:** 2026-09-19  
**Base:** COM v1.1.2 / ASI v0.7.2

## Objetivo
Pulido de navegación y confirmaciones del flujo de transferencias entre Compañías, sin cambiar la lógica de negocio aprobada ni la estructura de base de datos.

## Cambios
1. **Kaibil por defecto** en Asignaciones para Presidencia, Director Nacional, Director Zonal y Jefe Regional.
2. **Selector de Compañía siempre visible** para esos perfiles, incluso cuando Kaibil está seleccionada.
3. **Confirmación SGI al enviar transferencia**: se elimina `window.confirm` de este flujo y se usa un modal propio con origen, destino, impacto operativo y manejo de error visible.
4. Aceptar / Rechazar / Anular continúan utilizando modal SGI.
5. No se agrega migración de base de datos. Última migración: `V18__core_companies_and_company_transfers.sql`.

## Reglas preservadas
- SIC: RRHH continúa como SoR de relación persona–Compañía.
- Transferencia requiere aceptación del destino.
- El origen puede anular mientras está pendiente.
- Al iniciar transferencia se liberan asignaciones futuras del origen.
- Turno actual se conserva hasta finalizar.
- Transferencia aceptada es irreversible como transacción.
