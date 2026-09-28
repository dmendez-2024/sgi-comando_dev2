-- SGI: Comando / SER v0.7.3
-- Corrección de compatibilidad sobre tablas canónicas de Patrullas.
-- No crea tablas paralelas. Se refuerzan defaults/índices necesarios para que
-- patrol_definition y patrol_checkpoint sirvan tanto a Configuración como a Operación.

-- Compatibilidad con columnas baseline NOT NULL.
ALTER TABLE patrol_definition ALTER COLUMN status SET DEFAULT 'BORRADOR';
ALTER TABLE patrol_definition ALTER COLUMN version SET DEFAULT 1;
ALTER TABLE patrol_checkpoint ALTER COLUMN validation_rule_json SET DEFAULT '{}';

-- Índices únicos parciales: solo aplican a definiciones gestionadas por SER.
CREATE UNIQUE INDEX IF NOT EXISTS uq_patrol_definition_protocol_code
ON patrol_definition(instance_country_id, protocol_id, code)
WHERE protocol_id IS NOT NULL AND code IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_patrol_checkpoint_definition_code
ON patrol_checkpoint(instance_country_id, patrol_definition_id, code)
WHERE code IS NOT NULL;

-- Limpieza defensiva: después de v0.7.2 estas tablas no deben existir.
DROP TABLE IF EXISTS patrol_config_checkpoint_rule;
DROP TABLE IF EXISTS patrol_config_checkpoint;
DROP TABLE IF EXISTS patrol_config_definition;
