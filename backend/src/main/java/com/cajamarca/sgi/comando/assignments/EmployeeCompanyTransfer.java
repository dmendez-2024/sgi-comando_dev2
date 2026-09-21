package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="employee_company_transfer")
public class EmployeeCompanyTransfer extends BaseEntity {
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(name="origin_company_id", nullable=false) public UUID originCompanyId;
    @Column(name="destination_company_id", nullable=false) public UUID destinationCompanyId;
    @Column(name="reason_code", nullable=false, length=40) public String reasonCode;
    @Column(name="reason_label_snapshot", nullable=false, length=160) public String reasonLabelSnapshot;
    @Column(name="observations", nullable=false, length=500) public String observations;
    @Column(nullable=false, length=40) public String status;
    @Column(name="initiated_by_username", nullable=false, length=80) public String initiatedByUsername;
    @Column(name="initiated_at", nullable=false) public Instant initiatedAt;
    @Column(name="decision_by_username", length=80) public String decisionByUsername;
    @Column(name="decision_at") public Instant decisionAt;
    @Column(name="decision_note", length=1000) public String decisionNote;
    @Column(name="effective_at") public Instant effectiveAt;
    @Column(name="completed_at") public Instant completedAt;
    @Column(name="current_shift_assignment_id") public UUID currentShiftAssignmentId;
    @Column(name="released_future_assignments", nullable=false) public int releasedFutureAssignments;
    @Column(name="rrhh_sync_status", nullable=false, length=32) public String rrhhSyncStatus;
}
