CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_user (
  id uuid PRIMARY KEY,
  username varchar(80) NOT NULL UNIQUE,
  password_hash varchar(255) NOT NULL,
  roles varchar(500) NOT NULL,
  display_name varchar(160) NOT NULL,
  instance_country_id uuid NOT NULL,
  active boolean NOT NULL DEFAULT true
);

CREATE TABLE company (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  code varchar(32) NOT NULL,
  name varchar(160) NOT NULL,
  status varchar(32) NOT NULL,
  required_change_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id, code),
  UNIQUE(instance_country_id, name)
);
CREATE INDEX ix_company_tenant_status ON company(instance_country_id,status);

CREATE TABLE service (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  code varchar(40) NOT NULL,
  name varchar(180) NOT NULL,
  client_name varchar(180) NOT NULL,
  commercial_status varchar(32) NOT NULL,
  config_status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,code)
);
CREATE INDEX ix_service_tenant_status ON service(instance_country_id,commercial_status,config_status);

CREATE TABLE point (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  service_id uuid NOT NULL REFERENCES service(id),
  code varchar(40) NOT NULL,
  name varchar(180) NOT NULL,
  province varchar(120) NOT NULL,
  city varchar(120) NOT NULL,
  client_name varchar(180) NOT NULL,
  company_id uuid REFERENCES company(id),
  status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,code)
);
CREATE INDEX ix_point_tenant_service ON point(instance_country_id,service_id,status);
CREATE INDEX ix_point_tenant_company ON point(instance_country_id,company_id,status);

CREATE TABLE post (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  point_id uuid NOT NULL REFERENCES point(id),
  code varchar(64) NOT NULL,
  name varchar(180) NOT NULL,
  format varchar(80) NOT NULL,
  fhe numeric(8,2) NOT NULL,
  tier varchar(16) NOT NULL,
  config_status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(instance_country_id,code)
);
CREATE INDEX ix_post_tenant_point ON post(instance_country_id,point_id,config_status);

CREATE TABLE company_membership (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, company_id uuid NOT NULL REFERENCES company(id), employee_id uuid NOT NULL,
 membership_type varchar(32) NOT NULL, role_code varchar(80) NOT NULL, starts_at timestamptz NOT NULL, ends_at timestamptz,
 required_change boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_membership_primary_active ON company_membership(instance_country_id,employee_id) WHERE membership_type='PRIMARY' AND ends_at IS NULL;
CREATE INDEX ix_membership_company_active ON company_membership(instance_country_id,company_id,role_code) WHERE ends_at IS NULL;

CREATE TABLE assignment_plan (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, company_id uuid NOT NULL REFERENCES company(id), week_start date NOT NULL,
 status varchar(32) NOT NULL, published_at timestamptz, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(instance_country_id,company_id,week_start)
);
CREATE TABLE shift_occurrence (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, post_id uuid NOT NULL REFERENCES post(id), starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 commercial_version varchar(80), required boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_shift_occurrence_active ON shift_occurrence(instance_country_id,post_id,starts_at,ends_at);
CREATE TABLE operational_assignment (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, assignment_plan_id uuid NOT NULL REFERENCES assignment_plan(id), shift_occurrence_id uuid NOT NULL REFERENCES shift_occurrence(id),
 employee_id uuid, actual_employee_id uuid, compatibility_index numeric(5,2), id_score numeric(5,2), status varchar(32) NOT NULL,
 reassignment_reason text, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_assignment_employee_time ON operational_assignment(instance_country_id,employee_id,status);

CREATE TABLE relief_event (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, post_id uuid NOT NULL REFERENCES post(id), shift_occurrence_id uuid,
 outgoing_employee_id uuid, incoming_employee_id uuid, status varchar(40) NOT NULL, planned_at timestamptz,
 executed_at timestamptz, unilateral boolean NOT NULL DEFAULT false, inventory_result varchar(32), consignments_confirmed boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_relief_post_time ON relief_event(instance_country_id,post_id,planned_at DESC);

CREATE TABLE consignment (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, code varchar(32) NOT NULL, point_id uuid NOT NULL REFERENCES point(id), post_id uuid REFERENCES post(id),
 title varchar(200) NOT NULL, instruction text NOT NULL, priority varchar(16) NOT NULL, status varchar(32) NOT NULL,
 validity_type varchar(32) NOT NULL, application_type varchar(32) NOT NULL, published_at timestamptz,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(instance_country_id,code)
);
CREATE INDEX ix_consignment_point_active ON consignment(instance_country_id,point_id,status,published_at DESC);

CREATE TABLE finding (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, point_id uuid NOT NULL REFERENCES point(id), post_id uuid REFERENCES post(id),
 finding_type varchar(48) NOT NULL, status varchar(32) NOT NULL, title varchar(200) NOT NULL, description text,
 active_until timestamptz, ats_reconciliation_status varchar(32), created_by uuid, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_finding_point_active ON finding(instance_country_id,point_id,finding_type,status,active_until);

CREATE TABLE vulnerability (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, point_id uuid NOT NULL REFERENCES point(id), post_id uuid REFERENCES post(id),
 vulnerability_type varchar(32) NOT NULL, status varchar(32) NOT NULL, title varchar(200) NOT NULL, description text,
 created_by uuid, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_vulnerability_point_status ON vulnerability(instance_country_id,point_id,status);
CREATE TABLE vulnerability_confirmation (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, vulnerability_id uuid NOT NULL REFERENCES vulnerability(id), employee_id uuid NOT NULL,
 shift_occurrence_id uuid, confirmed_at timestamptz NOT NULL, observation text,
 UNIQUE(instance_country_id,vulnerability_id,employee_id,shift_occurrence_id)
);
CREATE TABLE vulnerability_state_proposal (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, vulnerability_id uuid NOT NULL REFERENCES vulnerability(id), proposed_state varchar(32) NOT NULL,
 proposed_by uuid NOT NULL, proposed_at timestamptz NOT NULL, reason text, approval_status varchar(32) NOT NULL,
 reviewed_by uuid, reviewed_at timestamptz
);

CREATE TABLE work_origin (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, work_type varchar(32) NOT NULL, subtype varchar(80), point_id uuid REFERENCES point(id), post_id uuid REFERENCES post(id),
 title varchar(200) NOT NULL, description text, source_channel varchar(32) NOT NULL, created_by uuid, routing_status varchar(32) NOT NULL,
 stc_task_id varchar(120), stc_status_snapshot varchar(80), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_work_origin_stc ON work_origin(instance_country_id,work_type,routing_status,created_at DESC);

CREATE TABLE access_protocol (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, point_id uuid NOT NULL REFERENCES point(id), owner_company_id uuid REFERENCES company(id),
 code varchar(64) NOT NULL, name varchar(180) NOT NULL, scope varchar(32) NOT NULL, movement_scope varchar(16) NOT NULL,
 structure_json text NOT NULL, status varchar(32) NOT NULL, version integer NOT NULL, source_protocol_id uuid, forced_standard boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(instance_country_id,code,version)
);
CREATE INDEX ix_access_protocol_point_active ON access_protocol(instance_country_id,point_id,status);

CREATE TABLE access_movement (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, point_id uuid NOT NULL REFERENCES point(id), post_id uuid NOT NULL REFERENCES post(id),
 protocol_id uuid REFERENCES access_protocol(id), movement_type varchar(16) NOT NULL, result varchar(32) NOT NULL,
 performed_by_employee_id uuid NOT NULL, occurred_at timestamptz NOT NULL, synced_at timestamptz, observations text,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_access_movement_point_time ON access_movement(instance_country_id,point_id,occurred_at DESC);
CREATE TABLE access_object_instance (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, movement_id uuid NOT NULL REFERENCES access_movement(id), object_type varchar(16) NOT NULL,
 identity_key varchar(180), payload_json text NOT NULL, result varchar(32) NOT NULL, created_at timestamptz NOT NULL
);
CREATE INDEX ix_access_object_lookup ON access_object_instance(instance_country_id,object_type,identity_key);
CREATE TABLE presence_session (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, point_id uuid NOT NULL REFERENCES point(id), object_type varchar(16) NOT NULL,
 identity_key varchar(180) NOT NULL, ingress_movement_id uuid NOT NULL REFERENCES access_movement(id), egress_movement_id uuid REFERENCES access_movement(id),
 entered_at timestamptz NOT NULL, exited_at timestamptz, status varchar(32) NOT NULL, regularized_at timestamptz,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_presence_open ON presence_session(instance_country_id,point_id,object_type,identity_key) WHERE status IN ('INSIDE','TO_REGULARIZE');
CREATE INDEX ix_presence_current ON presence_session(instance_country_id,point_id,status,entered_at);
CREATE TABLE checkpoint_passage (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, presence_session_id uuid NOT NULL REFERENCES presence_session(id), post_id uuid NOT NULL REFERENCES post(id),
 protocol_id uuid REFERENCES access_protocol(id), occurred_at timestamptz NOT NULL, result varchar(32) NOT NULL, payload_json text NOT NULL,
 created_at timestamptz NOT NULL
);

CREATE TABLE access_list_entry (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, scope_type varchar(16) NOT NULL, scope_id uuid NOT NULL, list_type varchar(16) NOT NULL,
 object_type varchar(16) NOT NULL, identity_key varchar(180) NOT NULL, display_name varchar(200), reason text, valid_from timestamptz, valid_until timestamptz,
 status varchar(32) NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_access_list_match ON access_list_entry(instance_country_id,scope_type,scope_id,list_type,object_type,identity_key,status);

CREATE TABLE patrol_definition (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, point_id uuid NOT NULL REFERENCES point(id), name varchar(180) NOT NULL,
 structure_type varchar(16) NOT NULL, sequence_type varchar(16), status varchar(32) NOT NULL, version integer NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE patrol_checkpoint (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, patrol_definition_id uuid NOT NULL REFERENCES patrol_definition(id), sequence_no integer NOT NULL,
 name varchar(180) NOT NULL, latitude numeric(10,7), longitude numeric(10,7), radius_m integer, validation_rule_json text NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE patrol_plan (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, patrol_definition_id uuid NOT NULL REFERENCES patrol_definition(id), post_id uuid NOT NULL REFERENCES post(id),
 schedule_type varchar(20) NOT NULL, schedule_json text, active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE patrol_occurrence (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, patrol_plan_id uuid NOT NULL REFERENCES patrol_plan(id), window_start timestamptz, window_end timestamptz,
 status varchar(32) NOT NULL, execution_id uuid, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX ix_patrol_occurrence_due ON patrol_occurrence(instance_country_id,status,window_start,window_end);
CREATE TABLE patrol_execution (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, patrol_plan_id uuid NOT NULL REFERENCES patrol_plan(id), occurrence_id uuid REFERENCES patrol_occurrence(id),
 employee_id uuid NOT NULL, started_at timestamptz NOT NULL, finished_at timestamptz, result varchar(32), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE patrol_checkpoint_execution (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, patrol_execution_id uuid NOT NULL REFERENCES patrol_execution(id), checkpoint_id uuid REFERENCES patrol_checkpoint(id),
 dynamic_name varchar(180), result varchar(32) NOT NULL, validated_at timestamptz, evidence_json text, created_at timestamptz NOT NULL
);
CREATE TABLE patrol_gps_sample (
 id bigserial PRIMARY KEY, instance_country_id uuid NOT NULL, patrol_execution_id uuid NOT NULL REFERENCES patrol_execution(id), captured_at timestamptz NOT NULL,
 latitude numeric(10,7) NOT NULL, longitude numeric(10,7) NOT NULL, accuracy_m numeric(8,2)
);
CREATE INDEX ix_patrol_gps_execution_time ON patrol_gps_sample(instance_country_id,patrol_execution_id,captured_at);

CREATE TABLE regesep_version (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, point_id uuid NOT NULL REFERENCES point(id), version integer NOT NULL, code varchar(80) NOT NULL,
 status varchar(20) NOT NULL, effective_at timestamptz NOT NULL, trigger_type varchar(80) NOT NULL, source_manifest_json text NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(instance_country_id,point_id,version)
);
CREATE UNIQUE INDEX ux_regesep_current ON regesep_version(instance_country_id,point_id) WHERE status='CURRENT';

CREATE TABLE file_object (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, bucket varchar(120) NOT NULL, object_key varchar(500) NOT NULL, content_type varchar(160),
 size_bytes bigint NOT NULL, sha256 char(64) NOT NULL, linked_entity_type varchar(80) NOT NULL, linked_entity_id uuid NOT NULL,
 created_at timestamptz NOT NULL
);
CREATE INDEX ix_file_entity ON file_object(instance_country_id,linked_entity_type,linked_entity_id);

CREATE TABLE audit_event (
 id bigserial PRIMARY KEY, instance_country_id uuid NOT NULL, event_type varchar(100) NOT NULL, entity_type varchar(80) NOT NULL,
 entity_id uuid, actor_id uuid, occurred_at timestamptz NOT NULL, payload_json text NOT NULL
);
CREATE INDEX ix_audit_entity_time ON audit_event(instance_country_id,entity_type,entity_id,occurred_at DESC);

CREATE TABLE outbox_event (
 id uuid PRIMARY KEY, instance_country_id uuid NOT NULL, aggregate_type varchar(80) NOT NULL, aggregate_id uuid NOT NULL,
 event_type varchar(120) NOT NULL, payload_json text NOT NULL, created_at timestamptz NOT NULL, published_at timestamptz
);
CREATE INDEX ix_outbox_pending ON outbox_event(instance_country_id,created_at) WHERE published_at IS NULL;
