-- SGI: Comando / COM — Compañías v0.1
-- Scope: COM only. TER v1.0 remains frozen and unmodified.

ALTER TABLE company
  ADD COLUMN IF NOT EXISTS zone_id uuid REFERENCES territory_zone(id),
  ADD COLUMN IF NOT EXISTS historical_review varchar(750),
  ADD COLUMN IF NOT EXISTS logo_data_url text,
  ADD COLUMN IF NOT EXISTS version_number integer NOT NULL DEFAULT 1;

UPDATE company c
SET zone_id = r.zone_id
FROM territory_region r
WHERE c.region_id = r.id AND c.zone_id IS NULL;

CREATE TABLE IF NOT EXISTS company_region (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  company_id uuid NOT NULL REFERENCES company(id),
  region_id uuid NOT NULL REFERENCES territory_region(id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, company_id, region_id)
);
CREATE INDEX IF NOT EXISTS ix_company_region_company ON company_region(instance_country_id, company_id);
CREATE INDEX IF NOT EXISTS ix_company_region_region ON company_region(instance_country_id, region_id);

INSERT INTO company_region(id, instance_country_id, company_id, region_id, created_at, updated_at)
SELECT gen_random_uuid(), c.instance_country_id, c.id, c.region_id, now(), now()
FROM company c
WHERE c.region_id IS NOT NULL
ON CONFLICT (instance_country_id, company_id, region_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS company_version (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  company_id uuid NOT NULL REFERENCES company(id),
  version_number integer NOT NULL,
  change_type varchar(48) NOT NULL,
  change_reason varchar(500),
  actor_username varchar(80) NOT NULL,
  effective_at timestamptz NOT NULL,
  snapshot_json text NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, company_id, version_number)
);
CREATE INDEX IF NOT EXISTS ix_company_version_company ON company_version(instance_country_id, company_id, version_number DESC);

INSERT INTO company_version(
  id, instance_country_id, company_id, version_number, change_type, change_reason,
  actor_username, effective_at, snapshot_json, created_at, updated_at
)
SELECT
  gen_random_uuid(), c.instance_country_id, c.id, 1, 'BASELINE', 'Migración inicial COM v0.1',
  'SYSTEM', now(),
  jsonb_build_object(
    'code', c.code,
    'name', c.name,
    'status', c.status,
    'zoneId', c.zone_id,
    'regionIds', COALESCE((SELECT jsonb_agg(cr.region_id ORDER BY cr.region_id) FROM company_region cr WHERE cr.company_id=c.id), '[]'::jsonb),
    'historicalReview', c.historical_review,
    'logoDataUrl', c.logo_data_url,
    'requiredChangeCount', c.required_change_count
  )::text,
  now(), now()
FROM company c
WHERE NOT EXISTS (
  SELECT 1 FROM company_version v
  WHERE v.instance_country_id=c.instance_country_id AND v.company_id=c.id AND v.version_number=1
);
