-- Extensión aditiva del evento SIC:DHO -> SGI:Comando.
-- Los campos quedan nullable para permitir el despliegue de Comando antes que DHO.
ALTER TABLE employee_operational_snapshot
  ADD COLUMN identification_number varchar(80),
  ADD COLUMN corporate_email varchar(254),
  ADD COLUMN photo_bucket varchar(63),
  ADD COLUMN photo_content_type varchar(40),
  ADD COLUMN photo_sha256 varchar(64),
  ADD COLUMN photo_size_bytes bigint;

-- El contrato ya valida 255 caracteres; ampliar la columna evita que la base
-- rechace un nombre que el endpoint consideró válido.
ALTER TABLE employee_operational_snapshot
  ALTER COLUMN full_name TYPE varchar(255);

CREATE UNIQUE INDEX ux_employee_snapshot_identification
  ON employee_operational_snapshot(instance_country_id, upper(btrim(identification_number)))
  WHERE identification_number IS NOT NULL AND btrim(identification_number) <> '';

CREATE INDEX ix_employee_snapshot_corporate_email
  ON employee_operational_snapshot(instance_country_id, lower(btrim(corporate_email)))
  WHERE corporate_email IS NOT NULL AND btrim(corporate_email) <> '';
