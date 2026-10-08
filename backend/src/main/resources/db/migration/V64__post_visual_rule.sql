CREATE TABLE post_visual_rule (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL UNIQUE REFERENCES post(id),
  threshold_percent integer CHECK (threshold_percent BETWEEN 0 AND 100),
  reference_image_count integer CHECK (reference_image_count BETWEEN 1 AND 3),
  historical_presentation varchar(1000),
  updated_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX ix_post_visual_rule_tenant_post ON post_visual_rule(instance_country_id, post_id);

CREATE TABLE post_visual_rule_history (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL REFERENCES post(id),
  threshold_percent integer,
  reference_image_count integer,
  historical_presentation varchar(1000),
  changed_by_username varchar(80) NOT NULL,
  changed_at timestamptz NOT NULL
);
CREATE INDEX ix_post_visual_rule_history_post ON post_visual_rule_history(instance_country_id, post_id, changed_at DESC);
