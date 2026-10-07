-- Copia de V58 (renumerada en 03125ce para el servidor de desarrollo). Tolerante a la repetición:
-- donde V58 ya se aplicó no cambia nada; donde no, aplica el mismo cambio.
-- Radio GPS configurable desde la base (antes: sgi.evidence.default-radius-m fijo en 50 m).
-- Resolución: radio propio de la tarea (Hito, Consigna, Puesto) → predeterminado de la instancia (operational_setting) → 50 m.

-- Parámetros operativos por instancia (país), editables desde Configuración.
CREATE TABLE IF NOT EXISTS operational_setting (
  instance_country_id uuid NOT NULL,
  setting_key varchar(80) NOT NULL,
  setting_value varchar(200) NOT NULL,
  updated_by_username varchar(80) NOT NULL,
  updated_at timestamptz NOT NULL,
  PRIMARY KEY (instance_country_id, setting_key)
);

-- Radio propio (opcional) de cada referencia. NULL = predeterminado de la instancia.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_patrol_checkpoint_radius') THEN
    ALTER TABLE patrol_checkpoint ADD CONSTRAINT ck_patrol_checkpoint_radius CHECK (radius_m IS NULL OR radius_m BETWEEN 5 AND 5000);
  END IF;
END $$;
ALTER TABLE consignment ADD COLUMN IF NOT EXISTS expected_radius_m integer CHECK (expected_radius_m BETWEEN 5 AND 5000);
ALTER TABLE post_operational_config ADD COLUMN IF NOT EXISTS radius_m integer CHECK (radius_m BETWEEN 5 AND 5000);

-- Con qué radio y a qué distancia se evaluó cada foto (snapshot: cambiar el radio después no altera los avisos pasados).
ALTER TABLE evidence_object ADD COLUMN IF NOT EXISTS reference_radius_m integer, ADD COLUMN IF NOT EXISTS reference_distance_m integer;
