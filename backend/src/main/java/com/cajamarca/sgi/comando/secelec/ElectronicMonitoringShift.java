package com.cajamarca.sgi.comando.secelec;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/** Horario comercial de un dispositivo de seguridad electrónica. */
@Entity
@Table(name = "electronic_monitoring_shift")
public class ElectronicMonitoringShift extends BaseEntity {
  @Column(name = "monitoring_post_id", nullable = false)
  public UUID deviceId;

  @Column(name = "shift_code", nullable = false, length = 64)
  public String shiftCode;

  @Column(name = "shift_name", nullable = false, length = 160)
  public String shiftName;

  @Column(name = "start_time", nullable = false)
  public LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  public LocalTime endTime;

  @Column(name = "day_mask", nullable = false)
  public Integer dayMask;

  @Column(nullable = false)
  public boolean active;

  @Column(name = "commercial_version", length = 120)
  public String commercialVersion;
}
