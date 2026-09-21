# SGI: Comando — SER v0.7.3 UAT

## Motivo de la corrección
SER v0.7.2 consolidó las tablas duplicadas de Patrullas, pero el CRUD Java todavía no poblaba todos los campos `NOT NULL` heredados del baseline de `patrol_definition` / `patrol_checkpoint`. Además, la UI de Patrullas seguía demasiado comprimida.

## Persistencia
- Se mantienen como únicas entidades canónicas de configuración:
  - `patrol_protocol`
  - `patrol_definition`
  - `patrol_checkpoint`
  - `patrol_checkpoint_rule`
- No se crean tablas nuevas `patrol_config_*`.
- `PatrolDefinition` ahora persiste explícitamente:
  - `point_id`
  - `status`
  - `version`
  - además de los campos SER (`protocol_id`, `code`, `schedule_type`, etc.).
- `PatrolCheckpoint` ahora persiste `validation_rule_json` requerido por el baseline y conserva `radius_m` para compatibilidad.
- Crear, clonar, editar y publicar Patrullas sincroniza los campos baseline de estado/versión.
- Eliminar una Patrulla elimina primero reglas e Hitos del borrador para respetar FKs existentes.
- V16 agrega defaults e índices parciales sin alterar migraciones ya aplicadas de v0.7.2.

## UI
- Cabecera del Protocolo separada en barra de contexto/acciones + bloque de identidad.
- **Historial** y **Crear nueva versión** quedan juntos en la barra superior de acciones.
- El aviso de versión publicada se muestra como banner explícito de solo lectura.
- El nombre del Protocolo y de la Patrulla pasan a ser elementos jerárquicos principales.
- Se incrementan tipografías de tablas, Hitos, tabs, KPIs y textos de contexto.
- Se mantienen línea gráfica clara y paleta SGI: Comando ya aprobadas.

## Regla de versionado
Guardar modifica solo el borrador. Publicar crea el snapshot inmutable. Una versión publicada nunca se edita directamente.
