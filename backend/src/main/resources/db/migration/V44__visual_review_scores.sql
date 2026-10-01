-- Puntajes que devuelve VISINT (quality / match). Son informativos: el veredicto sigue siendo el status de VISINT.
ALTER TABLE visual_review ADD COLUMN quality_valid boolean;
ALTER TABLE visual_review ADD COLUMN quality_score double precision;
ALTER TABLE visual_review ADD COLUMN match_compatible boolean;
ALTER TABLE visual_review ADD COLUMN match_score double precision;
