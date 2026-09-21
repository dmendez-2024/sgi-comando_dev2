package com.cajamarca.sgi.comando.operations;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
@Entity @Table(name="service")
public class ServiceEntity extends BaseEntity {
    @Column(nullable=false) public String code;
    @Column(nullable=false) public String name;
    @Column(name="client_name",nullable=false) public String clientName;
    @Column(name="commercial_status",nullable=false) public String commercialStatus;
    @Column(name="config_status",nullable=false) public String configStatus;
    @Column(name="source_system",nullable=false,length=32) public String sourceSystem;
    @Column(name="source_version",length=80) public String sourceVersion;
}
