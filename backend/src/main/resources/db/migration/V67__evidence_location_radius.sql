-- Radio GPS configurable desde la base (antes: sgi.evidence.default-radius-m fijo en 50 m).
-- Resolución: radio propio de la tarea (Hito, Consigna, Puesto) → predeterminado de la instancia (operational_setting) → 50 m.

-- Parámetros operativos por instancia (país), editables desde Configuración.
CREATE TABLE operational_setting (
  instance_country_id uuid NOT NULL,
  setting_key varchar(80) NOT NULL,
  setting_value varchar(200) NOT NULL,
  updated_by_username varchar(80) NOT NULL,
  updated_at timestamptz NOT NULL,
  PRIMARY KEY (instance_country_id, setting_key)
);

-- Radio propio (opcional) de cada referencia. NULL = predeterminado de la instancia.
ALTER TABLE patrol_checkpoint ADD CONSTRAINT ck_patrol_checkpoint_radius CHECK (radius_m IS NULL OR radius_m BETWEEN 5 AND 5000);
ALTER TABLE consignment ADD COLUMN expected_radius_m integer CHECK (expected_radius_m BETWEEN 5 AND 5000);
ALTER TABLE post_operational_config ADD COLUMN radius_m integer CHECK (radius_m BETWEEN 5 AND 5000);

-- Con qué radio y a qué distancia se evaluó cada foto (snapshot: cambiar el radio después no altera los avisos pasados).
ALTER TABLE evidence_object ADD COLUMN reference_radius_m integer, ADD COLUMN reference_distance_m integer;
