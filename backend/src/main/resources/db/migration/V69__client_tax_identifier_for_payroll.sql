ALTER TABLE client
    ADD COLUMN IF NOT EXISTS tax_identifier varchar(32);

CREATE UNIQUE INDEX IF NOT EXISTS ux_client_instance_tax_identifier
    ON client(instance_country_id, tax_identifier)
    WHERE tax_identifier IS NOT NULL AND btrim(tax_identifier) <> '';

COMMENT ON COLUMN client.tax_identifier IS
    'Identificación tributaria del cliente utilizada por SIC:DHO para consolidar nómina.';
