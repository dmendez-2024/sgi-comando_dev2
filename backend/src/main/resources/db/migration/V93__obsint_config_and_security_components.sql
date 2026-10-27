-- Seguridad Electrónica (Componentes de Seguridad del Punto) y configuración OBSINT por Punto (protocolo versionado,
-- consignas con categoría, cámaras y horario). Transforma el esquema de V91, que solo tenía un catálogo DEMO global.
-- Módulos aislados: solo tablas secelec_* y obsint_*. point_id no lleva llave hacia point para no acoplarse a ese módulo;
-- el acceso al Punto se valida en la API.

-- Lo creado con V91 eran datos DEMO de UAT (catálogo de prueba y sus casos): se descartan. Las fotos de evidencia de esos
-- casos quedan en MinIO (evidence/{tenant}/obsint/...) y pueden borrarse a mano.
DELETE FROM obsint_case;      -- en cascada: tareas, evidencias y mensajes
DELETE FROM obsint_consigna;  -- en cascada: cámaras asociadas

-- ---------- Componentes de Seguridad ----------
CREATE TABLE secelec_component (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    point_id uuid NOT NULL,
    family varchar(24) NOT NULL CHECK (family IN ('PERIMETER','ACCESS_CONTROL','VIDEO','INTRUSION','LIGHTING','FIRE')),
    component_type varchar(80) NOT NULL,
    code varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    location varchar(200),
    coverage varchar(300),
    brand_model varchar(160),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','OBSERVED','INACTIVE')),
    observations varchar(1000),
    -- Conexión (solo Videovigilancia). La contraseña se guarda cifrada (AES-GCM) y nunca se devuelve por la API.
    ip varchar(255),
    port int CHECK (port BETWEEN 1 AND 65535),
    username varchar(120),
    password_cipher varchar(600),
    -- Video de pruebas mientras no exista el servidor de video (RTSP -> HLS/WebRTC).
    stream_url varchar(500),
    updated_by varchar(160) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (instance_country_id, point_id, code)
);
CREATE INDEX ix_secelec_component_point ON secelec_component(instance_country_id, point_id, family);

-- ---------- Protocolo OBSINT (mismo ciclo que Consignas: BORRADOR -> INACTIVO (publicado) -> ACTIVO) ----------
CREATE TABLE obsint_protocol (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    series_id uuid NOT NULL,
    based_on_protocol_id uuid REFERENCES obsint_protocol(id),
    point_id uuid NOT NULL,
    code varchar(32) NOT NULL,
    version_no int NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('BORRADOR','INACTIVO','ACTIVO')),
    published_at timestamptz,
    activated_at timestamptz,
    updated_by varchar(160) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (series_id, version_no)
);
CREATE UNIQUE INDEX ux_obsint_protocol_active_point ON obsint_protocol(instance_country_id, point_id) WHERE status = 'ACTIVO';
CREATE UNIQUE INDEX ux_obsint_protocol_draft_point ON obsint_protocol(instance_country_id, point_id) WHERE status = 'BORRADOR';

-- ---------- Consigna: pertenece a un protocolo del Punto; el código es único dentro del protocolo ----------
ALTER TABLE obsint_consigna DROP CONSTRAINT IF EXISTS obsint_consigna_instance_country_id_code_key;
ALTER TABLE obsint_consigna
    ADD COLUMN protocol_id uuid NOT NULL REFERENCES obsint_protocol(id) ON DELETE CASCADE,
    ADD COLUMN point_id uuid NOT NULL,
    ADD COLUMN category varchar(24) NOT NULL CHECK (category IN ('SSO','PRODUCTION','LOGISTICS','CLEANING','MOBILITY','CUSTOMER_SERVICE')),
    ADD CONSTRAINT ux_obsint_consigna_protocol_code UNIQUE (protocol_id, code);

-- Las cámaras de una consigna ahora son componentes de Videovigilancia del Punto.
DROP TABLE obsint_consigna_camera;
CREATE TABLE obsint_consigna_camera (
    consigna_id uuid NOT NULL REFERENCES obsint_consigna(id) ON DELETE CASCADE,
    component_id uuid NOT NULL REFERENCES secelec_component(id),
    position int NOT NULL,
    PRIMARY KEY (consigna_id, component_id)
);

-- Horario de ejecución: 1 = lunes … 7 = domingo; un tramo por día.
CREATE TABLE obsint_consigna_schedule (
    consigna_id uuid NOT NULL REFERENCES obsint_consigna(id) ON DELETE CASCADE,
    day_of_week int NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time time NOT NULL,
    end_time time NOT NULL CHECK (end_time > start_time),
    PRIMARY KEY (consigna_id, day_of_week)
);

-- La evidencia apunta a la cámara (componente) de la que se capturó.
ALTER TABLE obsint_evidence DROP CONSTRAINT IF EXISTS obsint_evidence_camera_id_fkey;
ALTER TABLE obsint_evidence ADD CONSTRAINT fk_obsint_evidence_camera FOREIGN KEY (camera_id) REFERENCES secelec_component(id);

DROP TABLE obsint_camera;
