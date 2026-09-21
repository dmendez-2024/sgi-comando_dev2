-- SGI: Comando / SER v0.9
-- Servicios recibidos desde SIC: COM llegan sin Compañía operativa.
-- SGI los presenta en la bandeja lógica Kaibil hasta que Coordinación asigne
-- una Compañía operativa dentro de su ámbito territorial.

ALTER TABLE service ADD COLUMN IF NOT EXISTS source_system varchar(32) NOT NULL DEFAULT 'SIC_COM';
ALTER TABLE service ADD COLUMN IF NOT EXISTS source_version varchar(80);

ALTER TABLE point ADD COLUMN IF NOT EXISTS operational_assignment_status varchar(32);
ALTER TABLE point ADD COLUMN IF NOT EXISTS received_from_sic_com_at timestamptz;
ALTER TABLE point ADD COLUMN IF NOT EXISTS assigned_by_username varchar(80);
ALTER TABLE point ADD COLUMN IF NOT EXISTS assigned_at timestamptz;
ALTER TABLE point ADD CONSTRAINT ck_point_operational_assignment_status
  CHECK (operational_assignment_status IS NULL OR operational_assignment_status IN ('PENDING','ASSIGNED'));

UPDATE point
   SET operational_assignment_status = CASE WHEN company_id IS NULL THEN 'PENDING' ELSE 'ASSIGNED' END,
       received_from_sic_com_at = COALESCE(received_from_sic_com_at, created_at),
       assigned_at = CASE WHEN company_id IS NULL THEN NULL ELSE COALESCE(assigned_at, updated_at, created_at) END,
       assigned_by_username = CASE WHEN company_id IS NULL THEN NULL ELSE COALESCE(assigned_by_username, 'MIGRATION_V19') END
 WHERE operational_assignment_status IS NULL;

-- Guardrail: Kaibil is a coordination inbox, never the operating Company of a client Service.
UPDATE point p
   SET company_id = NULL,
       operational_assignment_status = 'PENDING',
       assigned_by_username = NULL,
       assigned_at = NULL,
       received_from_sic_com_at = COALESCE(received_from_sic_com_at, p.created_at)
 WHERE p.company_id IN (
   SELECT c.id FROM company c
   WHERE c.instance_country_id = p.instance_country_id
     AND (c.always_active = true OR upper(c.company_type) = 'COORDINATION')
 );

ALTER TABLE point ALTER COLUMN operational_assignment_status SET DEFAULT 'PENDING';
ALTER TABLE point ALTER COLUMN operational_assignment_status SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_point_operational_assignment
  ON point(instance_country_id, operational_assignment_status, company_id, status);

CREATE TABLE IF NOT EXISTS service_company_assignment_event (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  service_id uuid NOT NULL REFERENCES service(id),
  point_id uuid NOT NULL REFERENCES point(id),
  origin_company_id uuid REFERENCES company(id),
  destination_company_id uuid NOT NULL REFERENCES company(id),
  action varchar(32) NOT NULL,
  observations varchar(1000),
  actor_username varchar(80) NOT NULL,
  occurred_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_service_company_assignment_event_point
  ON service_company_assignment_event(instance_country_id, point_id, occurred_at DESC);

-- UAT fixture: one pending SIC: COM service so the initial-assignment flow can be validated.
INSERT INTO service(id,instance_country_id,code,name,client_name,commercial_status,config_status,source_system,source_version,created_at,updated_at)
SELECT '92000000-0000-0000-0000-000000000001'::uuid,
       '11111111-1111-1111-1111-111111111111'::uuid,
       'SVC-SICCOM-UAT-001','Seguridad Física Estática','Cliente SIC COM UAT','ACTIVE','TO_CONFIGURE','SIC_COM','UAT-V19',now(),now()
WHERE NOT EXISTS (
  SELECT 1 FROM service WHERE instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND code='SVC-SICCOM-UAT-001'
);

INSERT INTO point(id,instance_country_id,service_id,code,name,province,city,client_name,company_id,status,operational_assignment_status,received_from_sic_com_at,created_at,updated_at)
SELECT '92000000-0000-0000-0000-000000000002'::uuid,
       '11111111-1111-1111-1111-111111111111'::uuid,
       s.id,'PTO-SICCOM-UAT-001','Punto Nuevo SIC COM','Guayas','Guayaquil','Cliente SIC COM UAT',NULL,'ACTIVE','PENDING',now(),now(),now()
FROM service s
WHERE s.instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND s.code='SVC-SICCOM-UAT-001'
  AND NOT EXISTS (
    SELECT 1 FROM point WHERE instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND code='PTO-SICCOM-UAT-001'
  );

INSERT INTO post(id,instance_country_id,point_id,code,name,format,fhe,tier,config_status,created_at,updated_at)
SELECT '92000000-0000-0000-0000-000000000003'::uuid,
       '11111111-1111-1111-1111-111111111111'::uuid,
       p.id,'GGTT-SICCOM-UAT-01','Control de Acceso Principal','24/7',3,'II','TO_CONFIGURE',now(),now()
FROM point p
WHERE p.instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND p.code='PTO-SICCOM-UAT-001'
  AND NOT EXISTS (
    SELECT 1 FROM post WHERE instance_country_id='11111111-1111-1111-1111-111111111111'::uuid AND code='GGTT-SICCOM-UAT-01'
  );
