-- PATROL_DISTANCE es una cuarta fuente del publicador desde V137.
-- Se conserva la tabla, los cursores y las entregas existentes.
ALTER TABLE smc_source_cursor DROP CONSTRAINT smc_source_cursor_source_type_check;
ALTER TABLE smc_source_cursor ADD CONSTRAINT smc_source_cursor_source_type_check
    CHECK (source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION','PATROL_DISTANCE'));
