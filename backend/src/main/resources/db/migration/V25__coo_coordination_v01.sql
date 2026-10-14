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
INSERT INTO coordination_post(id,instance_country_id,company_id,code,name,post_type,format,rotation,shift_start_time,day_mask,status,updated_by_username,created_at,updated_at)
SELECT 'c1000000-0000-0000-0000-000000000001'::uuid,'11111111-1111-1111-1111-111111111111'::uuid,c.id,'MON-001','Monitoreo Principal','MONITORING','24/7','6-2','06:00',127,'ACTIVE','MIGRATION_V25',now(),now()
FROM company c WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND c.code='COM-001'
AND NOT EXISTS (SELECT 1 FROM coordination_post p WHERE p.instance_country_id=c.instance_country_id AND p.company_id=c.id AND p.code='MON-001');

INSERT INTO coordination_post(id,instance_country_id,company_id,code,name,post_type,format,rotation,shift_start_time,day_mask,status,updated_by_username,created_at,updated_at)
SELECT 'c1000000-0000-0000-0000-000000000002'::uuid,'11111111-1111-1111-1111-111111111111'::uuid,c.id,'SUP-001','Supervisión Norte','SUPERVISION','24/7','6-2','06:00',127,'ACTIVE','MIGRATION_V25',now(),now()
FROM company c WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND c.code='COM-001'
AND NOT EXISTS (SELECT 1 FROM coordination_post p WHERE p.instance_country_id=c.instance_country_id AND p.company_id=c.id AND p.code='SUP-001');

INSERT INTO coordination_post(id,instance_country_id,company_id,code,name,post_type,format,rotation,shift_start_time,day_mask,status,updated_by_username,created_at,updated_at)
SELECT 'c1000000-0000-0000-0000-000000000003'::uuid,'11111111-1111-1111-1111-111111111111'::uuid,c.id,'SUP-002','Supervisión Centro','SUPERVISION','12/5','5-2','07:00',31,'DRAFT','MIGRATION_V25',now(),now()
FROM company c WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND c.code='COM-001'
AND NOT EXISTS (SELECT 1 FROM coordination_post p WHERE p.instance_country_id=c.instance_country_id AND p.company_id=c.id AND p.code='SUP-002');

INSERT INTO supervision_route(id,instance_country_id,series_id,based_on_route_id,coordination_post_id,code,name,version_no,status,published_at,updated_by_username,created_at,updated_at)
SELECT 'c2000000-0000-0000-0000-000000000001'::uuid,'11111111-1111-1111-1111-111111111111'::uuid,'c2000000-0000-0000-0000-000000000001'::uuid,NULL,p.id,'RUT-SUP-001','Ruta Norte',1,'ACTIVE',now(),'MIGRATION_V25',now(),now()
FROM coordination_post p WHERE p.id='c1000000-0000-0000-0000-000000000002'::uuid
AND NOT EXISTS (SELECT 1 FROM supervision_route r WHERE r.instance_country_id=p.instance_country_id AND r.coordination_post_id=p.id);

INSERT INTO supervision_route_point(id,instance_country_id,route_id,point_id,sort_order,point_code_snapshot,point_name_snapshot,created_at,updated_at)
SELECT 'c3000000-0000-0000-0000-000000000001'::uuid,'11111111-1111-1111-1111-111111111111'::uuid,r.id,p.id,1,p.code,p.name,now(),now()
FROM supervision_route r JOIN point p ON p.id='40000000-0000-0000-0000-000000000001'::uuid
WHERE r.id='c2000000-0000-0000-0000-000000000001'::uuid
AND NOT EXISTS (SELECT 1 FROM supervision_route_point rp WHERE rp.instance_country_id=r.instance_country_id AND rp.route_id=r.id AND rp.point_id=p.id);
