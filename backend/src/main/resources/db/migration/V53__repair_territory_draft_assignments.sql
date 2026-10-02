-- TER: repara asignaciones en borrador que fueron escritas como configuración efectiva.

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
