package com.cajamarca.sgi.comando.secelec;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Registro comercial recibido desde SIC:COM para un servicio de seguridad
 * electrónica (por ahora, OBSINT). No es un {@link SecElecComponent}: un
 * dispositivo comercial puede requerir después varias cámaras, sensores u
 * otros componentes configurables.
 */
@Entity
@Table(name = "electronic_monitoring_post")
public class ElectronicMonitoringDevice extends BaseEntity {
  @Column(name = "point_id", nullable = false)
  public UUID pointId;

  /** Código visible SGI, por ejemplo GGTT-SE-001. */
  @Column(nullable = false, length = 64)
  public String code;

  /** Identificador externo recibido de SIC:COM (posts.code2). */
  @Column(name = "source_code2", nullable = false, length = 64)
  public String sourceCode2;

  @Column(name = "post_type", nullable = false, length = 32)
  public String postType;

  @Column(nullable = false, length = 160)
  public String name;

  @Column(nullable = false, length = 40)
  public String format;

  @Column(length = 160)
  public String product;

  @Column(name = "minutes_per_day")
  public Integer minutesPerDay;

  @Column(name = "commercial_status", nullable = false, length = 16)
  public String commercialStatus;

  @Column(name = "config_status", nullable = false, length = 24)
  public String configStatus;

  @Column(name = "commercial_version", length = 120)
  public String sourceVersion;
}
