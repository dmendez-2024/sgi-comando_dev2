-- SGI: Comando / SER v0.9.1
-- Reasignación de Servicios: Compañía operadora -> bandeja lógica Kaibil -> nueva Compañía.
-- La configuración permanece ligada al Punto/Servicio y no se destruye.

ALTER TABLE point ADD COLUMN IF NOT EXISTS operational_transition_until timestamptz;

ALTER TABLE service_company_assignment_event
  ALTER COLUMN destination_company_id DROP NOT NULL;
ALTER TABLE service_company_assignment_event
  ADD COLUMN IF NOT EXISTS released_future_assignments integer NOT NULL DEFAULT 0;
ALTER TABLE service_company_assignment_event
  ADD COLUMN IF NOT EXISTS retained_active_assignments integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS ix_service_company_assignment_event_action
  ON service_company_assignment_event(instance_country_id, action, occurred_at DESC);

-- Existing V19 rows are initial assignments; no historical data is rewritten.
