-- SIC:DHO -> SGI:Comando permissions/vacations inbound contract.
-- Schema-only migration: it does not delete records or seed business data.

ALTER TABLE employee_unavailability_snapshot
  ADD COLUMN IF NOT EXISTS source_reason_id bigint,
  ADD COLUMN IF NOT EXISTS source_reason_label text,
  ADD COLUMN IF NOT EXISTS source_state varchar(32),
  ADD COLUMN IF NOT EXISTS updated_from_source_at timestamptz;

UPDATE employee_unavailability_snapshot
SET updated_from_source_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE updated_from_source_at IS NULL;

ALTER TABLE employee_unavailability_snapshot
  ALTER COLUMN updated_from_source_at SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_unavailability_tenant_source_ref
  ON employee_unavailability_snapshot(instance_country_id, source_ref)
  WHERE source_ref IS NOT NULL;

CREATE TABLE IF NOT EXISTS sic_dho_unavailability_event_receipt (
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

CREATE INDEX IF NOT EXISTS ix_sic_dho_unavailability_receipt_processed
  ON sic_dho_unavailability_event_receipt(instance_country_id, processed_at DESC);

COMMENT ON COLUMN employee_unavailability_snapshot.source_reason_id
  IS 'Identifier permisos_motivos.id_pe_motivo received from SIC:DHO.';
COMMENT ON COLUMN employee_unavailability_snapshot.source_reason_label
  IS 'Reason label received from SIC:DHO at synchronization time.';
COMMENT ON COLUMN employee_unavailability_snapshot.source_state
  IS 'Original SIC:DHO permission state, separate from ACTIVE/INACTIVE operational status.';
COMMENT ON COLUMN employee_unavailability_snapshot.updated_from_source_at
  IS 'Last source modification timestamp used to reject stale events.';
