-- SGI: Comando — COM v1.1 / ASI v0.7
-- CORE is SoR for company identity. SGI activates companies from the CORE catalog.
-- SIC: RRHH remains SoR for employee -> company membership; SGI orchestrates transfer requests.

ALTER TABLE company ALTER COLUMN region_id DROP NOT NULL;
ALTER TABLE company ADD COLUMN IF NOT EXISTS core_catalog_id uuid;
ALTER TABLE company ADD COLUMN IF NOT EXISTS source_system varchar(32) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE company ADD COLUMN IF NOT EXISTS source_version varchar(80);
ALTER TABLE company ADD COLUMN IF NOT EXISTS company_type varchar(32) NOT NULL DEFAULT 'SECURITY';
ALTER TABLE company ADD COLUMN IF NOT EXISTS always_active boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS ux_company_core_catalog ON company(instance_country_id,core_catalog_id) WHERE core_catalog_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS core_company_catalog_snapshot (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  core_company_id uuid NOT NULL,
  code varchar(32) NOT NULL,
  name varchar(160) NOT NULL,
  historical_review varchar(750),
  logo_data_url text,
  company_type varchar(32) NOT NULL DEFAULT 'SECURITY',
  source_version varchar(80) NOT NULL,
  source_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  synced_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,core_company_id),
  UNIQUE(instance_country_id,code)
);

INSERT INTO core_company_catalog_snapshot(id,instance_country_id,core_company_id,code,name,historical_review,logo_data_url,company_type,source_version,source_status,synced_at,created_at,updated_at) VALUES
('a1000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','a2000000-0000-0000-0000-000000000001','COM-001','Galvarino','Compañía operativa de seguridad física.',NULL,'SECURITY','CORE-UAT-2026.09','ACTIVE',now(),now(),now()),
('a1000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','a2000000-0000-0000-0000-000000000002','COM-002','Atahualpa','Compañía operativa de seguridad integral.',NULL,'SECURITY','CORE-UAT-2026.09','ACTIVE',now(),now(),now()),
('a1000000-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','a2000000-0000-0000-0000-000000000003','COM-003','Lanceros','Compañía operativa del grupo.',NULL,'SECURITY','CORE-UAT-2026.09','ACTIVE',now(),now(),now()),
('a1000000-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','a2000000-0000-0000-0000-000000000004','COM-004','Cóndor','Compañía operativa del grupo.',NULL,'SECURITY','CORE-UAT-2026.09','ACTIVE',now(),now(),now()),
('a1000000-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','a2000000-0000-0000-0000-000000000005','KAI-001','Kaibil','Compañía de Operaciones. Aloja personal de coordinación y puestos operacionales no ligados a Servicios de clientes.',NULL,'COORDINATION','CORE-UAT-2026.09','ACTIVE',now(),now(),now()),
('a1000000-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','a2000000-0000-0000-0000-000000000006','COM-006','Andes','Compañía operativa del grupo.',NULL,'SECURITY','CORE-UAT-2026.09','ACTIVE',now(),now(),now())
ON CONFLICT(instance_country_id,core_company_id) DO UPDATE SET name=excluded.name,historical_review=excluded.historical_review,logo_data_url=excluded.logo_data_url,company_type=excluded.company_type,source_version=excluded.source_version,source_status=excluded.source_status,synced_at=excluded.synced_at,updated_at=now();

-- Preserve the UAT identity already visible for Galvarino while changing authority to CORE.
UPDATE core_company_catalog_snapshot core
SET logo_data_url=COALESCE(c.logo_data_url,core.logo_data_url),
    historical_review=COALESCE(c.historical_review,core.historical_review),
    updated_at=now()
FROM company c
WHERE core.instance_country_id=c.instance_country_id
  AND core.core_company_id='a2000000-0000-0000-0000-000000000001'
  AND c.instance_country_id='11111111-1111-1111-1111-111111111111' AND c.code='COM-001';

UPDATE company c SET core_catalog_id=core.core_company_id,source_system='CORE',source_version=core.source_version,company_type='SECURITY',always_active=false,
  name=core.name,historical_review=core.historical_review,logo_data_url=core.logo_data_url,version_number=GREATEST(c.version_number,1)+1
FROM core_company_catalog_snapshot core
WHERE c.instance_country_id=core.instance_country_id AND core.core_company_id='a2000000-0000-0000-0000-000000000001'
  AND c.instance_country_id='11111111-1111-1111-1111-111111111111' AND c.code='COM-001';

INSERT INTO company_version(id,instance_country_id,company_id,version_number,change_type,change_reason,actor_username,effective_at,snapshot_json,created_at,updated_at)
SELECT gen_random_uuid(),c.instance_country_id,c.id,c.version_number,'CORE_BOUND','Identidad de Compañía migrada a catálogo CORE','SYSTEM',now(),
 jsonb_build_object('code',c.code,'name',c.name,'status',c.status,'zoneId',c.zone_id,'regionIds',COALESCE((SELECT jsonb_agg(cr.region_id) FROM company_region cr WHERE cr.company_id=c.id),'[]'::jsonb),'historicalReview',c.historical_review,'logoDataUrl',c.logo_data_url,'sourceSystem',c.source_system,'companyType',c.company_type,'alwaysActive',c.always_active,'versionNumber',c.version_number)::text,
 now(),now()
FROM company c
WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111' AND c.code='COM-001'
ON CONFLICT(instance_country_id,company_id,version_number) DO NOTHING;

-- Additional active UAT companies are snapshots activated from CORE, not locally-created masters.
-- Reconciliation rule for upgraded UAT databases:
--   1) prefer an existing row already bound to the same CORE id;
--   2) otherwise prefer an existing row with the same canonical CORE name;
--   3) otherwise use an existing row with the same code;
--   4) only insert a new Company if none of the above exists.
-- This intentionally avoids destructive merges when a legacy UAT database contains one row
-- using the canonical code and another row using the canonical name.
DO $$
DECLARE
  r record;
  target_id uuid;
BEGIN
  FOR r IN
    SELECT * FROM (VALUES
      ('a2000000-0000-0000-0000-000000000002'::uuid,'COM-002'::varchar,'Atahualpa'::varchar,'92000000-0000-0000-0000-000000000004'::uuid,'91000000-0000-0000-0000-000000000002'::uuid,'Compañía operativa de seguridad integral.'::varchar,'SECURITY'::varchar,false),
      ('a2000000-0000-0000-0000-000000000003'::uuid,'COM-003'::varchar,'Lanceros'::varchar,'92000000-0000-0000-0000-000000000005'::uuid,'91000000-0000-0000-0000-000000000002'::uuid,'Compañía operativa del grupo.'::varchar,'SECURITY'::varchar,false),
      ('a2000000-0000-0000-0000-000000000004'::uuid,'COM-004'::varchar,'Cóndor'::varchar,'92000000-0000-0000-0000-000000000001'::uuid,'91000000-0000-0000-0000-000000000001'::uuid,'Compañía operativa del grupo.'::varchar,'SECURITY'::varchar,false),
      ('a2000000-0000-0000-0000-000000000005'::uuid,'KAI-001'::varchar,'Kaibil'::varchar,NULL::uuid,NULL::uuid,'Compañía de Operaciones. Aloja personal de coordinación y puestos operacionales no ligados a Servicios de clientes.'::varchar,'COORDINATION'::varchar,true),
      ('a2000000-0000-0000-0000-000000000006'::uuid,'COM-006'::varchar,'Andes'::varchar,'92000000-0000-0000-0000-000000000002'::uuid,'91000000-0000-0000-0000-000000000001'::uuid,'Compañía operativa del grupo.'::varchar,'SECURITY'::varchar,false)
    ) AS x(core_company_id,canonical_code,canonical_name,region_id,zone_id,historical_review,company_type,always_active)
  LOOP
    target_id := NULL;

    SELECT c.id INTO target_id
    FROM company c
    WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111'
      AND c.core_catalog_id=r.core_company_id
    LIMIT 1;

    IF target_id IS NULL THEN
      SELECT c.id INTO target_id
      FROM company c
      WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111'
        AND lower(trim(c.name))=lower(trim(r.canonical_name))
      ORDER BY c.updated_at DESC,c.id
      LIMIT 1;
    END IF;

    IF target_id IS NULL THEN
      SELECT c.id INTO target_id
      FROM company c
      WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111'
        AND c.code=r.canonical_code
      ORDER BY c.updated_at DESC,c.id
      LIMIT 1;
    END IF;

    IF target_id IS NULL THEN
      INSERT INTO company(
        id,instance_country_id,code,name,status,required_change_count,region_id,zone_id,
        historical_review,logo_data_url,version_number,core_catalog_id,source_system,
        source_version,company_type,always_active,created_at,updated_at
      ) VALUES (
        gen_random_uuid(),'11111111-1111-1111-1111-111111111111',r.canonical_code,r.canonical_name,'ACTIVE',0,
        r.region_id,r.zone_id,r.historical_review,NULL,1,r.core_company_id,'CORE','CORE-UAT-2026.09',
        r.company_type,r.always_active,now(),now()
      ) RETURNING id INTO target_id;
    ELSE
      UPDATE company c
      SET name=r.canonical_name,
          status=CASE WHEN r.always_active THEN 'ACTIVE' ELSE c.status END,
          region_id=r.region_id,
          zone_id=r.zone_id,
          historical_review=r.historical_review,
          version_number=GREATEST(c.version_number,1)+1,
          core_catalog_id=r.core_company_id,
          source_system='CORE',
          source_version='CORE-UAT-2026.09',
          company_type=r.company_type,
          always_active=r.always_active,
          updated_at=now()
      WHERE c.id=target_id;
    END IF;
  END LOOP;
END $$;

-- Regions point to the Company actually bound to each CORE company id, regardless of its legacy SGI code.
INSERT INTO company_region(id,instance_country_id,company_id,region_id,created_at,updated_at)
SELECT gen_random_uuid(),c.instance_country_id,c.id,m.region_id,now(),now()
FROM (VALUES
 ('a2000000-0000-0000-0000-000000000002'::uuid,'92000000-0000-0000-0000-000000000004'::uuid),
 ('a2000000-0000-0000-0000-000000000003'::uuid,'92000000-0000-0000-0000-000000000005'::uuid),
 ('a2000000-0000-0000-0000-000000000004'::uuid,'92000000-0000-0000-0000-000000000001'::uuid),
 ('a2000000-0000-0000-0000-000000000006'::uuid,'92000000-0000-0000-0000-000000000002'::uuid)
) AS m(core_company_id,region_id)
JOIN company c ON c.instance_country_id='11111111-1111-1111-1111-111111111111' AND c.core_catalog_id=m.core_company_id
ON CONFLICT(instance_country_id,company_id,region_id) DO NOTHING;

INSERT INTO company_version(id,instance_country_id,company_id,version_number,change_type,change_reason,actor_username,effective_at,snapshot_json,created_at,updated_at)
SELECT gen_random_uuid(),c.instance_country_id,c.id,c.version_number,'CORE_ACTIVATED','Activación/vinculación desde catálogo CORE','SYSTEM',now(),
 jsonb_build_object('code',c.code,'name',c.name,'status',c.status,'zoneId',c.zone_id,'regionIds',COALESCE((SELECT jsonb_agg(cr.region_id) FROM company_region cr WHERE cr.company_id=c.id),'[]'::jsonb),'historicalReview',c.historical_review,'logoDataUrl',c.logo_data_url,'sourceSystem',c.source_system,'companyType',c.company_type,'alwaysActive',c.always_active,'versionNumber',c.version_number)::text,
 now(),now()
FROM company c
WHERE c.instance_country_id='11111111-1111-1111-1111-111111111111'
  AND c.core_catalog_id IN (
    'a2000000-0000-0000-0000-000000000002',
    'a2000000-0000-0000-0000-000000000003',
    'a2000000-0000-0000-0000-000000000004',
    'a2000000-0000-0000-0000-000000000005',
    'a2000000-0000-0000-0000-000000000006'
  )
ON CONFLICT(instance_country_id,company_id,version_number) DO NOTHING;

CREATE TABLE IF NOT EXISTS transfer_reason_catalog (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, code varchar(40) NOT NULL, label varchar(160) NOT NULL, active boolean NOT NULL DEFAULT true,
 sort_order integer NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(instance_country_id,code)
);
INSERT INTO transfer_reason_catalog(id,instance_country_id,code,label,active,sort_order,created_at,updated_at) VALUES
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','OPERATIONAL_NEED','Necesidad operativa',true,10,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','RESTRUCTURING','Reestructuración',true,20,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','PROMOTION','Promoción / cambio de función',true,30,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','EMPLOYEE_REQUEST','Solicitud del colaborador',true,40,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','PERFORMANCE','Desempeño',true,50,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','DISCIPLINARY','Medida disciplinaria',true,60,now(),now()),
(gen_random_uuid(),'11111111-1111-1111-1111-111111111111','OTHER','Otro',true,99,now(),now())
ON CONFLICT(instance_country_id,code) DO NOTHING;

CREATE TABLE IF NOT EXISTS employee_company_transfer (
 id uuid PRIMARY KEY,
 instance_country_id uuid NOT NULL,
 employee_id uuid NOT NULL,
 origin_company_id uuid NOT NULL REFERENCES company(id),
 destination_company_id uuid NOT NULL REFERENCES company(id),
 reason_code varchar(40) NOT NULL,
 reason_label_snapshot varchar(160) NOT NULL,
 observations varchar(500) NOT NULL,
 status varchar(40) NOT NULL CHECK(status IN ('PENDING_ACCEPTANCE','ACCEPTED_PENDING_EFFECTIVE','EFFECTIVE','REJECTED','CANCELLED')),
 initiated_by_username varchar(80) NOT NULL,
 initiated_at timestamptz NOT NULL,
 decision_by_username varchar(80),
 decision_at timestamptz,
 decision_note varchar(1000),
 effective_at timestamptz,
 completed_at timestamptz,
 current_shift_assignment_id uuid REFERENCES operational_assignment(id),
 released_future_assignments integer NOT NULL DEFAULT 0,
 rrhh_sync_status varchar(32) NOT NULL DEFAULT 'PENDING',
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_employee_company_transfer_origin ON employee_company_transfer(instance_country_id,origin_company_id,status,initiated_at DESC);
CREATE INDEX IF NOT EXISTS ix_employee_company_transfer_destination ON employee_company_transfer(instance_country_id,destination_company_id,status,initiated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_one_open_transfer ON employee_company_transfer(instance_country_id,employee_id)
 WHERE status IN ('PENDING_ACCEPTANCE','ACCEPTED_PENDING_EFFECTIVE');

-- Kaibil UAT coordination personnel. SIC: RRHH LOCAL remains the source adapter for these snapshots.
-- Resolve Kaibil by its canonical tenant+code key so the migration also works if Kaibil already existed with another UUID.
INSERT INTO employee_operational_snapshot(id,instance_country_id,employee_id,company_id,full_name,role_code,employment_status,id_score,preferred_shift,required_change,photo_key,updated_from_source_at,created_at,updated_at)
SELECT v.id,'11111111-1111-1111-1111-111111111111',v.employee_id,c.id,v.full_name,v.role_code,'ACTIVE',v.id_score,v.preferred_shift,false,NULL,now(),now(),now()
FROM (VALUES
 ('81000000-0000-0000-0000-000000000101'::uuid,'82000000-0000-0000-0000-000000000101'::uuid,'Director Nacional UAT'::varchar,'DIRECTOR_NACIONAL'::varchar,9.70::numeric,'DIURNO'::varchar),
 ('81000000-0000-0000-0000-000000000102'::uuid,'82000000-0000-0000-0000-000000000102'::uuid,'Director Zonal UAT'::varchar,'DIRECTOR_ZONAL'::varchar,9.40::numeric,'DIURNO'::varchar),
 ('81000000-0000-0000-0000-000000000103'::uuid,'82000000-0000-0000-0000-000000000103'::uuid,'Jefe Regional UAT'::varchar,'JEFE_REGIONAL'::varchar,9.20::numeric,'DIURNO'::varchar),
 ('81000000-0000-0000-0000-000000000104'::uuid,'82000000-0000-0000-0000-000000000104'::uuid,'Monitor UAT'::varchar,'MONITOR'::varchar,8.90::numeric,'NOCTURNO'::varchar)
) AS v(id,employee_id,full_name,role_code,id_score,preferred_shift)
JOIN company c ON c.instance_country_id='11111111-1111-1111-1111-111111111111' AND c.core_catalog_id='a2000000-0000-0000-0000-000000000005'
ON CONFLICT(instance_country_id,employee_id) DO NOTHING;

INSERT INTO company_membership(id,instance_country_id,company_id,employee_id,membership_type,role_code,starts_at,ends_at,required_change,created_at,updated_at)
SELECT gen_random_uuid(),e.instance_country_id,e.company_id,e.employee_id,'PRIMARY',e.role_code,now(),NULL,false,now(),now()
FROM employee_operational_snapshot e
WHERE e.employee_id IN ('82000000-0000-0000-0000-000000000101','82000000-0000-0000-0000-000000000102','82000000-0000-0000-0000-000000000103','82000000-0000-0000-0000-000000000104')
AND NOT EXISTS (SELECT 1 FROM company_membership cm WHERE cm.instance_country_id=e.instance_country_id AND cm.employee_id=e.employee_id AND cm.membership_type='PRIMARY' AND cm.ends_at IS NULL);
