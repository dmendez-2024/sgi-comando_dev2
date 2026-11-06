package com.cajamarca.sgi.comando.secelec;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Secuencia aislada para códigos XXXX-SE-001; nunca comparte el contador SF. */
@Entity
@Table(name = "electronic_monitoring_code_allocation")
public class ElectronicMonitoringCodeAllocation extends BaseEntity {
  @Column(name = "base_code", nullable = false, length = 4)
  public String baseCode;

  @Column(name = "source_signature", nullable = false, length = 800)
  public String sourceSignature;

  @Column(nullable = false, length = 32)
  public String discriminator;

  @Column(name = "last_sequence", nullable = false)
  public int lastSequence;
}
