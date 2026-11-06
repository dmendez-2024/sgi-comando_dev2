-- Cada cámara puede pertenecer a un dispositivo OBSINT que envió SIC:COM (la línea contratada, con su horario).
-- Así la tarea OBSINT ofrece solo las cámaras de su dispositivo y el resumen avisa los dispositivos sin cámara.
ALTER TABLE secelec_component
    ADD COLUMN IF NOT EXISTS electronic_device_id uuid REFERENCES electronic_monitoring_post(id);

CREATE INDEX IF NOT EXISTS ix_secelec_component_electronic_device
    ON secelec_component(instance_country_id, electronic_device_id)
    WHERE electronic_device_id IS NOT NULL;
