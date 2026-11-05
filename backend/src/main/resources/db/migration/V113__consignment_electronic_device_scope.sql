-- Una consigna puede aplicar a un puesto físico o a una unidad comercial de
-- seguridad electrónica. Los alcances físicos existentes se conservan tal cual.
ALTER TABLE consignment_post_scope
    DROP CONSTRAINT IF EXISTS consignment_post_scope_post_id_fkey;

ALTER TABLE consignment_post_scope
    ALTER COLUMN post_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS electronic_device_id uuid REFERENCES electronic_monitoring_post(id);

ALTER TABLE consignment_post_scope
    ADD CONSTRAINT consignment_post_scope_post_id_fkey
    FOREIGN KEY (post_id) REFERENCES post(id);

CREATE INDEX IF NOT EXISTS ix_consignment_scope_electronic_device
    ON consignment_post_scope(instance_country_id,electronic_device_id)
    WHERE electronic_device_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_consignment_electronic_device_scope
    ON consignment_post_scope(instance_country_id,consignment_id,electronic_device_id)
    WHERE electronic_device_id IS NOT NULL;

ALTER TABLE consignment_post_scope
    ADD CONSTRAINT ck_consignment_scope_target
    CHECK ((post_id IS NOT NULL AND electronic_device_id IS NULL)
        OR (post_id IS NULL AND electronic_device_id IS NOT NULL));
