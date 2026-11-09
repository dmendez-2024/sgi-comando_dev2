-- Opciones versionadas para campos dinámicos de Bitácora de tipo SELECCION.
ALTER TABLE logbook_protocol_field
  ADD COLUMN IF NOT EXISTS options_json text NOT NULL DEFAULT '[]';

