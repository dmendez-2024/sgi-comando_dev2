-- Evolución del registro canónico de evidencias para Consola (CR-SGI_COM-CSL-0001).
ALTER TABLE evidence_object ADD COLUMN incident_id uuid REFERENCES incident_notification(id);
ALTER TABLE evidence_object ALTER COLUMN assignment_id DROP NOT NULL;
ALTER TABLE evidence_object ALTER COLUMN employee_id DROP NOT NULL;
ALTER TABLE evidence_object ALTER COLUMN username TYPE varchar(160);
ALTER TABLE evidence_object DROP CONSTRAINT ck_evidence_object_target_type;
ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type CHECK
 (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD','INVENTORY_ASSET',
 'SUPERVISOR_AGENT_PHOTO','POST_CONFIG','INCIDENT_DESCRIPTION','INCIDENT_RESOLUTION'));
ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_context CHECK (
 (target_type IN ('INCIDENT_DESCRIPTION','INCIDENT_RESOLUTION') AND incident_id IS NOT NULL
   AND incident_id=event_id AND incident_id=target_id AND assignment_id IS NULL AND employee_id IS NULL)
 OR (target_type NOT IN ('INCIDENT_DESCRIPTION','INCIDENT_RESOLUTION') AND incident_id IS NULL
   AND assignment_id IS NOT NULL AND employee_id IS NOT NULL));
CREATE INDEX ix_evidence_incident ON evidence_object(instance_country_id,incident_id,target_type);
