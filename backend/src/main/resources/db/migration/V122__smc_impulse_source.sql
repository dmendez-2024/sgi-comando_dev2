-- Las evaluaciones elegibles producen hechos independientes para SMC.
ALTER TABLE smc_source_cursor DROP CONSTRAINT smc_source_cursor_source_type_check;
ALTER TABLE smc_source_cursor ADD CONSTRAINT smc_source_cursor_source_type_check
    CHECK (source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION'));
CREATE INDEX ix_smc_impulse_incremental ON impulse_evaluation(instance_country_id, evaluated_at, id)
    WHERE decision IN ('AWARDED','NO_AWARD');
