-- Componentes de Seguridad · Cámaras: canal, coordenadas y streaming (formulario de cámara IP).
-- canal: número de canal del equipo (en un NVR, cada canal es una cámara). Ruta Hikvision /Streaming/Channels/{canal}01.
-- streaming_mode: COPY = el servidor de video reenvía el flujo tal cual; TRANSCODE = lo recodifica (p. ej. H.265 → H.264).
ALTER TABLE secelec_component
    ADD COLUMN channel           integer,
    ADD COLUMN latitude          numeric(9, 6),
    ADD COLUMN longitude         numeric(9, 6),
    ADD COLUMN streaming_enabled boolean,
    ADD COLUMN streaming_mode    varchar(20);

UPDATE secelec_component
   SET channel = 1, streaming_enabled = true, streaming_mode = 'COPY'
 WHERE family = 'VIDEO';

ALTER TABLE secelec_component
    ADD CONSTRAINT ck_secelec_component_channel CHECK (channel IS NULL OR channel BETWEEN 1 AND 256),
    ADD CONSTRAINT ck_secelec_component_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_secelec_component_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_secelec_component_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL)),
    ADD CONSTRAINT ck_secelec_component_streaming_mode CHECK (streaming_mode IS NULL OR streaming_mode IN ('COPY', 'TRANSCODE'));
