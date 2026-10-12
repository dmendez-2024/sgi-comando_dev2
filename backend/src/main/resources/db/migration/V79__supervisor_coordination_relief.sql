-- A Supervisor performs the relief of a coordination post, not an operational post.
-- Keep both targets explicit and mutually exclusive for traceability.
ALTER TABLE relief_event ADD COLUMN IF NOT EXISTS coordination_post_id uuid REFERENCES coordination_post(id);
ALTER TABLE relief_event ALTER COLUMN post_id DROP NOT NULL;

ALTER TABLE relief_event DROP CONSTRAINT IF EXISTS ck_relief_event_single_post_target;
ALTER TABLE relief_event ADD CONSTRAINT ck_relief_event_single_post_target
  CHECK ((post_id IS NOT NULL AND coordination_post_id IS NULL)
      OR (post_id IS NULL AND coordination_post_id IS NOT NULL));

CREATE INDEX IF NOT EXISTS ix_relief_coordination_post_time
  ON relief_event(instance_country_id,coordination_post_id,planned_at DESC)
  WHERE coordination_post_id IS NOT NULL;
