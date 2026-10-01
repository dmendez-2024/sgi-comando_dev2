-- Fase 4: fotos del agente y ejecuciones también para evidencias de Consigna y campos de Bitácora.
DO $$
DECLARE c text;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint WHERE conrelid = 'evidence_object'::regclass AND contype = 'c'
           AND pg_get_constraintdef(oid) LIKE '%target_type%' LOOP
    EXECUTE format('ALTER TABLE evidence_object DROP CONSTRAINT %I', c);
  END LOOP;
  FOR c IN SELECT conname FROM pg_constraint WHERE conrelid = 'task_execution'::regclass AND contype = 'c'
           AND pg_get_constraintdef(oid) LIKE '%execution_type%' LOOP
    EXECUTE format('ALTER TABLE task_execution DROP CONSTRAINT %I', c);
  END LOOP;
END $$;

ALTER TABLE evidence_object ADD CONSTRAINT ck_evidence_object_target_type
  CHECK (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD'));
ALTER TABLE task_execution ADD CONSTRAINT ck_task_execution_type
  CHECK (execution_type IN ('PATROL_CHECKPOINT_COMPLETED','TASK_EVIDENCE_SUBMITTED'));
