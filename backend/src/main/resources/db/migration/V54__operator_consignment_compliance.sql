CREATE TABLE consignment_compliance (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    protocol_id uuid NOT NULL REFERENCES consignment_protocol(id),
    consignment_id uuid NOT NULL REFERENCES consignment(id),
    assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
    employee_id uuid NOT NULL,
    username varchar(160) NOT NULL,
    result varchar(32) NOT NULL CHECK (result IN ('CUMPLIDA','NO_CUMPLIDA','CON_NOVEDAD','NO_APLICA')),
    comment varchar(2000) NOT NULL DEFAULT '',
    photo_count integer NOT NULL DEFAULT 0 CHECK (photo_count BETWEEN 0 AND 5),
    gps_required boolean NOT NULL DEFAULT false,
    payload_hash varchar(64) NOT NULL,
    payload_json text NOT NULL,
    confirmed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp
);

CREATE INDEX ix_consignment_compliance_assignment
    ON consignment_compliance(instance_country_id, assignment_id, confirmed_at DESC);

CREATE INDEX ix_consignment_compliance_consignment
    ON consignment_compliance(instance_country_id, consignment_id, confirmed_at DESC);

CREATE UNIQUE INDEX ux_consignment_compliance_assignment_item
    ON consignment_compliance(instance_country_id, assignment_id, consignment_id);
