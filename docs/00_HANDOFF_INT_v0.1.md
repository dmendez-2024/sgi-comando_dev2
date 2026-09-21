# 00 HANDOFF — SGI: Comando INT v0.1

## Lectura recomendada
1. `README_INT_v0.1.md`
2. `docs/INTERCONNECTIONS.md`
3. `docs/API_CATALOG.md`
4. `docs/SITCPACK.md`
5. `docs/SECURITY.md`
6. `sitc/SGI_Comando_CURRENT.sitcpack`

## Qué cambió
Se agregó un módulo backend genérico para integraciones salientes conforme a SITC-NOM-001 v3.0 y se asignaron IDs canónicos a todas las interconexiones SGI_COM actualmente definidas.

## Qué NO cambió
- UI: sin cambios.
- Verticales funcionales: permanecen FROZEN.
- Base de datos: sin migración nueva.
- Los lados legacy no se declaran terminados: permanecen MANUAL_PENDING hasta adecuación/prueba del DEV correspondiente.

## Importación CORE
El archivo principal es:
`sitc/v3/SITC-ECOSISTEMA-CM-20260921-SGI_COM-INTERCONNECTIONS-SCENARIO_SNAPSHOT.sitcpack`

La importación debe ejecutarse como preview/merge. No aplicar automáticamente cambios a Instancias PE productivas.
