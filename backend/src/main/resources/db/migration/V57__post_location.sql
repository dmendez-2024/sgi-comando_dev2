-- Ubicación GPS del Puesto: referencia para comparar la ubicación de las fotos de Bitácora y del Relevo.
-- Solo informativa: si la foto llega fuera del radio se marca OUT_OF_RANGE (aviso en Operación), nunca bloquea.
ALTER TABLE post_operational_config
  ADD COLUMN latitude double precision CHECK (latitude BETWEEN -90 AND 90),
  ADD COLUMN longitude double precision CHECK (longitude BETWEEN -180 AND 180),
  ADD CONSTRAINT ck_post_operational_config_location CHECK ((latitude IS NULL) = (longitude IS NULL));
