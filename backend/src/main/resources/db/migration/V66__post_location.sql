-- Copia de V57 (renumerada en 03125ce para el servidor de desarrollo). Tolerante a la repetición:
-- donde V57 ya se aplicó no cambia nada; donde no, aplica el mismo cambio.
-- Ubicación GPS del Puesto: referencia para comparar la ubicación de las fotos de Bitácora y del Relevo.
-- Solo informativa: si la foto llega fuera del radio se marca OUT_OF_RANGE (aviso en Operación), nunca bloquea.
ALTER TABLE post_operational_config
  ADD COLUMN IF NOT EXISTS latitude double precision CHECK (latitude BETWEEN -90 AND 90),
  ADD COLUMN IF NOT EXISTS longitude double precision CHECK (longitude BETWEEN -180 AND 180);
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_post_operational_config_location') THEN
    ALTER TABLE post_operational_config ADD CONSTRAINT ck_post_operational_config_location CHECK ((latitude IS NULL) = (longitude IS NULL));
  END IF;
END $$;
