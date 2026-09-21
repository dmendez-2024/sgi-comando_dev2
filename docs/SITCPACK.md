# SGI: Comando — SITCpack INT v0.1

## Baseline recibido
- `SITC-ECOSISTEMA-CM-SISTEMAS-20260921-SCENARIO_SNAPSHOT.sitcpack`
- `SITC-NOM-001 v3.0`

## Entregables
- `sitc/SGI_Comando_CURRENT.sitcpack` — snapshot acumulativo v3 importable a CORE.
- `sitc/SGI_COM_INT_v0.1_COMPONENT_DELTA.sitcpack` — delta de SGI_COM.
- `sitc/v3/SGI_COM-v1.1.0-INT-v0.1-COMPONENT_DELTA.sitcpack`
- `sitc/v3/SITC-ECOSISTEMA-CM-20260921-SGI_COM-INTERCONNECTIONS-SCENARIO_SNAPSHOT.sitcpack`

El snapshot acumulativo conserva los 23 Program IDs del maestro y agrega 24 interconexiones / 30 interfaces.

## Importación a CORE
1. Validar schema y referencias Program ID.
2. Preview/Merge: Programs por `programId`; interconexiones por `interconnectionRef`; interfaces por `interfaceId`.
3. Preservar revisión `interconnectionId`.
4. Mostrar CREATE / UPDATE / NO_CHANGE / CONFLICT / INVALID.
5. No importar secretos.
6. No activar ni modificar bindings productivos de Instancias PE automáticamente.
7. Tras aprobación arquitectónica, configurar/activar bindings por Instancia PE + ambiente de forma explícita.

## Runtime
SGI_COM usa `SGI_COM__CORE__00002__V0001` para resolver bindings. CORE devuelve configuración efectiva y SGI_COM la cachea; luego SGI_COM conversa directamente con el destino.
