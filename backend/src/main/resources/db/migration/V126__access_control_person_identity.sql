CREATE TABLE access_control_person (
    id UUID PRIMARY KEY,
    instance_country_id UUID NOT NULL,
    object_type VARCHAR(16) NOT NULL CHECK (object_type IN ('PAX','VHL','CONT')),
    identification VARCHAR(40) NOT NULL,
    identification_normalized VARCHAR(40) NOT NULL,
    last_names VARCHAR(180) NOT NULL,
    first_names VARCHAR(180) NOT NULL,
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
    CONSTRAINT uq_access_control_person_identity UNIQUE
        (instance_country_id, object_type, identification_normalized)
);

CREATE UNIQUE INDEX uq_access_control_person_qr_hash
    ON access_control_person(instance_country_id, qr_token_hash)
    WHERE qr_token_hash IS NOT NULL;

-- Conserva una sola identidad por documento y reutiliza su QR actual más reciente.
INSERT INTO access_control_person (
    id, instance_country_id, object_type, identification,
    identification_normalized, last_names, first_names,
    photo_data, photo_content_type, photo_filename,
    qr_token_hash, qr_png, qr_generated_at, qr_revoked_at,
    created_by_username, updated_by_username, created_at, updated_at
)
SELECT DISTINCT ON (instance_country_id, object_type, identification_normalized)
    gen_random_uuid(), instance_country_id, object_type, identification,
    identification_normalized, last_names, first_names,
    photo_data, photo_content_type, photo_filename,
    qr_token_hash, qr_png, qr_generated_at, qr_revoked_at,
    created_by_username, updated_by_username, created_at, updated_at
FROM access_control_entry
ORDER BY instance_country_id, object_type, identification_normalized,
         (qr_token_hash IS NOT NULL) DESC, updated_at DESC;

ALTER TABLE access_control_entry ADD COLUMN person_id UUID;

UPDATE access_control_entry entry
SET person_id = person.id
FROM access_control_person person
WHERE person.instance_country_id = entry.instance_country_id
  AND person.object_type = entry.object_type
  AND person.identification_normalized = entry.identification_normalized;

ALTER TABLE access_control_entry ALTER COLUMN person_id SET NOT NULL;
ALTER TABLE access_control_entry
    ADD CONSTRAINT fk_access_control_entry_person
    FOREIGN KEY (person_id) REFERENCES access_control_person(id);

CREATE INDEX ix_access_control_entry_person
    ON access_control_entry(instance_country_id, person_id, client_id, list_type);

-- Una persona puede conservar varias autorizaciones históricas sin sobrescribir
-- períodos anteriores. La aplicación impide vigencias superpuestas.
ALTER TABLE access_control_entry DROP CONSTRAINT uq_access_control_identity;
CREATE INDEX ix_access_control_authorization_history
    ON access_control_entry(instance_country_id, client_id, list_type, person_id, valid_from, valid_until);
