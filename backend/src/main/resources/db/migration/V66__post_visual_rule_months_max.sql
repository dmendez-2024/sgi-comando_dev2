ALTER TABLE post_visual_rule
  ADD CONSTRAINT ck_post_visual_rule_months_max CHECK (historical_presentation_months IS NULL OR historical_presentation_months <= 120);

ALTER TABLE post_visual_rule_history
  ADD CONSTRAINT ck_post_visual_rule_history_months_max CHECK (historical_presentation_months IS NULL OR historical_presentation_months <= 120);
