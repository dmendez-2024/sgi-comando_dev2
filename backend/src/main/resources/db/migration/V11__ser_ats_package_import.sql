-- SGI: Comando / SER v0.5 — importación y persistencia de paquetes .ats por Punto.
-- El .ats se trata como paquete ZIP publicado por ATS; SGI conserva el archivo original
-- y materializa el plano principal para visualización y selección de ubicaciones.

CREATE TABLE IF NOT EXISTS ats_point_package (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  point_id uuid NOT NULL REFERENCES point(id),
  revision_no integer NOT NULL,
  is_current boolean NOT NULL DEFAULT true,
  original_filename varchar(255) NOT NULL,
  ats_schema_version varchar(32),
  package_type varchar(96),
  source_pto_id varchar(80),
  source_pto_code varchar(80),
  source_pto_name varchar(255),
  model_revision integer,
  model_hash varchar(128),
  publication_id varchar(120),
  publication_version varchar(64),
  publication_status varchar(64),
  published_at timestamptz,
  package_hash varchar(128),
  archive_sha256 varchar(128) NOT NULL,
  risk_index double precision,
  plan_level_id varchar(120),
  plan_level_code varchar(80),
  plan_level_name varchar(255),
  plan_original_filename varchar(255),
  plan_internal_path varchar(512) NOT NULL,
  plan_content_type varchar(80) NOT NULL,
  plan_width_px integer,
  plan_height_px integer,
  plan_sha256 varchar(128),
  ats_file bytea NOT NULL,
  plan_image bytea NOT NULL,
  imported_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_ats_point_package_revision UNIQUE(point_id,revision_no)
);
CREATE INDEX IF NOT EXISTS ix_ats_point_package_current ON ats_point_package(instance_country_id,point_id,is_current);

-- Ubicación geométrica del Puesto sobre el plano ATS, normalizada en rango [0,1].
-- Los campos previos key/label se mantienen temporalmente para compatibilidad UAT.
ALTER TABLE post_operational_config ADD COLUMN IF NOT EXISTS ats_package_id uuid REFERENCES ats_point_package(id);
ALTER TABLE post_operational_config ADD COLUMN IF NOT EXISTS ats_location_x double precision;
ALTER TABLE post_operational_config ADD COLUMN IF NOT EXISTS ats_location_y double precision;
ALTER TABLE post_operational_config DROP CONSTRAINT IF EXISTS ck_post_operational_config_ats_xy;
ALTER TABLE post_operational_config ADD CONSTRAINT ck_post_operational_config_ats_xy CHECK (
  (ats_location_x IS NULL AND ats_location_y IS NULL)
  OR
  (ats_location_x BETWEEN 0 AND 1 AND ats_location_y BETWEEN 0 AND 1)
);
