-- Inventario del relevo: estado "Con novedad" (WITH_ISSUE) y estado precargado del último reporte del activo.

DO $$
DECLARE c text;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='inventory_report_item'::regclass AND contype='c'
           AND pg_get_constraintdef(oid) LIKE '%observed_condition%' LOOP
    EXECUTE format('ALTER TABLE inventory_report_item DROP CONSTRAINT %I',c);
  END LOOP;
END $$;

ALTER TABLE inventory_report_item ADD CONSTRAINT ck_inventory_report_item_observed_condition
  CHECK (observed_condition IN ('GOOD','WITH_ISSUE','DAMAGED','MISSING','REPLACED','NOT_VERIFIED'));

-- Estado que el agente vio precargado (último reporte del activo); null si el activo no tenía reportes.
ALTER TABLE inventory_report_item ADD COLUMN previous_condition varchar(32);
