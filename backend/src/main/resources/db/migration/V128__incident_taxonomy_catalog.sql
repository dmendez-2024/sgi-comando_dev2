CREATE TABLE incident_category_catalog (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    system_key varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    code integer,
    display_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_incident_category_catalog_code CHECK (code IS NULL OR code > 0),
    CONSTRAINT ck_incident_category_catalog_order CHECK (display_order > 0),
    CONSTRAINT uq_incident_category_catalog_tenant_id UNIQUE (instance_country_id, id),
    CONSTRAINT uq_incident_category_catalog_key UNIQUE (instance_country_id, system_key),
    CONSTRAINT uq_incident_category_catalog_name UNIQUE (instance_country_id, name),
    CONSTRAINT uq_incident_category_catalog_code UNIQUE (instance_country_id, code)
);

CREATE TABLE incident_subcategory_catalog (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name varchar(160) NOT NULL,
    code integer,
    display_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_incident_subcategory_catalog_code CHECK (code IS NULL OR code > 0),
    CONSTRAINT ck_incident_subcategory_catalog_order CHECK (display_order > 0),
    CONSTRAINT uq_incident_subcategory_catalog_tenant_id UNIQUE (instance_country_id, id),
    CONSTRAINT uq_incident_subcategory_catalog_name UNIQUE (instance_country_id, category_id, name),
    CONSTRAINT uq_incident_subcategory_catalog_code UNIQUE (instance_country_id, code),
    CONSTRAINT fk_incident_subcategory_catalog_category
        FOREIGN KEY (instance_country_id, category_id)
        REFERENCES incident_category_catalog (instance_country_id, id)
);

CREATE TABLE incident_type_catalog (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    subcategory_id uuid NOT NULL,
    name varchar(180) NOT NULL,
    code integer,
    display_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_incident_type_catalog_code CHECK (code IS NULL OR code > 0),
    CONSTRAINT ck_incident_type_catalog_order CHECK (display_order > 0),
    CONSTRAINT uq_incident_type_catalog_name UNIQUE (instance_country_id, subcategory_id, name),
    CONSTRAINT uq_incident_type_catalog_code UNIQUE (instance_country_id, code),
    CONSTRAINT fk_incident_type_catalog_subcategory
        FOREIGN KEY (instance_country_id, subcategory_id)
        REFERENCES incident_subcategory_catalog (instance_country_id, id)
);

CREATE TABLE incident_severity_catalog (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    system_key varchar(40) NOT NULL,
    name varchar(80) NOT NULL,
    code integer,
    display_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_incident_severity_catalog_code CHECK (code IS NULL OR code > 0),
    CONSTRAINT ck_incident_severity_catalog_order CHECK (display_order > 0),
    CONSTRAINT uq_incident_severity_catalog_key UNIQUE (instance_country_id, system_key),
    CONSTRAINT uq_incident_severity_catalog_name UNIQUE (instance_country_id, name),
    CONSTRAINT uq_incident_severity_catalog_code UNIQUE (instance_country_id, code)
);

CREATE INDEX ix_incident_category_catalog_active
    ON incident_category_catalog (instance_country_id, active, display_order);

CREATE INDEX ix_incident_subcategory_catalog_active
    ON incident_subcategory_catalog (instance_country_id, category_id, active, display_order);

CREATE INDEX ix_incident_type_catalog_active
    ON incident_type_catalog (instance_country_id, subcategory_id, active, display_order);

CREATE INDEX ix_incident_severity_catalog_active
    ON incident_severity_catalog (instance_country_id, active, display_order);
