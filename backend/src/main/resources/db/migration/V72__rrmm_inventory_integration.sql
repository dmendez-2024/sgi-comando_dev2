-- Integración de inventario SIC:RRMM <-> SGI Comando <-> SGI Operador.

CREATE TABLE inventory_expected_asset (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL REFERENCES post(id) ON DELETE CASCADE,
  asset_id uuid NOT NULL,
  product_id uuid,
  code varchar(100) NOT NULL,
  description varchar(300) NOT NULL,
  expected_condition varchar(32) NOT NULL,
  assignment_status varchar(32) NOT NULL,
  critical boolean NOT NULL DEFAULT false,
  source_version varchar(120) NOT NULL,
  source_payload text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  fetched_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,post_id,asset_id)
);
CREATE INDEX ix_inventory_expected_asset_current
  ON inventory_expected_asset(instance_country_id,post_id,active,code);

CREATE TABLE inventory_sync_state (
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL REFERENCES post(id) ON DELETE CASCADE,
  source_version varchar(120) NOT NULL,
  item_count integer NOT NULL,
  fetched_at timestamptz NOT NULL,
  last_correlation_id varchar(120),
  PRIMARY KEY(instance_country_id,post_id)
);

CREATE TABLE inventory_report (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  relief_event_id uuid NOT NULL REFERENCES relief_event(id) ON DELETE CASCADE,
  assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  post_id uuid NOT NULL REFERENCES post(id),
  point_id uuid NOT NULL REFERENCES point(id),
  employee_id uuid NOT NULL,
  username varchar(80) NOT NULL,
  client_request_id uuid NOT NULL,
  correlation_id uuid NOT NULL,
  source_version varchar(120),
  inventory_status varchar(32) NOT NULL,
  reported_at timestamptz NOT NULL,
  payload_hash char(64) NOT NULL,
  payload_json text NOT NULL,
  delivery_status varchar(24) NOT NULL DEFAULT 'PENDING',
  delivery_attempts integer NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL DEFAULT current_timestamp,
  delivered_at timestamptz,
  last_error varchar(1000),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,relief_event_id),
  UNIQUE(instance_country_id,client_request_id),
  CHECK (inventory_status IN ('REPORTED','COMPLETE')),
  CHECK (delivery_status IN ('PENDING','DELIVERED','FAILED'))
);
CREATE INDEX ix_inventory_report_delivery
  ON inventory_report(delivery_status,next_attempt_at,created_at);

CREATE TABLE inventory_report_item (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  report_id uuid NOT NULL REFERENCES inventory_report(id) ON DELETE CASCADE,
  asset_id uuid NOT NULL,
  product_id uuid,
  asset_code varchar(100) NOT NULL,
  asset_description varchar(300) NOT NULL,
  expected_condition varchar(32) NOT NULL,
  observed_condition varchar(32) NOT NULL,
  observation varchar(1000),
  compliant boolean NOT NULL,
  created_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,report_id,asset_id),
  CHECK (observed_condition IN ('GOOD','DAMAGED','MISSING','REPLACED','NOT_VERIFIED'))
);
CREATE INDEX ix_inventory_report_item_asset
  ON inventory_report_item(instance_country_id,asset_id,created_at DESC);

CREATE TABLE inventory_report_item_evidence (
  report_item_id uuid NOT NULL REFERENCES inventory_report_item(id) ON DELETE CASCADE,
  evidence_id uuid NOT NULL UNIQUE REFERENCES evidence_object(id),
  sort_order integer NOT NULL,
  PRIMARY KEY(report_item_id,evidence_id)
);

DO $$
DECLARE c text;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='evidence_object'::regclass AND contype='c'
           AND pg_get_constraintdef(oid) LIKE '%target_type%' LOOP
    EXECUTE format('ALTER TABLE evidence_object DROP CONSTRAINT %I',c);
  END LOOP;
END $$;

ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type
  CHECK (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD','INVENTORY_ASSET'));
