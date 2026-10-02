-- TER / CORE: referencia estable al catálogo territorial oficial.
-- La geometría continúa en CORE; SGI conserva únicamente identidad, metadata y clasificación operacional.

ALTER TABLE country_subdivision
  ADD COLUMN IF NOT EXISTS core_subdivision_id uuid,
  ADD COLUMN IF NOT EXISTS official_code varchar(32),
  ADD COLUMN IF NOT EXISTS subdivision_type varchar(32),
  ADD COLUMN IF NOT EXISTS source_version varchar(80);

CREATE UNIQUE INDEX IF NOT EXISTS ux_country_subdivision_core_id
  ON country_subdivision(instance_country_id,core_subdivision_id)
  WHERE core_subdivision_id IS NOT NULL;
