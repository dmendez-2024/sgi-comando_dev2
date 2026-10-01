-- Revisión visual (VISINT) de cada ejecución de un Hito con VISINT activado.
-- VISINT responde de forma síncrona PASS (cumple), FAIL (no cumple) o ERROR (no pudo evaluar). Sin puntajes.
CREATE TABLE visual_review (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  task_execution_id uuid NOT NULL UNIQUE REFERENCES task_execution(id),
  status varchar(24) NOT NULL,
  standard_target_type varchar(40) NOT NULL,
  standard_target_id uuid NOT NULL,
  standard_image_version integer NOT NULL,
  standard_bucket varchar(63),
  standard_object_key varchar(300),
  standard_sha256 char(64),
  simulated boolean NOT NULL DEFAULT false,
  visint_external_id varchar(120),
  attempts integer NOT NULL DEFAULT 0,
  next_attempt_at timestamptz,
  last_error varchar(500),
  result varchar(8),
  findings varchar(1000),
  correlation_id uuid NOT NULL,
  created_at timestamptz NOT NULL,
  requested_at timestamptz,
  reviewed_at timestamptz,
  CHECK (status IN ('QUEUED_FOR_VISINT','PASSED','FAILED','ERROR_RETRYABLE','ERROR_FINAL')),
  CHECK (result IS NULL OR result IN ('PASS','FAIL','ERROR'))
);
CREATE INDEX ix_visual_review_due ON visual_review(status, next_attempt_at);
