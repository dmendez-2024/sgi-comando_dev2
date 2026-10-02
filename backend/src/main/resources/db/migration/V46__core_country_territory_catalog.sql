-- CORE owns official country/subdivision identity. SGI keeps only a synchronized
-- reference snapshot plus its operational Zone/Region assignments.

ALTER TABLE country_subdivision
  ADD COLUMN IF NOT EXISTS core_subdivision_id uuid,
  ADD COLUMN IF NOT EXISTS core_dataset_version varchar(80),
  ADD COLUMN IF NOT EXISTS geometry_json text;

CREATE UNIQUE INDEX IF NOT EXISTS ux_country_subdivision_core_id
  ON country_subdivision(instance_country_id, core_subdivision_id)
  WHERE core_subdivision_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS core_instance_country_snapshot (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL UNIQUE,
  country_code varchar(8) NOT NULL,
  country_name varchar(160) NOT NULL,
  locale varchar(24) NOT NULL,
  timezone varchar(64) NOT NULL,
  currency varchar(8) NOT NULL,
  subdivision_type varchar(32) NOT NULL,
  subdivision_singular varchar(64) NOT NULL,
  subdivision_plural varchar(64) NOT NULL,
  territorial_dataset_version varchar(80) NOT NULL,
  synced_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
