-- Contrato temporal SGI Operador -> SGI Comando.
-- Se conserva como read model independiente para que CORE pueda asumir el enrutamiento
-- sin acoplar el payload móvil a las tablas de dominio finding/vulnerability.
CREATE TABLE operator_operational_report (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  employee_id uuid NOT NULL,
  username varchar(120) NOT NULL,
  point_id uuid NOT NULL REFERENCES point(id),
  post_id uuid REFERENCES post(id),
  report_type varchar(24) NOT NULL,
  category varchar(80) NOT NULL,
  subcategory varchar(120),
  title varchar(200) NOT NULL,
  description text NOT NULL,
  severity varchar(24) NOT NULL DEFAULT 'MEDIUM',
  status varchar(24) NOT NULL DEFAULT 'REPORTED',
  occurred_at timestamptz NOT NULL,
  source varchar(32) NOT NULL DEFAULT 'SGI_OPERADOR_LOCAL',
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT ck_operator_report_type CHECK (report_type IN ('INCIDENT','FINDING','VULNERABILITY')),
  CONSTRAINT ck_operator_report_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);
CREATE INDEX ix_operator_report_point_time ON operator_operational_report(instance_country_id,point_id,occurred_at DESC);
CREATE INDEX ix_operator_report_employee_time ON operator_operational_report(instance_country_id,employee_id,occurred_at DESC);
