-- Solicitudes ad-hoc originadas por SGI Operador. No son consignas vigentes
-- hasta que la operación responsable las revise y las incorpore al protocolo.
CREATE TABLE operator_consignment_review_request (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  employee_id uuid NOT NULL,
  username varchar(80) NOT NULL,
  point_id uuid NOT NULL REFERENCES point(id),
  post_id uuid NOT NULL REFERENCES post(id),
  title varchar(200) NOT NULL,
  instruction text NOT NULL,
  scope varchar(16) NOT NULL CHECK (scope IN ('Punto','Puesto')),
  character_type varchar(16) NOT NULL CHECK (character_type IN ('Permanente','Temporal')),
  end_date varchar(10),
  schedule varchar(20) NOT NULL CHECK (schedule IN ('Todo el tiempo','Horario')),
  schedule_days varchar(40),
  start_time varchar(5),
  end_time varchar(5),
  priority varchar(16) NOT NULL CHECK (priority IN ('Normal','Alta','Crítica')),
  has_coordinates boolean NOT NULL DEFAULT false,
  has_photo boolean NOT NULL DEFAULT false,
  status varchar(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','DISCARDED')),
  submitted_at timestamptz NOT NULL,
  reviewed_at timestamptz,
  reviewed_by varchar(80),
  review_comment varchar(500),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX ix_operator_consignment_review_pending
  ON operator_consignment_review_request(instance_country_id,status,submitted_at DESC);
CREATE INDEX ix_operator_consignment_review_assignment
  ON operator_consignment_review_request(instance_country_id,assignment_id,submitted_at DESC);
