-- SIC: COM remains the commercial System of Record.
-- SGI: Comando stores a local operational replica keyed by commercial codes.

CREATE TABLE client (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  code varchar(80) NOT NULL,
  name varchar(180) NOT NULL,
  commercial_status varchar(32) NOT NULL,
  source_system varchar(32) NOT NULL,
  source_version varchar(80),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, code)
);
CREATE INDEX ix_client_tenant_status ON client(instance_country_id, commercial_status);

-- Historical records only have a client name. They are retained as LEGACY
-- references until SIC: COM publishes its stable commercial client code.
INSERT INTO client(
  id, instance_country_id, code, name, commercial_status,
  source_system, source_version, created_at, updated_at
)
SELECT gen_random_uuid(), s.instance_country_id,
       'LEGACY-' || substring(md5(lower(trim(s.client_name))) from 1 for 24),
       s.client_name, 'ACTIVE', 'LEGACY', NULL, now(), now()
FROM service s
GROUP BY s.instance_country_id, s.client_name
ON CONFLICT (instance_country_id, code) DO NOTHING;

ALTER TABLE service ADD COLUMN client_id uuid;
UPDATE service s
   SET client_id = c.id
  FROM client c
 WHERE c.instance_country_id = s.instance_country_id
   AND c.code = 'LEGACY-' || substring(md5(lower(trim(s.client_name))) from 1 for 24)
   AND s.client_id IS NULL;
ALTER TABLE service ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE service
  ADD CONSTRAINT fk_service_client FOREIGN KEY (client_id) REFERENCES client(id);
CREATE INDEX ix_service_tenant_client ON service(instance_country_id, client_id);

ALTER TABLE post
  ADD COLUMN commercial_status varchar(32) NOT NULL DEFAULT 'ACTIVE';
CREATE INDEX ix_post_tenant_commercial_status
  ON post(instance_country_id, commercial_status);

CREATE TABLE sic_com_commercial_event_receipt (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  event_id varchar(160) NOT NULL,
  content_hash varchar(64) NOT NULL,
  commercial_version varchar(80) NOT NULL,
  processing_status varchar(32) NOT NULL,
  response_json text NOT NULL,
  occurred_at timestamptz NOT NULL,
  processed_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, event_id)
);
CREATE INDEX ix_sic_com_receipt_tenant_processed
  ON sic_com_commercial_event_receipt(instance_country_id, processed_at DESC);
