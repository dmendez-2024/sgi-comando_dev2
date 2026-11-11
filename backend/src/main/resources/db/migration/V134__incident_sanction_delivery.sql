ALTER TABLE incident_notification
    ADD COLUMN sanction_delivery_status varchar(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN sanction_event_id uuid,
    ADD COLUMN sanction_delivery_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN sanction_next_attempt_at timestamptz,
    ADD COLUMN sanction_sent_at timestamptz,
    ADD COLUMN sanction_last_http_status integer,
    ADD COLUMN sanction_last_error varchar(500),
    ADD CONSTRAINT ck_incident_sanction_delivery_status
        CHECK (sanction_delivery_status IN ('NOT_REQUIRED','PENDING','SENT','FAILED')),
    ADD CONSTRAINT ck_incident_sanction_delivery_attempts
        CHECK (sanction_delivery_attempts >= 0);

CREATE UNIQUE INDEX ux_incident_notification_sanction_event
    ON incident_notification(sanction_event_id)
    WHERE sanction_event_id IS NOT NULL;

CREATE INDEX ix_incident_notification_sanction_pending
    ON incident_notification(instance_country_id, sanction_next_attempt_at)
    WHERE sanction_delivery_status = 'PENDING';

CREATE UNIQUE INDEX ux_outbox_incident_sanction
    ON outbox_event(aggregate_id, event_type)
    WHERE aggregate_type = 'INCIDENT_NOTIFICATION'
      AND event_type = 'INCIDENT_SANCTION_REQUESTED';
