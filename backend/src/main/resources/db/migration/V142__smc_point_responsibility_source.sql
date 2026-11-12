ALTER TABLE smc_source_cursor DROP CONSTRAINT smc_source_cursor_source_type_check;
ALTER TABLE smc_source_cursor ADD CONSTRAINT smc_source_cursor_source_type_check
CHECK (source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION',
    'PATROL_DISTANCE','NOVELTY_APPROVAL','PATROL_NOT_EXECUTED','DAY_OFF_WORKED','POINT_RESPONSIBILITY'));
CREATE INDEX ix_smc_point_responsibility_end ON point_responsibility(instance_country_id,ends_at,id);
