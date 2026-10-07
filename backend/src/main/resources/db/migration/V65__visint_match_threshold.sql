-- Copia de V56 (renumerada en 03125ce para el servidor de desarrollo). Tolerante a la repetición:
-- donde V56 ya se aplicó no cambia nada; donde no, aplica el mismo cambio.
-- Umbral de coincidencia (matchThreshold, 0.00 a 1.00) que SGI envía a VISINT: PASS si la mejor coincidencia lo alcanza o supera.
-- Se configura junto a las fotos estándar de cada tarea. NULL = umbral predeterminado (sgi.visint.match-threshold).
ALTER TABLE patrol_checkpoint ADD COLUMN IF NOT EXISTS match_threshold numeric(3,2) CHECK (match_threshold BETWEEN 0 AND 1);
ALTER TABLE consignment_evidence ADD COLUMN IF NOT EXISTS match_threshold numeric(3,2) CHECK (match_threshold BETWEEN 0 AND 1);
ALTER TABLE logbook_protocol_field ADD COLUMN IF NOT EXISTS match_threshold numeric(3,2) CHECK (match_threshold BETWEEN 0 AND 1);
ALTER TABLE post_operational_config ADD COLUMN IF NOT EXISTS station_match_threshold numeric(3,2) CHECK (station_match_threshold BETWEEN 0 AND 1);

-- Umbral con el que se envió cada revisión (snapshot: si después cambia en la tarea, la revisión conserva el suyo).
ALTER TABLE visual_review ADD COLUMN IF NOT EXISTS match_threshold numeric(3,2) CHECK (match_threshold BETWEEN 0 AND 1);
