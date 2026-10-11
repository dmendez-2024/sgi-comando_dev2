ALTER TABLE coordination_post
  ADD COLUMN IF NOT EXISTS configuration_locked boolean NOT NULL DEFAULT false;

UPDATE coordination_post
SET configuration_locked=true
WHERE status='ACTIVE';
