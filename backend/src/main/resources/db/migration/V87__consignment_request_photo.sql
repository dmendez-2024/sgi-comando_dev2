-- Foto de referencia de una consigna nueva creada por el agente en SGI Operador (solicitud de revisión, V49).
-- Llega por POST /api/v1/operator/evidences con target_type CONSIGNMENT_REQUEST y target_id = event_id = id de la solicitud.
-- Se agrega el tipo; los existentes no cambian.
ALTER TABLE evidence_object DROP CONSTRAINT ck_evidence_object_target_type;
ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type CHECK
 (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD','INVENTORY_ASSET',
 'SUPERVISOR_AGENT_PHOTO','POST_CONFIG','INCIDENT_DESCRIPTION','INCIDENT_RESOLUTION','CONSIGNMENT_REQUEST'));
