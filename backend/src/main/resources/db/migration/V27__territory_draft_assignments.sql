-- TER: separa la configuración en borrador de la asignación territorial efectiva.

ALTER TABLE country_subdivision
  ADD COLUMN IF NOT EXISTS draft_zone_id uuid REFERENCES territory_zone(id),
  ADD COLUMN IF NOT EXISTS draft_region_id uuid REFERENCES territory_region(id);

CREATE INDEX IF NOT EXISTS ix_country_subdivision_draft_zone
  ON country_subdivision(instance_country_id,draft_zone_id);

CREATE INDEX IF NOT EXISTS ix_country_subdivision_draft_region
  ON country_subdivision(instance_country_id,draft_region_id);

-- Conserva como pendiente cualquier asignación que ya apunte a una entidad no activa.
UPDATE country_subdivision subdivision
SET draft_zone_id=subdivision.zone_id
FROM territory_zone zone
WHERE subdivision.zone_id=zone.id
  AND zone.status<>'ACTIVE';

UPDATE country_subdivision subdivision
SET draft_region_id=subdivision.region_id
FROM territory_region region
WHERE subdivision.region_id=region.id
  AND region.status<>'ACTIVE';

UPDATE country_subdivision
SET region_id=NULL
WHERE region_id IN (SELECT id FROM territory_region WHERE status<>'ACTIVE');

UPDATE country_subdivision
SET zone_id=NULL,region_id=NULL
WHERE zone_id IN (SELECT id FROM territory_zone WHERE status<>'ACTIVE');
