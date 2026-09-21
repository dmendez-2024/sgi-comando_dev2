# SGI: Comando — SER v0.7.2 UAT

## Objetivo
Entrega correctiva y de pulido para **Patrullas** dentro de SER.

## Cambios principales
1. **Persistencia canónica de Patrullas**
   - Se elimina el uso operativo de las tablas duplicadas `patrol_config_definition`, `patrol_config_checkpoint` y `patrol_config_checkpoint_rule`.
   - La configuración versionada de Patrullas se consolida sobre el árbol canónico:
     - `patrol_protocol`
     - `patrol_definition`
     - `patrol_checkpoint`
     - `patrol_checkpoint_rule`
   - La migración `V15__ser_patrols_canonical_tables.sql` evoluciona las tablas baseline, migra datos existentes y retira las tablas duplicadas.

2. **UI de Patrullas mejorada**
   - Reorganización de cabecera del editor de Protocolo.
   - Botón **Crear nueva versión** reubicado en una barra de acciones coherente.
   - Mensaje de solo lectura convertido en banner claro.
   - Tipografía y espaciados aumentados para mejor legibilidad.
   - Resumen visible de Puesto y Patrulla seleccionados en la cabecera.
   - Mejor jerarquía visual en tabs, lista de Hitos y panel del editor.

3. **Continuidad funcional**
   - Se conserva el patrón de versionado ya aprobado: publicado = snapshot inmutable; para editar se crea nueva versión borrador.
   - Se mantiene el modo mixto de configuración de Hitos:
     - **+ Agregar hito en plano**
     - **+ Agregar hito en campo**
   - Se conserva la foto estándar por Hito para futura integración VISINT.

## Notas de compatibilidad
- La definición funcional aprobada en SER v0.7 / v0.7.1 no cambia.
- El cambio es principalmente de **persistencia canónica** y **pulido UI**.
