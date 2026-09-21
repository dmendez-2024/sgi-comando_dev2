# SGI: Comando — SER v0.10.5 UAT

## Bitácora — cierre funcional UAT

Cambios finales solicitados para la vertical Bitácora:

1. **Confirmación también al activar por Puesto**
   - Para un Protocolo publicado, marcar un Puesto nuevo ya no aplica el cambio inmediatamente.
   - Se muestra el modal SGI de confirmación: el usuario debe confirmar **Guardar y activar**.
   - Desmarcar un Puesto conserva la confirmación de inactivación existente.
   - No se utilizan modales nativos del navegador.

2. **Pestaña por defecto = Definición**
   - Al entrar a Bitácora, la pestaña inicial es `Definición`.
   - Al cambiar de Puesto, la pestaña vuelve a `Definición`.
   - Al cambiar de Protocolo, la pestaña vuelve a `Definición`.
   - Seleccionar o crear una Acreditación sigue llevando a `Acreditación`, porque esa acción es explícita.

## Alcance técnico
- Cambio únicamente de frontend/comportamiento UAT.
- No se agregan ni modifican migraciones Flyway.
- Se preservan V22/V23 exactamente como fueron entregadas para evitar cambios de checksum.
