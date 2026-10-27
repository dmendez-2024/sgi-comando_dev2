-- OBSINT · Consola (mínimo del mockup OBSINT_mockup_real_v4 y de la diapositiva "Consola / OBSINT").
-- Catálogo (cámara, consigna OBSINT) → caso del monitorista (tareas en orden con tiempo máximo) → evidencia y mensajes.
-- Módulo aislado: solo tablas obsint_* y sin llaves hacia tablas de otros módulos. El enlace con Punto/Puesto se agregará
-- en otra migración cuando exista la configuración Seguridad Electrónica / OBSINT del Punto.

CREATE TABLE obsint_camera (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    location varchar(200),
    stream_url varchar(500) NOT NULL,
    live boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (instance_country_id, code)
);

CREATE TABLE obsint_consigna (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    code varchar(40) NOT NULL,
    title varchar(160) NOT NULL,
    instruction varchar(1000) NOT NULL,
    time_limit_seconds int NOT NULL CHECK (time_limit_seconds BETWEEN 10 AND 3600),
    position int NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (instance_country_id, code)
);

CREATE TABLE obsint_consigna_camera (
    consigna_id uuid NOT NULL REFERENCES obsint_consigna(id) ON DELETE CASCADE,
    camera_id uuid NOT NULL REFERENCES obsint_camera(id),
    position int NOT NULL,
    PRIMARY KEY (consigna_id, camera_id)
);

CREATE SEQUENCE obsint_case_code_seq;

CREATE TABLE obsint_case (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    code varchar(40) NOT NULL UNIQUE,
    operator varchar(160) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED')),
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX ix_obsint_case_operator ON obsint_case(instance_country_id, operator, started_at DESC);

CREATE TABLE obsint_case_task (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    case_id uuid NOT NULL REFERENCES obsint_case(id) ON DELETE CASCADE,
    consigna_id uuid NOT NULL REFERENCES obsint_consigna(id),
    position int NOT NULL,
    consigna_code varchar(40) NOT NULL,
    instruction varchar(1000) NOT NULL,
    time_limit_seconds int NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','ANSWERED')),
    started_at timestamptz,
    answered_at timestamptz,
    decision varchar(20) CHECK (decision IN ('YES','NO','UNCERTAIN')),
    justification varchar(500),
    seconds_used int,
    timed_out boolean,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (case_id, position)
);

CREATE TABLE obsint_evidence (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    task_id uuid NOT NULL REFERENCES obsint_case_task(id) ON DELETE CASCADE,
    camera_id uuid NOT NULL REFERENCES obsint_camera(id),
    video_time_seconds double precision NOT NULL,
    captured_at timestamptz NOT NULL,
    bucket varchar(120) NOT NULL,
    object_key varchar(400) NOT NULL,
    content_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX ix_obsint_evidence_task ON obsint_evidence(task_id, captured_at);

CREATE TABLE obsint_agent_message (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    task_id uuid NOT NULL REFERENCES obsint_case_task(id) ON DELETE CASCADE,
    operator varchar(160) NOT NULL,
    text varchar(500) NOT NULL,
    sent_at timestamptz NOT NULL,
    delivery_status varchar(20) NOT NULL CHECK (delivery_status IN ('SENT','FAILED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX ix_obsint_agent_message_task ON obsint_agent_message(task_id, sent_at);
