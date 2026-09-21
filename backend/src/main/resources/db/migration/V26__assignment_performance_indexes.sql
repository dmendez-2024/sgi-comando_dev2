-- SGI: Comando performance hardening for 2,000+ personnel / large assignment timelines.
-- No UI or functional behavior changes.

CREATE INDEX IF NOT EXISTS ix_employee_transfer_due
  ON employee_company_transfer(instance_country_id, status, effective_at)
  WHERE status = 'ACCEPTED_PENDING_EFFECTIVE';

CREATE INDEX IF NOT EXISTS ix_shift_occurrence_time_window
  ON shift_occurrence(instance_country_id, starts_at, ends_at);

CREATE INDEX IF NOT EXISTS ix_assignment_plan_active_shift
  ON operational_assignment(instance_country_id, assignment_plan_id, shift_occurrence_id)
  WHERE status <> 'REMOVED';

CREATE INDEX IF NOT EXISTS ix_assignment_employee_active_shift
  ON operational_assignment(instance_country_id, employee_id, shift_occurrence_id)
  WHERE status <> 'REMOVED' AND employee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_assignment_actual_employee_active_shift
  ON operational_assignment(instance_country_id, actual_employee_id, shift_occurrence_id)
  WHERE status <> 'REMOVED' AND actual_employee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_unavailability_active_employee_time
  ON employee_unavailability_snapshot(instance_country_id, employee_id, starts_at, ends_at)
  WHERE source_status = 'ACTIVE';

-- Personnel pool: default browse is company + alphabetical pagination; role is the
-- most common additional server-side filter. Keeping the employee id in the key
-- also gives PostgreSQL a stable tie-breaker without touching the UI contract.
CREATE INDEX IF NOT EXISTS ix_employee_snapshot_company_name
  ON employee_operational_snapshot(instance_country_id, company_id, full_name, employee_id);

CREATE INDEX IF NOT EXISTS ix_employee_snapshot_company_role_name
  ON employee_operational_snapshot(instance_country_id, company_id, role_code, full_name, employee_id);

-- Assigned-hours aggregation is scoped to the selected weekly plan and only to
-- the personnel page currently visible in the UI.
CREATE INDEX IF NOT EXISTS ix_assignment_plan_employee_active
  ON operational_assignment(instance_country_id, assignment_plan_id, employee_id)
  WHERE status <> 'REMOVED' AND employee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_assignment_plan_actual_employee_active
  ON operational_assignment(instance_country_id, assignment_plan_id, actual_employee_id)
  WHERE status <> 'REMOVED' AND actual_employee_id IS NOT NULL;

-- Availability filter is server-side in PERF-001, so the week-overlap scan must
-- not walk the full unavailability history when there are thousands of people.
CREATE INDEX IF NOT EXISTS ix_unavailability_active_week
  ON employee_unavailability_snapshot(instance_country_id, starts_at, ends_at, employee_id)
  WHERE source_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS ix_unavailability_active_type_week
  ON employee_unavailability_snapshot(instance_country_id, type, starts_at, ends_at, employee_id)
  WHERE source_status = 'ACTIVE';
