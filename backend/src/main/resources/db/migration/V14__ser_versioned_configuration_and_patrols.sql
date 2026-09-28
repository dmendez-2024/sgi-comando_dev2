-- SGI: Comando / SER v0.7.1 — Versionado inmutable de configuración + Patrullas.
-- Corrección UAT de SER v0.7: las tablas baseline patrol_definition/patrol_checkpoint
-- pertenecen al modelo histórico de ejecución y NO se reutilizan para Configuración.
-- Las nuevas entidades de Configuración usan nombres patrol_config_* para evitar colisiones.
-- Regla transversal: una versión publicada no se edita; editar crea un nuevo BORRADOR.

-- -----------------------------------------------------------------------------
-- Bitácora: convertir Protocolo en serie versionada sin perder los datos v0.6.1.
-- -----------------------------------------------------------------------------
ALTER TABLE logbook_protocol ADD COLUMN IF NOT EXISTS series_id uuid;
ALTER TABLE logbook_protocol ADD COLUMN IF NOT EXISTS based_on_protocol_id uuid REFERENCES logbook_protocol(id);
UPDATE logbook_protocol SET series_id=id WHERE series_id IS NULL;
ALTER TABLE logbook_protocol ALTER COLUMN series_id SET NOT NULL;
ALTER TABLE logbook_protocol DROP CONSTRAINT IF EXISTS uq_logbook_protocol_code_tenant;
ALTER TABLE logbook_protocol DROP CONSTRAINT IF EXISTS logbook_protocol_status_check;
ALTER TABLE logbook_protocol ADD CONSTRAINT logbook_protocol_status_check CHECK (status IN ('BORRADOR','VIGENTE','SUSPENDIDO','NO_VIGENTE'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_logbook_protocol_series_version ON logbook_protocol(instance_country_id,series_id,version_no);
CREATE INDEX IF NOT EXISTS ix_logbook_protocol_series ON logbook_protocol(instance_country_id,series_id,version_no DESC);

-- -----------------------------------------------------------------------------
-- Patrullas de CONFIGURACIÓN: Protocolo -> Patrulla -> Hito -> Reglas.
-- Se separan deliberadamente de patrol_definition/patrol_checkpoint del baseline.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS patrol_protocol (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  series_id uuid NOT NULL,
  based_on_protocol_id uuid REFERENCES patrol_protocol(id),
  post_id uuid NOT NULL REFERENCES post(id),
  code varchar(32) NOT NULL,
  name varchar(180) NOT NULL,
  description varchar(1000) NOT NULL DEFAULT '',
  status varchar(24) NOT NULL CHECK (status IN ('BORRADOR','VIGENTE','NO_VIGENTE')),
  version_no integer NOT NULL CHECK (version_no >= 1),
  last_published_at timestamptz,
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_patrol_protocol_series_version UNIQUE(instance_country_id,series_id,version_no)
);
CREATE INDEX IF NOT EXISTS ix_patrol_protocol_post ON patrol_protocol(instance_country_id,post_id,code);
CREATE INDEX IF NOT EXISTS ix_patrol_protocol_series ON patrol_protocol(instance_country_id,series_id,version_no DESC);

CREATE TABLE IF NOT EXISTS patrol_config_definition (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  protocol_id uuid NOT NULL REFERENCES patrol_protocol(id) ON DELETE CASCADE,
  code varchar(32) NOT NULL,
  name varchar(180) NOT NULL,
  description varchar(1000) NOT NULL DEFAULT '',
  structure_type varchar(16) NOT NULL CHECK (structure_type IN ('CLOSED','OPEN')),
  schedule_type varchar(20) NOT NULL CHECK (schedule_type IN ('PROGRAMMED','UNPROGRAMMED')),
  sequence_type varchar(16) CHECK (sequence_type IN ('STRICT','FLEXIBLE')),
  window_start time,
  window_end time,
  repetitions integer NOT NULL DEFAULT 1 CHECK (repetitions >= 1),
  version_no integer NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_patrol_config_definition_code_protocol UNIQUE(instance_country_id,protocol_id,code)
);
CREATE INDEX IF NOT EXISTS ix_patrol_config_definition_protocol ON patrol_config_definition(instance_country_id,protocol_id,code);

CREATE TABLE IF NOT EXISTS patrol_config_checkpoint (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  patrol_id uuid NOT NULL REFERENCES patrol_config_definition(id) ON DELETE CASCADE,
  sort_order integer NOT NULL CHECK (sort_order BETWEEN 1 AND 25),
  code varchar(32) NOT NULL,
  name varchar(180) NOT NULL,
  description varchar(1000) NOT NULL DEFAULT '',
  origin_mode varchar(16) NOT NULL CHECK (origin_mode IN ('ATS','FIELD','MIXED')),
  ats_package_id uuid REFERENCES ats_point_package(id),
  ats_x double precision,
  ats_y double precision,
  latitude double precision,
  longitude double precision,
  gps_accuracy_m double precision,
  location_captured_at timestamptz,
  location_captured_by varchar(80),
  control_type varchar(40) NOT NULL DEFAULT 'INSPECCION_VISUAL',
  requires_evidence boolean NOT NULL DEFAULT true,
  standard_image_original_name varchar(255),
  standard_image_content_type varchar(100),
  standard_image_data bytea,
  standard_image_version integer NOT NULL DEFAULT 0 CHECK (standard_image_version >= 0),
  standard_image_notes varchar(1000) NOT NULL DEFAULT '',
  visint_enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_patrol_config_checkpoint_code UNIQUE(instance_country_id,patrol_id,code)
);
CREATE INDEX IF NOT EXISTS ix_patrol_config_checkpoint_patrol ON patrol_config_checkpoint(instance_country_id,patrol_id,sort_order);

CREATE TABLE IF NOT EXISTS patrol_config_checkpoint_rule (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  checkpoint_id uuid NOT NULL REFERENCES patrol_config_checkpoint(id) ON DELETE CASCADE,
  sort_order integer NOT NULL,
  rule_type varchar(40) NOT NULL CHECK (rule_type IN ('INSPECCION_VISUAL','FOTOGRAFIA','CONFIRMACION','LECTURA')),
  required boolean NOT NULL DEFAULT true,
  evidence_required boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_patrol_config_checkpoint_rule UNIQUE(instance_country_id,checkpoint_id,rule_type)
);
CREATE INDEX IF NOT EXISTS ix_patrol_config_checkpoint_rule_checkpoint ON patrol_config_checkpoint_rule(instance_country_id,checkpoint_id,sort_order);

-- Semilla UAT versionada para GGTT01. IDs determinísticos para UAT.
INSERT INTO patrol_protocol(id,instance_country_id,series_id,based_on_protocol_id,post_id,code,name,description,status,version_no,last_published_at,updated_by_username,created_at,updated_at)
VALUES
('83000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','83000000-0000-0000-0000-000000000001',NULL,'50000000-0000-0000-0000-000000000001','PRO-PAT-0001','Patrullaje estándar','Configuración estándar de patrullajes del Puesto.','VIGENTE',1,now(),'coord',now(),now()),
('83000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','83000000-0000-0000-0000-000000000002',NULL,'50000000-0000-0000-0000-000000000001','PRO-PAT-0002','Contingencia nocturna','Configuración preparada para contingencias nocturnas.','BORRADOR',1,NULL,'coord',now(),now())
ON CONFLICT(id) DO NOTHING;

INSERT INTO patrol_config_definition(id,instance_country_id,protocol_id,code,name,description,structure_type,schedule_type,sequence_type,window_start,window_end,repetitions,version_no,updated_by_username,created_at,updated_at)
VALUES
('84000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','83000000-0000-0000-0000-000000000001','PAT-001','Perímetro general','Recorrido perimetral y puntos críticos exteriores.','CLOSED','PROGRAMMED','STRICT','22:00','22:45',1,1,'coord',now(),now()),
('84000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','83000000-0000-0000-0000-000000000001','PAT-002','Revisión estacionamientos','Ruta fija ejecutable según necesidad operativa.','CLOSED','UNPROGRAMMED','FLEXIBLE',NULL,NULL,1,1,'coord',now(),now()),
('84000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','83000000-0000-0000-0000-000000000001','PAT-003','Recorrido preventivo','Recorrido abierto durante una ventana programada.','OPEN','PROGRAMMED',NULL,'02:00','03:00',1,1,'coord',now(),now()),
('84000000-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','83000000-0000-0000-0000-000000000002','PAT-004','Patrullaje extraordinario','Recorrido abierto, no programado y disponible según necesidad.','OPEN','UNPROGRAMMED',NULL,NULL,NULL,1,1,'coord',now(),now())
ON CONFLICT(id) DO NOTHING;

INSERT INTO patrol_config_checkpoint(id,instance_country_id,patrol_id,sort_order,code,name,description,origin_mode,ats_x,ats_y,latitude,longitude,gps_accuracy_m,location_captured_at,location_captured_by,control_type,requires_evidence,standard_image_version,standard_image_notes,visint_enabled,created_at,updated_at)
VALUES
('85000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','84000000-0000-0000-0000-000000000001',1,'H01','Acceso Principal','Punto de control del acceso principal.','ATS',0.18,0.46,NULL,NULL,NULL,NULL,NULL,'INSPECCION_VISUAL',true,0,'Vista frontal completa del acceso.',false,now(),now()),
('85000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','84000000-0000-0000-0000-000000000001',2,'H02','Generador Norte','Verificar condición exterior del generador.','FIELD',NULL,NULL,-2.170000,-79.920000,8.0,now(),'coord','INSPECCION_VISUAL',true,0,'Equipo completo visible y sin obstrucciones.',false,now(),now()),
('85000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','84000000-0000-0000-0000-000000000001',3,'H03','Perímetro Este','Verificar cerramiento y condiciones del perímetro.','ATS',0.70,0.57,NULL,NULL,NULL,NULL,NULL,'INSPECCION_VISUAL',true,0,'Cerramiento completo visible.',false,now(),now())
ON CONFLICT(id) DO NOTHING;

INSERT INTO patrol_config_checkpoint_rule(id,instance_country_id,checkpoint_id,sort_order,rule_type,required,evidence_required,created_at,updated_at)
SELECT gen_random_uuid(),'11111111-1111-1111-1111-111111111111',c.id,r.ord,r.kind,true,r.evidence,now(),now()
FROM patrol_config_checkpoint c
CROSS JOIN (VALUES (1,'INSPECCION_VISUAL',false),(2,'FOTOGRAFIA',true),(3,'CONFIRMACION',false)) AS r(ord,kind,evidence)
WHERE c.id IN ('85000000-0000-0000-0000-000000000001','85000000-0000-0000-0000-000000000002','85000000-0000-0000-0000-000000000003')
ON CONFLICT(instance_country_id,checkpoint_id,rule_type) DO NOTHING;
