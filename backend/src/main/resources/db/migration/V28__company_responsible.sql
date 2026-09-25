-- SGI: Comando — COM v1.1.4
-- Responsable operacional opcional de la Compañía.

ALTER TABLE company
  ADD COLUMN IF NOT EXISTS responsible_employee_id uuid DEFAULT NULL;
