# SGI: Comando — NOV v0.1 UAT

Nueva vertical de **Operaciones → Novedades** para revisar, corregir, aprobar o descartar novedades levantadas desde **SGI: Operador**.

## Tipos
- Hallazgos
  - Orden
  - Limpieza
  - Mantenimiento
  - Estacionamiento
  - Componentes de Seguridad
- Vulnerabilidades
  - Acceso
  - Perímetro
  - Interno
- Incidentes
  - Servicio
  - Seguridad
  - Administrativo

## Flujo aprobado
1. Agente de Seguridad o Supervisor levanta la novedad en `SGI: Operador`.
2. La novedad ingresa a `SGI: Comando → Novedades` con estado `Pendiente`.
3. Perfiles autorizados dentro de su alcance pueden revisarla y corregir información antes de decidir.
4. `Aprobar` habilita la novedad para visualización en `SGI: Cliente`.
5. `Descartar` exige comentario, conserva histórico y mantiene la novedad oculta para `SGI: Cliente`.

## Perfiles y alcance
- Asistente de Coordinación / Coordinador: sus Compañías.
- Jefe Regional: Compañías de sus Regiones.
- Director Zonal: Compañías de sus Zonas.
- Director Nacional / Presidencia: ámbito nacional.

Todos estos perfiles pueden aprobar o descartar dentro de su alcance.

## Estados
- Pendiente
- Aprobada
- Descartada

## Filtros
Se reutiliza el estándar de filtros de CNS:
- Fila 1: búsqueda general.
- Fila 2: Ciudad / Compañía / Cliente / Punto / Puesto.
- Fila 3: Tipo de novedad / Estado / Reportado por / Fecha inicio / Fecha fin.

Dependencias:
- Punto requiere Cliente.
- Puesto requiere Punto.
- Reportado por requiere Compañía.
- Fecha inicio y fecha fin son obligatorias.
- Rango máximo: 1 año.

## UAT v0.1
El dataset es DEMO/local para validar UX/UI, triage, edición y flujo de aprobación/descartado. No se agregan migraciones de base de datos en esta versión.
