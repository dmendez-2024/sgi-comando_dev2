-- Foto de un reporte operativo (Incidente, Hallazgo, Vulnerabilidad) creado por el agente en SGI Operador (V78, photo_count).
-- Llega por POST /api/v1/operator/evidences con target_type OPERATIONAL_REPORT y target_id = event_id = id del reporte.
-- Se agrega el tipo; los existentes no cambian.
ALTER TABLE evidence_object DROP CONSTRAINT ck_evidence_object_target_type;
ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type CHECK
 (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD','INVENTORY_ASSET',
 'SUPERVISOR_AGENT_PHOTO','POST_CONFIG','INCIDENT_DESCRIPTION','INCIDENT_RESOLUTION','CONSIGNMENT_REQUEST',
 'OPERATIONAL_REPORT'));
