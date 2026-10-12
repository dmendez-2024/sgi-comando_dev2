-- Preserve the exact published supervision-route version used by an assignment shift.
ALTER TABLE shift_occurrence
  ADD COLUMN IF NOT EXISTS supervision_route_id uuid REFERENCES supervision_route(id);

UPDATE shift_occurrence shift
SET supervision_route_id = (
  SELECT r.id
  FROM supervision_route r
  WHERE r.instance_country_id = shift.instance_country_id
    AND r.coordination_post_id = shift.coordination_post_id
    AND r.status = 'ACTIVE'
  ORDER BY r.version_no DESC
  LIMIT 1
)
WHERE shift.coordination_post_id IS NOT NULL
  AND shift.supervision_route_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_shift_occurrence_supervision_route
  ON shift_occurrence(instance_country_id, supervision_route_id)
  WHERE supervision_route_id IS NOT NULL;
