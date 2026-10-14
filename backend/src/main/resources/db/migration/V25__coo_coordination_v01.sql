-- COO v0.1 — Coordinación: Puestos internos de Monitoreo/Supervisión y Rutas versionadas.
-- SER v0.10.10 permanece FROZEN; esta migración solo agrega entidades COO.

CREATE TABLE IF NOT EXISTS coordination_post (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  company_id uuid NOT NULL REFERENCES company(id),
  code varchar(24) NOT NULL,
  name varchar(160) NOT NULL,
  post_type varchar(24) NOT NULL,
  format varchar(16) NOT NULL,
  rotation varchar(16) NOT NULL,
  shift_start_time time NOT NULL,
  day_mask integer NOT NULL,
  status varchar(16) NOT NULL,
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_coordination_post_code UNIQUE(instance_country_id,company_id,code),
  CONSTRAINT ck_coordination_post_type CHECK(post_type IN ('MONITORING','SUPERVISION')),
  CONSTRAINT ck_coordination_post_format CHECK(format IN ('24/7','12/7','12/5')),
  CONSTRAINT ck_coordination_post_rotation CHECK(rotation IN ('6-2','5-2')),
  CONSTRAINT ck_coordination_post_status CHECK(status IN ('DRAFT','INACTIVE','ACTIVE')),
  CONSTRAINT ck_coordination_post_format_rotation CHECK(
    (format IN ('24/7','12/7') AND rotation='6-2') OR
    (format='12/5' AND rotation='5-2')
  )
);
CREATE INDEX IF NOT EXISTS ix_coordination_post_company ON coordination_post(instance_country_id,company_id,status,post_type);

CREATE TABLE IF NOT EXISTS supervision_route (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  series_id uuid NOT NULL,
  based_on_route_id uuid REFERENCES supervision_route(id),
  coordination_post_id uuid NOT NULL REFERENCES coordination_post(id) ON DELETE CASCADE,
  code varchar(32) NOT NULL,
  name varchar(160) NOT NULL,
  version_no integer NOT NULL,
  status varchar(16) NOT NULL,
  published_at timestamptz,
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_supervision_route_version UNIQUE(instance_country_id,series_id,version_no),
  CONSTRAINT ck_supervision_route_status CHECK(status IN ('DRAFT','ACTIVE','REPLACED'))
);
CREATE INDEX IF NOT EXISTS ix_supervision_route_post ON supervision_route(instance_country_id,coordination_post_id,status,version_no DESC);

CREATE TABLE IF NOT EXISTS supervision_route_point (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  route_id uuid NOT NULL REFERENCES supervision_route(id) ON DELETE CASCADE,
  point_id uuid NOT NULL REFERENCES point(id),
  sort_order integer NOT NULL,
  point_code_snapshot varchar(80) NOT NULL,
  point_name_snapshot varchar(180) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_supervision_route_point UNIQUE(instance_country_id,route_id,point_id),
  CONSTRAINT uq_supervision_route_order UNIQUE(instance_country_id,route_id,sort_order)
);
CREATE INDEX IF NOT EXISTS ix_supervision_route_point_route ON supervision_route_point(instance_country_id,route_id,sort_order);

-- UAT fixtures for Galvarino. They are intentionally small so CRUD/versioning can be validated.
