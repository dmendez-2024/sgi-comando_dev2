-- RRMM holdings include identified assets and material balances by lot/product.
ALTER TABLE inventory_expected_asset
  ADD COLUMN record_type varchar(16) NOT NULL DEFAULT 'IDENTIFIED',
  ADD COLUMN quantity numeric(18,4) NOT NULL DEFAULT 1,
  ADD COLUMN serial_number varchar(120),
  ADD COLUMN lot_code varchar(120);

ALTER TABLE inventory_expected_asset
  ADD CONSTRAINT ck_inventory_expected_record_type
    CHECK (record_type IN ('IDENTIFIED','FUNGIBLE','LOTTED')),
  ADD CONSTRAINT ck_inventory_expected_quantity CHECK (quantity > 0);

-- Holdings do not carry an expected condition; compliance is unknown until RRMM defines it.
ALTER TABLE inventory_report_item ALTER COLUMN compliant DROP NOT NULL;
