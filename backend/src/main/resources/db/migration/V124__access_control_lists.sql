CREATE TABLE access_control_entry (
    id UUID PRIMARY KEY,
    instance_country_id UUID NOT NULL,
    client_id UUID NOT NULL REFERENCES client(id),
    list_type VARCHAR(16) NOT NULL CHECK (list_type IN ('WHITE','BLACK')),
    object_type VARCHAR(16) NOT NULL DEFAULT 'PAX' CHECK (object_type IN ('PAX','VHL','CONT')),
    identification VARCHAR(40) NOT NULL,
    identification_normalized VARCHAR(40) NOT NULL,
    last_names VARCHAR(180) NOT NULL,
    first_names VARCHAR(180) NOT NULL,
    accreditation VARCHAR(120) NOT NULL,
    organizational_reference VARCHAR(240),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    observations VARCHAR(1000),
    photo_data BYTEA,
    photo_content_type VARCHAR(80),
    photo_filename VARCHAR(255),
    qr_token_hash VARCHAR(64),
    qr_png BYTEA,
    qr_generated_at TIMESTAMPTZ,
    qr_revoked_at TIMESTAMPTZ,
    created_by_username VARCHAR(120) NOT NULL,
    updated_by_username VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_access_control_validity CHECK (valid_until >= valid_from),
    CONSTRAINT ck_access_control_qr_white_only CHECK (list_type = 'WHITE' OR qr_token_hash IS NULL),
    CONSTRAINT uq_access_control_identity UNIQUE (instance_country_id, client_id, list_type, object_type, identification_normalized)
);

CREATE INDEX ix_access_control_entry_lookup
    ON access_control_entry(instance_country_id, client_id, list_type, status, identification_normalized);

CREATE UNIQUE INDEX uq_access_control_qr_hash
    ON access_control_entry(instance_country_id, qr_token_hash)
    WHERE qr_token_hash IS NOT NULL;

CREATE TABLE access_control_entry_point (
    entry_id UUID NOT NULL REFERENCES access_control_entry(id) ON DELETE CASCADE,
    point_id UUID NOT NULL REFERENCES point(id),
    instance_country_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (entry_id, point_id)
);

CREATE INDEX ix_access_control_entry_point_point
    ON access_control_entry_point(instance_country_id, point_id);
