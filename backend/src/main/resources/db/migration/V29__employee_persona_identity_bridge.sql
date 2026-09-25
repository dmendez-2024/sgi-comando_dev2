-- Puente temporal entre personas.id de DHO y el UUID operacional de SGI:Comando.
-- Es nullable para no inventar el origen de los snapshots históricos/UAT.
ALTER TABLE employee_operational_snapshot
  ADD COLUMN persona_id bigint;

CREATE UNIQUE INDEX ux_employee_snapshot_persona_id
  ON employee_operational_snapshot(instance_country_id, persona_id)
  WHERE persona_id IS NOT NULL;
