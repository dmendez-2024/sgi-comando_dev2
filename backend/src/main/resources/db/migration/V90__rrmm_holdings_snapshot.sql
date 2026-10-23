-- RRMM holdings include identified assets and material balances by lot/product.
-- DEVELOPMENT may already have these columns from the SQL applied manually before
-- Flyway ran this migration. Keep the migration safe for both database states.
ALTER TABLE inventory_expected_asset
  ADD COLUMN IF NOT EXISTS record_type varchar(16) NOT NULL DEFAULT 'IDENTIFIED',
  ADD COLUMN IF NOT EXISTS quantity numeric(18,4) NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS serial_number varchar(120),
  ADD COLUMN IF NOT EXISTS lot_code varchar(120);

ALTER TABLE inventory_expected_asset
  ALTER COLUMN record_type SET DEFAULT 'IDENTIFIED',
  ALTER COLUMN record_type SET NOT NULL,
  ALTER COLUMN quantity SET DEFAULT 1,
  ALTER COLUMN quantity SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'inventory_expected_asset'::regclass
      AND conname = 'ck_inventory_expected_record_type'
  ) THEN
    ALTER TABLE inventory_expected_asset
      ADD CONSTRAINT ck_inventory_expected_record_type
        CHECK (record_type IN ('IDENTIFIED','FUNGIBLE','LOTTED'));
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'inventory_expected_asset'::regclass
      AND conname = 'ck_inventory_expected_quantity'
  ) THEN
    ALTER TABLE inventory_expected_asset
      ADD CONSTRAINT ck_inventory_expected_quantity CHECK (quantity > 0);
  END IF;
END $$;

-- Holdings do not carry an expected condition; compliance is unknown until RRMM defines it.
ALTER TABLE inventory_report_item ALTER COLUMN compliant DROP NOT NULL;
