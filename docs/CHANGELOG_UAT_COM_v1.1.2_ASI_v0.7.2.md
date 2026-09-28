# SGI: Comando — COM v1.1.2 / ASI v0.7.2 UAT FIX

## Alcance
No cambia la lógica funcional aprobada de transferencias. Corrige un defecto transaccional detectado en UAT y mejora la confirmación visual.

## Corrección backend
Al aceptar una transferencia efectiva de inmediato, `company_membership` ya contenía una membresía `PRIMARY` activa para el colaborador. La restricción parcial `ux_membership_primary_active` permite una sola. Hibernate podía programar el INSERT de la nueva membresía antes del UPDATE que cerraba la anterior.

La corrección cierra la membresía actual y fuerza su persistencia/flush antes de insertar la nueva membresía de destino.

## Corrección UI
- Se elimina el diálogo nativo de Chrome para decisiones de transferencia.
- Se incorpora modal SGI para confirmar Aceptar / Rechazar / Anular.
- La aceptación muestra claramente la irreversibilidad.
- Si el backend devuelve un error, este se presenta dentro del modal y no queda oculto detrás de la ficha.
- El mensaje de éxito distingue entre transferencia efectiva inmediata y aceptada pendiente del fin de turno.

## Base de datos
No agrega migraciones. Se mantiene V18 como última migración.
