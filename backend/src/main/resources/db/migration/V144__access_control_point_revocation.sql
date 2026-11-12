ALTER TABLE access_control_entry_point
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by_username VARCHAR(120),
    ADD COLUMN revocation_reason VARCHAR(500);
