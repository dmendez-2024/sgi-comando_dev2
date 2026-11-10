package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="point_responsibility")
public class PointResponsibilityEntity extends BaseEntity {
    @Column(name="assignment_plan_id", nullable=false) public UUID assignmentPlanId;
    @Column(name="point_id", nullable=false) public UUID pointId;
    @Column(name="starts_at", nullable=false) public Instant startsAt;
    @Column(name="ends_at", nullable=false) public Instant endsAt;
    @Column(name="employee_id") public UUID employeeId;
    @Column(name="selected_at", nullable=false) public Instant selectedAt;
    @Column(name="selected_by", nullable=false) public String selectedBy;
}
