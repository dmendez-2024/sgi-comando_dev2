-- La foto del Agente de la evaluación de Supervisor (V81) se guarda en evidence_object con
-- target_type SUPERVISOR_AGENT_PHOTO, pero ck_evidence_object_target_type (V72) no lo permitía
-- y el envío de la evaluación fallaba con 500. Se agrega el tipo; los existentes no cambian.
ALTER TABLE evidence_object DROP CONSTRAINT ck_evidence_object_target_type;
ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type
  CHECK (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD','INVENTORY_ASSET','SUPERVISOR_AGENT_PHOTO','POST_CONFIG'));
