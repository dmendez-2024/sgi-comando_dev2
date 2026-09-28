package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="operational_assignment")
public class OperationalAssignmentEntity extends BaseEntity {
    @Column(name="assignment_plan_id", nullable=false) public UUID assignmentPlanId;
    @Column(name="shift_occurrence_id", nullable=false) public UUID shiftOccurrenceId;
    @Column(name="employee_id") public UUID employeeId;
    @Column(name="actual_employee_id") public UUID actualEmployeeId;
    @Column(name="compatibility_index", precision=5, scale=2) public BigDecimal compatibilityIndex;
    @Column(name="id_score", precision=5, scale=2) public BigDecimal idScore;
    @Column(nullable=false) public String status;
    @Column(name="reassignment_reason", columnDefinition="text") public String reassignmentReason;
    @Column(name="assigned_by_username") public String assignedByUsername;
    @Column(name="assigned_at") public Instant assignedAt;
    @Column(name="actual_assigned_by_username") public String actualAssignedByUsername;
    @Column(name="actual_assigned_at") public Instant actualAssignedAt;
    @Column(name="warning_json", columnDefinition="text") public String warningJson;

    public UUID effectiveEmployeeId(){ return actualEmployeeId!=null?actualEmployeeId:employeeId; }
}
