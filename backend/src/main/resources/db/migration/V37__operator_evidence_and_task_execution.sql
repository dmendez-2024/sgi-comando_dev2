-- Fotos del agente (almacenadas en MinIO) y ejecuciones de tareas. Fase 1: solo Hitos de patrulla.
ALTER TABLE patrol_execution ALTER COLUMN patrol_plan_id DROP NOT NULL;
ALTER TABLE patrol_execution ADD COLUMN patrol_definition_id uuid REFERENCES patrol_definition(id),
  ADD COLUMN assignment_id uuid REFERENCES operational_assignment(id), ADD COLUMN username varchar(80);

CREATE TABLE evidence_object (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  client_evidence_id uuid NOT NULL,
  upload_batch_id uuid NOT NULL,
  event_id uuid NOT NULL,
  assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  employee_id uuid NOT NULL,
  username varchar(80) NOT NULL,
  target_type varchar(40) NOT NULL,
  target_id uuid NOT NULL,
  bucket varchar(63) NOT NULL,
  object_key varchar(300) NOT NULL,
  content_type varchar(40) NOT NULL,
  size_bytes bigint NOT NULL,
  sha256 char(64) NOT NULL,
  captured_at timestamptz NOT NULL,
  latitude double precision, longitude double precision, accuracy_m double precision,
  source varchar(16) NOT NULL,
  flags varchar(200) NOT NULL DEFAULT '',
  status varchar(16) NOT NULL,
  received_at timestamptz NOT NULL,
  UNIQUE (instance_country_id, client_evidence_id),
  CHECK (size_bytes BETWEEN 1 AND 5242880),
  CHECK (target_type IN ('PATROL_CHECKPOINT')),
  CHECK (source IN ('CAMERA','GALLERY')),
  CHECK (status IN ('STORED','ATTACHED'))
);
CREATE INDEX ix_evidence_object_event ON evidence_object(instance_country_id, event_id);
CREATE INDEX ix_evidence_object_sha ON evidence_object(instance_country_id, sha256);

CREATE TABLE task_execution (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  execution_type varchar(40) NOT NULL,
  assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  shift_occurrence_id uuid NOT NULL,
  point_id uuid NOT NULL,
  post_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  username varchar(80) NOT NULL,
  target_type varchar(40) NOT NULL,
  target_id uuid NOT NULL,
  protocol_id uuid NOT NULL,
  protocol_version_no integer NOT NULL,
  patrol_execution_id uuid REFERENCES patrol_execution(id),
  executed_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL,
  latitude double precision, longitude double precision, accuracy_m double precision,
  observation varchar(1000),
  batch_id uuid NOT NULL, correlation_id uuid NOT NULL, device_id varchar(120) NOT NULL,
  payload_hash char(64) NOT NULL,
  payload_json text NOT NULL,
  status varchar(24) NOT NULL,
  CHECK (execution_type IN ('PATROL_CHECKPOINT_COMPLETED')),
  CHECK (status IN ('RECEIVED')),
  UNIQUE (instance_country_id, patrol_execution_id, target_id)
);
CREATE INDEX ix_task_execution_post_time ON task_execution(instance_country_id, post_id, executed_at DESC);

CREATE TABLE task_execution_evidence (
  task_execution_id uuid NOT NULL REFERENCES task_execution(id),
  evidence_id uuid NOT NULL UNIQUE REFERENCES evidence_object(id),
  sort_order integer NOT NULL,
  PRIMARY KEY (task_execution_id, evidence_id)
);
