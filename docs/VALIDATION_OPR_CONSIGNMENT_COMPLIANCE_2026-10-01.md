# Validación OPR-CONSIGNMENT-COMPLIANCE-001

## Criterios UAT

1. `POST /api/v1/operator/consignment-compliances` no responde 404.
2. Solo el empleado autenticado y vinculado puede confirmar una Consigna de su asignación.
3. La Consigna debe pertenecer al Protocolo activo y aplicar al Punto/Puesto.
4. Fotografía y observación se validan según parametrización.
5. Repetir el mismo `executionId` y contenido es idempotente; cambiar el contenido responde 409.
6. Después de sincronizar, el runtime informa el último resultado, usuario y fecha.

## Impacto normativo

- SoR: SGI Comando.
- Contrato: implementación de la operación ya catalogada para SGI_COM–SGI_OPR.
- BD: migración versionada `V38`, aditiva y reversible mediante restauración previa del servicio/BD.
- Sin nuevas credenciales, permisos, redes o topologías.
