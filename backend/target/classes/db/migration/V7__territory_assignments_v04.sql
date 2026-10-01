-- SGI: Comando UAT v0.4
-- SGI-00T Territorio v0.2 + SGI-06 Asignaciones v0.4

ALTER TABLE assignment_plan
  ADD COLUMN IF NOT EXISTS draft_saved_at timestamptz,
  ADD COLUMN IF NOT EXISTS draft_saved_by_username varchar(80);

ALTER TABLE territory_zone ADD COLUMN IF NOT EXISTS responsible_employee_id uuid;
ALTER TABLE territory_region ADD COLUMN IF NOT EXISTS responsible_employee_id uuid;

CREATE TABLE IF NOT EXISTS country_subdivision (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  code varchar(16) NOT NULL,
  name varchar(120) NOT NULL,
  zone_id uuid REFERENCES territory_zone(id),
  region_id uuid REFERENCES territory_region(id),
  status varchar(24) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,code),
  UNIQUE(instance_country_id,name)
);
CREATE INDEX IF NOT EXISTS ix_country_subdivision_zone ON country_subdivision(instance_country_id,zone_id);
CREATE INDEX IF NOT EXISTS ix_country_subdivision_region ON country_subdivision(instance_country_id,region_id);

CREATE TABLE IF NOT EXISTS territory_audit_event (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  entity_type varchar(32) NOT NULL,
  entity_id uuid NOT NULL,
  event_type varchar(64) NOT NULL,
  actor_username varchar(80) NOT NULL,
  payload_json text NOT NULL,
  occurred_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_territory_audit_time ON territory_audit_event(instance_country_id,occurred_at DESC);

-- Provincias del Ecuador. La asignación UAT se puede editar desde Territorio.
INSERT INTO country_subdivision(id,instance_country_id,code,name,status,created_at,updated_at) VALUES
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','AZU','Azuay','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','BOL','Bolívar','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','CAN','Cañar','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','CAR','Carchi','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','CHI','Chimborazo','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','COT','Cotopaxi','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','EOR','El Oro','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','ESM','Esmeraldas','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','GAL','Galápagos','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','GUA','Guayas','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','IMB','Imbabura','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','LOJ','Loja','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','LRI','Los Ríos','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','MAN','Manabí','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','MOS','Morona Santiago','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','NAP','Napo','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','ORE','Orellana','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','PAS','Pastaza','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','PIC','Pichincha','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','SDE','Santo Domingo de los Tsáchilas','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','SEL','Santa Elena','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','SUC','Sucumbíos','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','TUN','Tungurahua','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','ZCH','Zamora Chinchipe','ACTIVE',now(),now())
ON CONFLICT (instance_country_id,code) DO NOTHING;

-- UAT territorial baseline based on the Ecuador operational reference map.
UPDATE country_subdivision SET zone_id='91000000-0000-0000-0000-000000000001' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND code IN ('CAR','ESM','IMB','PIC','SDE','COT','NAP','ORE','SUC','PAS');
UPDATE country_subdivision SET zone_id='91000000-0000-0000-0000-000000000002' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND zone_id IS NULL;

UPDATE country_subdivision SET region_id='92000000-0000-0000-0000-000000000001' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND code IN ('CAR','IMB','PIC');
UPDATE country_subdivision SET region_id='92000000-0000-0000-0000-000000000002' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND code IN ('ESM','SDE','COT');
UPDATE country_subdivision SET region_id='92000000-0000-0000-0000-000000000003' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND code IN ('NAP','ORE','SUC','PAS');
UPDATE country_subdivision SET region_id='92000000-0000-0000-0000-000000000004' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND code IN ('MAN','LRI','GUA','SEL','GAL');
UPDATE country_subdivision SET region_id='92000000-0000-0000-0000-000000000005' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND code IN ('BOL','CHI','TUN','CAN','AZU','MOS');
UPDATE country_subdivision SET region_id='92000000-0000-0000-0000-000000000006' WHERE instance_country_id='11111111-1111-1111-1111-111111111111' AND code IN ('EOR','LOJ','ZCH');

UPDATE territory_zone SET responsible_employee_id='82000000-0000-0000-0000-000000000011' WHERE id='91000000-0000-0000-0000-000000000002';
UPDATE territory_region SET responsible_employee_id='82000000-0000-0000-0000-000000000004' WHERE id='92000000-0000-0000-0000-000000000004';
