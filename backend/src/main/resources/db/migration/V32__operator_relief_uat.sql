-- REL integration v1: reception is NOT proof of operational completion.
CREATE TABLE operator_employee_binding (
 instance_country_id uuid NOT NULL,
 username varchar(80) NOT NULL,
 employee_id uuid NOT NULL,
 active boolean NOT NULL DEFAULT true,
 PRIMARY KEY(instance_country_id, username)
);
CREATE TABLE operator_relief_submission (
 id uuid PRIMARY KEY REFERENCES relief_event(id),
 instance_country_id uuid NOT NULL,
 assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
 employee_id uuid NOT NULL,
 username varchar(80) NOT NULL,
 device_id varchar(120) NOT NULL,
 batch_id uuid NOT NULL,
 correlation_id uuid NOT NULL,
 payload_hash char(64) NOT NULL,
 payload_json text NOT NULL,
 context_json text NOT NULL,
 received_at timestamptz NOT NULL,
 UNIQUE(instance_country_id, assignment_id)
);
CREATE INDEX ix_operator_relief_received ON operator_relief_submission(instance_country_id, received_at DESC);
CREATE TABLE operator_relief_evidence (
 id uuid PRIMARY KEY,
 instance_country_id uuid NOT NULL,
 event_id uuid NOT NULL,
 assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
 username varchar(80) NOT NULL,
 purpose varchar(40) NOT NULL,
 sha256 char(64) NOT NULL,
 content_type varchar(80) NOT NULL,
 content bytea NOT NULL,
 created_at timestamptz NOT NULL,
 UNIQUE(instance_country_id,event_id,purpose),
 CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);
-- Binary storage is isolated UAT storage. No automatic promotion to production.
