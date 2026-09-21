-- SGI: Comando / SER v0.4 — configuración operacional por Puesto.
-- Los atributos contractuales del Puesto siguen siendo de solo lectura y pertenecen a SIC: COM.

CREATE TABLE IF NOT EXISTS post_operational_config (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL REFERENCES post(id),
  post_type varchar(8) NOT NULL CHECK (post_type IN ('CAA','PAT','VIG','MIX')),
  description varchar(600) NOT NULL,
  ats_location_key varchar(64) NOT NULL,
  ats_location_label varchar(160) NOT NULL,
  skill_attendance integer NOT NULL CHECK (skill_attendance BETWEEN 1 AND 5),
  skill_access_control integer NOT NULL CHECK (skill_access_control BETWEEN 1 AND 5),
  skill_patrol integer NOT NULL CHECK (skill_patrol BETWEEN 1 AND 5),
  skill_judgement integer NOT NULL CHECK (skill_judgement BETWEEN 1 AND 5),
  skill_tactical integer NOT NULL CHECK (skill_tactical BETWEEN 1 AND 5),
  skill_bearing integer NOT NULL CHECK (skill_bearing BETWEEN 1 AND 5),
  skill_leadership integer NOT NULL CHECK (skill_leadership BETWEEN 1 AND 5),
  skill_customer_service integer NOT NULL CHECK (skill_customer_service BETWEEN 1 AND 5),
  adjustment_justification varchar(600),
  config_status varchar(24) NOT NULL CHECK (config_status IN ('DRAFT','CONFIGURED')),
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_post_operational_config_post UNIQUE(post_id),
  CONSTRAINT ck_post_operational_config_skill_economy CHECK (
    skill_attendance + skill_access_control + skill_patrol + skill_judgement +
    skill_tactical + skill_bearing + skill_leadership + skill_customer_service <= 22
    AND
    ((CASE WHEN skill_attendance=5 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_access_control=5 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_patrol=5 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_judgement=5 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_tactical=5 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_bearing=5 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_leadership=5 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_customer_service=5 THEN 1 ELSE 0 END)) <= 1
    AND
    ((CASE WHEN skill_attendance=4 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_access_control=4 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_patrol=4 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_judgement=4 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_tactical=4 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_bearing=4 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_leadership=4 THEN 1 ELSE 0 END) +
     (CASE WHEN skill_customer_service=4 THEN 1 ELSE 0 END)) <= 2
  )
);
CREATE INDEX IF NOT EXISTS ix_post_operational_config_tenant_post ON post_operational_config(instance_country_id,post_id);

-- Semilla UAT SGI. No modifica ningún dato contractual recibido de SIC: COM.
INSERT INTO post_operational_config(
  id,instance_country_id,post_id,post_type,description,ats_location_key,ats_location_label,
  skill_attendance,skill_access_control,skill_patrol,skill_judgement,skill_tactical,skill_bearing,skill_leadership,skill_customer_service,
  adjustment_justification,config_status,updated_by_username,created_at,updated_at
)
SELECT
  gen_random_uuid(),p.instance_country_id,p.id,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 'CAA' WHEN lower(p.name) LIKE '%perímetro%' THEN 'PAT' ELSE 'VIG' END,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 'Control de ingreso, validación de accesos y reporte de novedades.'
       WHEN lower(p.name) LIKE '%perímetro%' THEN 'Vigilancia del perímetro asignado y detección de novedades.'
       ELSE 'Vigilancia y control operacional del área asignada.' END,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 'ACCESS' WHEN lower(p.name) LIKE '%perímetro%' THEN 'NORTH' ELSE 'LOBBY' END,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 'Acceso Principal' WHEN lower(p.name) LIKE '%perímetro%' THEN 'Perímetro Norte' ELSE 'Lobby' END,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 2 ELSE 2 END,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 4 ELSE 1 END,
  CASE WHEN lower(p.name) LIKE '%perímetro%' THEN 3 ELSE 2 END,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 4 ELSE 2 END,
  2,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 3 ELSE 2 END,
  1,
  CASE WHEN lower(p.name) LIKE '%acceso%' THEN 3 ELSE 1 END,
  'Valores UAT de referencia; la plantilla institucional definitiva se parametrizará posteriormente.',
  'CONFIGURED','SYSTEM',now(),now()
FROM post p
WHERE p.instance_country_id='11111111-1111-1111-1111-111111111111'::uuid
ON CONFLICT(post_id) DO NOTHING;
