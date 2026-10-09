CREATE SEQUENCE incident_notification_code_seq;

CREATE TABLE incident_notification (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    code varchar(40) NOT NULL UNIQUE,
    status varchar(16) NOT NULL CHECK (status IN ('DRAFT','FINALIZED')),
    company_id uuid REFERENCES company(id),
    point_id uuid REFERENCES point(id),
    post_id uuid REFERENCES post(id),
    created_by varchar(160) NOT NULL,
    updated_by varchar(160) NOT NULL,
    payload_json text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX ix_incident_notification_scope
    ON incident_notification(instance_country_id, company_id, updated_at DESC);
CREATE INDEX ix_incident_notification_author
    ON incident_notification(instance_country_id, created_by, updated_at DESC);
