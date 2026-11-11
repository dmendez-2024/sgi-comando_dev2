CREATE TABLE incident_sanction_employee (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    incident_id uuid NOT NULL REFERENCES incident_notification(id) ON DELETE CASCADE,
    employee_id uuid NOT NULL,
    persona_id bigint NOT NULL,
    dho_sanction_id bigint,
    decision_status varchar(20) NOT NULL DEFAULT 'PENDING',
    decision_reason text,
    decided_by varchar(160),
    decided_at timestamptz,
    last_event_id uuid,
    last_event_hash varchar(64),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_incident_sanction_employee_snapshot
        FOREIGN KEY (instance_country_id, employee_id)
        REFERENCES employee_operational_snapshot(instance_country_id, employee_id),
    CONSTRAINT uq_incident_sanction_employee
        UNIQUE (instance_country_id, incident_id, employee_id),
    CONSTRAINT ck_incident_sanction_decision_status
        CHECK (decision_status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_incident_sanction_persona
        CHECK (persona_id > 0),
    CONSTRAINT ck_incident_sanction_dho_id
        CHECK (dho_sanction_id IS NULL OR dho_sanction_id > 0)
);

CREATE UNIQUE INDEX ux_incident_sanction_dho_id
    ON incident_sanction_employee(instance_country_id, dho_sanction_id)
    WHERE dho_sanction_id IS NOT NULL;

CREATE UNIQUE INDEX ux_incident_sanction_last_event
    ON incident_sanction_employee(last_event_id)
    WHERE last_event_id IS NOT NULL;

CREATE INDEX ix_incident_sanction_incident
    ON incident_sanction_employee(instance_country_id, incident_id, created_at);

CREATE INDEX ix_incident_sanction_pending
    ON incident_sanction_employee(instance_country_id, decision_status, updated_at)
    WHERE decision_status = 'PENDING';
