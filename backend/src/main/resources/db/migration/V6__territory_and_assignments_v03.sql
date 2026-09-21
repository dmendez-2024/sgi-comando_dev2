-- SGI: Comando UAT v0.3
-- SGI-00T Territorio + SGI-06 Asignaciones v0.3

CREATE TABLE territory_zone (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  code varchar(32) NOT NULL,
  name varchar(160) NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,code),
  UNIQUE(instance_country_id,name)
);
CREATE INDEX ix_territory_zone_status ON territory_zone(instance_country_id,status);

CREATE TABLE territory_region (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  zone_id uuid NOT NULL REFERENCES territory_zone(id),
  code varchar(32) NOT NULL,
  name varchar(160) NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,code),
  UNIQUE(instance_country_id,name)
);
CREATE INDEX ix_territory_region_zone ON territory_region(instance_country_id,zone_id,status);

ALTER TABLE company ADD COLUMN region_id uuid REFERENCES territory_region(id);
CREATE INDEX ix_company_region ON company(instance_country_id,region_id,status);

CREATE TABLE user_operational_scope (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  username varchar(80) NOT NULL,
  scope_type varchar(24) NOT NULL,
  scope_id uuid,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX ix_user_operational_scope_user ON user_operational_scope(instance_country_id,username,scope_type);

-- Ecuador UAT: two zones and six regions from the operational reference map.
INSERT INTO territory_zone(id,instance_country_id,code,name,status,created_at,updated_at) VALUES
('91000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','ZN','Zona Norte','ACTIVE',now(),now()),
('91000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','ZS','Zona Sur','ACTIVE',now(),now());

INSERT INTO territory_region(id,instance_country_id,zone_id,code,name,status,created_at,updated_at) VALUES
('92000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','91000000-0000-0000-0000-000000000001','R-N1','Región Norte 1','ACTIVE',now(),now()),
('92000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','91000000-0000-0000-0000-000000000001','R-N2','Región Norte 2','ACTIVE',now(),now()),
('92000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','91000000-0000-0000-0000-000000000001','R-N3','Región Norte 3','ACTIVE',now(),now()),
('92000000-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','91000000-0000-0000-0000-000000000002','R-S1','Región Sur 1','ACTIVE',now(),now()),
('92000000-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','91000000-0000-0000-0000-000000000002','R-S2','Región Sur 2','ACTIVE',now(),now()),
('92000000-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','91000000-0000-0000-0000-000000000002','R-S3','Región Sur 3','ACTIVE',now(),now());

-- Galvarino belongs to R-S1, and therefore to Zona Sur.
UPDATE company SET region_id='92000000-0000-0000-0000-000000000004'
WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND region_id IS NULL;
ALTER TABLE company ALTER COLUMN region_id SET NOT NULL;

-- UAT hierarchy users. Password for all: CajamarcaUAT!2026
INSERT INTO app_user(id,username,password_hash,roles,display_name,instance_country_id,active) VALUES
('00000000-0000-0000-0000-000000000009','presidente','$2a$10$56cltNbyR/gzUjN.SM0SQ.CgvYX1/yg67XjMMoe6qi9M9cjxZieFO','PRESIDENTE','Presidente','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000010','dlatam','$2a$10$56cltNbyR/gzUjN.SM0SQ.CgvYX1/yg67XjMMoe6qi9M9cjxZieFO','DIRECTOR_OPERACIONES_LATAM','Director de Operaciones LATAM','11111111-1111-1111-1111-111111111111',true),
('00000000-0000-0000-0000-000000000011','jregional','$2a$10$56cltNbyR/gzUjN.SM0SQ.CgvYX1/yg67XjMMoe6qi9M9cjxZieFO','JEFE_REGIONAL','Jefe Regional de Operaciones R-S1','11111111-1111-1111-1111-111111111111',true);

INSERT INTO user_operational_scope(id,instance_country_id,username,scope_type,scope_id,created_at,updated_at) VALUES
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','presidente','COUNTRY',NULL,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','dlatam','COUNTRY',NULL,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','don','COUNTRY',NULL,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','dnacional','COUNTRY',NULL,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','dzonal','ZONE','91000000-0000-0000-0000-000000000002',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','jregional','REGION','92000000-0000-0000-0000-000000000004',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','coord','COMPANY','20000000-0000-0000-0000-000000000001',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','asistente','COMPANY','20000000-0000-0000-0000-000000000001',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','supervisor','COMPANY','20000000-0000-0000-0000-000000000001',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','agente','COMPANY','20000000-0000-0000-0000-000000000001',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','cliente','COMPANY','20000000-0000-0000-0000-000000000001',now(),now());

-- UAT case: same Puesto with three 8-hour commercial shifts from SIC: COM.
UPDATE post SET format='24/7',fhe=3.00 WHERE id='50000000-0000-0000-0000-000000000003';
UPDATE post_shift_template
SET shift_code='T1',shift_name='Turno 1',start_time='06:00',end_time='14:00',commercial_version='SIC-COM-UAT-2026.09-v03',updated_at=now()
WHERE id='83000000-0000-0000-0000-000000000005';

-- Re-shape already materialized unassigned occurrences of the old 07:00-19:00 template.
UPDATE shift_occurrence s
SET shift_code='T1',shift_name='Turno 1',starts_at=(s.local_date::timestamp + time '06:00') AT TIME ZONE 'America/Guayaquil',ends_at=(s.local_date::timestamp + time '14:00') AT TIME ZONE 'America/Guayaquil',commercial_version='SIC-COM-UAT-2026.09-v03',updated_at=now()
WHERE s.template_id='83000000-0000-0000-0000-000000000005'
AND NOT EXISTS (SELECT 1 FROM operational_assignment a WHERE a.shift_occurrence_id=s.id AND a.status<>'REMOVED');

INSERT INTO post_shift_template(id,instance_country_id,post_id,shift_code,shift_name,start_time,end_time,day_mask,active,commercial_version,created_at,updated_at) VALUES
('83000000-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000003','T2','Turno 2','14:00','22:00',127,true,'SIC-COM-UAT-2026.09-v03',now(),now()),
('83000000-0000-0000-0000-000000000007','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000003','T3','Turno 3','22:00','06:00',127,true,'SIC-COM-UAT-2026.09-v03',now(),now());
