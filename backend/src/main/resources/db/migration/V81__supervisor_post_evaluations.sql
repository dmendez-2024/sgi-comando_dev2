CREATE TABLE supervisor_evaluation (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  supervisor_assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  agent_assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  supervisor_employee_id uuid NOT NULL,
  agent_employee_id uuid NOT NULL,
  point_id uuid NOT NULL REFERENCES point(id),
  post_id uuid NOT NULL REFERENCES post(id),
  captured_at timestamptz NOT NULL,
  vigilance_level varchar(16) NOT NULL,
  cleanliness_level varchar(16) NOT NULL,
  consignment_knowledge_level varchar(16) NOT NULL,
  situational_level varchar(16) NOT NULL,
  consignment_id uuid,
  consignment_version integer,
  consignment_snapshot text,
  agent_photo_evidence_id uuid NOT NULL REFERENCES evidence_object(id),
  inventory_overall_state varchar(24) NOT NULL,
  inventory_comments text,
  supervisor_comments text,
  point_post_notes text,
  status varchar(24) NOT NULL DEFAULT 'COMPLETED',
  correlation_id uuid NOT NULL,
  created_by varchar(120) NOT NULL,
  created_at timestamptz NOT NULL,
  payload_hash varchar(64) NOT NULL,
  CONSTRAINT ck_supervisor_evaluation_level CHECK (
    vigilance_level IN ('LOW','MEDIUM','HIGH','VERY_HIGH') AND
    cleanliness_level IN ('LOW','MEDIUM','HIGH','VERY_HIGH') AND
    consignment_knowledge_level IN ('LOW','MEDIUM','HIGH','VERY_HIGH') AND
    situational_level IN ('LOW','MEDIUM','HIGH','VERY_HIGH')),
  CONSTRAINT ck_supervisor_inventory_state CHECK (inventory_overall_state IN ('COMPLETE','OBSERVATIONS','INCOMPLETE','CRITICAL'))
);

CREATE UNIQUE INDEX ux_supervisor_evaluation_assignment_post
  ON supervisor_evaluation(instance_country_id,supervisor_assignment_id,post_id);

CREATE TABLE supervisor_evaluation_inventory_item (
  evaluation_id uuid NOT NULL REFERENCES supervisor_evaluation(id) ON DELETE CASCADE,
  asset_id uuid NOT NULL,
  description_snapshot text NOT NULL,
  observed_state varchar(24) NOT NULL,
  comment text,
  PRIMARY KEY(evaluation_id,asset_id),
  CONSTRAINT ck_supervisor_inventory_item_state CHECK (observed_state IN ('GOOD','DAMAGED','MISSING','NOT_VERIFIED'))
);

CREATE TABLE supervisor_evaluation_novelty_decision (
  evaluation_id uuid NOT NULL REFERENCES supervisor_evaluation(id) ON DELETE CASCADE,
  novelty_id uuid NOT NULL REFERENCES operator_operational_report(id),
  novelty_type varchar(24) NOT NULL,
  decision varchar(16) NOT NULL,
  decided_by varchar(120) NOT NULL,
  decided_at timestamptz NOT NULL,
  PRIMARY KEY(evaluation_id,novelty_id),
  CONSTRAINT ck_supervisor_novelty_decision CHECK (decision IN ('CONFIRMED','DISCARDED'))
);

CREATE INDEX ix_supervisor_evaluation_post_time
  ON supervisor_evaluation(instance_country_id,post_id,captured_at DESC);
