package com.cajamarca.sgi.comando.secelec;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

/** Componente de seguridad instalado en un Punto (cámara, sensor, lector, luminaria…). Las cámaras son la familia VIDEO. */
@Entity
@Table(name = "secelec_component")
public class SecElecComponent extends BaseEntity {
    public enum Family { PERIMETER, ACCESS_CONTROL, VIDEO, INTRUSION, LIGHTING, FIRE }
    public enum Status { ACTIVE, OBSERVED, INACTIVE }
    /** COPY: el servidor de video reenvía el flujo tal cual. TRANSCODE: lo recodifica (p. ej. H.265 → H.264 para el navegador). */
    public enum StreamingMode { COPY, TRANSCODE }

    @Column(name = "point_id", nullable = false) public UUID pointId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) public Family family;
    @Column(name = "component_type", nullable = false, length = 80) public String componentType;
    @Column(nullable = false, length = 40) public String code;
    @Column(nullable = false, length = 160) public String name;
    @Column(length = 200) public String location;
    @Column(length = 300) public String coverage;
    @Column(name = "brand_model", length = 160) public String brandModel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Status status;
    @Column(length = 1000) public String observations;
    @Column(length = 255) public String ip;
    public Integer port;
    /** Canal del equipo (1 para una cámara sola; en un NVR, cada canal es una cámara). */
    public Integer channel;
    @Column(precision = 9, scale = 6) public java.math.BigDecimal latitude;
    @Column(precision = 9, scale = 6) public java.math.BigDecimal longitude;
    @Column(name = "streaming_enabled") public Boolean streamingEnabled;
    @Enumerated(EnumType.STRING) @Column(name = "streaming_mode", length = 20) public StreamingMode streamingMode;
    @Column(length = 120) public String username;
    /** Contraseña cifrada con {@link CameraSecretCipher}; nunca sale por la API. */
    @Column(name = "password_cipher", length = 600) public String passwordCipher;
    /** Video de pruebas mientras no exista el servidor de video. Relativo a la web o absoluto. */
    @Column(name = "stream_url", length = 500) public String streamUrl;
    @Column(name = "updated_by", nullable = false, length = 160) public String updatedBy;
}
