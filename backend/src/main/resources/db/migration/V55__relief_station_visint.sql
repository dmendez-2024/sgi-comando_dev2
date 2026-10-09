-- Copia de V55 (renumerada en 03125ce para el servidor de desarrollo). Tolerante a la repetición:
-- donde V55 ya se aplicó no cambia nada; donde no, aplica el mismo cambio.
-- VISINT opcional en el relevo: las 3 fotos del puesto (station_0..2) se comparan con las fotos estándar del Puesto (POST_CONFIG).
-- Se activa por Puesto y nunca bloquea el relevo: cada foto queda como una ejecución RELIEF_STATION_CAPTURED con su revisión visual.
ALTER TABLE post_operational_config ADD COLUMN IF NOT EXISTS station_visint_enabled boolean NOT NULL DEFAULT false;

-- El relevo no pertenece a un protocolo.
ALTER TABLE task_execution ALTER COLUMN protocol_id DROP NOT NULL;

ALTER TABLE task_execution DROP CONSTRAINT IF EXISTS ck_task_execution_type;
ALTER TABLE task_execution ADD CONSTRAINT ck_task_execution_type
  CHECK (execution_type IN ('PATROL_CHECKPOINT_COMPLETED','TASK_EVIDENCE_SUBMITTED','RELIEF_STATION_CAPTURED'));
ALTER TABLE evidence_object DROP CONSTRAINT IF EXISTS ck_evidence_object_target_type;
ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type
  CHECK (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD','POST_CONFIG'));

-- Las 3 fotos de un relevo comparten relevo (group_id) y Puesto (target_id): se distinguen por la foto de origen.
DROP INDEX IF EXISTS uq_task_execution_group_capture;
CREATE UNIQUE INDEX uq_task_execution_group_capture ON task_execution(instance_country_id, group_id, target_id, capture_no)
  WHERE patrol_execution_id IS NULL AND group_id IS NOT NULL AND execution_type <> 'RELIEF_STATION_CAPTURED';
ALTER TABLE task_execution ADD COLUMN IF NOT EXISTS relief_evidence_id uuid UNIQUE REFERENCES operator_relief_evidence(id);
