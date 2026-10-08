ALTER TABLE post_visual_rule
  ADD COLUMN threshold_value double precision CHECK (threshold_value BETWEEN 0 AND 1),
  ADD COLUMN historical_presentation_months integer CHECK (historical_presentation_months > 0);
UPDATE post_visual_rule SET threshold_value = threshold_percent / 100.0 WHERE threshold_percent IS NOT NULL;

ALTER TABLE post_visual_rule_history
  ADD COLUMN threshold_value double precision CHECK (threshold_value BETWEEN 0 AND 1),
  ADD COLUMN historical_presentation_months integer CHECK (historical_presentation_months > 0);
UPDATE post_visual_rule_history SET threshold_value = threshold_percent / 100.0 WHERE threshold_percent IS NOT NULL;
