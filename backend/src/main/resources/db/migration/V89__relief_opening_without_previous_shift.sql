-- Relevo: distingue el relevo normal, el unilateral y el inicio de puesto sin turno anterior inmediato.
-- relief_mode lo envía SGI Operador (reliefMode); opening_cause la calcula Comando con la regla del turno contiguo.
-- Los relevos existentes conservan su modo a partir de unilateral.
ALTER TABLE relief_event ADD COLUMN relief_mode varchar(40);
UPDATE relief_event SET relief_mode = CASE WHEN unilateral THEN 'UNILATERAL' ELSE 'NORMAL' END;
ALTER TABLE relief_event ALTER COLUMN relief_mode SET NOT NULL;
ALTER TABLE relief_event ALTER COLUMN relief_mode SET DEFAULT 'NORMAL';
ALTER TABLE relief_event ADD COLUMN opening_cause varchar(60);
ALTER TABLE relief_event ADD CONSTRAINT ck_relief_event_mode CHECK (
 (relief_mode = 'NORMAL' AND NOT unilateral AND opening_cause IS NULL)
 OR (relief_mode = 'UNILATERAL' AND unilateral AND opening_cause IS NULL)
 OR (relief_mode = 'OPENING_WITHOUT_PREVIOUS_SHIFT' AND NOT unilateral AND outgoing_employee_id IS NULL
     AND opening_cause IN ('NO_PREVIOUS_SHIFT','PREVIOUS_SHIFT_NOT_CONSECUTIVE','PREVIOUS_SHIFT_SAME_AGENT')));
