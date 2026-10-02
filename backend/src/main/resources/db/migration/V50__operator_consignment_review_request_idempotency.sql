-- Preserve the canonical request fingerprint to make replay detection explicit.
-- Existing V35 rows are historical UAT rows and require an operator review before
-- they can be retried; new submissions always persist this digest.
ALTER TABLE operator_consignment_review_request
  ADD COLUMN payload_hash varchar(64);

CREATE INDEX ix_operator_consignment_review_idempotency
  ON operator_consignment_review_request(instance_country_id, id, payload_hash);
