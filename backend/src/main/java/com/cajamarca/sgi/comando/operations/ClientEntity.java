package com.cajamarca.sgi.comando.operations;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name="client")
public class ClientEntity extends BaseEntity {
  @Column(nullable=false, length=80) public String code;
  @Column(nullable=false, length=180) public String name;
  @Column(name="commercial_status", nullable=false, length=32) public String commercialStatus;
  @Column(name="source_system", nullable=false, length=32) public String sourceSystem;
  @Column(name="source_version", length=80) public String sourceVersion;
}
