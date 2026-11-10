package com.cajamarca.sgi.comando.secelec;

/**
 * Marca de la cámara: define la ruta RTSP del video, así el formulario no la pide.
 * Solo están las marcas cuya ruta es segura (formato oficial del fabricante); cualquier otra se registra como "Otra",
 * con la ruta del manual de la cámara escrita a mano (rtsp_path).
 * {canal} es el número de canal: 1 en una cámara sola; el de la cámara en un grabador NVR/DVR.
 * Cada marca tiene la ruta del stream principal (alta calidad) y la del secundario (liviano), ver StreamQuality.
 */
public enum CameraBrand {
    HIKVISION("Hikvision", "/Streaming/Channels/{canal}01", "/Streaming/Channels/{canal}02"),
    DAHUA("Dahua", "/cam/realmonitor?channel={canal}&subtype=0", "/cam/realmonitor?channel={canal}&subtype=1"),
    /** Tapo y VIGI: no tienen canales; stream1 = principal, stream2 = secundario. */
    TPLINK("TP-Link / Tapo", "/stream1", "/stream2"),
    OTHER("Otra", null, null);

    public static final int DEFAULT_PORT = 554;

    /** Calidad del video: MAIN = stream principal (alta calidad), SUB = secundario (liviano, el recomendado para la consola). */
    public enum StreamQuality { MAIN, SUB }

    public final String label;
    /** Ruta del stream principal; null en OTHER. */
    public final String pathTemplate;
    /** Ruta del stream secundario; null en OTHER. */
    public final String subPathTemplate;

    CameraBrand(String label, String pathTemplate, String subPathTemplate) {
        this.label = label;
        this.pathTemplate = pathTemplate;
        this.subPathTemplate = subPathTemplate;
    }

    /** Ruta RTSP del stream principal para ese canal; en OTHER, la ruta escrita a mano. */
    public String path(int channel, String customPath) { return path(channel, StreamQuality.MAIN, customPath); }

    /** Ruta RTSP para ese canal y calidad; en OTHER, la ruta escrita a mano (ya lleva canal y calidad). */
    public String path(int channel, StreamQuality quality, String customPath) {
        String template = quality == StreamQuality.SUB ? subPathTemplate : pathTemplate;
        if (template == null) return customPath;
        return template.replace("{canal}", String.valueOf(channel));
    }

    /** Ruta de una cámara guardada; las anteriores a la marca usan la de Hikvision y las anteriores a la calidad, la principal. */
    public static String pathOf(SecElecComponent c) {
        CameraBrand brand = c.cameraBrand == null ? HIKVISION : c.cameraBrand;
        return brand.path(c.channel == null ? 1 : c.channel, c.streamQuality == null ? StreamQuality.MAIN : c.streamQuality, c.rtspPath);
    }
}
