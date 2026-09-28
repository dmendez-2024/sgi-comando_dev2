package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="employee_unavailability_snapshot")
public class EmployeeUnavailabilitySnapshot extends BaseEntity {
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(nullable=false) public String type;
    @Column(name="starts_at", nullable=false) public Instant startsAt;
    @Column(name="ends_at", nullable=false) public Instant endsAt;
    @Column(name="source_ref") public String sourceRef;
    @Column(name="source_status", nullable=false) public String sourceStatus;
}
