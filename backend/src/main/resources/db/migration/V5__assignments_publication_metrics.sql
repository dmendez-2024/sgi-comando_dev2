-- SGI-06 Asignaciones v0.2: cobertura publicada vs actual y cierre semanal.
ALTER TABLE assignment_plan
  ADD COLUMN IF NOT EXISTS published_required_shifts integer,
  ADD COLUMN IF NOT EXISTS published_assigned_shifts integer,
  ADD COLUMN IF NOT EXISTS published_coverage_pct numeric(6,2);

CREATE INDEX IF NOT EXISTS ix_assignment_plan_status_week
  ON assignment_plan(instance_country_id,status,week_start);
