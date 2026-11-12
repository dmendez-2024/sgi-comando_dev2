-- Motivos de baja recibidos desde SIC:RRHH. No se infieren desde el estado INACTIVE.
CREATE TABLE employee_separation_event (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    company_id uuid NOT NULL,
    separation_reason varchar(32) NOT NULL,
    effective_at timestamptz NOT NULL,
    source_updated_at timestamptz NOT NULL,
    source_idempotency_key varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_employee_separation_reason
        CHECK (separation_reason IN ('RESIGNATION','DISMISSAL','POINT_CLOSURE','OTHER')),
    CONSTRAINT ux_employee_separation_source
        UNIQUE (instance_country_id, source_idempotency_key)
);

CREATE INDEX ix_employee_separation_dashboard
    ON employee_separation_event(instance_country_id, company_id, effective_at, separation_reason);
