# Referencias UI aprobadas — COM v1.1 + ASI v0.7

Estas imágenes forman parte del handoff UAT y documentan la línea visual y el comportamiento funcional aprobado para esta entrega.

## COM v1.1
- `COM_v1.1_mockup_editar_compania_CORE.png`: edición de Compañía activada desde catálogo CORE. Logo, Nombre y Reseña histórica son solo lectura / Fuente CORE. Zona, Regiones operativas, Estado y Motivo del cambio permanecen editables en SGI.

## ASI v0.7
- `ASI_v0.7_mockup_filtro_transferencias.png`: filtros `Transferencias salientes` y `Transferencias entrantes` dentro de Personal disponible.
- `ASI_v0.7_mockup_ficha_persona_transferir.png`: ficha del colaborador con bloque de Movimiento entre Compañías y acción `Transferir a otra Compañía`.
- `ASI_v0.7_mockup_iniciar_transferencia.png`: modal de inicio de transferencia con Compañía destino, Motivo de catálogo, Observaciones y advertencias operativas.
- `ASI_v0.7_mockup_revisar_transferencia.png`: revisión de transferencia entrante con acciones `Aceptar transferencia` y `Rechazar transferencia`.

## Reglas funcionales asociadas
- SIC: RRHH permanece SoR de la relación laboral persona–Compañía.
- El origen solo puede transferir personal actualmente perteneciente a su propia Compañía; nadie puede “jalar” personal de otra Compañía.
- La transferencia queda pendiente hasta aceptación de la Compañía destino.
- Mientras esté pendiente, el origen puede anularla.
- Al iniciar la transferencia se liberan inmediatamente las asignaciones futuras del origen; el histórico y el turno en ejecución no se alteran.
- Si el destino rechaza o el origen anula, la persona vuelve a quedar disponible en origen; las asignaciones futuras liberadas no se reconstruyen automáticamente.
- Si se acepta durante un turno en ejecución, la transferencia se hace efectiva al cierre de ese turno.
- Una transferencia aceptada es una transacción inmutable/auditable; una futura transferencia nueva sigue siendo posible.
