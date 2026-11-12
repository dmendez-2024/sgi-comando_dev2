CREATE TABLE day_off_worked_accreditation (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
    employee_id uuid NOT NULL,
    rest_date date NOT NULL,
    occurred_at timestamptz NOT NULL,
    point_id uuid,
    post_id uuid,
    shift_id uuid NOT NULL,
    rest_evidence_ref varchar(500) NOT NULL,
    work_evidence_ref varchar(500) NOT NULL,
    reason varchar(2000) NOT NULL,
    accredited_by varchar(160) NOT NULL,
    accredited_at timestamptz NOT NULL DEFAULT current_timestamp,
    payload_hash varchar(64) NOT NULL,
    UNIQUE(instance_country_id,employee_id,rest_date),
    UNIQUE(instance_country_id,assignment_id)
);
CREATE INDEX ix_day_off_worked_incremental
    ON day_off_worked_accreditation(instance_country_id,accredited_at,id);

ALTER TABLE smc_source_cursor DROP CONSTRAINT smc_source_cursor_source_type_check;
ALTER TABLE smc_source_cursor ADD CONSTRAINT smc_source_cursor_source_type_check
CHECK (source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION',
                      'PATROL_DISTANCE','NOVELTY_APPROVAL','PATROL_NOT_EXECUTED','DAY_OFF_WORKED'));

CREATE INDEX ix_smc_patrol_not_executed
    ON patrol_execution(instance_country_id,updated_at,id)
    WHERE finished_at IS NOT NULL AND result='NO_EJECUTADA' AND employee_id IS NOT NULL;
CREATE INDEX ix_smc_patrol_occurrence_not_executed
    ON patrol_occurrence(instance_country_id,updated_at,id) WHERE status='NO_EJECUTADA';
