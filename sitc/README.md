# SGI: Comando — SITC / Interconexiones

## CURRENT v3.0
`SGI_Comando_CURRENT.sitcpack`

Es un **SCENARIO_SNAPSHOT SITC-NOM-001 v3.0** (ZIP con extensión `.sitcpack`) preparado para importación a CORE. Conserva el catálogo maestro recibido el 21-09-2026 y agrega el módulo genérico + catálogo de interconexiones de SGI_COM.

## Artefactos v3
- `v3/SITC-ECOSISTEMA-CM-20260921-SGI_COM-INTERCONNECTIONS-SCENARIO_SNAPSHOT.sitcpack`: acumulativo para CORE.
- `v3/SGI_COM-v1.1.0-INT-v0.1-COMPONENT_DELTA.sitcpack`: delta de SGI_COM.
- `SGI_COM_INT_v0.1_COMPONENT_DELTA.sitcpack`: alias/copia de entrega.
- `SGI_Comando_CURRENT_LEGACY_v0.2.json`: formato histórico preservado solo para trazabilidad.

## Reglas CORE
- Programs: merge por `programId`.
- Interconnections: merge por `interconnectionRef`; preservar `interconnectionId` como revisión.
- Interfaces: merge por `interfaceId`.
- Mostrar preview `CREATE / UPDATE / NO_CHANGE / CONFLICT / INVALID`.
- Nunca importar secretos efectivos; solo `credentialRef`/templates.
- Importar arquitectura **no** activa/modifica automáticamente Instancias PE productivas.
- Binding efectivo se configura luego por Instancia PE + ambiente.

## Runtime
SGI_COM usa `SGI_COM__CORE__00002__V0001` para resolver bindings. La llamada funcional va directamente desde SGI_COM al sistema destino; CORE no es proxy.
