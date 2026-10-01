-- SGI: Comando / SER v0.8 — Configuración versionada de Consignas.
-- Estructura: Punto -> Protocolo de Consignas -> Consigna -> Alcance -> Reglas/Evidencias.
-- Regla: un solo Protocolo VIGENTE por Punto; varias series/versiones pueden quedar PUBLICADAS o en BORRADOR.

CREATE TABLE IF NOT EXISTS consignment_protocol (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  series_id uuid NOT NULL,
  based_on_protocol_id uuid REFERENCES consignment_protocol(id),
  point_id uuid NOT NULL REFERENCES point(id),
  code varchar(32) NOT NULL,
  name varchar(180) NOT NULL,
  description varchar(1000) NOT NULL DEFAULT '',
  status varchar(24) NOT NULL CHECK (status IN ('BORRADOR','PUBLICADO','VIGENTE','NO_VIGENTE')),
  version_no integer NOT NULL CHECK (version_no >= 1),
  published_at timestamptz,
  activated_at timestamptz,
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_consignment_protocol_series_version UNIQUE(instance_country_id,series_id,version_no)
);
CREATE INDEX IF NOT EXISTS ix_consignment_protocol_point ON consignment_protocol(instance_country_id,point_id,code,version_no DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_consignment_protocol_vigente ON consignment_protocol(instance_country_id,point_id) WHERE status='VIGENTE';

-- Crear un Protocolo legado por Punto con Consignas existentes.
WITH points_with_consignments AS (
  SELECT DISTINCT instance_country_id, point_id
  FROM consignment
), numbered AS (
  SELECT instance_country_id, point_id,
         row_number() over (partition by instance_country_id order by point_id::text) AS rn,
         gen_random_uuid() AS new_id
  FROM points_with_consignments
)
INSERT INTO consignment_protocol(
  id,instance_country_id,series_id,based_on_protocol_id,point_id,code,name,description,status,version_no,published_at,activated_at,updated_by_username,created_at,updated_at
)
SELECT new_id,instance_country_id,new_id,NULL,point_id,
       'PRO-CON-'||lpad(rn::text,4,'0'),
       'Operación cotidiana','Protocolo migrado desde la configuración base de Consignas.',
       'VIGENTE',1,now(),now(),'system',now(),now()
FROM numbered n
WHERE NOT EXISTS (
  SELECT 1 FROM consignment_protocol p
  WHERE p.instance_country_id=n.instance_country_id AND p.point_id=n.point_id
);

ALTER TABLE consignment ADD COLUMN IF NOT EXISTS protocol_id uuid REFERENCES consignment_protocol(id);
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS scope_type varchar(16) NOT NULL DEFAULT 'POINT';
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS validity_from timestamptz;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS validity_until timestamptz;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS application_days_json text NOT NULL DEFAULT '[]';
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS application_time_from time;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS application_time_to time;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS acknowledgment_required boolean NOT NULL DEFAULT false;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS confirmation_required boolean NOT NULL DEFAULT true;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS evidence_required boolean NOT NULL DEFAULT false;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS gps_required boolean NOT NULL DEFAULT false;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS observation_required boolean NOT NULL DEFAULT false;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS expected_location_mode varchar(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS ats_package_id uuid REFERENCES ats_point_package(id);
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS ats_x double precision;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS ats_y double precision;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS expected_latitude double precision;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS expected_longitude double precision;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS updated_by_username varchar(80) NOT NULL DEFAULT 'system';

UPDATE consignment c
SET protocol_id=p.id,
    scope_type=CASE WHEN c.post_id IS NULL THEN 'POINT' ELSE 'POSTS' END,
    status=CASE c.status WHEN 'ACTIVE' THEN 'VIGENTE' WHEN 'DRAFT' THEN 'BORRADOR' ELSE c.status END,
    priority=CASE upper(c.priority) WHEN 'HIGH' THEN 'HIGH' WHEN 'CRITICAL' THEN 'CRITICAL' WHEN 'LOW' THEN 'LOW' ELSE 'MEDIUM' END,
    validity_type=CASE upper(c.validity_type) WHEN 'TEMPORARY' THEN 'TEMPORARY' ELSE 'PERMANENT' END,
    application_type=CASE upper(c.application_type) WHEN 'CALENDAR' THEN 'CALENDAR' ELSE 'ALL_TIME' END
FROM consignment_protocol p
WHERE c.protocol_id IS NULL
  AND p.instance_country_id=c.instance_country_id
  AND p.point_id=c.point_id
  AND p.status='VIGENTE';

ALTER TABLE consignment ALTER COLUMN protocol_id SET NOT NULL;
-- El código de una Consigna debe poder repetirse entre versiones del Protocolo.
ALTER TABLE consignment DROP CONSTRAINT IF EXISTS consignment_instance_country_id_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_consignment_protocol_code ON consignment(instance_country_id,protocol_id,code);
CREATE INDEX IF NOT EXISTS ix_consignment_protocol ON consignment(instance_country_id,protocol_id,code);

CREATE TABLE IF NOT EXISTS consignment_post_scope (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  consignment_id uuid NOT NULL REFERENCES consignment(id) ON DELETE CASCADE,
  post_id uuid NOT NULL REFERENCES post(id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_consignment_post_scope UNIQUE(instance_country_id,consignment_id,post_id)
);
CREATE INDEX IF NOT EXISTS ix_consignment_post_scope_consignment ON consignment_post_scope(instance_country_id,consignment_id);

INSERT INTO consignment_post_scope(id,instance_country_id,consignment_id,post_id,created_at,updated_at)
SELECT gen_random_uuid(),instance_country_id,id,post_id,now(),now()
FROM consignment c
WHERE post_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM consignment_post_scope s WHERE s.instance_country_id=c.instance_country_id AND s.consignment_id=c.id AND s.post_id=c.post_id);

CREATE TABLE IF NOT EXISTS consignment_evidence (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  consignment_id uuid NOT NULL REFERENCES consignment(id) ON DELETE CASCADE,
  sort_order integer NOT NULL,
  name varchar(180) NOT NULL,
  description varchar(1000) NOT NULL DEFAULT '',
  evidence_type varchar(32) NOT NULL CHECK (evidence_type IN ('PHOTO','DOCUMENT','TEXT','CONFIRMATION')),
  required boolean NOT NULL DEFAULT true,
  standard_image_original_name varchar(255),
  standard_image_content_type varchar(100),
  standard_image_data bytea,
  standard_image_version integer NOT NULL DEFAULT 0,
  standard_image_notes varchar(1000) NOT NULL DEFAULT '',
  visint_enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_consignment_evidence_consignment ON consignment_evidence(instance_country_id,consignment_id,sort_order);

-- Semilla UAT adicional para hacer visible la vertical sin duplicar el concepto existente.
INSERT INTO consignment(id,instance_country_id,code,point_id,post_id,title,instruction,priority,status,validity_type,application_type,published_at,created_at,updated_at,
 protocol_id,scope_type,application_days_json,acknowledgment_required,confirmation_required,evidence_required,gps_required,observation_required,expected_location_mode,updated_by_username)
SELECT '60000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','CON-000002','40000000-0000-0000-0000-000000000001',NULL,
 'Verificar generador norte','Verificar el estado general del generador norte y registrar evidencia visual de la inspección.','HIGH','VIGENTE','PERMANENT','CALENDAR',now(),now(),now(),
 p.id,'POINT','["MON","TUE","WED","THU","FRI","SAT","SUN"]',false,true,true,true,true,'NONE','system'
FROM consignment_protocol p
WHERE p.instance_country_id='11111111-1111-1111-1111-111111111111' AND p.point_id='40000000-0000-0000-0000-000000000001' AND p.status='VIGENTE'
ON CONFLICT(id) DO NOTHING;

INSERT INTO consignment(id,instance_country_id,code,point_id,post_id,title,instruction,priority,status,validity_type,application_type,published_at,created_at,updated_at,
 protocol_id,scope_type,application_days_json,acknowledgment_required,confirmation_required,evidence_required,gps_required,observation_required,expected_location_mode,updated_by_username)
SELECT '60000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','CON-000003','40000000-0000-0000-0000-000000000001',NULL,
 'No permitir ingreso por puerta norte','Mantener cerrada la puerta norte y no permitir ingreso salvo autorización expresa.','CRITICAL','VIGENTE','PERMANENT','ALL_TIME',now(),now(),now(),
 p.id,'POSTS','[]',true,true,false,false,true,'NONE','system'
FROM consignment_protocol p
WHERE p.instance_country_id='11111111-1111-1111-1111-111111111111' AND p.point_id='40000000-0000-0000-0000-000000000001' AND p.status='VIGENTE'
ON CONFLICT(id) DO NOTHING;

INSERT INTO consignment_post_scope(id,instance_country_id,consignment_id,post_id,created_at,updated_at)
VALUES
('61000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','60000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000001',now(),now()),
('61000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','60000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000002',now(),now())
ON CONFLICT(instance_country_id,consignment_id,post_id) DO NOTHING;

INSERT INTO consignment_evidence(id,instance_country_id,consignment_id,sort_order,name,description,evidence_type,required,standard_image_version,standard_image_notes,visint_enabled,created_at,updated_at)
VALUES
('62000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','60000000-0000-0000-0000-000000000002',1,'Foto del generador','Vista general del generador al momento de la inspección.','PHOTO',true,0,'Equipo completo visible, tablero cerrado y área libre de obstrucciones.',false,now(),now())
ON CONFLICT(id) DO NOTHING;

-- Protocolo alternativo preparado como borrador.
INSERT INTO consignment_protocol(id,instance_country_id,series_id,based_on_protocol_id,point_id,code,name,description,status,version_no,updated_by_username,created_at,updated_at)
VALUES ('63000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','63000000-0000-0000-0000-000000000001',NULL,'40000000-0000-0000-0000-000000000001','PRO-CON-0002','Emergencia','Protocolo alternativo de Consignas para escenario de emergencia.','BORRADOR',1,'coord',now(),now())
ON CONFLICT(id) DO NOTHING;
