package com.cajamarca.sgi.comando.secelec;

/**
 * Marca de la cámara: define la ruta RTSP del video, así el formulario no la pide.
 * {canal} es el número de canal (1 en una cámara sola; el de la cámara en un grabador NVR/DVR) y {canal2} el mismo con dos dígitos.
 * OTHER ("Otra") no tiene plantilla: la ruta se escribe a mano (rtsp_path).
 */
public enum CameraBrand {
    HIKVISION("Hikvision", "/Streaming/Channels/{canal}01"),
    DAHUA("Dahua", "/cam/realmonitor?channel={canal}&subtype=0"),
    UNIVIEW("Uniview", "/unicast/c{canal}/s0/live"),
    REOLINK("Reolink", "/h264Preview_{canal2}_main"),
    AXIS("Axis", "/axis-media/media.amp?camera={canal}"),
    TPLINK("TP-Link / Tapo", "/stream1"),
    OTHER("Otra", null);

    public static final int DEFAULT_PORT = 554;

    public final String label;
    /** null en OTHER. */
    public final String pathTemplate;

    CameraBrand(String label, String pathTemplate) {
        this.label = label;
        this.pathTemplate = pathTemplate;
    }

    /** Ruta RTSP para ese canal; en OTHER, la ruta escrita a mano. */
    public String path(int channel, String customPath) {
        if (pathTemplate == null) return customPath;
        return pathTemplate.replace("{canal2}", "%02d".formatted(channel)).replace("{canal}", String.valueOf(channel));
    }

    /** Ruta de una cámara guardada; las anteriores a la marca usan la de Hikvision. */
    public static String pathOf(SecElecComponent c) {
        CameraBrand brand = c.cameraBrand == null ? HIKVISION : c.cameraBrand;
        return brand.path(c.channel == null ? 1 : c.channel, c.rtspPath);
    }
}
