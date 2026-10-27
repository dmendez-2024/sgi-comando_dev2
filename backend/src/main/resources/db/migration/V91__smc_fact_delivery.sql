-- Bandeja durable exclusiva de SMC. No altera tablas ni estados operativos.
CREATE TABLE smc_fact_delivery (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    source_type varchar(40) NOT NULL,
    source_id uuid NOT NULL,
    event_id uuid NOT NULL,
    fact_type varchar(40) NOT NULL,
    payload_json text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    last_status_code integer,
    last_error text,
    smc_fact_id varchar(64),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    CONSTRAINT uk_smc_fact_delivery_source UNIQUE (instance_country_id, source_type, source_id),
    CONSTRAINT uk_smc_fact_delivery_event UNIQUE (event_id),
    CONSTRAINT ck_smc_fact_delivery_status CHECK (status IN ('PENDING','SENT','REJECTED')),
    CONSTRAINT ck_smc_fact_delivery_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_smc_fact_delivery_pending
    ON smc_fact_delivery(instance_country_id, next_attempt_at, created_at) WHERE status = 'PENDING';
