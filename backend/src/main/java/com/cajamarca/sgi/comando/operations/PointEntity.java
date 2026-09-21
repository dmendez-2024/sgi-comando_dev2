package com.cajamarca.sgi.comando.operations;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(name="point")
public class PointEntity extends BaseEntity {
    @Column(name="service_id",nullable=false) public UUID serviceId;
    @Column(nullable=false) public String code;
    @Column(nullable=false) public String name;
    @Column(nullable=false) public String province;
    @Column(nullable=false) public String city;
    @Column(name="client_name",nullable=false) public String clientName;
    @Column(name="company_id") public UUID companyId;
    @Column(name="operational_assignment_status", nullable=false, length=32) public String operationalAssignmentStatus;
    @Column(name="received_from_sic_com_at") public Instant receivedFromSicComAt;
    @Column(name="assigned_by_username", length=80) public String assignedByUsername;
    @Column(name="assigned_at") public Instant assignedAt;
    @Column(name="operational_transition_until") public Instant operationalTransitionUntil;
    @Column(nullable=false) public String status;
}
