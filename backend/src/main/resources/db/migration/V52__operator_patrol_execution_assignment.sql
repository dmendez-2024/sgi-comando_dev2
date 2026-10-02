CREATE INDEX ix_patrol_execution_assignment
    ON patrol_execution(instance_country_id, assignment_id, started_at DESC);

CREATE UNIQUE INDEX ux_patrol_execution_active_assignment_plan
    ON patrol_execution(instance_country_id, assignment_id, patrol_plan_id)
    WHERE assignment_id IS NOT NULL AND finished_at IS NULL;
