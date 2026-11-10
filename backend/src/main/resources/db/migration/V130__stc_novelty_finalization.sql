-- taskCode es el único dato de correlación enviado por STC; debe identificar una sola novedad globalmente.
DROP INDEX IF EXISTS uq_operator_report_stc_task;

CREATE UNIQUE INDEX uq_operator_report_stc_task
    ON operator_operational_report(routing_reference_id)
    WHERE routing_target = 'STC' AND routing_reference_id IS NOT NULL;

ALTER TABLE operator_operational_report
    DROP CONSTRAINT IF EXISTS ck_operator_report_status;

ALTER TABLE operator_operational_report
    ADD CONSTRAINT ck_operator_report_status
    CHECK (status IN ('PENDING','CONFIRMED','DISCARDED','APPROVED','FINALIZED'));

ALTER TABLE operator_operational_report_decision
    DROP CONSTRAINT IF EXISTS ck_operator_report_decision_action;

ALTER TABLE operator_operational_report_decision
    ADD CONSTRAINT ck_operator_report_decision_action
    CHECK (action IN ('CONFIRM','DISCARD','APPROVE','FINALIZE'));

COMMENT ON COLUMN operator_operational_report.routing_status IS
    'PENDING_INTEGRATION hasta aprobar; CREATED cuando STC crea la tarea; FINALIZED cuando STC confirma el cierre.';
