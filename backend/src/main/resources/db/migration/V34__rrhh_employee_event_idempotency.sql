-- SGI: Comando | P1 interconnection hardening
-- Persistent idempotency/audit receipt for SIC_RRHH_SGI_COM_0001_v001.
-- This is schema-only. It does not seed, delete or rewrite employee data.

CREATE TABLE sic_rrhh_employee_event_receipt (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  content_hash varchar(64) NOT NULL,
  source_updated_at timestamptz NOT NULL,
  processing_status varchar(32) NOT NULL,
  processed_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, idempotency_key)
);

CREATE INDEX ix_sic_rrhh_receipt_tenant_processed
  ON sic_rrhh_employee_event_receipt(instance_country_id, processed_at DESC);
