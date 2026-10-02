# CR — SGI_COM BIT-INT-001

- Estado: `RC1 / PENDIENTE DE APROBACION FORMAL`
- Fecha: 2026-09-29
- Programa: `SGI_COM`
- Baseline origen: entrega congelada 2026-09-28
- Vertical: Bitácora (`SGI-09`) / interconexión `SGI_OPR_SGI_COM_0001_v001`
- Clasificación: `REQUIERE APROBACION`

## Alcance

Extender `GET /api/v1/operator/runtime` con `relief.bitacoraProtocols`. SGI_COM, como System of Record, entrega únicamente protocolos con estado `ACTIVO` y asociados al puesto de la asignación autenticada. Incluye acreditaciones y campos configurados.

## Fuera de alcance

- Persistencia de movimientos de Bitácora desde SGI_OPR.
- Modificación de tablas o migraciones.
- Cambios a la baseline congelada.

## Reglas

1. `BORRADOR` e `INACTIVO` no se exponen.
2. El backend aplica `tenant + post + status=ACTIVO`; el cliente vuelve a validar defensivamente.
3. Cero opciones bloquea el registro; una se preselecciona; varias requieren elección.
4. Hosts, puertos y credenciales se resuelven por ambiente, nunca en código.

## Riesgo y rollback

Riesgo bajo-medio: extensión aditiva del payload v1. Rollback: retirar `bitacoraProtocols` del ensamblado de runtime y reinstalar la imagen de la baseline; no hay rollback de datos.

