-- Consulta de cumplimientos operativos por tenant y período desde CNS.
CREATE INDEX IF NOT EXISTS ix_consignment_compliance_tenant_confirmed
    ON consignment_compliance(instance_country_id, confirmed_at DESC);
