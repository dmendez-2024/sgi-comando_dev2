ALTER TABLE core_instance_country_snapshot
  ADD COLUMN IF NOT EXISTS core_instance_country_id uuid;
