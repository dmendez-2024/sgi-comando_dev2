-- Breadcrumb offline por ejecución. No instala PostGIS ni altera los datos previos.
ALTER TABLE patrol_gps_sample ADD COLUMN sample_id uuid;
ALTER TABLE patrol_gps_sample ADD COLUMN speed_mps double precision;
CREATE UNIQUE INDEX uq_patrol_gps_sample_event
 ON patrol_gps_sample(instance_country_id,patrol_execution_id,sample_id) WHERE sample_id IS NOT NULL;

-- Snapshot inmutable: el cambio de política no reescribe un hecho ya publicado.
CREATE TABLE patrol_distance_measurement (
 id uuid PRIMARY KEY REFERENCES patrol_execution(id),
 instance_country_id uuid NOT NULL,
 employee_id uuid NOT NULL,
 occurred_at timestamptz NOT NULL,
 measured_at timestamptz NOT NULL DEFAULT current_timestamp,
 kilometers numeric(16,6) NOT NULL CHECK(kilometers>=0),
 sample_count integer NOT NULL,
 accepted_segments integer NOT NULL,
 rejected_segments integer NOT NULL,
 max_accuracy_m double precision NOT NULL,
 max_gap_seconds bigint NOT NULL,
 algorithm_version varchar(40) NOT NULL DEFAULT 'HAVERSINE_SEGMENTS_V1'
);
CREATE INDEX ix_patrol_distance_discovery ON patrol_distance_measurement(instance_country_id,measured_at,id);
