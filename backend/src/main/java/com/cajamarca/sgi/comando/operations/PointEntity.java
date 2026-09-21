package com.cajamarca.sgi.comando.operations;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="point")
public class PointEntity extends BaseEntity {
    @Column(name="service_id",nullable=false) public UUID serviceId;
    @Column(nullable=false) public String code;
    @Column(nullable=false) public String name;
    @Column(nullable=false) public String province;
    @Column(nullable=false) public String city;
    @Column(name="client_name",nullable=false) public String clientName;
    @Column(name="company_id") public UUID companyId;
    @Column(nullable=false) public String status;
}
