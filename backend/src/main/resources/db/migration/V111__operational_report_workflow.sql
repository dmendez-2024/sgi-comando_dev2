-- Flujo formal de Novedades: Pendiente -> Confirmada -> Aprobada,
-- o Pendiente/Confirmada -> Descartada. La tabla de decisiones conserva trazabilidad.
UPDATE operator_operational_report SET status='PENDING' WHERE status='REPORTED';

ALTER TABLE operator_operational_report
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE operator_operational_report
    DROP CONSTRAINT IF EXISTS ck_operator_report_status;

ALTER TABLE operator_operational_report
    ADD CONSTRAINT ck_operator_report_status
    CHECK (status IN ('PENDING','CONFIRMED','DISCARDED','APPROVED'));

CREATE TABLE operator_operational_report_decision (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    report_id uuid NOT NULL REFERENCES operator_operational_report(id),
    action varchar(24) NOT NULL,
    previous_status varchar(24) NOT NULL,
    resulting_status varchar(24) NOT NULL,
    actor_username varchar(160) NOT NULL,
    actor_role varchar(48) NOT NULL,
    actor_employee_id uuid,
    assignment_id uuid,
    shift_occurrence_id uuid,
    comment varchar(1000),
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_operator_report_decision_action
      CHECK (action IN ('CONFIRM','DISCARD','APPROVE'))
);

CREATE INDEX ix_operator_report_decision_history
    ON operator_operational_report_decision(instance_country_id,report_id,created_at);

