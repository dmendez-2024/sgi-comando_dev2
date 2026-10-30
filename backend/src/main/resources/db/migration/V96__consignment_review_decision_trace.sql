-- Trazabilidad de la decisión sobre consignas propuestas desde SGI Operador.
-- La solicitud conserva su contenido original; la versión revisada queda en la
-- consigna de borrador y se enlaza explícitamente para auditoría.
ALTER TABLE operator_consignment_review_request
  ADD COLUMN target_protocol_id uuid REFERENCES consignment_protocol(id),
  ADD COLUMN approved_consignment_id uuid REFERENCES consignment(id);

CREATE UNIQUE INDEX ux_operator_consignment_review_approved_item
  ON operator_consignment_review_request(instance_country_id, approved_consignment_id)
  WHERE approved_consignment_id IS NOT NULL;

CREATE INDEX ix_operator_consignment_review_target_protocol
  ON operator_consignment_review_request(instance_country_id, target_protocol_id)
  WHERE target_protocol_id IS NOT NULL;
