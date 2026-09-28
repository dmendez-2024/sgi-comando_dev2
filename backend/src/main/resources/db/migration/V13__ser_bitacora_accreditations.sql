-- SGI: Comando / SER v0.6.1 — Acreditaciones explícitas dentro de Protocolo.
-- Jerarquía: Punto -> Puesto -> Protocolo -> Acreditación -> Reglas/Campos.

CREATE TABLE IF NOT EXISTS logbook_accreditation (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  protocol_id uuid NOT NULL REFERENCES logbook_protocol(id) ON DELETE CASCADE,
  code varchar(32) NOT NULL,
  name varchar(180) NOT NULL,
  description varchar(1000) NOT NULL DEFAULT '',
  identification_logic varchar(8) NOT NULL DEFAULT 'ALL' CHECK (identification_logic IN ('ALL','ANY')),
  verification_logic varchar(8) NOT NULL DEFAULT 'ALL' CHECK (verification_logic IN ('ALL','ANY')),
  auth_preapproval boolean NOT NULL DEFAULT true,
  auth_client boolean NOT NULL DEFAULT true,
  auth_supervisor boolean NOT NULL DEFAULT false,
  white_list_enabled boolean NOT NULL DEFAULT true,
  black_list_enabled boolean NOT NULL DEFAULT true,
  capture_manual boolean NOT NULL DEFAULT true,
  capture_qr boolean NOT NULL DEFAULT true,
  capture_barcode boolean NOT NULL DEFAULT false,
  capture_nfc boolean NOT NULL DEFAULT false,
  capture_automatic boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_logbook_accreditation_code_protocol UNIQUE(instance_country_id,protocol_id,code)
);
CREATE INDEX IF NOT EXISTS ix_logbook_accreditation_protocol ON logbook_accreditation(instance_country_id,protocol_id);

-- Materializa una acreditación principal para cada Protocolo existente y conserva las reglas v0.6.
INSERT INTO logbook_accreditation(
  id,instance_country_id,protocol_id,code,name,description,
  identification_logic,verification_logic,auth_preapproval,auth_client,auth_supervisor,
  white_list_enabled,black_list_enabled,capture_manual,capture_qr,capture_barcode,capture_nfc,capture_automatic,
  created_at,updated_at
)
SELECT
  (substr(md5(p.id::text || ':ACC-001'),1,8)||'-'||substr(md5(p.id::text || ':ACC-001'),9,4)||'-'||substr(md5(p.id::text || ':ACC-001'),13,4)||'-'||substr(md5(p.id::text || ':ACC-001'),17,4)||'-'||substr(md5(p.id::text || ':ACC-001'),21,12))::uuid,
  p.instance_country_id,p.id,'ACC-001',
  CASE p.code
    WHEN 'PRO-BA-0001' THEN 'Visitante autorizado'
    WHEN 'PRO-BA-0002' THEN 'Visitante con ingreso registrado'
    WHEN 'PRO-BA-0003' THEN 'Vehículo autorizado'
    WHEN 'PRO-BA-0004' THEN 'Contenedor autorizado'
    WHEN 'PRO-BA-0005' THEN 'Persona autorizada en perímetro'
    ELSE 'Acreditación principal'
  END,
  'Acreditación migrada desde la configuración original del protocolo.',
  p.identification_logic,p.verification_logic,p.auth_preapproval,p.auth_client,p.auth_supervisor,
  p.white_list_enabled,p.black_list_enabled,true,p.capture_qr,p.capture_barcode,p.capture_nfc,p.capture_automatic,
  now(),now()
FROM logbook_protocol p
WHERE NOT EXISTS (
  SELECT 1 FROM logbook_accreditation a
  WHERE a.instance_country_id=p.instance_country_id AND a.protocol_id=p.id
);

ALTER TABLE logbook_protocol_field ADD COLUMN IF NOT EXISTS accreditation_id uuid;
UPDATE logbook_protocol_field f
SET accreditation_id=(
  SELECT a.id FROM logbook_accreditation a
  WHERE a.instance_country_id=f.instance_country_id AND a.protocol_id=f.protocol_id
  ORDER BY a.code LIMIT 1
)
WHERE f.accreditation_id IS NULL;
ALTER TABLE logbook_protocol_field ALTER COLUMN accreditation_id SET NOT NULL;
DO $$ BEGIN
  ALTER TABLE logbook_protocol_field ADD CONSTRAINT fk_logbook_protocol_field_accreditation
    FOREIGN KEY(accreditation_id) REFERENCES logbook_accreditation(id) ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
CREATE INDEX IF NOT EXISTS ix_logbook_protocol_field_accreditation ON logbook_protocol_field(instance_country_id,accreditation_id,section,sort_order);
