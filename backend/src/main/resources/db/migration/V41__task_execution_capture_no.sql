-- Fase 3: si VISINT dice "no cumple", el agente puede tomar una nueva foto del mismo Hito en la misma ronda.
-- Cada intento es una ejecución con su número de captura (1, 2, …).
ALTER TABLE task_execution ADD COLUMN capture_no integer NOT NULL DEFAULT 1;

DO $$
DECLARE c text;
BEGIN
  SELECT conname INTO c FROM pg_constraint
  WHERE conrelid = 'task_execution'::regclass AND contype = 'u'
    AND pg_get_constraintdef(oid) = 'UNIQUE (instance_country_id, patrol_execution_id, target_id)';
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE task_execution DROP CONSTRAINT %I', c); END IF;
END $$;

ALTER TABLE task_execution ADD CONSTRAINT uq_task_execution_capture UNIQUE (instance_country_id, patrol_execution_id, target_id, capture_no);
