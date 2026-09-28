-- SGI-06 Asignaciones UAT v0.2
-- Operational snapshots are LOCAL adapters for SIC: RRHH / SMC / SIC: COM.

CREATE TABLE employee_operational_snapshot (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  company_id uuid NOT NULL REFERENCES company(id),
  full_name varchar(180) NOT NULL,
  role_code varchar(80) NOT NULL,
  employment_status varchar(32) NOT NULL,
  id_score numeric(5,2),
  preferred_shift varchar(40),
  required_change boolean NOT NULL DEFAULT false,
  photo_key varchar(500),
  updated_from_source_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, employee_id)
);
CREATE INDEX ix_employee_snapshot_company_active ON employee_operational_snapshot(instance_country_id,company_id,employment_status,role_code);
CREATE INDEX ix_employee_snapshot_name ON employee_operational_snapshot(instance_country_id,company_id,lower(full_name));

CREATE TABLE employee_skill_snapshot (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  skill_access numeric(3,1) NOT NULL,
  skill_patrol numeric(3,1) NOT NULL,
  skill_observation numeric(3,1) NOT NULL,
  skill_tactical numeric(3,1) NOT NULL,
  skill_customer numeric(3,1) NOT NULL,
  skill_communication numeric(3,1) NOT NULL,
  skill_discipline numeric(3,1) NOT NULL,
  skill_response numeric(3,1) NOT NULL,
  source_version varchar(80),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, employee_id)
);

CREATE TABLE employee_unavailability_snapshot (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  type varchar(32) NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  source_ref varchar(120),
  source_status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX ix_unavailability_employee_time ON employee_unavailability_snapshot(instance_country_id,employee_id,starts_at,ends_at);

CREATE TABLE post_skill_requirement (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL REFERENCES post(id),
  required_role_code varchar(80) NOT NULL DEFAULT 'AGENTE_SEGURIDAD',
  skill_access numeric(3,1) NOT NULL,
  skill_patrol numeric(3,1) NOT NULL,
  skill_observation numeric(3,1) NOT NULL,
  skill_tactical numeric(3,1) NOT NULL,
  skill_customer numeric(3,1) NOT NULL,
  skill_communication numeric(3,1) NOT NULL,
  skill_discipline numeric(3,1) NOT NULL,
  skill_response numeric(3,1) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,post_id)
);

CREATE TABLE post_shift_template (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL REFERENCES post(id),
  shift_code varchar(40) NOT NULL,
  shift_name varchar(100) NOT NULL,
  start_time time NOT NULL,
  end_time time NOT NULL,
  day_mask integer NOT NULL DEFAULT 127,
  active boolean NOT NULL DEFAULT true,
  commercial_version varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,post_id,shift_code)
);
CREATE INDEX ix_post_shift_template_active ON post_shift_template(instance_country_id,post_id,active);

ALTER TABLE assignment_plan
  ADD COLUMN IF NOT EXISTS published_by_username varchar(80),
  ADD COLUMN IF NOT EXISTS published_snapshot_json text,
  ADD COLUMN IF NOT EXISTS closed_at timestamptz;

ALTER TABLE shift_occurrence
  ADD COLUMN IF NOT EXISTS template_id uuid REFERENCES post_shift_template(id),
  ADD COLUMN IF NOT EXISTS local_date date,
  ADD COLUMN IF NOT EXISTS shift_code varchar(40),
  ADD COLUMN IF NOT EXISTS shift_name varchar(100);
CREATE UNIQUE INDEX IF NOT EXISTS ux_shift_occurrence_template_date
  ON shift_occurrence(instance_country_id,template_id,local_date)
  WHERE template_id IS NOT NULL;

ALTER TABLE operational_assignment
  ADD COLUMN IF NOT EXISTS assigned_by_username varchar(80),
  ADD COLUMN IF NOT EXISTS assigned_at timestamptz,
  ADD COLUMN IF NOT EXISTS actual_assigned_by_username varchar(80),
  ADD COLUMN IF NOT EXISTS actual_assigned_at timestamptz,
  ADD COLUMN IF NOT EXISTS warning_json text;
CREATE UNIQUE INDEX IF NOT EXISTS ux_assignment_one_active_per_shift
  ON operational_assignment(instance_country_id,shift_occurrence_id)
  WHERE status <> 'REMOVED';
CREATE INDEX IF NOT EXISTS ix_assignment_actual_employee
  ON operational_assignment(instance_country_id,actual_employee_id,status)
  WHERE actual_employee_id IS NOT NULL;

CREATE TABLE assignment_event (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  assignment_plan_id uuid NOT NULL REFERENCES assignment_plan(id),
  assignment_id uuid REFERENCES operational_assignment(id),
  shift_occurrence_id uuid REFERENCES shift_occurrence(id),
  event_type varchar(80) NOT NULL,
  original_employee_id uuid,
  new_employee_id uuid,
  actor_username varchar(80) NOT NULL,
  reason text,
  payload_json text NOT NULL,
  occurred_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX ix_assignment_event_plan_time ON assignment_event(instance_country_id,assignment_plan_id,occurred_at DESC);
CREATE INDEX ix_assignment_event_assignment_time ON assignment_event(instance_country_id,assignment_id,occurred_at DESC);

-- UAT RRHH LOCAL personnel for Galvarino.
INSERT INTO employee_operational_snapshot(id,instance_country_id,employee_id,company_id,full_name,role_code,employment_status,id_score,preferred_shift,required_change,photo_key,updated_from_source_at,created_at,updated_at) VALUES
('81000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','Juan Pérez','AGENTE_SEGURIDAD','ACTIVE',9.10,'DIURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001','María Quispe','AGENTE_SEGURIDAD','ACTIVE',8.30,'NOCTURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000001','Carlos Rojas','AGENTE_SEGURIDAD','ACTIVE',8.80,'DIURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001','Edison Morales','SUPERVISOR_SEGURIDAD','ACTIVE',8.60,'NOCTURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000001','Luis Cóndor','ESCOLTA_SEGURIDAD','ACTIVE',9.00,'DIURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000001','Ana Lucía Vega','AGENTE_SEGURIDAD','ACTIVE',7.20,'DIURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000007','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000001','Pedro Gómez','AGENTE_SEGURIDAD','ACTIVE',7.80,'NOCTURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000008','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000001','Sofía Andrade','AGENTE_SEGURIDAD','ACTIVE',9.60,'DIURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000009','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000001','Diego Zambrano','AGENTE_SEGURIDAD','ACTIVE',8.40,'NOCTURNO',true,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000010','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000010','20000000-0000-0000-0000-000000000001','Valentina León','AGENTE_SEGURIDAD','ACTIVE',7.60,'DIURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000011','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000011','20000000-0000-0000-0000-000000000001','Miguel Escobar','SUPERVISOR_SEGURIDAD','ACTIVE',9.20,'DIURNO',false,NULL,now(),now(),now()),
('81000000-0000-0000-0000-000000000012','11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000012','20000000-0000-0000-0000-000000000001','Daniela Torres','ESCOLTA_SEGURIDAD','ACTIVE',8.10,'NOCTURNO',false,NULL,now(),now(),now());

INSERT INTO company_membership(id,instance_country_id,company_id,employee_id,membership_type,role_code,starts_at,ends_at,required_change,created_at,updated_at)
SELECT gen_random_uuid(),'11111111-1111-1111-1111-111111111111','20000000-0000-0000-0000-000000000001',employee_id,'PRIMARY',role_code,'2026-01-01T00:00:00Z',NULL,required_change,now(),now()
FROM employee_operational_snapshot;

INSERT INTO employee_skill_snapshot(id,instance_country_id,employee_id,skill_access,skill_patrol,skill_observation,skill_tactical,skill_customer,skill_communication,skill_discipline,skill_response,source_version,created_at,updated_at) VALUES
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000001',4.8,4.2,4.7,4.3,4.4,4.6,4.9,4.5,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000002',4.3,4.4,4.5,3.8,4.5,4.2,4.6,4.1,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000003',4.5,4.6,4.3,4.0,4.1,4.4,4.7,4.6,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000004',4.7,4.1,4.8,4.5,4.2,4.8,4.8,4.5,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000005',4.1,4.8,4.6,4.9,3.9,4.2,4.7,4.8,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000006',3.8,3.7,4.0,3.5,4.3,4.1,4.4,3.9,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000007',4.0,4.3,3.9,4.1,3.8,4.0,4.5,4.2,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000008',4.9,4.8,4.9,4.6,4.7,4.8,5.0,4.9,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000009',4.2,4.0,4.1,3.9,4.0,4.1,4.2,4.0,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000010',4.0,3.8,4.2,3.6,4.4,4.3,4.5,4.0,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000011',4.6,4.4,4.7,4.5,4.3,4.7,4.8,4.6,'RRHH-LOCAL-v1',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000012',4.0,4.7,4.4,4.8,4.0,4.1,4.6,4.7,'RRHH-LOCAL-v1',now(),now());

-- SIC: RRHH owns these periods; SGI consumes them as read-only operational snapshots.
INSERT INTO employee_unavailability_snapshot(id,instance_country_id,employee_id,type,starts_at,ends_at,source_ref,source_status,created_at,updated_at) VALUES
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000004','MEDICAL_LEAVE','2026-09-07T05:00:00Z','2026-09-10T05:00:00Z','RRHH-PM-001','ACTIVE',now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','82000000-0000-0000-0000-000000000006','VACATION','2026-09-09T05:00:00Z','2026-09-14T05:00:00Z','RRHH-VAC-001','ACTIVE',now(),now());

-- Requirements by post. Role mismatch is WARNING only, never an assignment blocker.
INSERT INTO post_skill_requirement(id,instance_country_id,post_id,required_role_code,skill_access,skill_patrol,skill_observation,skill_tactical,skill_customer,skill_communication,skill_discipline,skill_response,created_at,updated_at) VALUES
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000001','AGENTE_SEGURIDAD',4.5,3.5,4.5,3.5,4.0,4.0,4.5,4.0,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000002','AGENTE_SEGURIDAD',3.0,4.5,4.5,4.2,3.5,4.0,4.5,4.5,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000003','AGENTE_SEGURIDAD',4.0,3.0,4.0,3.0,4.2,4.0,4.3,3.8,now(),now());

-- SIC: COM LOCAL turn definitions. SGI materializes concrete ShiftOccurrence rows per requested week.
INSERT INTO post_shift_template(id,instance_country_id,post_id,shift_code,shift_name,start_time,end_time,day_mask,active,commercial_version,created_at,updated_at) VALUES
('83000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000001','D12','Diurno','06:00','18:00',127,true,'SIC-COM-UAT-2026.09',now(),now()),
('83000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000001','N12','Nocturno','18:00','06:00',127,true,'SIC-COM-UAT-2026.09',now(),now()),
('83000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000002','D12','Diurno','06:00','18:00',127,true,'SIC-COM-UAT-2026.09',now(),now()),
('83000000-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000002','N12','Nocturno','18:00','06:00',127,true,'SIC-COM-UAT-2026.09',now(),now()),
('83000000-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','50000000-0000-0000-0000-000000000003','D12','Diurno','07:00','19:00',127,true,'SIC-COM-UAT-2026.09',now(),now());
