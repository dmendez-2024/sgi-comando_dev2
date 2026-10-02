# Validación - OPR-CONSIGNMENT-VISIBILITY-001 RC1

Fecha: 2026-10-01  
Baseline: `SGI_COM_OPR_ASSIGNMENT_RC2_2026-09-30`  
Frontend: `0.11.1`

## Clasificación SITC

- Tipo: corrección de defecto de presentación/integración en RC derivada.
- Línea base congelada: preservada; el cambio queda documentado como RC posterior.
- Backend, BD, Flyway, contrato API e interconexión `SGI_OPR -> SGI_COM`: sin cambios.
- Impacto SITC: ninguno en topología, identidad, payload, SoR o seguridad. No requiere nuevo `*.sitcpack`.

## Causa raíz

`GET /api/v1/operator/consignment-review-requests` ya aplica el alcance RBAC en el backend. Después de recibir la respuesta, `ConsignasExecution` aplicaba una segunda validación usando `scopeByUser`, cuyos códigos pertenecen al dataset DEMO. La consigna real de Galvarino devolvía `companyCode=COM-001`, mientras el frontend esperaba el alias DEMO `GAL`; por ello la fila se descartaba.

## Corrección

- Las solicitudes reales autorizadas por el backend se incorporan directamente al conjunto visible.
- `withinScope` permanece aplicado exclusivamente a `DATA`, el dataset estático de demostración.
- No se amplían permisos: el endpoint continúa omitiendo las compañías fuera del alcance del actor autenticado.

## Casos UAT

1. Ingresar con un perfil de alcance Galvarino.
2. Desde SGI Operador registrar y sincronizar una consigna para `companyCode=COM-001`.
3. En SGI Comando abrir `Operaciones > Consignas` y pulsar `Buscar`.
4. Confirmar que la consigna aparece como `Consigna ad-hoc` y estado `Pendiente`.
5. Confirmar que un perfil sin acceso a esa compañía no recibe la solicitud desde el endpoint.
6. Confirmar que las filas DEMO continúan limitadas por `scopeByUser`.

## Evidencia del caso diagnosticado

- Título: `Prueba 3`.
- Estado persistido: `PENDING`.
- Compañía canónica: `COM-001` / Galvarino.
- Cliente / Punto / Puesto: Telconet / Telco-City / Control de Acceso Principal.
- Resultado previo: persistida y retornable por API, pero descartada por el filtro DEMO del frontend.

## Gates

- Compilación TypeScript: PASS.
- Build Vite de producción: PASS (`1898` módulos transformados).
- Disponibilidad local: PASS; frontend `http://localhost:5173` y backend `http://localhost:8080/q/health` respondieron HTTP 200.
- Verificación funcional en navegador del caso `Prueba 3`: pendiente de confirmación visual del usuario UAT.
- Regresión de alcance backend: preservada por diseño; pendiente de comprobación funcional UAT.
