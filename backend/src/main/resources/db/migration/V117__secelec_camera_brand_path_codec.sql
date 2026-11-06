-- Cámaras de cualquier marca para el servidor de video (MediaMTX): la marca define la ruta RTSP;
-- "Otra" (OTHER) lleva su propia ruta. El códec lo guarda "Probar conexión" (H.265 no se reproduce en el navegador).
-- Antes era V108 y luego V112 (chocaban con la V108 y la V112 de sgiComandoDev). Solo corre si las columnas no existen.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'secelec_component' AND column_name = 'camera_brand') THEN
        ALTER TABLE secelec_component
            ADD COLUMN camera_brand varchar(24),
            ADD COLUMN rtsp_path varchar(300),
            ADD COLUMN detected_codec varchar(20);

        -- Hasta ahora la ruta era siempre la de Hikvision (/Streaming/Channels/{canal}01).
        UPDATE secelec_component SET camera_brand = 'HIKVISION' WHERE family = 'VIDEO';
    END IF;
END $$;
