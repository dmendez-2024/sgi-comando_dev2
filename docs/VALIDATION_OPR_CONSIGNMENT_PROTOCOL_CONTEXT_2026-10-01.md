# Validación OPR-CONSIGNMENT-CONTEXT-001 RC1

## Objetivo

Mostrar en SGI Operador el Protocolo de Consignas vigente configurado en SGI Comando, junto con las reglas operativas de cada Consigna.

## Alcance normativo

- Fuente de verdad: SGI Comando.
- Contrato: ampliación aditiva de `GET /api/v1/operator/runtime`.
- Seguridad: se conserva autenticación, tenant, vínculo usuario-empleado y autorización por asignación.
- SITC: sin nueva interconexión, credencial, red, topología ni migración de datos.
- Baseline congelada: preservada; el cambio se identifica como RC derivada.

## Criterios UAT

1. Una asignación del Punto con Protocolo de Consignas activo recibe únicamente Consignas `VIGENTE` aplicables a su Punto/Puesto.
2. Cada elemento informa `protocolCode` y `protocolName` sin valores fijos en la aplicación móvil.
3. Prioridad, horario, alcance y requisitos coinciden con la parametrización de SGI Comando.
4. Si no existe Protocolo vigente aplicable, la app conserva el estado explícito `SIN PROTOCOLO VIGENTE`.
5. El backend compila y su verificación de salud finaliza correctamente.

## Reversión

Restaurar la imagen backend anterior. No se requiere reversión de base de datos porque no existe migración asociada.
