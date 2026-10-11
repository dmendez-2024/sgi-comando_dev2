-- COO -> ASI: materialize and assign shifts for active Coordination posts.
ALTER TABLE shift_occurrence
  ALTER COLUMN post_id DROP NOT NULL,
  ADD COLUMN IF NOT EXISTS coordination_post_id uuid REFERENCES coordination_post(id);

ALTER TABLE shift_occurrence
  DROP CONSTRAINT IF EXISTS ck_shift_occurrence_single_target;

ALTER TABLE shift_occurrence
  ADD CONSTRAINT ck_shift_occurrence_single_target CHECK (
    (post_id IS NOT NULL AND coordination_post_id IS NULL) OR
    (post_id IS NULL AND coordination_post_id IS NOT NULL)
  );

CREATE INDEX IF NOT EXISTS ix_shift_occurrence_coordination_post
  ON shift_occurrence(instance_country_id, coordination_post_id, starts_at, ends_at)
  WHERE coordination_post_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_shift_occurrence_coordination_date_code
  ON shift_occurrence(instance_country_id, coordination_post_id, local_date, shift_code)
  WHERE coordination_post_id IS NOT NULL;
