CREATE TABLE operator_shift_sync (
    instance_country_id uuid NOT NULL,
    assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
    employee_id uuid NOT NULL,
    device_id varchar(120) NOT NULL,
    expected_json text NOT NULL,
    prepared_at timestamptz NOT NULL DEFAULT current_timestamp,
    completed_at timestamptz,
    manifest_json text,
    manifest_hash varchar(64),
    patrols_not_executed integer NOT NULL DEFAULT 0,
    consignments_not_done integer NOT NULL DEFAULT 0,
    PRIMARY KEY(instance_country_id,assignment_id)
);
CREATE TABLE operator_shift_patrol_omission (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    patrol_definition_id uuid NOT NULL REFERENCES patrol_definition(id),
    point_id uuid NOT NULL,
    post_id uuid NOT NULL,
    shift_id uuid NOT NULL,
    window_start timestamptz NOT NULL,
    window_end timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    UNIQUE(instance_country_id,assignment_id,patrol_definition_id,window_start),
    FOREIGN KEY(instance_country_id,assignment_id) REFERENCES operator_shift_sync(instance_country_id,assignment_id)
);
CREATE INDEX ix_shift_patrol_omission_smc ON operator_shift_patrol_omission(instance_country_id,created_at,id);
ALTER TABLE consignment_compliance ADD COLUMN executed_at timestamptz;
ALTER TABLE consignment_compliance ADD COLUMN occurred_at timestamptz;

ALTER TABLE smc_source_cursor DROP CONSTRAINT smc_source_cursor_source_type_check;
ALTER TABLE smc_source_cursor ADD CONSTRAINT smc_source_cursor_source_type_check
CHECK (source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION',
 'PATROL_DISTANCE','NOVELTY_APPROVAL','PATROL_NOT_EXECUTED','DAY_OFF_WORKED',
 'POINT_RESPONSIBILITY','SHIFT_PATROL_OMISSION'));
