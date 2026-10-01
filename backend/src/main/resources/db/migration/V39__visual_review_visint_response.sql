-- Datos de la respuesta real de VISINT (POST /v1/faces/match): qué foto del agente coincidió, el código y la versión del modelo.
-- Los puntajes (score) no se guardan: VISINT decide el veredicto.
ALTER TABLE visual_review ADD COLUMN matched_evidence_id uuid;
ALTER TABLE visual_review ADD COLUMN reason_code varchar(60);
ALTER TABLE visual_review ADD COLUMN model_version varchar(120);
