-- Cámaras: calidad del video (stream principal o secundario) y solo marcas con ruta segura.
-- 1) stream_quality: MAIN = principal (alta calidad), SUB = secundario (liviano). Las cámaras existentes quedan en MAIN,
--    que es lo que usaban, así su video no cambia.
ALTER TABLE secelec_component ADD COLUMN IF NOT EXISTS stream_quality varchar(8);
UPDATE secelec_component SET stream_quality = 'MAIN' WHERE family = 'VIDEO' AND stream_quality IS NULL;

-- 2) Uniview, Reolink y Axis salen de la lista (su ruta varía según el modelo). Las que ya estaban pasan a "Otra" con la
--    misma ruta que usaban, escrita a mano: siguen conectando igual.
UPDATE secelec_component
   SET rtsp_path = CASE camera_brand
                     WHEN 'UNIVIEW' THEN '/unicast/c' || coalesce(channel, 1) || '/s0/live'
                     WHEN 'REOLINK' THEN '/h264Preview_' || lpad(coalesce(channel, 1)::text, 2, '0') || '_main'
                     WHEN 'AXIS'    THEN '/axis-media/media.amp?camera=' || coalesce(channel, 1)
                   END,
       camera_brand = 'OTHER'
 WHERE family = 'VIDEO' AND camera_brand IN ('UNIVIEW', 'REOLINK', 'AXIS');
