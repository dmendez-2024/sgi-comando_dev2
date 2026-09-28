package com.cajamarca.sgi.comando.services;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="service_company_assignment_event")
public class ServiceCompanyAssignmentEvent extends BaseEntity {
    @Column(name="service_id", nullable=false) public UUID serviceId;
    @Column(name="point_id", nullable=false) public UUID pointId;
    @Column(name="origin_company_id") public UUID originCompanyId;
    @Column(name="destination_company_id") public UUID destinationCompanyId;
    @Column(nullable=false, length=32) public String action;
    @Column(length=1000) public String observations;
    @Column(name="actor_username", nullable=false, length=80) public String actorUsername;
    @Column(name="occurred_at", nullable=false) public Instant occurredAt;
    @Column(name="released_future_assignments", nullable=false) public int releasedFutureAssignments;
    @Column(name="retained_active_assignments", nullable=false) public int retainedActiveAssignments;
}
