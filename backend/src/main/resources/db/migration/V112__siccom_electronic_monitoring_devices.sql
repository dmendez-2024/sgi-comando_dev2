-- Servicio comercial de Seguridad Electrónica recibido desde SIC:COM.
-- Es distinto de secelec_component: el primero representa OBSINT contratado;
-- el segundo representa sus cámaras, sensores y demás componentes físicos.
CREATE TABLE IF NOT EXISTS electronic_monitoring_post (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    point_id uuid NOT NULL,
    code varchar(64) NOT NULL,
    source_code2 varchar(64) NOT NULL,
    post_type varchar(32) NOT NULL,
    name varchar(160) NOT NULL,
    format varchar(40) NOT NULL,
    product varchar(160),
    minutes_per_day integer,
    commercial_status varchar(16) NOT NULL CHECK (commercial_status IN ('ACTIVE','INACTIVE')),
    config_status varchar(24) NOT NULL,
    commercial_version varchar(120),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ux_electronic_monitoring_post_code UNIQUE (instance_country_id, code),
    CONSTRAINT ux_electronic_monitoring_post_source UNIQUE (instance_country_id, source_code2)
);
ALTER TABLE electronic_monitoring_post
    ADD COLUMN IF NOT EXISTS post_type varchar(32) NOT NULL DEFAULT 'OBSINT';
CREATE INDEX IF NOT EXISTS ix_electronic_monitoring_post_point
    ON electronic_monitoring_post(instance_country_id, point_id);

CREATE TABLE IF NOT EXISTS electronic_monitoring_shift (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    monitoring_post_id uuid NOT NULL REFERENCES electronic_monitoring_post(id) ON DELETE CASCADE,
    shift_code varchar(64) NOT NULL,
    shift_name varchar(160) NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    day_mask integer NOT NULL,
    active boolean NOT NULL,
    commercial_version varchar(120),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ux_electronic_monitoring_shift_code UNIQUE (instance_country_id, monitoring_post_id, shift_code)
);

CREATE TABLE IF NOT EXISTS electronic_monitoring_code_allocation (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    base_code varchar(4) NOT NULL,
    source_signature varchar(800) NOT NULL,
    discriminator varchar(32) NOT NULL,
    last_sequence integer NOT NULL DEFAULT 0 CHECK (last_sequence BETWEEN 0 AND 999),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ux_electronic_monitoring_code_allocation UNIQUE (instance_country_id, base_code, source_signature)
);
