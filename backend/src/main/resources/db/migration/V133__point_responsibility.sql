CREATE TABLE point_responsibility (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    assignment_plan_id uuid NOT NULL REFERENCES assignment_plan(id),
    point_id uuid NOT NULL REFERENCES point(id),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    employee_id uuid,
    selected_at timestamptz NOT NULL,
    selected_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (ends_at > starts_at),
    UNIQUE (instance_country_id, point_id, starts_at, ends_at)
);
CREATE INDEX ix_point_responsibility_plan ON point_responsibility(instance_country_id, assignment_plan_id);
CREATE INDEX ix_point_responsibility_history ON assignment_event(instance_country_id, event_type, reason)
    WHERE event_type='POINT_RESPONSIBLE_SELECTED';
