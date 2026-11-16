-- La fotografía del empleado deja de recibirse en el evento SIC:DHO -> SGI:Comando.
-- Se consultará directamente a DHO y no se persistirá en PostgreSQL ni en MinIO.

-- photo_key existía antes de esta integración y conserva los avatares locales.
-- Limpiar únicamente las claves de objetos que fueron sincronizadas a MinIO desde DHO.
UPDATE employee_operational_snapshot
SET photo_key = NULL
WHERE photo_bucket IS NOT NULL;

ALTER TABLE employee_operational_snapshot
  DROP COLUMN photo_bucket,
  DROP COLUMN photo_content_type,
  DROP COLUMN photo_sha256,
  DROP COLUMN photo_size_bytes;
