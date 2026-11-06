-- Sanción registrada por el Supervisor en el paso 6 de la Supervisión de Puesto (SGI Operador).
-- Llega en POST /api/v1/operator/supervision/evaluations: texto en metadata.sanctionDescription
-- e imágenes (máximo 3) en la parte multipart sanctionFiles. Las imágenes se guardan como
-- evidence_object con target_type SUPERVISOR_SANCTION_IMAGE. Las evaluaciones existentes quedan sin sanción.
ALTER TABLE supervisor_evaluation ADD COLUMN sanction_description text;

CREATE TABLE supervisor_evaluation_sanction_image (
  evaluation_id uuid NOT NULL REFERENCES supervisor_evaluation(id) ON DELETE CASCADE,
  position smallint NOT NULL,
  evidence_id uuid NOT NULL UNIQUE REFERENCES evidence_object(id),
  PRIMARY KEY (evaluation_id, position),
  CONSTRAINT ck_supervisor_sanction_image_position CHECK (position BETWEEN 1 AND 3)
);

ALTER TABLE evidence_object DROP CONSTRAINT ck_evidence_object_target_type;
ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type CHECK
 (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD','INVENTORY_ASSET',
 'SUPERVISOR_AGENT_PHOTO','POST_CONFIG','INCIDENT_DESCRIPTION','INCIDENT_RESOLUTION','CONSIGNMENT_REQUEST',
 'OPERATIONAL_REPORT','SUPERVISOR_SANCTION_IMAGE'));
