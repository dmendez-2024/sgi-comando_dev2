-- SGI: Comando / SER v0.7.2
-- Patrullas: consolidación del modelo para evitar tablas duplicadas de configuración.
-- Se adopta como canónico:
--   patrol_protocol -> patrol_definition -> patrol_checkpoint -> patrol_checkpoint_rule
-- y se reaprovechan las tablas baseline de ejecución ya existentes.

-- -----------------------------------------------------------------------------
-- 1) Evolucionar tablas canónicas existentes para soportar configuración versionada
-- -----------------------------------------------------------------------------
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS protocol_id uuid REFERENCES patrol_protocol(id);
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS code varchar(32);
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS description varchar(1000) NOT NULL DEFAULT '';
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS schedule_type varchar(20);
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS window_start time;
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS window_end time;
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS repetitions integer NOT NULL DEFAULT 1;
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS version_no integer NOT NULL DEFAULT 1;
ALTER TABLE patrol_definition ADD COLUMN IF NOT EXISTS updated_by_username varchar(80) NOT NULL DEFAULT 'system';

UPDATE patrol_definition
   SET code = COALESCE(code, 'PAT-LEGACY-' || substr(replace(id::text, '-', ''), 1, 6)),
       description = COALESCE(description, ''),
       schedule_type = COALESCE(schedule_type, 'PROGRAMMED'),
       repetitions = COALESCE(repetitions, 1),
       version_no = COALESCE(version_no, version, 1),
       updated_by_username = COALESCE(updated_by_username, 'system')
 WHERE code IS NULL
    OR schedule_type IS NULL
    OR version_no IS NULL
    OR updated_by_username IS NULL;

ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS code varchar(32);
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS description varchar(1000) NOT NULL DEFAULT '';
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS origin_mode varchar(16);
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS ats_package_id uuid REFERENCES ats_point_package(id);
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS ats_x double precision;
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS ats_y double precision;
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS gps_accuracy_m double precision;
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS location_captured_at timestamptz;
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS location_captured_by varchar(80);
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS control_type varchar(40);
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS requires_evidence boolean NOT NULL DEFAULT true;
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS standard_image_original_name varchar(255);
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS standard_image_content_type varchar(100);
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS standard_image_data bytea;
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS standard_image_version integer NOT NULL DEFAULT 0;
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS standard_image_notes varchar(1000) NOT NULL DEFAULT '';
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS visint_enabled boolean NOT NULL DEFAULT false;

UPDATE patrol_checkpoint
   SET code = COALESCE(code, 'H' || lpad(sequence_no::text, 2, '0')),
       description = COALESCE(description, ''),
       origin_mode = COALESCE(origin_mode,
                     CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL THEN 'FIELD' ELSE 'ATS' END),
       control_type = COALESCE(control_type, 'INSPECCION_VISUAL'),
       standard_image_version = COALESCE(standard_image_version, 0),
       standard_image_notes = COALESCE(standard_image_notes, ''),
       visint_enabled = COALESCE(visint_enabled, false),
       requires_evidence = COALESCE(requires_evidence, true)
 WHERE code IS NULL
    OR origin_mode IS NULL
    OR control_type IS NULL;

CREATE TABLE IF NOT EXISTS patrol_checkpoint_rule (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  checkpoint_id uuid NOT NULL REFERENCES patrol_checkpoint(id) ON DELETE CASCADE,
  sort_order integer NOT NULL,
  rule_type varchar(40) NOT NULL CHECK (rule_type IN ('INSPECCION_VISUAL','FOTOGRAFIA','CONFIRMACION','LECTURA')),
  required boolean NOT NULL DEFAULT true,
  evidence_required boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_patrol_checkpoint_rule UNIQUE(instance_country_id, checkpoint_id, rule_type)
);
CREATE INDEX IF NOT EXISTS ix_patrol_checkpoint_rule_checkpoint ON patrol_checkpoint_rule(instance_country_id, checkpoint_id, sort_order);
CREATE INDEX IF NOT EXISTS ix_patrol_definition_protocol ON patrol_definition(instance_country_id, protocol_id, code);
CREATE INDEX IF NOT EXISTS ix_patrol_checkpoint_patrol ON patrol_checkpoint(instance_country_id, patrol_definition_id, sequence_no);

-- -----------------------------------------------------------------------------
-- 2) Migrar desde tablas temporales patrol_config_* si existen
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'patrol_config_definition'
  ) THEN
    INSERT INTO patrol_definition (
      id, instance_country_id, point_id, protocol_id, code, name, description,
      structure_type, sequence_type, status, version, schedule_type,
      window_start, window_end, repetitions, version_no, updated_by_username,
      created_at, updated_at
    )
    SELECT
      cd.id,
      cd.instance_country_id,
      post.point_id,
      cd.protocol_id,
      cd.code,
      cd.name,
      cd.description,
      cd.structure_type,
      cd.sequence_type,
      COALESCE(pp.status, 'BORRADOR'),
      COALESCE(cd.version_no, 1),
      cd.schedule_type,
      cd.window_start,
      cd.window_end,
      COALESCE(cd.repetitions, 1),
      COALESCE(cd.version_no, 1),
      COALESCE(cd.updated_by_username, 'system'),
      cd.created_at,
      cd.updated_at
    FROM patrol_config_definition cd
    JOIN patrol_protocol pp ON pp.id = cd.protocol_id
    JOIN post ON post.id = pp.post_id
    WHERE NOT EXISTS (SELECT 1 FROM patrol_definition pd WHERE pd.id = cd.id);

    INSERT INTO patrol_checkpoint (
      id, instance_country_id, patrol_definition_id, sequence_no,
      code, name, description, latitude, longitude, radius_m,
      validation_rule_json, origin_mode, ats_package_id, ats_x, ats_y,
      gps_accuracy_m, location_captured_at, location_captured_by,
      control_type, requires_evidence,
      standard_image_original_name, standard_image_content_type, standard_image_data,
      standard_image_version, standard_image_notes, visint_enabled,
      created_at, updated_at
    )
    SELECT
      c.id,
      c.instance_country_id,
      c.patrol_id,
      c.sort_order,
      c.code,
      c.name,
      c.description,
      c.latitude,
      c.longitude,
      NULL,
      '{}'::text,
      c.origin_mode,
      c.ats_package_id,
      c.ats_x,
      c.ats_y,
      c.gps_accuracy_m,
      c.location_captured_at,
      c.location_captured_by,
      c.control_type,
      c.requires_evidence,
      c.standard_image_original_name,
      c.standard_image_content_type,
      c.standard_image_data,
      c.standard_image_version,
      c.standard_image_notes,
      c.visint_enabled,
      c.created_at,
      c.updated_at
    FROM patrol_config_checkpoint c
    WHERE NOT EXISTS (SELECT 1 FROM patrol_checkpoint pc WHERE pc.id = c.id);

    IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'public' AND table_name = 'patrol_config_checkpoint_rule'
    ) THEN
      INSERT INTO patrol_checkpoint_rule (
        id, instance_country_id, checkpoint_id, sort_order, rule_type,
        required, evidence_required, created_at, updated_at
      )
      SELECT
        r.id,
        r.instance_country_id,
        r.checkpoint_id,
        r.sort_order,
        r.rule_type,
        r.required,
        r.evidence_required,
        r.created_at,
        r.updated_at
      FROM patrol_config_checkpoint_rule r
      WHERE NOT EXISTS (SELECT 1 FROM patrol_checkpoint_rule pr WHERE pr.id = r.id);
    END IF;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 3) Retirar tablas duplicadas una vez migrados los datos
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS patrol_config_checkpoint_rule;
DROP TABLE IF EXISTS patrol_config_checkpoint;
DROP TABLE IF EXISTS patrol_config_definition;
