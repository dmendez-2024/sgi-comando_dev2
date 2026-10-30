-- Cursor durable de descubrimiento, separado del estado de entrega a SMC.
CREATE TABLE smc_source_cursor (
    instance_country_id uuid NOT NULL,
    source_type varchar(40) NOT NULL,
    changed_at timestamptz NOT NULL,
    last_source_id uuid,
    initialized_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (instance_country_id, source_type),
    CHECK (source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE'))
);

-- Rangos por recepción/actualización local, no por la fecha del hecho del teléfono.
CREATE INDEX ix_smc_patrol_incremental ON patrol_execution(instance_country_id, updated_at, id)
    WHERE finished_at IS NOT NULL AND result IN ('COMPLETA','INCOMPLETA') AND employee_id IS NOT NULL;
CREATE INDEX ix_smc_consignment_incremental ON consignment_compliance(instance_country_id, created_at, id)
    WHERE result='NO_CUMPLIDA' AND employee_id IS NOT NULL;
